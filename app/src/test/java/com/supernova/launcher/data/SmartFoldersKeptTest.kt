package com.supernova.launcher.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Smart Folders start off now (Supernova issue 327); an install that has
// the folders they made while on by default keeps them on.
class SmartFoldersKeptTest {

    private val made = DrawerGroup("Photos", setOf("0/p/p.Main"), type = DrawerGroupType.FOLDER, smart = true).encode()
    private val mine = DrawerGroup("Mine", setOf("0/x/x.Main")).encode()

    @Test
    fun anInstallWithSmartFoldersAndNoSwitchStaysOn() = runBlocking {
        val old = mutablePreferencesOf(Pref.drawerGroups.key to setOf(mine, made))
        assertTrue(SmartFoldersKept.shouldMigrate(old))
        assertEquals(true, SmartFoldersKept.migrate(old)[Pref.smartFolders.key])
    }

    @Test
    fun aFreshInstallStartsOff() = runBlocking {
        assertFalse(SmartFoldersKept.shouldMigrate(emptyPreferences()))
        assertFalse(SmartFoldersKept.shouldMigrate(mutablePreferencesOf(Pref.drawerGroups.key to setOf(mine))))
        assertFalse(LauncherPreferences.decode(emptyPreferences()).smartFolders)
    }

    @Test
    fun aSwitchAlreadySetIsLeftAsItIs() = runBlocking {
        val off = mutablePreferencesOf(Pref.drawerGroups.key to setOf(made), Pref.smartFolders.key to false)
        assertFalse(SmartFoldersKept.shouldMigrate(off))
    }
}
