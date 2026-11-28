package com.novaredux.launcher.ui

import com.novaredux.launcher.R
import com.novaredux.launcher.data.AppPairs
import com.novaredux.launcher.data.ContainerType
import com.novaredux.launcher.data.ItemType
import com.novaredux.launcher.data.LauncherItem

// An app's home menu rows for app pairs (#12): pair it with another app, or
// part a pair.
internal object AppPairRows {

    fun add(menu: NovaReduxPopupMenu, surfaces: ItemSurfaces, item: LauncherItem) {
        if (item.itemType != ItemType.APP || item.container == ContainerType.FOLDER) return
        val activity = surfaces.activity
        val viewModel = surfaces.viewModel
        if (item.pairWith != null) {
            menu.addRow(activity.getString(R.string.pair_part), R.drawable.ic_menu_unpair) {
                val first = viewModel.state.value.apps.firstOrNull {
                    it.packageName == item.packageName && it.className == item.className
                }
                viewModel.updateApp(item.id) { AppPairs.parted(it, first?.label ?: item.label) }
            }
            return
        }
        menu.addRow(activity.getString(R.string.pair_with), R.drawable.ic_menu_pair) {
            // The app alone: a pair opens apps, not their shortcuts.
            val title = activity.getString(R.string.pair_with)
            ActionPickerSheet.showApps(activity, viewModel, title, withShortcuts = false) { pick ->
                val second = (pick as? ActionPick.App)?.app ?: return@showApps
                val label = activity.getString(R.string.pair_label, item.label, second.label)
                viewModel.updateApp(item.id) { AppPairs.paired(it, second, label) }
            }
        }
    }
}
