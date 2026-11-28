package com.novaredux.launcher.feed

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.novaredux.launcher.runLogged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

// The screen time card (#17): today's time in apps, the apps used most and
// the unlocks, read from Android's usage stats on the phone. Reading them
// takes usage access, a system setting; until it's on, the card offers it,
// and turns into the day's numbers the moment it is.
class ScreenTimeCardSource(
    private val context: Context,
    private val scope: CoroutineScope
) : CardSource<ScreenTimeCardSource.ScreenTimeState> {

    sealed class ScreenTimeState {
        object NeedsAccess : ScreenTimeState()
        data class Today(val totalMs: Long, val apps: List<Pair<String, Long>>, val unlocks: Int) : ScreenTimeState()
    }

    override val state = MutableStateFlow<ScreenTimeState?>(null)

    private var shown = false
    private var readJob: Job? = null
    private val appOps: AppOpsManager? = context.getSystemService(AppOpsManager::class.java)

    // Usage access switched in Settings while the feed is open.
    private val accessChanged = AppOpsManager.OnOpChangedListener { _, packageName ->
        if (packageName == context.packageName) scope.launch { refresh() }
    }

    override fun onShown() {
        if (shown) return
        shown = true
        runCatching {
            appOps?.startWatchingMode(AppOpsManager.OPSTR_GET_USAGE_STATS, context.packageName, accessChanged)
        }
        refresh()
    }

    override fun onHidden() {
        if (!shown) return
        shown = false
        runCatching { appOps?.stopWatchingMode(accessChanged) }
        readJob?.cancel()
        readJob = null
        state.value = null
    }

    fun refresh() {
        if (!shown) return
        if (!granted()) {
            state.value = ScreenTimeState.NeedsAccess
            return
        }
        readJob?.cancel()
        readJob = scope.launch {
            val today = withContext(Dispatchers.IO) { readToday() }
            if (shown) state.value = today
        }
    }

    private fun granted(): Boolean {
        val ops = appOps ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        // Left at its default, the permission itself decides.
        val permitted = context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
            PackageManager.PERMISSION_GRANTED
        return mode == AppOpsManager.MODE_ALLOWED || (mode == AppOpsManager.MODE_DEFAULT && permitted)
    }

    private fun readToday(): ScreenTimeState.Today {
        val now = System.currentTimeMillis()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val events = ArrayList<ScreenTime.Event>()
        runLogged("read today's usage") {
            val usage = context.getSystemService(UsageStatsManager::class.java)
            val stream = usage?.queryEvents(start, now)
            val event = UsageEvents.Event()
            while (stream != null && stream.hasNextEvent()) {
                stream.getNextEvent(event)
                val kind = ScreenTime.kindOf(event.eventType) ?: continue
                val app = event.packageName.orEmpty()
                events += ScreenTime.Event(kind, app, event.className.orEmpty(), event.timeStamp)
            }
        }
        val tally = ScreenTime.tally(events, start, now, exclude = setOf(context.packageName))
        return ScreenTimeState.Today(tally.totalMs, tally.top(TOP_APPS), tally.unlocks)
    }

    companion object {
        // The apps used most, longest first: more than the card names, as
        // some (with no icon in the launcher) are skipped.
        const val TOP_APPS = 8
    }
}
