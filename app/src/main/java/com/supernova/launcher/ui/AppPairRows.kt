package com.supernova.launcher.ui

import com.supernova.launcher.R
import com.supernova.launcher.data.AppPairs
import com.supernova.launcher.data.ContainerType
import com.supernova.launcher.data.ItemType
import com.supernova.launcher.data.LauncherItem

// An app's home menu rows for app pairs (#12): pair it with another app, or
// part a pair.
internal object AppPairRows {

    fun add(menu: SupernovaPopupMenu, surfaces: ItemSurfaces, item: LauncherItem) {
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
            ActionPickerSheet.showApps(activity, viewModel, activity.getString(R.string.pair_with)) { pick ->
                val second = (pick as? ActionPick.App)?.app ?: return@showApps
                val label = activity.getString(R.string.pair_label, item.label, second.label)
                viewModel.updateApp(item.id) { AppPairs.paired(it, second, label) }
            }
        }
    }
}
