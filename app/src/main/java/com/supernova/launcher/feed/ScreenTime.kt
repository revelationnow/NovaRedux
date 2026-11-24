package com.supernova.launcher.feed

// Today's screen time worked out from the phone's usage events since
// midnight (#17): each app's time in front, from one of its screens coming
// up to it going or the screen going off, and how often the phone was
// unlocked.
object ScreenTime {

    enum class Kind { RESUMED, PAUSED, SCREEN_OFF, UNLOCKED }

    data class Event(val kind: Kind, val packageName: String, val className: String, val timeMs: Long)

    data class Tally(val byApp: Map<String, Long>, val unlocks: Int) {
        val totalMs: Long get() = byApp.values.sum()

        // The apps used most, longest first.
        fun top(count: Int): List<Pair<String, Long>> =
            byApp.entries.sortedByDescending { it.value }.take(count).map { it.key to it.value }
    }

    // UsageEvents.Event's types, by number: the names Android 10 gave the
    // first two are newer than Android 9, where they had others.
    private const val ACTIVITY_RESUMED = 1
    private const val ACTIVITY_PAUSED = 2
    private const val SCREEN_NON_INTERACTIVE = 16
    private const val KEYGUARD_HIDDEN = 18

    fun kindOf(type: Int): Kind? = when (type) {
        ACTIVITY_RESUMED -> Kind.RESUMED
        ACTIVITY_PAUSED -> Kind.PAUSED
        SCREEN_NON_INTERACTIVE -> Kind.SCREEN_OFF
        KEYGUARD_HIDDEN -> Kind.UNLOCKED
        else -> null
    }

    // From start (midnight) to now. A screen whose first event today is its
    // going was in front since before midnight; one still up counts to now.
    // The apps in `exclude` (the launcher itself) are left out.
    fun tally(events: List<Event>, start: Long, now: Long, exclude: Set<String>): Tally {
        val open = HashMap<String, Long>()
        val seen = HashSet<String>()
        val byApp = HashMap<String, Long>()
        var unlocks = 0
        fun add(screen: String, from: Long, to: Long) {
            val app = screen.substringBefore('/')
            byApp[app] = (byApp[app] ?: 0L) + (to - from).coerceAtLeast(0L)
        }
        fun close(screen: String, at: Long) = open.remove(screen)?.let { add(screen, it, at) }
        for (event in events.sortedBy { it.timeMs }) {
            val screen = "${event.packageName}/${event.className}"
            val first = seen.add(screen)
            when (event.kind) {
                Kind.RESUMED -> open.putIfAbsent(screen, event.timeMs)
                Kind.PAUSED -> if (first) add(screen, start, event.timeMs) else close(screen, event.timeMs)
                Kind.SCREEN_OFF -> open.keys.toList().forEach { close(it, event.timeMs) }
                Kind.UNLOCKED -> unlocks++
            }
        }
        open.keys.toList().forEach { close(it, now) }
        return Tally(byApp.filterKeys { it !in exclude && it.isNotEmpty() }, unlocks)
    }
}
