package com.supernova.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Test

// Calendar events in search (#7): which occurrences show, in what order.
class EventRankingTest {

    private val now = 1_000_000L
    private val hour = 3_600_000L

    private fun event(id: Long, begin: Long, end: Long = begin + hour) =
        SearchResult.Event(id, "Event $id", begin, end, allDay = false, location = null, color = null)

    @Test
    fun upcomingComeFirstSoonestFirstThenThePastLatestFirst() {
        val found = listOf(
            event(1, now - 48 * hour), event(2, now + 5 * hour), event(3, now - 2 * hour), event(4, now + hour)
        )
        assertEquals(listOf(4L, 2L, 3L, 1L), EventRanking.nearest(found, now, 10).map { it.eventId })
    }

    @Test
    fun anEventUnderWayCountsAsUpcoming() {
        val found = listOf(event(1, now - 3 * hour), event(2, now - hour, end = now + hour))
        assertEquals(listOf(2L, 1L), EventRanking.nearest(found, now, 10).map { it.eventId })
    }

    @Test
    fun aRecurringEventShowsOnceAtItsNextOccurrence() {
        val found = listOf(event(7, now - 24 * hour), event(7, now + 24 * hour), event(7, now + 48 * hour))
        val shown = EventRanking.nearest(found, now, 10)
        assertEquals(1, shown.size)
        assertEquals(now + 24 * hour, shown.single().begin)
    }

    @Test
    fun onlyAFewShow() {
        val found = (1L..9L).map { event(it, now + it * hour) }
        assertEquals(listOf(1L, 2L, 3L, 4L), EventRanking.nearest(found, now, 4).map { it.eventId })
    }

    @Test
    fun theQuerysOwnWildcardsAreTakenLiterally() {
        assertEquals("50\\% off\\_now\\\\", EventRanking.escapeLike("50% off_now\\"))
    }
}
