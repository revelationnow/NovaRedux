package com.supernova.launcher.data

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// The home screen layout (the model document, StoreKeys.model) in a file of
// its own. In the main store, every layout edit - a drop, a resize, a
// rename - rewrote every setting with it, and every setting changed rewrote
// the layout and its inline icons; each side's readers woke for the other's
// writes too.
//
// An unreadable file is replaced rather than thrown on every read, and the
// newest automatic backup restored (StoreRecovery), as for the main store.
val Context.supernovaLayoutStore by preferencesDataStore(
    name = LayoutStore.NAME,
    corruptionHandler = ReplaceFileCorruptionHandler { failure ->
        StoreRecovery.onCorrupt(failure, LayoutStore.NAME)
        emptyPreferences()
    },
    produceMigrations = { context -> listOf(LayoutMigration { context.supernovaStore }) }
)

object LayoutStore {
    const val NAME = "supernova_layout"
}

// An install from before the split holds the layout in the main store: it
// moves across on the first read, and leaves the main store once it has
// landed. A layout this store already holds is the newer one and is kept.
internal class LayoutMigration(
    private val mainStore: () -> DataStore<Preferences>
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        StoreKeys.model in mainStore().data.first()

    override suspend fun migrate(currentData: Preferences): Preferences {
        val layout = mainStore().data.first()[StoreKeys.model] ?: return currentData
        if (StoreKeys.model in currentData) return currentData
        return currentData.toMutablePreferences().apply { this[StoreKeys.model] = layout }
    }

    override suspend fun cleanUp() {
        mainStore().edit { it.remove(StoreKeys.model) }
    }
}
