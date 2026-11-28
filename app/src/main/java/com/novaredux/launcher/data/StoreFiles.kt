package com.novaredux.launcher.data

import java.io.File

// The stores' files and the crash log were named for Supernova until 28 Nov
// 2026 (docs/NAMES.md). Renamed before any store opens (the application's
// attachBaseContext), so the settings, the layout and the usage history
// carry over. A file already there under its new name is the newer one and
// stays.
object StoreFiles {
    const val SETTINGS = "novaredux_store"
    const val LAYOUT = "novaredux_layout"
    const val USAGE = "novaredux_usage"
    const val CRASH_LOG = "novaredux_crash.log"

    private val RENAMED = listOf(
        "datastore/supernova_store.preferences_pb" to "datastore/$SETTINGS.preferences_pb",
        "datastore/supernova_layout.preferences_pb" to "datastore/$LAYOUT.preferences_pb",
        "datastore/supernova_usage.preferences_pb" to "datastore/$USAGE.preferences_pb",
        "supernova_crash.log" to CRASH_LOG
    )

    fun renameOld(filesDir: File) {
        for ((old, new) in RENAMED) {
            val from = File(filesDir, old)
            val to = File(filesDir, new)
            if (from.exists() && !to.exists()) from.renameTo(to)
        }
    }
}
