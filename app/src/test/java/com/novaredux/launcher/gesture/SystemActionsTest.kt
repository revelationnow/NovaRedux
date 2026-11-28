package com.novaredux.launcher.gesture

import com.novaredux.launcher.data.LauncherPrefs
import com.novaredux.launcher.ui.PermissionPrompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Screenshot, power menu, split screen and torch (#5): offered where the
// other launcher actions are, stored by name, and only the first three
// need the accessibility service.
class SystemActionsTest {

    private val added = listOf(
        GestureAction.SCREENSHOT, GestureAction.POWER_MENU, GestureAction.SPLIT_SCREEN, GestureAction.TORCH
    )

    @Test
    fun gesturesAndHomeIconsOfferThem() {
        assertTrue(GestureDefaults.pickerActions.containsAll(added))
        assertTrue(ActionShortcut.choices.containsAll(added))
    }

    @Test
    fun aBindingKeepsThemByName() {
        added.forEach { action ->
            val stored = setOf(GestureDefaults.encode(Gesture.DOUBLE_TAP, GestureBinding(action)))
            assertEquals(action, GestureDefaults.decode(stored)[Gesture.DOUBLE_TAP]?.action)
            assertEquals(action, ActionShortcut.decode(ActionShortcut.encode(action))?.action)
        }
    }

    @Test
    fun onlyTheTorchDoesWithoutTheAccessibilityService() {
        fun bound(action: GestureAction) = LauncherPrefs(
            gestureBindings = setOf(GestureDefaults.encode(Gesture.DOUBLE_TAP, GestureBinding(action)))
        )
        added.filter { it != GestureAction.TORCH }.forEach { action ->
            assertTrue(action.name, PermissionPrompts.gesturesNeedAccessibility(bound(action)))
        }
        assertFalse(PermissionPrompts.gesturesNeedAccessibility(bound(GestureAction.TORCH)))
    }
}
