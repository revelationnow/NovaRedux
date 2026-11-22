package com.supernova.launcher.ui

import com.supernova.launcher.data.SettingEnumSerializer

// Nova's Discover theme: the page from another app (DiscoverClient) in the
// light or dark look, or following the launcher's night mode.
@kotlinx.serialization.Serializable(with = DiscoverTheme.Names::class)
enum class DiscoverTheme {
    AUTO,
    LIGHT,
    DARK;

    // Its name in the store and in a backup, read back leniently.
    object Names : SettingEnumSerializer<DiscoverTheme>("DiscoverTheme", entries, AUTO)

    companion object {
        fun fromName(name: String): DiscoverTheme = Names.parse(name)
    }
}
