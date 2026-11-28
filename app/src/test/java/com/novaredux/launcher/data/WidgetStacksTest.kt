package com.novaredux.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Widget stacks (#10): adding, taking out, turning and restoring widgets in
// one item's cells.
class WidgetStacksTest {

    private val single = LauncherItem(
        id = "w", itemType = ItemType.WIDGET, appWidgetId = 1, widgetProvider = "a/.One", label = "One"
    )
    private val two = StackedWidget(2, "b/.Two", 0L, "Two")
    private val three = StackedWidget(3, "c/.Three", 10L, "Three")

    @Test
    fun anAddedWidgetJoinsTheStackAndShows() {
        val stacked = WidgetStacks.added(WidgetStacks.added(single, two), three)
        assertEquals(listOf(1, 2, 3), WidgetStacks.ids(stacked))
        assertEquals(2, WidgetStacks.shown(stacked))
        assertEquals(3, WidgetStacks.size(stacked))
    }

    @Test
    fun theShownWidgetActsAsTheItemsOwn() {
        val stacked = WidgetStacks.added(WidgetStacks.added(single, two), three)
        val shown = WidgetStacks.asShown(stacked, 2)
        assertEquals(3, shown.appWidgetId)
        assertEquals("c/.Three", shown.widgetProvider)
        assertEquals(10L, shown.userSerial)
        assertEquals("w", shown.id)
        assertEquals(1, WidgetStacks.asShown(stacked, 3).appWidgetId)
    }

    @Test
    fun takingOutTheItemsOwnMovesTheNextUp() {
        val stacked = WidgetStacks.added(WidgetStacks.added(single, two), three)
        val (left, freed) = WidgetStacks.removed(stacked, 0)
        assertEquals(1, freed)
        assertEquals(2, left.appWidgetId)
        assertEquals("b/.Two", left.widgetProvider)
        assertEquals(listOf(2, 3), WidgetStacks.ids(left))
        assertEquals(0, WidgetStacks.shown(left))
    }

    @Test
    fun takingOutAnotherKeepsTheRest() {
        val stacked = WidgetStacks.added(WidgetStacks.added(single, two), three)
        val (left, freed) = WidgetStacks.removed(stacked, 2)
        assertEquals(3, freed)
        assertEquals(listOf(1, 2), WidgetStacks.ids(left))
        assertEquals(1, WidgetStacks.shown(left))
    }

    @Test
    fun aLoneWidgetIsNotTakenOutOfItself() {
        val (left, freed) = WidgetStacks.removed(single, 0)
        assertEquals(single, left)
        assertNull(freed)
    }

    @Test
    fun aStoredShownOutOfRangeComesBackIntoIt() {
        val stacked = WidgetStacks.added(single, two).copy(stackShown = 7)
        assertEquals(1, WidgetStacks.shown(stacked))
    }

    @Test
    fun aRestoredMemberGetsItsNewId() {
        val restored = WidgetStacks.added(single, two.copy(appWidgetId = null))
        assertEquals(listOf(1, 9), WidgetStacks.ids(WidgetStacks.withMemberId(restored, 1, 9)))
    }

    @Test
    fun aRestoreClearsAStackedWidgetNoLongerBound() {
        val model = LauncherModel(items = listOf(WidgetStacks.added(single, two)))
        val cleared = RestoredWidgets.withStaleCleared(model) { id, _ -> id == 1 }
        assertEquals(listOf(1), WidgetStacks.ids(cleared.items.single()))
        assertEquals("b/.Two", cleared.items.single().stack.single().widgetProvider)
        assertEquals(1, RestoredWidgets.needingRebind(cleared))
    }
}
