package com.novaredux.launcher.ui.theme

import com.novaredux.launcher.data.LauncherPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightShadeTest {

    private val surface = 0xFF1A2230.toInt()

    @Test
    fun darkKeepsTheThemesOwnColour() {
        assertEquals(surface, NightShade.DARK.of(surface))
    }

    @Test
    fun darkerGoesHalfwayToBlack() {
        assertEquals(0xFF0D1118.toInt(), NightShade.DARKER.of(surface))
        // The alpha stays as it was.
        assertEquals(0x800D1118.toInt(), NightShade.DARKER.of(0x801A2230.toInt()))
    }

    @Test
    fun blackIsBlackKeepingItsAlpha() {
        assertEquals(0xFF000000.toInt(), NightShade.BLACK.of(surface))
        assertEquals(0x80000000.toInt(), NightShade.BLACK.of(0x801A2230.toInt()))
    }

    @Test
    fun nightDarkensOnlyTheChosenParts() {
        val prefs = LauncherPrefs(nightDrawer = false, nightMenus = false)
        assertTrue(NightParts.darkens(prefs, SurfaceId.SEARCH_BAR))
        assertFalse(NightParts.darkens(prefs, SurfaceId.DRAWER))
        assertFalse(NightParts.darkens(prefs, SurfaceId.DRAWER_SEARCH_BAR))
        assertTrue(NightParts.darkens(prefs, SurfaceId.FOLDER))
        assertFalse(NightParts.darkens(prefs, SurfaceId.CARDS))
        // The dock and settings always follow night mode.
        assertTrue(NightParts.darkens(prefs, SurfaceId.DOCK))
        assertTrue(NightParts.darkens(prefs, SurfaceId.SETTINGS))
    }
}
