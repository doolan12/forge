package com.doolan.forge.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant

data class HealthReading(val sleepHours: Double?, val restingHr: Int?)

/**
 * Reads sleep and resting heart rate that your watch or health app has already saved into Android's Health Connect.
 * Read only, and only when you tap the button in the check-in.
 */
object HealthSource {
    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class)
    )

    /** 0 = ready, otherwise a message to show. */
    fun problem(context: Context): String? = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> null
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
            "Health Connect needs an update. Update it in the Play Store and try again."
        else -> "Health Connect is not available on this phone."
    }

    suspend fun hasPermissions(context: Context): Boolean =
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

    suspend fun read(context: Context): HealthReading {
        val client = HealthConnectClient.getOrCreate(context)
        val now = Instant.now()
        val from = now.minus(Duration.ofHours(36))

        // sleep that ended in the last 36 hours, minus time spent awake in bed
        val sleeps = client.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(from, now))
        ).records
        var sleepSec = 0L
        for (s in sleeps) {
            var sec = Duration.between(s.startTime, s.endTime).seconds
            for (st in s.stages) {
                if (st.stage == SleepSessionRecord.STAGE_TYPE_AWAKE || st.stage == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED) {
                    sec -= Duration.between(st.startTime, st.endTime).seconds
                }
            }
            sleepSec += maxOf(0L, sec)
        }

        val hr = client.readRecords(
            ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.between(now.minus(Duration.ofHours(48)), now))
        ).records.maxByOrNull { it.time }

        return HealthReading(
            sleepHours = if (sleepSec > 0) Math.round(sleepSec / 360.0) / 10.0 else null,
            restingHr = hr?.beatsPerMinute?.toInt()
        )
    }
}
