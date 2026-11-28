package com.novaredux.launcher.data

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import com.novaredux.launcher.gesture.ActionShortcut

// The launcher-action links and the "App icons" marker were
// "supernova://action/" and "supernova:app" until 28 Nov 2026
// (docs/NAMES.md), as they still are in Supernova. A layout from before then,
// or a Supernova backup, takes the new ones as it loads; ActionShortcut.decode
// and AppIconSource.isAppIcon still read an old one wherever one is left.
object RenamedMarkers {

    fun action(uri: String?): String? =
        if (uri?.startsWith(ActionShortcut.SUPERNOVA_PREFIX) == true) {
            ActionShortcut.PREFIX + uri.removePrefix(ActionShortcut.SUPERNOVA_PREFIX)
        } else {
            uri
        }

    fun pack(pack: String?): String? = if (pack == AppIconSource.SUPERNOVA_PACK) AppIconSource.PACK else pack

    fun applied(model: LauncherModel): LauncherModel {
        val items = model.items.map(::item)
        val overrides = model.appOverrides.mapValues { (_, edit) -> edit(edit) }
        return if (items == model.items && overrides == model.appOverrides) {
            model
        } else {
            model.copy(items = items, appOverrides = overrides)
        }
    }

    private fun item(item: LauncherItem): LauncherItem = item.copy(
        intentUri = action(item.intentUri),
        swipeAction = action(item.swipeAction),
        swipeActionDown = action(item.swipeActionDown),
        customIconPack = pack(item.customIconPack),
        shortcutEdit = item.shortcutEdit?.let(::edit),
        appEdit = item.appEdit?.let(::edit)
    )

    private fun edit(edit: AppOverride): AppOverride = edit.copy(iconPackPackage = pack(edit.iconPackPackage))
}

// The same for the drawer folders' own icons, which the settings keep. Only
// a group with the old marker is written again; the rest stay as stored.
internal object RenamedGroupIcons : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[Pref.drawerGroups.key].orEmpty().any { renamed(it) != it }

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            this[Pref.drawerGroups.key] = currentData[Pref.drawerGroups.key].orEmpty().mapTo(LinkedHashSet(), ::renamed)
        }

    override suspend fun cleanUp() = Unit

    fun renamed(entry: String): String {
        val group = DrawerGroup.decode(entry)
        return if (group != null && group.iconPack == AppIconSource.SUPERNOVA_PACK) {
            group.copy(iconPack = AppIconSource.PACK).encode()
        } else {
            entry
        }
    }
}
