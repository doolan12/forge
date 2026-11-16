package com.doolan.forge.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object Calc {
    /** Epley estimated one-rep max. */
    fun e1rm(weight: Double, reps: Int): Double = when {
        weight <= 0.0 || reps <= 0 -> 0.0
        reps == 1 -> weight
        else -> weight * (1.0 + reps / 30.0)
    }

    fun kg(d: Double): String =
        if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.US, "%.1f", d)

    fun today(): Long = LocalDate.now().toEpochDay()

    fun weekStart(epochDay: Long): Long {
        val d = LocalDate.ofEpochDay(epochDay)
        return d.minusDays((d.dayOfWeek.value - 1).toLong()).toEpochDay()
    }

    fun dayName(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    fun dayNameFull(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    fun dayNumber(epochDay: Long): Int = LocalDate.ofEpochDay(epochDay).dayOfMonth

    fun dateLabel(epochDay: Long): String {
        val d = LocalDate.ofEpochDay(epochDay)
        val m = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        return "${d.dayOfMonth} $m"
    }

    fun weekdayShort(n: Int): String =
        DayOfWeek.of(n).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    fun paceLabel(distanceKm: Double, durationSec: Int): String {
        if (distanceKm <= 0.0 || durationSec <= 0) return "-"
        val secPerKm = (durationSec / distanceKm).toInt()
        return "%d:%02d /km".format(secPerKm / 60, secPerKm % 60)
    }

    fun durationLabel(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun clock(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
}
