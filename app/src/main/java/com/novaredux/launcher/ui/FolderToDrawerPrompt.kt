package com.novaredux.launcher.ui

import android.view.View
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.novaredux.launcher.R
import com.novaredux.launcher.data.DrawerTarget
import com.novaredux.launcher.data.FolderToDrawer
import com.novaredux.launcher.data.LauncherItem
import com.novaredux.launcher.databinding.DialogMoveToDrawerBinding

// Move to drawer: a home or dock folder becomes a drawer folder, on the
// main list or in a tab, or a tab of its own. Its apps go with it; what a
// drawer group can't hold (shortcuts, contacts) stays on the home screen.
class FolderToDrawerPrompt(private val surfaces: ItemSurfaces) {
    private val activity get() = surfaces.activity
    private val viewModel get() = surfaces.viewModel

    // The row, in a home or dock folder's menus: offered on an unlocked
    // desktop, for a folder holding an app. `first` runs before the dialog
    // opens (the open folder closes).
    fun addRow(menu: NovaReduxPopupMenu, folder: LauncherItem, first: () -> Unit = {}) {
        val movable = FolderToDrawer.movable(viewModel.folderMembers(folder.id))
        if (viewModel.state.value.prefs.desktopLocked || movable.isEmpty()) return
        menu.addRow(activity.getString(R.string.move_to_drawer), R.drawable.ic_menu_export) {
            first()
            prompt(folder)
        }
    }

    private fun prompt(folder: LauncherItem) {
        val members = viewModel.folderMembers(folder.id)
        val apps = FolderToDrawer.movable(members).size
        val stays = members.size - apps
        val name = FolderToDrawer.groupName(folder.label)
        val res = activity.resources
        val view = DialogMoveToDrawerBinding.inflate(activity.layoutInflater)
        val iconSize = res.getDimensionPixelSize(R.dimen.move_folder_icon)
        val icon = surfaces.itemIcons.folderPreviewDrawable(folder)?.apply { setBounds(0, 0, iconSize, iconSize) }
        view.moveFolderSummary.setCompoundDrawablesRelative(icon, null, null, null)
        view.moveFolderSummary.text = res.getQuantityString(R.plurals.move_to_drawer_summary, apps, name, apps)
        view.moveTabHint.text = activity.getString(R.string.move_to_drawer_tab_hint, name)
        view.moveLeftBehind.isVisible = stays > 0
        view.moveLeftBehind.text = res.getQuantityString(R.plurals.move_to_drawer_stays, stays, stays)
        val tabTargets = addTabChips(view.moveTabs)
        view.moveType.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            view.moveTabSection.isVisible = id == R.id.moveAsFolder
            view.moveTabHint.isVisible = id == R.id.moveAsTab
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.move_to_drawer)
            .setView(view.root)
            .show()
        view.moveCancel.setOnClickListener { dialog.dismiss() }
        view.moveConfirm.setOnClickListener {
            dialog.dismiss()
            val target = if (view.moveType.checkedButtonId == R.id.moveAsTab) {
                DrawerTarget.OwnTab
            } else {
                tabTargets[view.moveTabs.checkedChipId] ?: DrawerTarget.MainList
            }
            viewModel.moveFolderToDrawer(folder, target)?.let(surfaces.removal::showFolderMoved)
        }
    }

    // The Put it in chips: All, then each drawer tab, All chosen. Returns
    // each chip's id with the place it stands for.
    private fun addTabChips(group: ChipGroup): Map<Int, DrawerTarget> {
        val targets = listOf(DrawerTarget.MainList) +
            viewModel.drawerTabGroups().map { DrawerTarget.InTab(it.id, it.name) }
        val byId = targets.associateBy { View.generateViewId() }
        byId.forEach { (id, target) ->
            val chip = activity.layoutInflater.inflate(R.layout.chip_move_tab, group, false) as Chip
            chip.id = id
            chip.text = if (target is DrawerTarget.InTab) target.label else activity.getString(R.string.drawer_all)
            group.addView(chip)
        }
        group.check(byId.keys.first())
        return byId
    }
}
