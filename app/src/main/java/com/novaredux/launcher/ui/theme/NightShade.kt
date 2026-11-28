package com.novaredux.launcher.ui.theme

import com.novaredux.launcher.data.LauncherPrefs
import com.novaredux.launcher.data.SettingEnumSerializer

// How dark night mode goes, as Nova 6.1 offered dark, darker and darkest:
// DARK is the theme's own night colours, DARKER takes them halfway to black,
// BLACK is black, for OLED screens. Only the theme's surfaces change; a
// colour set in Styles stays as set.
@kotlinx.serialization.Serializable(with = NightShade.Names::class)
enum class NightShade {
    DARK,
    DARKER,
    BLACK;

    // A night surface colour in this shade, its alpha kept: halfway to
    // black is each colour channel halved.
    fun of(color: Int): Int = when (this) {
        DARK -> color
        DARKER -> (color and ALPHA) or ((color and HALVABLE) ushr 1)
        BLACK -> color and ALPHA
    }

    // Its name in the store and in a backup, read back leniently.
    object Names : SettingEnumSerializer<NightShade>("NightShade", entries, DARK)

    companion object {
        private const val ALPHA = 0xFF000000.toInt()
        // The colour channels less each one's lowest bit, so shifting right
        // by one halves every channel without spilling into the next.
        private const val HALVABLE = 0x00FEFEFE

        fun fromName(name: String): NightShade = Names.parse(name)
    }
}

// Which parts night mode darkens (Nova 4.3's choice): the home search bar,
// the app drawer and its field, folders, and menus with cards. A part left
// out keeps its day colours at night. Everything else always follows.
object NightParts {

    fun darkens(prefs: LauncherPrefs, id: SurfaceId): Boolean = when (id) {
        SurfaceId.SEARCH_BAR -> prefs.nightSearchBar
        SurfaceId.DRAWER, SurfaceId.DRAWER_SEARCH_BAR -> prefs.nightDrawer
        SurfaceId.FOLDER -> prefs.nightFolders
        SurfaceId.POPUP, SurfaceId.CARDS -> prefs.nightMenus
        SurfaceId.DOCK, SurfaceId.SETTINGS, SurfaceId.HOME -> true
    }

    // What of it changes the resolved colours, for the resolver's cache key.
    fun key(prefs: LauncherPrefs): String =
        listOf(prefs.nightShade.name, prefs.nightSearchBar, prefs.nightDrawer, prefs.nightFolders, prefs.nightMenus)
            .joinToString(":")
}
