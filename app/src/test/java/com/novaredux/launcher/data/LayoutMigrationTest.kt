package com.novaredux.launcher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// The layout used to live in the main store, with the settings. An upgrade
// must carry it into the layout store, take it out of the main one, and
// leave the settings there as they were.
class LayoutMigrationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @After
    fun tearDown() = scope.cancel()

    private fun store(name: String, migration: LayoutMigration? = null): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOfNotNull(migration),
            scope = scope,
            produceFile = { folder.root.resolve("$name.preferences_pb") }
        )

    @Test
    fun `the layout moves out of the main store and the settings stay`() = runBlocking {
        val main = store("main")
        main.edit {
            it[StoreKeys.model] = """{"version":9,"items":[]}"""
            it[StoreKeys.onboardingDone] = true
            it[Pref.gridRows.key] = 6
        }
        val layout = store("layout", LayoutMigration { main })

        assertEquals("""{"version":9,"items":[]}""", layout.data.first()[StoreKeys.model])
        val left = main.data.first()
        assertFalse("the layout is still in the main store", StoreKeys.model in left)
        assertEquals(true, left[StoreKeys.onboardingDone])
        assertEquals(6, left[Pref.gridRows.key])
    }

    @Test
    fun `a layout already moved is the newer one`() = runBlocking {
        val main = store("main")
        main.edit { it[StoreKeys.model] = "old" }
        store("seed").edit { it[StoreKeys.model] = "new" }
        // The layout store's file, written before by a build with the split.
        folder.root.resolve("seed.preferences_pb").copyTo(folder.root.resolve("layout.preferences_pb"))
        val layout = store("layout", LayoutMigration { main })

        assertEquals("new", layout.data.first()[StoreKeys.model])
        assertNull(main.data.first()[StoreKeys.model])
    }

    @Test
    fun `a fresh install has nothing to move`() = runBlocking {
        val main = store("main")
        main.edit { it[StoreKeys.onboardingDone] = false }
        val layout = store("layout", LayoutMigration { main })

        assertNull(layout.data.first()[StoreKeys.model])
        assertEquals(false, main.data.first()[StoreKeys.onboardingDone])
    }
}
