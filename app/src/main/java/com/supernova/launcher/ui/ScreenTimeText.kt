package com.supernova.launcher.ui

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import java.util.Locale

// A screen time as the phone's language writes a duration (#17):
// "2 hr, 15 min", or "15 min" under an hour.
object ScreenTimeText {

    private const val MS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60L

    fun duration(ms: Long): String {
        val minutes = ms / MS_PER_MINUTE
        val hours = minutes / MINUTES_PER_HOUR
        val format = MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.SHORT)
        return if (hours > 0) {
            format.formatMeasures(
                Measure(hours, MeasureUnit.HOUR),
                Measure(minutes % MINUTES_PER_HOUR, MeasureUnit.MINUTE)
            )
        } else {
            format.formatMeasures(Measure(minutes, MeasureUnit.MINUTE))
        }
    }
}
