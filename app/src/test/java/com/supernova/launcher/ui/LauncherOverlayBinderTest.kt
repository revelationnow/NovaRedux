package com.supernova.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

// The overlay services read a call by its place in their interface: these
// codes are the wire format, not a choice.
class LauncherOverlayBinderTest {

    @Test
    fun overlayCallsKeepTheirPlacesInTheInterface() {
        val places = mapOf(
            LauncherOverlayBinder.START_SCROLL to 1,
            LauncherOverlayBinder.ON_SCROLL to 2,
            LauncherOverlayBinder.END_SCROLL to 3,
            LauncherOverlayBinder.WINDOW_ATTACHED to 4,
            LauncherOverlayBinder.WINDOW_DETACHED to 5,
            LauncherOverlayBinder.CLOSE_OVERLAY to 6,
            LauncherOverlayBinder.ON_PAUSE to 7,
            LauncherOverlayBinder.ON_RESUME to 8,
            LauncherOverlayBinder.OPEN_OVERLAY to 9,
            LauncherOverlayBinder.WINDOW_ATTACHED_2 to 14,
            LauncherOverlayBinder.SET_ACTIVITY_STATE to 16
        )
        places.forEach { (code, place) -> assertEquals(place, code) }
        assertEquals(1, LauncherOverlayBinder.SCROLL_CHANGED)
        assertEquals(2, LauncherOverlayBinder.STATUS_CHANGED)
    }

    @Test
    fun interfaceNamesAreTheLauncherClients() {
        assertEquals("com.google.android.libraries.launcherclient.ILauncherOverlay", LauncherOverlayBinder.OVERLAY)
        assertEquals(
            "com.google.android.libraries.launcherclient.ILauncherOverlayCallback",
            LauncherOverlayBinder.CALLBACK
        )
    }
}
