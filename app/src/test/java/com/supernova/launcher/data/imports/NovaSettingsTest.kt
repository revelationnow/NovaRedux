package com.supernova.launcher.data.imports

import com.supernova.launcher.data.ContainerType
import com.supernova.launcher.data.IconShape
import com.supernova.launcher.data.ItemType
import com.supernova.launcher.data.LauncherPrefs
import com.supernova.launcher.gesture.ActionShortcut
import com.supernova.launcher.gesture.Gesture
import com.supernova.launcher.gesture.GestureAction
import com.supernova.launcher.gesture.GestureDefaults
import com.supernova.launcher.ui.AnimationSpeed
import com.supernova.launcher.ui.DockBgStyle
import com.supernova.launcher.ui.DrawerStyle
import com.supernova.launcher.ui.DrawerTabBar
import com.supernova.launcher.ui.FolderMode
import com.supernova.launcher.ui.FolderScroll
import com.supernova.launcher.ui.IndicatorStyle
import com.supernova.launcher.ui.LabelConfig
import com.supernova.launcher.ui.LabelContext
import com.supernova.launcher.ui.LabelFont
import com.supernova.launcher.ui.ScrollEffect
import com.supernova.launcher.ui.SearchBarConfig
import com.supernova.launcher.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Nova's own settings out of a .novabackup. The names and value formats are
// those of a real Nova 6.2 preferences file; the values are made up.
class NovaSettingsTest {

    private fun action(label: String, action: String) =
        "$label\$#Intent;action=com.teslacoilsw.launcher.ACTION;launchFlags=0x10000000;" +
            "component=com.teslacoilsw.launcher/.NovaShortcutHandler;S.LAUNCHER_ACTION=$action;end"

    private val novaSettings = "Nova Settings\$#Intent;action=android.intent.action.MAIN;" +
        "launchFlags=0x30008000;component=com.teslacoilsw.launcher/.preferences.SettingsActivity;end"

    private val camera = "Camera\$#Intent;action=android.intent.action.MAIN;" +
        "category=android.intent.category.LAUNCHER;component=com.cam/.Main;end"

    private val nova6 = PrefsXml.parse(
        """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
        <map>
            <string name="searchbar_placement">NONE</string>
            <string name="drawer_cellspecs">1.25:true:13.0:#40302:false:sans-serif-condensed:true:true</string>
            <int name="desktop_grid_cols" value="6" />
            <int name="drawer_transparency" value="128" />
            <string name="night_mode">NEVER</string>
            <boolean name="dock_styled" value="false" />
            <string name="drawer_search_style">M_LETTER_COLOR:WIRE:#ff000000:56:0</string>
            <string name="animation_speed">FAST</string>
            <string name="scroll_effect">CUBE_OUT</string>
            <string name="drawer_scroll_effect">SIMPLE</string>
            <boolean name="dock_enable" value="true" />
            <string name="folder_window_config">WINDOW:172:-1:24:true:true:3:4:VERTICAL</string>
            <string name="desktop_scroll_indicator">DOTS</string>
            <string name="screen_orientation">LANDSCAPE</string>
            <boolean name="show_drop_targets" value="true" />
            <string name="drawer_style">LIST</string>
            <boolean name="drawer_predictive_apps" value="false" />
            <string name="dock_shape">PLATFORM</string>
            <string name="qsb_style">M_LETTER_MONO:PILLRECT:#ffffffff:56:1</string>
            <string name="wallpaper_scrolling">REVERSE</string>
            <int name="dock_icon_size" value="80" />
            <int name="dock_alpha" value="255" />
            <boolean name="auto_add_shortcuts" value="false" />
            <string name="adaptive_icon_shape">SQUIRCLE</string>
            <boolean name="sanify_icon_size" value="true" />
            <boolean name="show_notification_bar" value="false" />
            <int name="drawer_background_color" value="-1" />
            <boolean name="drawer_enable_tabs" value="false" />
            <string name="gesture_swipe_down">${action("Expand notifications", "EXPAND_STATUS_BAR")}</string>
            <string name="gesture_pinch">${action("Show previews", "SHOW_PREVIEWS")}</string>
            <string name="gesture_pinch_out">${action("Recent apps", "SHOW_RECENT_APPS")}</string>
            <string name="gesture_two_finger_scroll_up">$novaSettings</string>
            <string name="gesture_two_finger_scroll_down">$camera</string>
            <string name="gesture_double_tap"></string>
            <string name="home_button_intent">${action("App search", "APP_SEARCH")}</string>
            <boolean name="home_button_action_default_page_only" value="true" />
        </map>"""
    )

