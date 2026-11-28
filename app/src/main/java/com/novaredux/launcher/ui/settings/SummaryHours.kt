package com.novaredux.launcher.ui.settings

import android.content.Context
import android.text.format.DateFormat
import com.novaredux.launcher.R
import com.novaredux.launcher.notification.SummaryRules
import java.util.Calendar

// Notification summary's delivery hours (#16) as the phone writes times:
// "8:00 AM" or "08:00".
internal object SummaryHours {

    fun label(context: Context, stored: String): String {
        val hours = SummaryRules.hours(stored)
        return if (hours.isEmpty()) {
            context.getString(R.string.option_none)
        } else {
            hours.joinToString(", ") { time(context, it) }
        }
    }

    fun time(context: Context, hour: Int): String {
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
        }
        return DateFormat.getTimeFormat(context).format(at.time)
    }
}
