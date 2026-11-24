package com.supernova.launcher.notification

import android.app.Notification
import com.supernova.launcher.data.LauncherPrefs
import java.time.Duration
import java.time.ZonedDateTime

// Notification summary (#16), as Niagara has it: notifications from chosen
// apps wait for the next of a few delivery hours a day. A held notification
// is snoozed through Android's own snooze, which brings it back at that
// hour by itself, with or without NovaRedux running.
data class SummaryRules(val apps: Set<String>, val hours: List<Int>) {

    // What decides whether a notification may wait.
    data class Facts(
        val ongoing: Boolean,
        val clearable: Boolean,
        val category: String?,
        val fullScreen: Boolean,
        val media: Boolean,
        val foregroundService: Boolean
    ) {
        // Calls, alarms, media and anything ongoing are never held.
        val holdable: Boolean
            get() = !ongoing && clearable && !fullScreen && !media && !foregroundService && category !in NEVER_HELD
    }

    // How long a notification posted now waits; null lets it through. One
    // posted in a delivery's first minutes passes, as does one coming back
    // from being held (dueBack: when it was due).
    fun holdMillis(app: String, facts: Facts, now: ZonedDateTime, dueBack: Long?): Long? {
        val back = dueBack != null && now.toInstant().toEpochMilli() >= dueBack - EARLY_MS
        val held = app in apps && facts.holdable && !back && !delivering(now)
        return if (held) untilNext(now)?.toMillis() else null
    }

    fun delivering(now: ZonedDateTime): Boolean = now.hour in hours && now.minute < WINDOW_MINUTES

    // Until the next delivery hour after now; null with none set.
    fun untilNext(now: ZonedDateTime): Duration? =
        (0L..1L).asSequence()
            .map { now.toLocalDate().plusDays(it) }
            .flatMap { day -> hours.asSequence().map { day.atTime(it, 0).atZone(now.zone) } }
            .firstOrNull { it.isAfter(now) }
            ?.let { Duration.between(now, it) }

    companion object {
        const val HOURS_PER_DAY = 24

        // How long a delivery lets everything through: the held notifications
        // coming back at its hour may land a little late.
        private const val WINDOW_MINUTES = 10

        // A notification back a little early from its snooze is still back.
        private const val EARLY_MS = 60_000L

        // Never held, whichever app posts them. Missed calls and stopwatches
        // by name: their constants are newer than Android 9.
        private val NEVER_HELD = setOf(
            Notification.CATEGORY_CALL, "missed_call", Notification.CATEGORY_ALARM,
            Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_NAVIGATION, "stopwatch",
            Notification.CATEGORY_PROGRESS, Notification.CATEGORY_SERVICE, Notification.CATEGORY_SYSTEM
        )

        // Off until it is on, with apps and hours to hold for.
        fun of(prefs: LauncherPrefs): SummaryRules? {
            val hours = hours(prefs.summaryHours)
            val on = prefs.notificationSummary && prefs.summaryApps.isNotEmpty() && hours.isNotEmpty()
            return if (on) SummaryRules(prefs.summaryApps, hours) else null
        }

        // How the setting holds an app: its profile and package.
        fun appKey(userSerial: Long, packageName: String): String = "$userSerial/$packageName"

        // The stored hours ("8,12,18"), in order, each once.
        fun hours(stored: String): List<Int> =
            stored.split(',').mapNotNull { it.trim().toIntOrNull() }.let(::encodable)

        fun encode(hours: Collection<Int>): String = encodable(hours).joinToString(",")

        private fun encodable(hours: Collection<Int>): List<Int> =
            hours.filter { it in 0 until HOURS_PER_DAY }.distinct().sorted()
    }
}
