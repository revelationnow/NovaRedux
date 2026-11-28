package com.novaredux.launcher.ui

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import com.novaredux.launcher.R
import java.util.Locale

// A screen time as the phone's language writes a duration (#17):
// "2 hr, 15 min", "15 min" under an hour, and "< 1 min" under a minute,
// where a phone on the emulator showed "0 min" beside an app just opened.
object ScreenTimeText {

    private const val MS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60L

    fun duration(context: Context, ms: Long): String {
        val minutes = ms / MS_PER_MINUTE
        val hours = minutes / MINUTES_PER_HOUR
        val format = MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.SHORT)
        return when {
            hours > 0 -> format.formatMeasures(
                Measure(hours, MeasureUnit.HOUR),
                Measure(minutes % MINUTES_PER_HOUR, MeasureUnit.MINUTE)
            )
            minutes > 0 -> format.formatMeasures(Measure(minutes, MeasureUnit.MINUTE))
            else -> context.getString(
                R.string.feed_screen_time_under,
                format.formatMeasures(Measure(1, MeasureUnit.MINUTE))
            )
        }
    }
}
