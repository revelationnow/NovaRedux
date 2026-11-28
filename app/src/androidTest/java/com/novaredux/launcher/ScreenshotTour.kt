package com.novaredux.launcher

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
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.novaredux.launcher.data.Pref
import com.novaredux.launcher.data.novaReduxStore
import com.novaredux.launcher.onboarding.DefaultLayoutBuilder
import com.novaredux.launcher.ui.DesktopSearchBarConfig
import com.novaredux.launcher.ui.SearchBarConfig
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
// settings.searchbar (the search bar's settings page), settings.search (the
// Search page, at its search window rows), feed (the page to the
// left of home), menu.home (the first home icon's menu), menu.drawer (the
// first drawer app's) and folder.drawer (the first drawer folder's card; with
// pref.b.disable_animations true, as the software-rendered emulator may not
// have drawn its zoom by the time of the picture). The default is the first
// four. `query` is typed into search before its shot, with time for web
// suggestions to come back (through NovaRedux Connect, when installed).
// `hide_own true`, for store screenshots: this debug build's own entries
// (NovaRedux Debug, Debug Settings) are off the home screen and the drawer.
// `a11y true` saves each screen's window dump beside its picture, for the
// workflow to list the views TalkBack couldn't name. `rotate left` turns the
// phone on its side first.
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
        // "-e locale ru": the app in another of its languages, through the
        // per-app language setting.
        args.getString("locale")?.let { device.executeShellCommand("cmd locale set-app-locales $appId --locales $it") }
        val shots = args.getString("shots")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: DEFAULT_SHOTS
        // "-e rotate left": the phone on its side, for landscape layouts and
        // a cutout along the long edge.
        if (args.getString("rotate") == "left") device.setOrientationLeft()
        showHome()
        shots.forEachIndexed { index, shot ->
            if (open(shot)) {
                settle()
                val name = String.format(Locale.ROOT, "%02d-%s", index + 1, shot)
                device.takeScreenshot(File(dir, "$name.png"))
                // For tools/ci/a11y_audit.py: what TalkBack would find here,
                // views kept out of accessibility left out.
                if (args.getString("a11y") == "true") {
                    device.setCompressedLayoutHierarchy(true)
                    device.dumpWindowHierarchy(File(dir, "$name.xml"))
                    device.setCompressedLayoutHierarchy(false)
                }
            }
            showHome()
        }
    }

    private fun prepare() = runBlocking {
        val graph = context.appGraph
        // Planted whatever the seed's claim says (FeatureTour.prepare).
        if (graph.repository.modelFlow.first().items.isEmpty()) {
            graph.preferences.claimDefaultLayoutSeed()
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
        context.novaReduxStore.edit { store ->
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
        "feed" -> openFeed()
        "menu.home" -> holdFirst("icon")
        "menu.drawer" -> openDrawer() && holdFirst("appLabel")
        "folder.drawer" -> openDrawer() && tapFirst(By.descStartsWith(FOLDER_DESC))
        "settings" -> startSettings(Intent())
        "settings.searchbar" -> startSettings(Intent().putExtra(SettingsActivity.EXTRA_OPEN_SEARCH_BAR, true))
        "settings.search" -> startSettings(
            Intent().putExtra(SettingsActivity.EXTRA_SECTION_TITLE_RES, R.string.settings_section_search)
                .putExtra(SettingsActivity.EXTRA_HIGHLIGHT_TITLE_RES, R.string.search_show_apps_row)
        )
        else -> false
    }

    // The first view of this id held as a finger holds it, and let go: its
    // menu stays up.
    private fun holdFirst(viewId: String): Boolean {
        val view = device.wait(Until.findObject(By.res(appId, viewId)), WAIT_MS) ?: return false
        view.longClick()
        return true
    }

    private fun tapFirst(selector: BySelector): Boolean {
        val view = device.wait(Until.findObject(selector), WAIT_MS) ?: return false
        view.click()
        return true
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
    // A pull past half way: a software-rendered emulator takes a frame per
    // step, so on a big screen the swipe is too slow to count as a fling, and
    // a pull of exactly half the height (as this was) settles closed again.
    private fun openDrawer(): Boolean {
        val x = device.displayWidth / 2
        val height = device.displayHeight
        device.swipe(x, height * PULL_FROM / PULL_SHARES, x, height * PULL_TO / PULL_SHARES, SLOW_SWIPE_STEPS)
        if (shows("drawerSheet")) return true
        // What the swipe left on screen, and the views then, for a look at
        // why it didn't open.
        val screens = File(context.getExternalFilesDir(null), "screens")
        device.takeScreenshot(File(screens, "00-drawer-swipe.png"))
        device.dumpWindowHierarchy(File(screens, "00-drawer-swipe.dump"))
        device.findObject(By.res(appId, "desktopSearchBar"))?.click() ?: return false
        val opened = shows("drawerSheet")
        if (opened) {
            device.pressBack()
            settle()
        }
        return opened && device.hasObject(By.res(appId, "drawerSheet"))
    }

    // A slow pull rightward across the first page: the feed, NovaRedux's own
    // or another app's page (pref.s.feed_provider), which draws its own
    // window and settles open on its own.
    private fun openFeed(): Boolean {
        val y = device.displayHeight / 2
        val edge = device.displayWidth / EDGE_SHARE
        device.swipe(edge, y, device.displayWidth - edge, y, SLOW_SWIPE_STEPS)
        Thread.sleep(FEED_WAIT_MS)
        return true
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

        // The drawer pull runs from 15/20 of the height (above the dock, as
        // before) to 1/20: 70% of it.
        const val PULL_SHARES = 20
        const val PULL_FROM = 15
        const val PULL_TO = 1
        const val EDGE_SHARE = 10
        const val FEED_WAIT_MS = 4_000L
        const val BACK_PRESSES = 2

        // A drawer folder tile's description (folder_desc), in English.
        const val FOLDER_DESC = "Folder: "
    }
}