    private val applied = NovaSettings.applyTo(LauncherPrefs(), nova6)

    @Test
    fun aBackupWithoutNovasKeysChangesNothing() {
        val other = mapOf("desktop_grid" to "6x5", "scroll_effect" to "CUBE", "dock_enable" to "false")
        assertFalse(NovaSettings.isNova(other))
        assertEquals(LauncherPrefs(), NovaSettings.applyTo(LauncherPrefs(), other))
    }

    @Test
    fun generalSettingsCarryOver() {
        assertTrue(NovaSettings.isNova(nova6))
        assertEquals(ScrollEffect.CUBE, applied.scrollEffect)
        assertEquals(ScrollEffect.SIMPLE, applied.drawerPageEffect)
        assertEquals(AnimationSpeed.FAST, applied.animationSpeed)
        assertEquals(IndicatorStyle.DOTS, applied.pageIndicatorStyle)
        assertEquals(ThemeMode.LIGHT, applied.themeMode)
        assertEquals("REVERSE", applied.wallpaperScrollMode)
        assertTrue(applied.wallpaperScroll)
        assertEquals(DrawerStyle.VERTICAL_LIST, applied.drawerStyle)
        assertFalse(applied.drawerFrequentRow)
        assertEquals(DrawerTabBar.NONE, applied.drawerTabBar)
        assertFalse(applied.autoAddApps)
        assertTrue(applied.dropTargets)
        assertTrue(applied.hideStatusBar)
        assertEquals(IconShape.SQUIRCLE, applied.iconShape)
        assertTrue(applied.iconNormalize)
    }

    @Test
    fun theScreenOrientationStaysWithThePhone() {
        assertEquals(LauncherPrefs().homeOrientation, applied.homeOrientation)
    }

    @Test
    fun aValueWithNoTwinKeepsTheSetting() {
        val prefs = NovaSettings.applyTo(LauncherPrefs(), nova6 + ("scroll_effect" to "THROW"))
        assertEquals(LauncherPrefs().scrollEffect, prefs.scrollEffect)
    }

    @Test
    fun theDrawersIconsLabelsAndBackgroundCarryOver() {
        assertFalse(applied.drawerMatchDesktop)
        assertEquals(1.25f, applied.drawerIconScale, DELTA)
        assertTrue(applied.drawerLabels)
        val drawer = LabelConfig.decode(applied.labelConfig).of(LabelContext.DRAWER)
        assertEquals(13, drawer.size)
        assertEquals(LabelFont.CONDENSED.name, drawer.font)
        assertEquals(128 / 255f, applied.drawerBgOpacity, DELTA)
        assertEquals("#FFFFFF", applied.drawerBgColor)
    }

    @Test
    fun theDockKeepsItsOwnIconSizeAndNoBackground() {
        assertTrue(applied.dockEnabled)
        assertFalse(applied.dockMatchDesktop)
        assertEquals(0.8f, applied.dockIconScale, DELTA)
        assertEquals(DockBgStyle.NONE, applied.dockBgStyle)
        assertEquals(1f, applied.dockBgOpacity, DELTA)
        val styled = NovaSettings.applyTo(LauncherPrefs(), nova6 + ("dock_styled" to "true"))
        assertEquals(DockBgStyle.ROUNDED, styled.dockBgStyle)
    }

    @Test
    fun foldersKeepTheirStyleAndScrolling() {
        assertEquals(FolderMode.WINDOW, applied.folderMode)
        assertEquals(FolderScroll.VERTICAL, applied.folderScroll)
    }

