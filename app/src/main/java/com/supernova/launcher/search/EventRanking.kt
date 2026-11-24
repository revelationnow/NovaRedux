package com.supernova.launcher.search

// Which calendar events search shows (#7): each event once, at its
// occurrence nearest to now. Upcoming and ongoing ones come first, soonest
// first, then past ones, latest first.
object EventRanking {

    fun nearest(found: List<SearchResult.Event>, now: Long, max: Int): List<SearchResult.Event> {
        val (upcoming, past) = found.partition { it.end >= now }
        return (upcoming.sortedBy { it.begin } + past.sortedByDescending { it.begin })
            .distinctBy { it.eventId }
            .take(max)
    }

    // The query's own %, _ and \ taken literally in a LIKE pattern.
    fun escapeLike(text: String): String =
        text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
