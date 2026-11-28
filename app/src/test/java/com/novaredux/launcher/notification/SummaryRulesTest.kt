package com.novaredux.launcher.notification

import com.novaredux.launcher.data.LauncherPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime

// Notification summary (#16): what waits, and until when.
class SummaryRulesTest {

    private val rules = SummaryRules(setOf("0/com.shop"), listOf(8, 12, 18))
    private val message = SummaryRules.Facts(
        ongoing = false, clearable = true, category = "msg", fullScreen = false, media = false,
        foregroundService = false
    )

    private fun at(hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 11, 24, hour, minute, 0, 0, ZoneOffset.UTC)

    private fun ms(hours: Long, minutes: Long = 0): Long = Duration.ofHours(hours).plusMinutes(minutes).toMillis()

    @Test
    fun offUntilOnWithAppsAndHours() {
        val on = LauncherPrefs(notificationSummary = true, summaryApps = setOf("0/com.shop"), summaryHours = "9,17")
        assertEquals(SummaryRules(setOf("0/com.shop"), listOf(9, 17)), SummaryRules.of(on))
        assertNull(SummaryRules.of(on.copy(notificationSummary = false)))
        assertNull(SummaryRules.of(on.copy(summaryApps = emptySet())))
        assertNull(SummaryRules.of(on.copy(summaryHours = "")))
        assertNull(SummaryRules.of(LauncherPrefs()))
    }

    @Test
    fun hoursReadInOrderOnceEach() {
        assertEquals(listOf(8, 18), SummaryRules.hours("18, 8,8,25,x,-1"))
        assertEquals("8,18", SummaryRules.encode(listOf(18, 8, 8, 30)))
        assertEquals(emptyList<Int>(), SummaryRules.hours(""))
    }

    @Test
    fun callsAlarmsMediaAndOngoingAreNeverHeld() {
        assertTrue(message.holdable)
        assertFalse(message.copy(ongoing = true).holdable)
        assertFalse(message.copy(clearable = false).holdable)
        assertFalse(message.copy(category = "call").holdable)
        assertFalse(message.copy(category = "missed_call").holdable)
        assertFalse(message.copy(category = "alarm").holdable)
        assertFalse(message.copy(media = true).holdable)
        assertFalse(message.copy(fullScreen = true).holdable)
        assertFalse(message.copy(foregroundService = true).holdable)
    }

    @Test
    fun heldUntilTheNextDeliveryHour() {
        assertEquals(ms(2, 30), rules.holdMillis("0/com.shop", message, at(9, 30), null))
        assertEquals(ms(6), rules.untilNext(at(12))?.toMillis())
        // After the last one, the first tomorrow.
        assertEquals(ms(13), rules.untilNext(at(19))?.toMillis())
        assertNull(SummaryRules(setOf("0/com.shop"), emptyList()).untilNext(at(9)))
    }

    @Test
    fun otherAppsPass() {
        assertNull(rules.holdMillis("0/com.chat", message, at(9, 30), null))
        assertNull(rules.holdMillis("10/com.shop", message, at(9, 30), null))
    }

    @Test
    fun aDeliveryLetsEverythingThrough() {
        assertTrue(rules.delivering(at(12, 5)))
        assertFalse(rules.delivering(at(12, 10)))
        assertFalse(rules.delivering(at(13, 5)))
        assertNull(rules.holdMillis("0/com.shop", message, at(12, 3), null))
    }

    @Test
    fun aHeldNotificationComingBackLatePasses() {
        val due = at(12).toInstant().toEpochMilli()
        assertNull(rules.holdMillis("0/com.shop", message, at(12, 40), due))
        // Posted again before it was due: held again.
        assertEquals(ms(2), rules.holdMillis("0/com.shop", message, at(10), due))
    }
}
