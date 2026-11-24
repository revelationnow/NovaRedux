package com.supernova.launcher.ui

import com.supernova.launcher.data.LauncherPrefs
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Lock hidden apps (#4): when hidden apps may show.
class HiddenAppsLockTest {

    private val locked = LauncherPrefs(hiddenAppsLock = true)

    @After
    fun relock() = HiddenAppsLock.relock()

    @Test
    fun withTheSwitchOffHiddenAppsAlwaysShow() {
        assertTrue(HiddenAppsLock.isOpen(LauncherPrefs()))
    }

    @Test
    fun withTheSwitchOnTheyWaitForAnUnlock() {
        assertFalse(HiddenAppsLock.isOpen(locked))
        HiddenAppsLock.unlock()
        assertTrue(HiddenAppsLock.isOpen(locked))
    }

    @Test
    fun leavingTheScreenLocksThemAgain() {
        HiddenAppsLock.unlock()
        HiddenAppsLock.relock()
        assertFalse(HiddenAppsLock.isOpen(locked))
    }

    @Test
    fun theSwitchStartsOff() {
        assertFalse(LauncherPrefs().hiddenAppsLock)
    }
}
