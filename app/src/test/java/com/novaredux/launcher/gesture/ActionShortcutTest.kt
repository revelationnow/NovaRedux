package com.novaredux.launcher.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionShortcutTest {

    @Test
    fun encodeDecodeRoundTripsAPayloadlessAction() {
        val uri = ActionShortcut.encode(GestureAction.OPEN_DRAWER)
        assertEquals("novaredux://action/OPEN_DRAWER", uri)
        val decoded = ActionShortcut.decode(uri)
        assertEquals(GestureAction.OPEN_DRAWER, decoded?.action)
        assertNull(decoded?.payload)
    }

    @Test
    fun encodeDecodeCarriesAPayload() {
        val uri = ActionShortcut.encode(GestureAction.JUMP_TO_PAGE, "2")
        val decoded = ActionShortcut.decode(uri)
        assertEquals(GestureAction.JUMP_TO_PAGE, decoded?.action)
        assertEquals("2", decoded?.payload)
    }

    @Test
    fun decodeReturnsNullForForeignUris() {
        assertNull(ActionShortcut.decode(null))
        assertNull(ActionShortcut.decode("https://example.com"))
        assertNull(ActionShortcut.decode("intent:#Intent;action=android.intent.action.VIEW;end"))
    }

    @Test
    fun payloadActionWithoutPayloadDecodesToNull() {
        // A page-jump with no page index is meaningless; decode rejects it so
        // launchItem falls through rather than jumping nowhere.
        assertNull(ActionShortcut.decode("novaredux://action/JUMP_TO_PAGE"))
    }

    // The links' name before 28 Nov 2026, and Supernova's.
    @Test
    fun oldLinksStillDecode() {
        assertEquals(GestureAction.OPEN_DRAWER, ActionShortcut.decode("supernova://action/OPEN_DRAWER")?.action)
        val page = ActionShortcut.decode("supernova://action/JUMP_TO_PAGE?p=2")
        assertEquals(GestureAction.JUMP_TO_PAGE, page?.action)
        assertEquals("2", page?.payload)
    }

    @Test
    fun unknownActionNameDecodesToNoneBinding() {
        val decoded = ActionShortcut.decode("novaredux://action/NOT_A_REAL_ACTION")
        assertEquals(GestureAction.NONE, decoded?.action)
    }

    @Test
    fun privateSpaceIsOfferedOnlyWithAPrivateSpace() {
        val withSpace = ActionShortcut.offered(ActionShortcut.choices, hasPrivateSpace = true, splitScreen = true)
        val without = ActionShortcut.offered(ActionShortcut.choices, hasPrivateSpace = false, splitScreen = true)
        assertEquals(ActionShortcut.choices, withSpace)
        assertEquals(ActionShortcut.choices - GestureAction.PRIVATE_SPACE, without)
        assert(GestureAction.PRIVATE_SPACE in GestureDefaults.pickerActions)
    }

    @Test
    fun splitScreenIsLeftOutWhereTheSystemNoLongerDoesIt() {
        val offered = ActionShortcut.offered(ActionShortcut.choices, hasPrivateSpace = true, splitScreen = false)
        assertEquals(ActionShortcut.choices - GestureAction.SPLIT_SCREEN, offered)
        assertEquals(
            GestureDefaults.pickerActions - GestureAction.SPLIT_SCREEN,
            ActionShortcut.offered(GestureDefaults.pickerActions, hasPrivateSpace = true, splitScreen = false)
        )
    }

    @Test
    fun choicesAreAllRenderableAndPayloadless() {
        // Everything offered as a standalone icon must be launchable with no
        // target; payload actions are added through their own target pickers.
        ActionShortcut.choices.forEach { action ->
            val uri = ActionShortcut.encode(action)
            assertEquals(action, ActionShortcut.decode(uri)?.action)
        }
    }
}
