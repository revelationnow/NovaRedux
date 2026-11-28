package com.novaredux.launcher.data

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences

// Smart Folders went from on to off by default. An install that never set
// the switch but has the folders it made while on stays on: its drawer stays
// as it was, and the switch says so. A fresh install has no such folders and
// starts off.
internal object SmartFoldersKept : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        Pref.smartFolders.key !in currentData && madeSmartFolders(currentData[Pref.drawerGroups.key])

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply { this[Pref.smartFolders.key] = true }

    override suspend fun cleanUp() = Unit

    // Folders Smart Folders made, marked as such among the drawer groups.
    fun madeSmartFolders(groups: Set<String>?): Boolean =
        groups.orEmpty().any { DrawerGroup.decode(it)?.smart == true }
}
