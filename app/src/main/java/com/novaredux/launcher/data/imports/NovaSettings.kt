package com.novaredux.launcher.data.imports

import com.novaredux.launcher.data.IconShape
import com.novaredux.launcher.data.LauncherPrefs
import com.novaredux.launcher.ui.AnimationSpeed
import com.novaredux.launcher.ui.DiscoverTheme
import com.novaredux.launcher.ui.DrawerStyle
import com.novaredux.launcher.ui.DrawerTabBar
import com.novaredux.launcher.ui.IndicatorStyle
import com.novaredux.launcher.ui.LaunchAnimation
import com.novaredux.launcher.ui.ScrollEffect
import com.novaredux.launcher.ui.WallpaperScrollMode
import com.novaredux.launcher.ui.theme.ThemeMode
import java.util.Locale

// Nova Launcher keeps its settings in one preferences file
// (com.teslacoilsw.launcher_preferences.xml in a .novabackup). These are the
// ones NovaRedux has a twin for, by the names Nova 6 and 7 write them. A value
// it doesn't recognise, or a setting the backup leaves out, keeps NovaRedux's
// own. Device-bound settings (screen orientation, density) are left alone:
// the backup may come from another kind of device.
internal object NovaSettings {

    fun isNova(values: Map<String, String>): Boolean = MARKERS.any { it in values }

    fun applyTo(prefs: LauncherPrefs, values: Map<String, String>): LauncherPrefs {
        if (!isNova(values)) return prefs
        val nova = NovaValues(values)
        return NovaGestures.applyTo(NovaLooks.applyTo(general(prefs, nova), nova), nova)
    }

    private fun general(prefs: LauncherPrefs, nova: NovaValues): LauncherPrefs =
        system(drawerAndDock(motion(prefs, nova), nova), nova)

    private fun motion(prefs: LauncherPrefs, nova: NovaValues): LauncherPrefs {
        val wallpaper = nova.choice("wallpaper_scrolling", WALLPAPER)
        return prefs.copy(
            scrollEffect = nova.choice("scroll_effect", SCROLL_EFFECTS) ?: prefs.scrollEffect,
            drawerPageEffect = nova.choice("drawer_scroll_effect", SCROLL_EFFECTS) ?: prefs.drawerPageEffect,
            animationSpeed = nova.choice("animation_speed", ANIMATION_SPEEDS) ?: prefs.animationSpeed,
            launchAnimation = nova.choice("activity_animation", LAUNCH_ANIMATIONS) ?: prefs.launchAnimation,
            pageIndicatorStyle = nova.choice("desktop_scroll_indicator", INDICATORS) ?: prefs.pageIndicatorStyle,
            themeMode = nova.choice("night_mode", NIGHT_MODES) ?: prefs.themeMode,
            wallpaperScroll = if (wallpaper == null) prefs.wallpaperScroll else wallpaper != WallpaperScrollMode.OFF,
            wallpaperScrollMode = wallpaper?.name ?: prefs.wallpaperScrollMode
        )
    }

    private fun drawerAndDock(prefs: LauncherPrefs, nova: NovaValues): LauncherPrefs = prefs.copy(
        dockEnabled = nova.bool("dock_enable") ?: prefs.dockEnabled,
        dockBgBehindNav = nova.bool("dock_background_draw_behind_navbar") ?: prefs.dockBgBehindNav,
        drawerStyle = nova.choice("drawer_style", DRAWER_STYLES) ?: prefs.drawerStyle,
        drawerFastScroller = nova.bool("drawer_fastscroll") ?: prefs.drawerFastScroller,
        drawerFrequentRow = nova.bool("drawer_predictive_apps") ?: prefs.drawerFrequentRow,
        drawerFoldersFirst = nova.bool("drawer_folders_before_apps") ?: prefs.drawerFoldersFirst,
        drawerTabBar = tabBar(prefs.drawerTabBar, nova.bool("drawer_enable_tabs"))
    )

