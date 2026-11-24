package com.supernova.launcher.ui

import com.supernova.launcher.data.LauncherPrefs
import com.supernova.launcher.onboarding.OnboardingChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

// A font file for labels (#6): how its name and arrival are kept, and that
// the rest of the app takes the new label font.
class FontFilesTest {

    @Test
    fun theStampKeepsTheFileName() {
        assertEquals("Inter Display.ttf", FontFiles.nameOf(FontFiles.stamp("Inter Display.ttf", 1L)))
        assertEquals("a|b.otf", FontFiles.nameOf(FontFiles.stamp("a|b.otf", 2L)))
    }

    @Test
    fun theSameFileAgainIsANewStamp() {
        assertNotEquals(FontFiles.stamp("Inter.ttf", 1L), FontFiles.stamp("Inter.ttf", 2L))
    }

    @Test
    fun theFontIsStoredByName() {
        assertEquals(LabelFont.FILE, LabelFont.fromName("FILE"))
    }

    @Test
    fun noOnboardingTileStandsForAFile() {
        assertNull(OnboardingChoices.Names.of(LauncherPrefs(labelFont = LabelFont.FILE)))
    }
}
