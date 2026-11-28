package com.novaredux.launcher.ui

import com.novaredux.launcher.data.SettingEnumSerializer

// What the empty search window's row of apps holds, picked by the tabs above
// it as in Nova 5's search: the apps opened most recently, the ones opened
// most, or the newest installs and updates. The last pick is kept
// (searchAppsRow).
@kotlinx.serialization.Serializable(with = AppsRowTab.Names::class)
enum class AppsRowTab {
    RECENT,
    FREQUENT,
    NEW;

    // Its name in the store and in a backup, read back leniently.
    object Names : SettingEnumSerializer<AppsRowTab>("AppsRowTab", entries, RECENT)

    companion object {
        fun fromName(name: String): AppsRowTab = Names.parse(name)
    }
}
