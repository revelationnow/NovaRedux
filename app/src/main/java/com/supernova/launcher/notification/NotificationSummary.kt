package com.supernova.launcher.notification

import android.app.Notification
import android.content.Context
import android.os.UserManager
import android.service.notification.StatusBarNotification
import com.supernova.launcher.data.LauncherPreferences
import com.supernova.launcher.data.supernovaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

// The notification listener's side of Notification summary (#16): the
// rules, kept from the settings while the listener is connected, and the
// notifications it held, so one coming back at its delivery passes even
// when it lands after the delivery's first minutes.
internal class NotificationSummary(private val context: Context) {

    @Volatile
    private var rules: SummaryRules? = null

    // Held notifications by key, to when each is due back.
    private val dueBack = ConcurrentHashMap<String, Long>()

    private val users: UserManager? = context.getSystemService(UserManager::class.java)

    fun watch(scope: CoroutineScope) {
        scope.launch {
            context.supernovaStore.data
                .map { SummaryRules.of(LauncherPreferences.decode(it)) }
                .distinctUntilChanged()
                .collect { rules = it }
        }
    }

    // How long to snooze a notification just posted; null lets it through.
    fun holdFor(sbn: StatusBarNotification): Long? {
        val current = rules ?: return null
        val now = ZonedDateTime.now()
        val app = SummaryRules.appKey(users?.getSerialNumberForUser(sbn.user) ?: 0L, sbn.packageName)
        val hold = current.holdMillis(app, factsOf(sbn), now, dueBack.remove(sbn.key))
        if (hold != null) {
            val nowMs = now.toInstant().toEpochMilli()
            // One the app took back while it waited never comes back.
            dueBack.values.removeIf { it < nowMs - FORGET_MS }
            dueBack[sbn.key] = nowMs + hold
        }
        return hold
    }

    private fun factsOf(sbn: StatusBarNotification): SummaryRules.Facts {
        val notification = sbn.notification
        return SummaryRules.Facts(
            ongoing = sbn.isOngoing,
            clearable = sbn.isClearable,
            category = notification.category,
            fullScreen = notification.fullScreenIntent != null,
            media = notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION),
            foregroundService = (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        )
    }

    private companion object {
        const val FORGET_MS = 24 * 60 * 60 * 1000L
    }
}
