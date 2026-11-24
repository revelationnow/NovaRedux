package com.supernova.launcher.feed

import com.supernova.launcher.feed.ScreenTime.Event
import com.supernova.launcher.feed.ScreenTime.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Today's screen time from the usage events (#17).
class ScreenTimeTest {

    private val start = 0L
    private val min = 60_000L

    private fun up(app: String, at: Long, screen: String = ".Main") = Event(Kind.RESUMED, app, screen, at * min)
    private fun down(app: String, at: Long, screen: String = ".Main") = Event(Kind.PAUSED, app, screen, at * min)

    @Test
    fun eachAppsTimeInFront() {
        val tally = ScreenTime.tally(
            listOf(
                up("chat", 10), down("chat", 25), up("maps", 30), down("maps", 35), up("chat", 40), down("chat", 45)
            ),
            start, 100 * min, emptySet()
        )
        assertEquals(mapOf("chat" to 20 * min, "maps" to 5 * min), tally.byApp)
        assertEquals(25 * min, tally.totalMs)
        assertEquals(listOf("chat" to 20 * min, "maps" to 5 * min), tally.top(3))
        assertEquals(listOf("chat" to 20 * min), tally.top(1))
    }

    @Test
    fun theScreenGoingOffEndsWhatWasInFront() {
        val tally = ScreenTime.tally(
            listOf(up("chat", 10), Event(Kind.SCREEN_OFF, "android", "", 15 * min), down("chat", 16)),
            start, 100 * min, emptySet()
        )
        assertEquals(mapOf("chat" to 5 * min), tally.byApp)
    }

    @Test
    fun anAppInFrontSinceBeforeMidnightOrStillUp() {
        val tally = ScreenTime.tally(listOf(down("news", 7), up("maps", 90)), start, 100 * min, emptySet())
        assertEquals(mapOf("news" to 7 * min, "maps" to 10 * min), tally.byApp)
    }

    @Test
    fun unlocksAreCountedAndTheLauncherLeftOut() {
        val unlock = Event(Kind.UNLOCKED, "android", "", 1 * min)
        val tally = ScreenTime.tally(
            listOf(unlock, up("launcher", 2), down("launcher", 9), unlock.copy(timeMs = 50 * min)),
            start, 100 * min, setOf("launcher")
        )
        assertEquals(2, tally.unlocks)
        assertEquals(emptyMap<String, Long>(), tally.byApp)
    }

    @Test
    fun usageEventTypesByNumber() {
        assertEquals(Kind.RESUMED, ScreenTime.kindOf(1))
        assertEquals(Kind.PAUSED, ScreenTime.kindOf(2))
        assertEquals(Kind.SCREEN_OFF, ScreenTime.kindOf(16))
        assertEquals(Kind.UNLOCKED, ScreenTime.kindOf(18))
        assertNull(ScreenTime.kindOf(23))
    }
}
