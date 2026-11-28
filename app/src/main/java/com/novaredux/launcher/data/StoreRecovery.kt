package com.novaredux.launcher.data

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.datastore.core.CorruptionException
import com.novaredux.launcher.Diagnostics
import com.novaredux.launcher.runLogged
import com.novaredux.launcher.widget.NovaReduxWidgetHost
import java.io.File

// A settings store that can't be read (a write cut short, a bad disk) used to
// throw on every read, so the launcher crashed on every start. The store is
// replaced with an empty one instead: its file is kept beside it for a report,
// and the newest automatic backup is restored before anything draws.
object StoreRecovery {
    @Volatile
    private var filesDir: File? = null

    @Volatile
    var corrupted = false
        private set

    // From the application's onCreate, before the store is first read.
    fun install(context: Context) {
        filesDir = context.filesDir
    }

    // DataStore's corruption handler, for the settings store and the layout
    // store (`name`): the unreadable file is about to be replaced.
    fun onCorrupt(failure: CorruptionException, name: String) {
        corrupted = true
        Diagnostics.caught("read $name", failure)
        runLogged("keep unreadable $name") {
            val dir = File(filesDir ?: return@runLogged, "datastore")
            File(dir, "$name.preferences_pb").copyTo(File(dir, "$name.unreadable"), overwrite = true)
        }
    }

    // After a corrupt store was replaced: the newest automatic backup back in
    // (layout and settings), and the first-run wizard kept away. True when a
    // backup was restored.
    suspend fun recover(
        context: Context,
        repository: LauncherRepository,
        preferences: LauncherPreferences,
        catalog: AppCatalog
    ): Boolean {
        val newest = repository.autoBackups().firstOrNull()?.takeIf { corrupted } ?: return false
        corrupted = false
        return runLogged("restore settings after an unreadable store") {
            val manager = AppWidgetManager.getInstance(context)
            val bound = NovaReduxWidgetHost(context, NovaReduxWidgetHost.HOST_ID).appWidgetIds.toSet()
            repository.importBackup(
                newest.readText(), preferences.snapshot(), catalog.actions::isInstalled,
                widgetBound = { id, provider ->
                    id in bound && manager.getAppWidgetInfo(id)?.provider?.flattenToString() == provider
                },
                snapshotFirst = false
            )
            preferences.markOnboardingComplete()
            Diagnostics.note("settings store unreadable", "Restored ${newest.name}")
        }.isSuccess
    }
}