    @Test
    fun theSearchBarsTakeNovasPlacementAndShapes() {
        val bars = SearchBarConfig.decode(applied.searchBarConfig)
        assertEquals("NONE", bars.desktop.placement)
        assertEquals(24, bars.desktop.radius)
        assertFalse(bars.desktop.wireframe)
        assertFalse(bars.drawer.filled)
    }

    @Test
    fun gesturesAndTheHomeButtonCarryOver() {
        val bindings = GestureDefaults.decode(applied.gestureBindings)
        assertEquals(GestureAction.EXPAND_NOTIFICATIONS, bindings[Gesture.SWIPE_DOWN]?.action)
        assertEquals(GestureAction.DESKTOP_PREVIEWS, bindings[Gesture.PINCH_IN]?.action)
        assertEquals(GestureAction.RECENTS, bindings[Gesture.PINCH_OUT]?.action)
        assertEquals(GestureAction.OPEN_SETTINGS, bindings[Gesture.TWO_FINGER_SWIPE_UP]?.action)
        val app = bindings[Gesture.TWO_FINGER_SWIPE_DOWN]
        assertEquals(GestureAction.LAUNCH_APP, app?.action)
        assertEquals("com.cam/com.cam.Main", app?.payload)
        assertEquals("Camera", app?.label)
        assertEquals(GestureAction.NONE, bindings[Gesture.DOUBLE_TAP]?.action)
        // Not in the backup: NovaRedux's own binding stays.
        assertEquals(GestureAction.OPEN_DRAWER, bindings[Gesture.SWIPE_UP]?.action)
        assertEquals(GestureAction.OPEN_SEARCH.name, applied.homeButtonAction)
        assertTrue(applied.homeButtonOnMainPage)
    }

    @Test
    fun iconSwipeActionsBecomeNovaReduxOnes() {
        assertEquals(ActionShortcut.encode(GestureAction.OPEN_SETTINGS) to null, NovaGestures.swipeAction(novaSettings))
        assertEquals("com.cam/com.cam.Main" to "Camera", NovaGestures.swipeAction(camera))
        assertNull(NovaGestures.swipeAction(""))
        assertNull(NovaGestures.swipeAction(null))
    }

    @Test
    fun nova6GridsAndIconSwipesComeThroughTheLayout() {
        val rows = listOf(
            Row(
                mapOf(
                    "_id" to 1L, "itemType" to 0L, "container" to -100L, "screen" to 0L, "cellX" to 1L,
                    "cellY" to 2L, "title" to "Camera", "flingUpIntent" to novaSettings,
                    "intent" to camera.substringAfter('$')
                )
            ),
            Row(
                mapOf(
                    "_id" to 2L, "itemType" to 2L, "container" to -100L, "screen" to 0L, "cellX" to 0L,
                    "cellY" to 0L, "title" to "Tools", "flingAsTap" to "true"
                )
            ),
            Row(mapOf("_id" to 3L, "itemType" to 0L, "container" to 2L, "intent" to camera.substringAfter('$')))
        )
        val prefs = nova6 + mapOf("desktop_grid_rows" to "7", "drawer_app_grid_cols" to "5", "drawer_app_grid_rows" to "8")
        val layout = BackupLayouts.layout(BackupContents(tables = mapOf("favorites" to rows), prefs = prefs))!!
        assertEquals(6, layout.grid.cols)
        assertEquals(7, layout.grid.rows)
        assertEquals(5, layout.grid.drawerCols)
        assertEquals(8, layout.grid.drawerRows)

        val result = LayoutConverter.convert(layout, LauncherPrefs())
        val app = result.items.single { it.itemType == ItemType.APP && it.container == ContainerType.WORKSPACE }
        assertEquals(ActionShortcut.encode(GestureAction.OPEN_SETTINGS), app.swipeAction)
        assertTrue(result.items.single { it.itemType == ItemType.FOLDER }.swipeToOpen)
        assertEquals(ScrollEffect.CUBE, result.prefs.scrollEffect)
        assertEquals(6, result.prefs.gridCols)
    }

    private companion object {
        const val DELTA = 0.001f
    }
}
