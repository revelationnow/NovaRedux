package com.novaredux.launcher.data

import com.novaredux.launcher.gesture.ActionShortcut
import com.novaredux.launcher.gesture.GestureAction
import com.novaredux.launcher.gesture.GestureDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

// An icon swipe that shows a widget (#14): its target, the ids the orphan
// sweep keeps, and a restore's stale ids.
class ShutterWidgetsTest {

    private val gmail = "com.google.android.gm/com.google.android.gm.widget.GmailWidgetProvider"
    private val app = LauncherItem(
        id = "a", itemType = ItemType.APP, packageName = "com.google.android.gm", label = "Gmail",
        swipeAction = ShutterWidgets.target(12, gmail), swipeLabel = "Widget: Inbox"
    )

    @Test
    fun aTargetReadsBackItsWidgetAndProvider() {
        assertEquals(ShutterWidgets.Shutter(12, gmail), ShutterWidgets.of(app.swipeAction))
        assertEquals(GestureAction.SHOW_WIDGET, ActionShortcut.decode(app.swipeAction)?.action)
        assertEquals(ShutterWidgets.Shutter(null, gmail), ShutterWidgets.of(ShutterWidgets.target(null, gmail)))
    }

    @Test
    fun otherSwipeTargetsShowNoWidget() {
        assertNull(ShutterWidgets.of(null))
        assertNull(ShutterWidgets.of(ActionShortcut.encode(GestureAction.OPEN_DRAWER)))
        assertNull(ShutterWidgets.of(ActionShortcut.encode(GestureAction.JUMP_TO_PAGE, "2")))
        assertNull(ShutterWidgets.of("com.example/.Main@0"))
    }

    @Test
    fun bothSwipesWidgetsAreKeptFromTheSweep() {
        val both = app.copy(swipeActionDown = ShutterWidgets.target(30, "b/.Clock"))
        assertEquals(listOf(12, 30), ShutterWidgets.ids(both))
        assertEquals(emptyList<Int>(), ShutterWidgets.ids(app.copy(swipeAction = "com.example/.Main@0")))
    }

    @Test
    fun aRestoreDropsAnIdNotBoundHereAndKeepsTheProvider() {
        val both = app.copy(swipeActionDown = ShutterWidgets.target(30, "b/.Clock"))
        val cleared = ShutterWidgets.withStaleCleared(both) { id, provider -> id == 30 && provider == "b/.Clock" }
        assertEquals(ShutterWidgets.Shutter(null, gmail), ShutterWidgets.of(cleared.swipeAction))
        assertEquals(both.swipeActionDown, cleared.swipeActionDown)
        assertEquals("Widget: Inbox", cleared.swipeLabel)
        assertEquals(1, ShutterWidgets.needingRebind(cleared))
    }

    @Test
    fun aRestoreLeavesOtherSwipesAlone() {
        val plain = app.copy(
            swipeAction = "com.example/.Main@0",
            swipeActionDown = ActionShortcut.encode(GestureAction.TORCH)
        )
        assertEquals(plain, ShutterWidgets.withStaleCleared(plain) { _, _ -> false })
        assertEquals(0, ShutterWidgets.needingRebind(plain))
    }

    @Test
    fun aRestoredLayoutCountsSwipeWidgetsToSetUpAgain() {
        val model = LauncherModel(items = listOf(app))
        val cleared = RestoredWidgets.withStaleCleared(model) { _, _ -> false }
        assertEquals(ShutterWidgets.Shutter(null, gmail), ShutterWidgets.of(cleared.items.single().swipeAction))
        assertEquals(1, RestoredWidgets.needingRebind(cleared))
    }

    @Test
    fun onlyAnIconsSwipeOffersAWidget() {
        assertFalse(GestureAction.SHOW_WIDGET in GestureDefaults.pickerActions)
        assertFalse(GestureAction.SHOW_WIDGET in ActionShortcut.choices)
        assert(GestureAction.SHOW_WIDGET.needsPayload)
    }
}
