package com.supernova.launcher.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// The line a label shows for its app's newest notification (#15).
class NotificationTextTest {

    @Test
    fun whoAndWhat() {
        assertEquals("Anna: See you at 7", NotificationText.line("Anna", "See you at 7"))
    }

    @Test
    fun whicheverOfTheTwoItHas() {
        assertEquals("Anna", NotificationText.line("Anna", null))
        assertEquals("Build passed", NotificationText.line(" ", "Build passed"))
    }

    @Test
    fun nothingToShow() {
        assertNull(NotificationText.line(null, null))
        assertNull(NotificationText.line("  ", "\n"))
    }

    @Test
    fun onOneLine() {
        assertEquals("Anna: Running late, sorry!", NotificationText.line(" Anna ", "Running late,\n\n sorry!"))
    }

    @Test
    fun keptShortWithoutCuttingAnEmoji() {
        val long = "a".repeat(79) + "😀" + "rest"
        val line = NotificationText.line(null, long)
        assertEquals("a".repeat(79), line)
        assertEquals(80, NotificationText.line(null, "b".repeat(200))?.length)
    }
}
