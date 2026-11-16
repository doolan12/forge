package com.doolan.forge.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.doolan.forge.ForgeApp
import com.doolan.forge.MainActivity
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

data class ReminderSettings(
    val trainOn: Boolean = false,
    val trainTime: String = "06:00",
    val checkOn: Boolean = false,
    val checkTime: String = "07:00"
)

object Reminders {
    const val CHANNEL = "forge_reminders"
    const val TRAIN = "train"
    const val CHECK = "check"

    fun parseTime(text: String): LocalTime? = try {
        val parts = text.trim().split(":")
        if (parts.size == 2) LocalTime.of(parts[0].toInt(), parts[1].toInt()) else null
    } catch (e: Exception) {
        null
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun delayMs(time: String): Long {
        val t = parseTime(time) ?: LocalTime.of(7, 0)
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(t)
        if (!target.isAfter(now.plusMinutes(1))) target = target.plusDays(1)
        return Duration.between(now, target).toMillis()
    }

    /** Sets up (or cancels) the daily reminders. Android may deliver them a few minutes late to save battery. */
    fun schedule(context: Context, s: ReminderSettings, fromWorker: Boolean) {
        val wm = WorkManager.getInstance(context)
        fun one(type: String, on: Boolean, time: String) {
            val name = "forge_reminder_$type"
            if (!on) {
                wm.cancelUniqueWork(name)
                return
            }
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delayMs(time), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("type" to type))
                .build()
            wm.enqueueUniqueWork(name, if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, req)
        }
        one(TRAIN, s.trainOn, s.trainTime)
        one(CHECK, s.checkOn, s.checkTime)
    }

    fun notify(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            // permission was removed; nothing to show
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val type = inputData.getString("type") ?: return Result.success()
        val repo = (applicationContext as ForgeApp).repo
        val settings = repo.store.reminders.first()
        val today = Calc.today()
        try {
            if (type == Reminders.TRAIN && settings.trainOn) {
                val planned = repo.db.sessions().allNow()
                    .filter { it.dateEpochDay == today && it.status == "PLANNED" && it.discipline != "REST" }
                if (planned.isNotEmpty()) {
                    val text = planned.joinToString(", ") { it.title }
                    Reminders.notify(applicationContext, 1, "Training today", text)
                }
            } else if (type == Reminders.CHECK && settings.checkOn) {
                val done = repo.db.checkIns().allNow().any { it.dateEpochDay == today }
                if (!done) Reminders.notify(applicationContext, 2, "Daily check-in", "Log how you slept and feel. It takes 20 seconds.")
            }
        } finally {
            Reminders.schedule(applicationContext, settings, true)
        }
        return Result.success()
    }
}