    private fun system(prefs: LauncherPrefs, nova: NovaValues): LauncherPrefs = prefs.copy(
        autoAddApps = nova.bool("auto_add_shortcuts") ?: prefs.autoAddApps,
        dropTargets = nova.bool("show_drop_targets") ?: prefs.dropTargets,
        undoRemove = nova.bool("show_undo_bar") ?: prefs.undoRemove,
        transparentStatus = nova.bool("notification_bar_transparency") ?: prefs.transparentStatus,
        transparentNav = nova.bool("navigation_bar_transparency") ?: prefs.transparentNav,
        hideStatusBar = nova.bool("show_notification_bar")?.not() ?: prefs.hideStatusBar,
        iconShape = nova.choice("adaptive_icon_shape", ICON_SHAPES) ?: prefs.iconShape,
        iconNormalize = nova.bool("sanify_icon_size") ?: prefs.iconNormalize,
        // Nova's Normalize icon size, as the switch of that name shrinks
        // icons (IconNormalizer) where legacy icons aren't reshaped.
        iconNormalization = nova.bool("sanify_icon_size") ?: prefs.iconNormalization,
        iconPack = nova.text("theme_icon_pack") ?: prefs.iconPack,
        // Nova's Discover theme. Its page itself comes from another app here,
        // which the backup can't name: Feed > Page picks it.
        discoverTheme = nova.choice("google_now_theme", DISCOVER_THEMES) ?: prefs.discoverTheme
    )

    // Nova's tabs switch; a bar NovaRedux already shows stays where it is.
    private fun tabBar(current: DrawerTabBar, tabs: Boolean?): DrawerTabBar = when (tabs) {
        false -> DrawerTabBar.NONE
        true -> if (current == DrawerTabBar.NONE) DrawerTabBar.TOP else current
        null -> current
    }

    // Keys only Nova writes: any one of them marks the backup as Nova's.
    private val MARKERS = setOf(
        "qsb_style", "drawer_cellspecs", "folder_cellspecs", "folder_window_config", "searchbar_placement",
        "drawer_search_style", "dock_shape"
    )

    private fun <E : Enum<E>> named(entries: List<E>, vararg aliases: Pair<String, E>): Map<String, E> =
        entries.associateBy { it.name } + aliases

    private val SCROLL_EFFECTS = named(
        ScrollEffect.entries,
        "CUBE_IN" to ScrollEffect.CUBE, "CUBE_OUT" to ScrollEffect.CUBE,
        "ZOOM_IN" to ScrollEffect.ZOOM, "ZOOM_OUT" to ScrollEffect.ZOOM, "CARDSTACK" to ScrollEffect.CARD_STACK
    )
    private val ANIMATION_SPEEDS = mapOf(
        "FAST" to AnimationSpeed.FAST, "VERY_FAST" to AnimationSpeed.FAST, "SNAPPY" to AnimationSpeed.FAST,
        "NORMAL" to AnimationSpeed.RELAXED, "NOVA" to AnimationSpeed.RELAXED, "SLOW" to AnimationSpeed.RELAXED,
        "RELAXED" to AnimationSpeed.RELAXED
    )
    private val LAUNCH_ANIMATIONS = named(LaunchAnimation.entries)
    private val INDICATORS = named(IndicatorStyle.entries)
    private val NIGHT_MODES = mapOf(
        "NEVER" to ThemeMode.LIGHT, "ALWAYS" to ThemeMode.DARK, "AUTO" to ThemeMode.AUTO,
        "AUTO_TIME" to ThemeMode.AUTO, "SYSTEM" to ThemeMode.SYSTEM, "FOLLOW_SYSTEM" to ThemeMode.SYSTEM
    )
    private val WALLPAPER = named(WallpaperScrollMode.entries)
    private val DISCOVER_THEMES = named(
        DiscoverTheme.entries,
        "NIGHT_MODE" to DiscoverTheme.AUTO, "FOLLOW_NIGHT_MODE" to DiscoverTheme.AUTO, "SYSTEM" to DiscoverTheme.AUTO
    )
    private val DRAWER_STYLES = mapOf(
        "VERTICAL" to DrawerStyle.VERTICAL_GRID, "HORIZONTAL" to DrawerStyle.PAGED_GRID,
        "LIST" to DrawerStyle.VERTICAL_LIST
    )
    private val ICON_SHAPES = named(
        IconShape.entries,
        "ROUNDED_RECT" to IconShape.ROUNDED_SQUARE, "ROUNDRECT" to IconShape.ROUNDED_SQUARE,
        "SYSTEM_DEFAULT" to IconShape.SYSTEM, "DEFAULT" to IconShape.SYSTEM
    )
}

// A backup's preference entries, read leniently: a value of the wrong shape
// reads as null, which keeps NovaRedux's setting.
internal class NovaValues(private val values: Map<String, String>) {

    fun raw(key: String): String? = values[key]

    fun text(key: String): String? = values[key]?.takeIf { it.isNotBlank() }

    fun bool(key: String): Boolean? = values[key]?.trim()?.toBooleanStrictOrNull()

    fun int(key: String): Int? = values[key]?.trim()?.toIntOrNull()

    fun <T> choice(key: String, choices: Map<String, T>): T? =
        values[key]?.trim()?.uppercase(Locale.ROOT)?.let(choices::get)
}
