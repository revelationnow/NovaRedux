package com.novaredux.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// The stores' and the crash log's files from before 28 Nov 2026 take their
// new names, so an update keeps the settings, the layout and the history.
class StoreFilesTest {

    @get:Rule
    val dir = TemporaryFolder()

    private fun write(path: String, text: String): File =
        File(dir.root, path).apply {
            parentFile?.mkdirs()
            writeText(text)
        }

    @Test
    fun oldFilesTakeTheirNewNames() {
        write("datastore/supernova_store.preferences_pb", "settings")
        write("datastore/supernova_layout.preferences_pb", "layout")
        write("datastore/supernova_usage.preferences_pb", "usage")
        write("supernova_crash.log", "crashes")
        StoreFiles.renameOld(dir.root)
        assertEquals("settings", File(dir.root, "datastore/novaredux_store.preferences_pb").readText())
        assertEquals("layout", File(dir.root, "datastore/novaredux_layout.preferences_pb").readText())
        assertEquals("usage", File(dir.root, "datastore/novaredux_usage.preferences_pb").readText())
        assertEquals("crashes", File(dir.root, StoreFiles.CRASH_LOG).readText())
        assertFalse(File(dir.root, "datastore/supernova_store.preferences_pb").exists())
    }

    // A file under its new name is the newer one: the old one is left alone.
    @Test
    fun aFileAlreadyUnderItsNewNameStays() {
        val old = write("datastore/supernova_store.preferences_pb", "older")
        write("datastore/novaredux_store.preferences_pb", "newer")
        StoreFiles.renameOld(dir.root)
        assertEquals("newer", File(dir.root, "datastore/novaredux_store.preferences_pb").readText())
        assertTrue(old.exists())
    }

    @Test
    fun aFreshInstallHasNothingToMove() {
        StoreFiles.renameOld(dir.root)
        assertFalse(File(dir.root, "datastore").exists())
    }
}
