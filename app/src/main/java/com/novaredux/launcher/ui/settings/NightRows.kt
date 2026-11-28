package com.novaredux.launcher.ui.settings

import androidx.annotation.StringRes
import com.novaredux.launcher.R
import com.novaredux.launcher.data.LauncherPrefs
import com.novaredux.launcher.data.Pref
import com.novaredux.launcher.data.PrefSpec
import com.novaredux.launcher.ui.LauncherViewModel
import com.novaredux.launcher.ui.theme.NightShade
import com.novaredux.launcher.ui.theme.ThemeMode

// The Style tab's night mode rows, under Theme: how dark it goes, and what it
// darkens (Nova 6.1's shades and 4.3's parts). Out of sight while the theme
// is always light, as the settings search says.
internal object NightRows {

    private val lightOnly: (LauncherPrefs) -> SettingItem.HiddenBy? = { prefs ->
        SettingItem.HiddenBy(R.string.theme_mode, SettingItem.HiddenBy.Needs.OTHER)
            .takeIf { prefs.themeMode == ThemeMode.LIGHT }
    }

    fun of(viewModel: LauncherViewModel, rows: SettingsCatalogue.Rows): List<SettingItem> {
        fun part(@StringRes title: Int, get: (LauncherPrefs) -> Boolean, pref: PrefSpec<Boolean>) =
            SettingItem.Toggle(title, get = get, set = { viewModel.set(pref, it) }).hiddenWhen(lightOnly).nested()
        return listOf(
            rows.choice(
                R.string.night_shade, NightShade.entries, { it.nightShade }, { EnumLabels.of(it) }
            ) { viewModel.set(Pref.nightShade, it) }.hiddenWhen(lightOnly).nested(),
            part(R.string.night_search_bar, { it.nightSearchBar }, Pref.nightSearchBar),
            part(R.string.night_drawer, { it.nightDrawer }, Pref.nightDrawer),
            part(R.string.night_folders, { it.nightFolders }, Pref.nightFolders),
            part(R.string.night_menus, { it.nightMenus }, Pref.nightMenus)
        )
    }
}
