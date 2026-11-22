package com.supernova.launcher

import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager
import android.util.Base64
import android.widget.EditText
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.supernova.launcher.data.Pref
import com.supernova.launcher.data.supernovaStore
import com.supernova.launcher.onboarding.DefaultLayoutBuilder
import com.supernova.launcher.ui.DesktopSearchBarConfig
import com.supernova.launcher.ui.SearchBarConfig
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Not a check of behaviour: the Screens workflow runs this on an emulator to
// photograph the launcher, so a change to how it looks can be seen without a
// phone. It sets the app up the way a first run leaves it (the default layout,
// the fresh search bar, onboarding done), applies the settings the workflow
// passes, and saves PNGs to the app's external files dir, under screens/,
// which the workflow pulls.
//
// Settings come as instrumentation arguments, typed by their prefix:
//   pref.b.<key> true · pref.i.<key> 5 · pref.f.<key> 1.5 · pref.s.<key> TEXT
//   pref.s64.<key> <base64 of the text>, for values with spaces or quotes.
// `shots` lists the screens, comma-separated: home, drawer, search, settings,
// and settings.searchbar (the search bar's settings page). The default is the
// first four. `query` is typed into search before its shot, with time for web
// suggestions to come back (through NovaRedux Connect, when installed).
// `hide_own true`, for store screenshots: this debug build's own entries
// (NovaRedux Debug, Debug Settings) are off the home screen and the drawer.
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext
    private val appId = context.packageName
    private val args = InstrumentationRegistry.getArguments()

    @Test
    fun photograph() {
        val dir = File(context.getExternalFilesDir(null), "screens").apply {
            deleteRecursively()
            mkdirs()
        }
        prepare()
        val shots = args.getString("shots")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: DEFAULT_SHOTS
        showHome()
        shots.forEachIndexed { index, shot ->
            if (open(shot)) {
                settle()
                device.takeScreenshot(File(dir, String.format(Locale.ROOT, "%02d-%s.png", index + 1, shot)))
            }
            showHome()
        }
    }

    private fun prepare() = runBlocking {
        val graph = context.appGraph
        if (graph.repository.modelFlow.first().items.isEmpty() && graph.preferences.claimDefaultLayoutSeed()) {
            val prefs = graph.preferences.snapshot()
            val items = DefaultLayoutBuilder.buildItems(
                context, graph.catalog, prefs.gridCols, prefs.gridRows, prefs.dockCount
            )
            graph.repository.seedDefaultLayout(items)
            graph.preferences.set(
                Pref.searchBarConfig,
                SearchBarConfig(desktop = DesktopSearchBarConfig.FRESH).encode()
            )
        }
        graph.preferences.markOnboardingDone()
        val hidden = if (args.getString("hide_own") == "true") hideOwnEntries() else emptySet()
        context.supernovaStore.edit { store ->
            if (hidden.isNotEmpty()) {
                val key = stringSetPreferencesKey(Pref.hiddenApps.key.name)
                store[key] = store[key].orEmpty() + hidden
            }
            // Coming back home between shots is a Home press while home is in
            // front, which would open the drawer; an argument can set it back.
            store[stringPreferencesKey(Pref.homeButtonAction.key.name)] = "NONE"
            args.keySet().filter { it.startsWith(PREF) }.forEach { name -> applyArgument(store, name) }
        }
    }

    // This build's own icons come off the home screen; the drawer keys of its
    // activities come back, for the drawer to hide them.
    private suspend fun hideOwnEntries(): Set<String> {
        val user = Process.myUserHandle()
        val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(user)
        context.appGraph.repository.removeItemsForPackage(appId, serial)
        return context.getSystemService(LauncherApps::class.java).getActivityList(appId, user)
            .mapTo(mutableSetOf()) { "$serial/$appId/${it.componentName.className}" }
    }

    private fun applyArgument(store: MutablePreferences, name: String) {
        val raw = args.getString(name) ?: return
        val type = name.removePrefix(PREF).substringBefore('.')
        val key = name.removePrefix(PREF).substringAfter('.')
        when (type) {
            "b" -> store[booleanPreferencesKey(key)] = raw.toBoolean()
            "i" -> store[intPreferencesKey(key)] = raw.toInt()
            "f" -> store[floatPreferencesKey(key)] = raw.toFloat()
            "s" -> store[stringPreferencesKey(key)] = raw
            "s64" -> store[stringPreferencesKey(key)] = String(Base64.decode(raw, Base64.DEFAULT))
        }
    }

    // False for a screen it doesn't know or couldn't reach, which it skips.
    private fun open(shot: String): Boolean = when (shot) {
        "home" -> true
        "drawer" -> openDrawer()
        "search" -> {
            val bar = device.findObject(By.res(appId, "desktopSearchBar"))
            bar?.click()
            (bar != null && shows("drawerSheet")).also { opened -> if (opened) typeQuery() }
        }
        "settings" -> startSettings(Intent())
        "settings.searchbar" -> startSettings(Intent().putExtra(SettingsActivity.EXTRA_OPEN_SEARCH_BAR, true))
        else -> false
    }

    // Into whichever search field has the focus.
    private fun typeQuery() {
        val query = args.getString("query") ?: return
        val field = device.wait(Until.findObject(By.focused(true).clazz(EditText::class.java)), WAIT_MS) ?: return
        field.text = query
        Thread.sleep(QUERY_WAIT_MS)
    }

    // A slow swipe up from the lower page; failing that, the search bar's
    // tap opens the drawer too, and Back puts the keyboard away.
    private fun openDrawer(): Boolean {
        val x = device.displayWidth / 2
        device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, SLOW_SWIPE_STEPS)
        if (shows("drawerSheet")) return true
        device.findObject(By.res(appId, "desktopSearchBar"))?.click() ?: return false
        val opened = shows("drawerSheet")
        if (opened) {
            device.pressBack()
            settle()
        }
        return opened && device.hasObject(By.res(appId, "drawerSheet"))
    }

    private fun startSettings(extras: Intent): Boolean {
        context.startActivity(
            Intent(extras).setClass(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return shows("settingsCoordinator")
    }

    // By class: this build may not be the default home app.
    private fun showHome() {
        context.startActivity(
            Intent(context, LauncherActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        device.wait(Until.hasObject(By.res(appId, "workspacePager")), WAIT_MS)
        // Whatever a shot left open: the drawer, search, a sheet.
        repeat(BACK_PRESSES) {
            if (device.hasObject(By.res(appId, "drawerSheet"))) device.pressBack()
        }
        assertTrue("the home screen didn't draw", shows("workspacePager"))
        settle()
    }

    private fun shows(viewId: String): Boolean = device.wait(Until.hasObject(By.res(appId, viewId)), WAIT_MS)

    private fun settle() {
        device.waitForIdle()
        Thread.sleep(SETTLE_MS)
    }

    private companion object {
        const val PREF = "pref."
        val DEFAULT_SHOTS = listOf("home", "drawer", "search", "settings")
        const val WAIT_MS = 8_000L
        const val SETTLE_MS = 1_500L
        const val QUERY_WAIT_MS = 4_000L
        const val SLOW_SWIPE_STEPS = 60
        const val BACK_PRESSES = 2
    }
}
