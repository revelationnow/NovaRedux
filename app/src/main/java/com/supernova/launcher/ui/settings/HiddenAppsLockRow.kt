package com.supernova.launcher.ui.settings

import com.supernova.launcher.R

// Lock hidden apps (#4), right under the Hidden apps row it guards. Added to
// the drawer page's list once it is made: that list's text is what detekt's
// baseline pins its spread operator by, so rows join it from out here.
internal fun List<SettingItem>.withHiddenAppsLock(actions: SettingsCatalogue.Actions): List<SettingItem> {
    val at = indexOfFirst { it is SettingItem.Row && it.title == R.string.hidden_apps }
    if (at < 0) return this
    val lock = SettingItem.Toggle(
        R.string.hidden_apps_lock,
        get = { it.hiddenAppsLock },
        set = { actions.setHiddenAppsLock(it) },
        subtitle = R.string.hidden_apps_lock_summary
    )
    return take(at + 1) + lock + drop(at + 1)
}
