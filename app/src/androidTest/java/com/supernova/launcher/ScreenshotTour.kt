package com.supernova.launcher

import android.content.Intent
import android.util.Base64
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
// first four.
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
        context.supernovaStore.edit { store ->
            args.keySet().filter { it.startsWith(PREF) }.forEach { name -> applyArgument(store, name) }
        }
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
        "drawer" -> {
            device.swipe(device.displayWidth / 2, device.displayHeight * 2 / 3, device.displayWidth / 2,
                device.displayHeight / 5, SWIPE_STEPS)
            shows("drawerSheet")
        }
        "search" -> {
            val bar = device.findObject(By.res(appId, "desktopSearchBar"))
            bar?.click()
            bar != null && shows("drawerSheet")
        }
        "settings" -> startSettings(Intent())
        "settings.searchbar" -> startSettings(Intent().putExtra(SettingsActivity.EXTRA_OPEN_SEARCH_BAR, true))
        else -> false
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
        assertTrue("the home screen didn't draw", shows("workspacePager"))
        if (device.hasObject(By.res(appId, "drawerSheet"))) device.pressBack()
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
        const val SWIPE_STEPS = 20
    }
}
