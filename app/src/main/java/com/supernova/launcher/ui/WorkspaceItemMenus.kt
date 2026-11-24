package com.supernova.launcher.ui

import android.content.ComponentName
import android.view.View
import android.widget.Toast
import com.supernova.launcher.R
import com.supernova.launcher.data.ContainerType
import com.supernova.launcher.data.ItemType
import com.supernova.launcher.data.LauncherItem
import com.supernova.launcher.data.LauncherModel
import com.supernova.launcher.data.WidgetStacks

// The menus of what is on the home screen: an icon's, a folder's and a
// widget's, on the desktop or in the dock, and an app's in an open home
// folder.
class WorkspaceItemMenus(private val surfaces: ItemSurfaces) {
    private val activity get() = surfaces.activity
    private val viewModel get() = surfaces.viewModel
    private val kit get() = surfaces.menuKit

    fun show(anchor: View, item: LauncherItem): SupernovaPopupMenu {
        val locked = viewModel.state.value.prefs.desktopLocked
        val menu = kit.popup(forItem = true)
        if (item.itemType == ItemType.APP || item.itemType == ItemType.SHORTCUT) {
            kit.addNotifications(menu, item.packageName, item.userSerial)
        }
        when (item.itemType) {
            ItemType.FOLDER -> addFolderRows(menu, anchor, item, locked)
            ItemType.WIDGET -> addWidgetRows(menu, anchor, item, locked)
            ItemType.APP, ItemType.SHORTCUT -> addAppRows(menu, anchor, item, locked)
        }
        if (!menu.hasRows) {
            Toast.makeText(activity, R.string.desktop_locked_toast, Toast.LENGTH_SHORT).show()
            return menu
        }
        menu.show(anchor, surfaces.menuTouchX)
        return menu
    }

    // Remove, Select apps, Sort, Edit. The icon is changed in Edit, as an
    // app's is.
    private fun addFolderRows(menu: SupernovaPopupMenu, anchor: View, item: LauncherItem, locked: Boolean) {
        if (!locked) {
            menu.addRow(activity.getString(R.string.remove), R.drawable.ic_menu_remove_item) {
                surfaces.removal.remove(item)
            }
            menu.addRow(activity.getString(R.string.select_apps), R.drawable.ic_menu_select_apps) {
                surfaces.pickers.folderApps(item)
            }
            menu.addRow(activity.getString(R.string.sort), R.drawable.ic_menu_sort) {
                surfaces.removal.sortFolder(item.id)
            }
            kit.addIconSizeRow(menu, item)
        }
        surfaces.moveToDrawer.addRow(menu, item)
        // Folder Edit keeps the name and icon on a locked desktop, as an
        // app's Edit does.
        menu.addRow(activity.getString(R.string.edit), R.drawable.ic_menu_edit) {
            surfaces.itemEdits.showFolderSheet(item)
        }
        kit.addExportPngRow(menu, anchor, item.label)
    }

    private fun addWidgetRows(menu: SupernovaPopupMenu, anchor: View, item: LauncherItem, locked: Boolean) {
        val widgets = surfaces.widgets
        // In a stack (#10), App info, the widget's settings and the picture
        // are the showing widget's.
        val shown = WidgetStacks.asShown(item, (anchor as? WidgetStackView)?.shown ?: WidgetStacks.shown(item))
        if (!locked) {
            menu.addRow(activity.getString(R.string.widget_resize), R.drawable.ic_menu_resize) {
                widgets.showResizeFrame(item, anchor)
            }
            menu.addRow(activity.getString(R.string.padding), R.drawable.ic_menu_padding) {
                widgets.showPaddingDialog(item, anchor)
            }
            // The quickmenu pair, for a widget that overlaps other items:
            // which of them draws on top and takes the touch. Each row is
            // offered only when it would change something.
            val stacking = viewModel.stacking(item)
            if (stacking.above) {
                menu.addRow(activity.getString(R.string.bring_to_front), R.drawable.ic_menu_to_front) {
                    viewModel.restackItem(item.id, toFront = true)
                }
            }
            if (stacking.below) {
                menu.addRow(activity.getString(R.string.send_to_back), R.drawable.ic_menu_to_back) {
                    viewModel.restackItem(item.id, toFront = false)
                }
            }
            addStackRows(menu, anchor, item)
            menu.addRow(activity.getString(R.string.remove), R.drawable.ic_menu_remove_item) {
                surfaces.removal.remove(item)
            }
        }
        // The widget menu has the provider's App info, locked or not, as an
        // app's menu does. The provider names the package and, through the
        // user, the profile its details open in.
        shown.widgetProvider?.let(ComponentName::unflattenFromString)?.let { provider ->
            menu.addRow(activity.getString(R.string.app_info), R.drawable.ic_menu_info) {
                viewModel.appCatalog().actions.openAppInfo(provider.packageName, provider.className, shown.userSerial)
            }
        }
        // The widget's own settings screen, for widgets that offer it after
        // they are placed.
        if (widgets.isReconfigurable(shown)) {
            menu.addRow(activity.getString(R.string.widget_settings), R.drawable.ic_menu_edit) {
                widgets.openWidgetSettings(shown)
            }
        }
        kit.addExportPngRow(menu, anchor, shown.appWidgetId?.let(widgets::label) ?: shown.label)
    }

    // Widget stacks (#10): another widget into these cells, and, in a
    // stack, the showing one out of it.
    private fun addStackRows(menu: SupernovaPopupMenu, anchor: View, item: LauncherItem) {
        if (item.container != ContainerType.WORKSPACE) return
        menu.addRow(activity.getString(R.string.stack_add_widget), R.drawable.ic_menu_stack) {
            surfaces.widgets.addToStack(item)
        }
        if (item.stack.isNotEmpty()) {
            menu.addRow(activity.getString(R.string.stack_remove_widget), R.drawable.ic_menu_unstack) {
                surfaces.widgets.removeShownFromStack(item, anchor)
            }
        }
    }

    // Remove, Widgets, App info, Edit. Everything configurable (swipe
    // gestures, label, icon) lives in Edit; the per-context "extra" rows are
    // opt-in through settings. Workspace semantics: tapping a shortcut
    // launches it.
    private fun addAppRows(menu: SupernovaPopupMenu, anchor: View, item: LauncherItem, locked: Boolean) {
        if (!locked) {
            menu.addRow(activity.getString(R.string.remove), R.drawable.ic_menu_remove_item) {
                surfaces.removal.remove(item)
            }
        }
        if (item.itemType == ItemType.APP && !locked) addWidgetsRow(menu, item.packageName) {}
        if (!locked) kit.addIconSizeRow(menu, item)
        // A launcher action or a picked activity has no package, and the row
        // did nothing for it.
        item.packageName?.let { pkg ->
            menu.addRow(activity.getString(R.string.app_info), R.drawable.ic_menu_info) {
                viewModel.appCatalog().actions.openAppInfo(pkg, item.className, item.userSerial)
            }
        }
        menu.addRow(activity.getString(R.string.edit), R.drawable.ic_menu_edit) {
            surfaces.itemEdits.showItemSheet(item, allowSwipe = !locked)
        }
        item.toLaunchableApp()?.let { app -> surfaces.appActions.addRows(menu, homeExtras(), app) }
        kit.addExportPngRow(menu, anchor, item.label)
        item.packageName?.let { pkg ->
            kit.addShortcuts(menu, pkg, item.userSerial, draggable = !locked) { shortcut ->
                surfaces.launchShortcut(pkg, shortcut.id, item.userSerial)
            }
        }
    }

    // An app in a home folder: its home icon's rows, with Move to Home among
    // them. The folder's own order is changed by dragging. `applied` is
    // handed the model from before a change, to redraw the open folder once
    // that change is saved.
    fun folderMember(
        folder: LauncherItem,
        member: LauncherItem,
        memberView: View,
        closeFolder: () -> Unit,
        applied: (LauncherModel) -> Unit
    ): SupernovaPopupMenu {
        val locked = viewModel.state.value.prefs.desktopLocked
        val menu = kit.popup(forItem = true)
        kit.addNotifications(menu, member.packageName, member.userSerial)
        if (!locked) {
            menu.addRow(activity.getString(R.string.remove), R.drawable.ic_menu_remove_item) {
                val before = viewModel.state.value.model
                // The folder's last app takes the folder with it. A member's
                // Undo bar shows in the folder's own window, as Sort's does;
                // the last one closes the folder with it. With one app left,
                // the folder gives way to it (#345) and closes: the bar shows
                // on the home screen.
                when (viewModel.folderMembers(folder.id).size) {
                    0, 1 -> surfaces.removal.remove(folder)
                    2 -> surfaces.removal.remove(member)
                    else -> surfaces.removal.remove(member, memberView, applied)
                }
                applied(before)
            }
        }
        member.packageName?.let { pkg ->
            if (member.itemType == ItemType.APP && !locked) addWidgetsRow(menu, pkg, closeFolder)
            menu.addRow(activity.getString(R.string.app_info), R.drawable.ic_menu_info) {
                viewModel.appCatalog().actions.openAppInfo(pkg, member.className, member.userSerial)
            }
        }
        menu.addRow(activity.getString(R.string.edit), R.drawable.ic_menu_edit) {
            // The folder closes first: as a focusable window it would keep
            // the keyboard from the sheet's name field.
            closeFolder()
            surfaces.itemEdits.showItemSheet(member, allowSwipe = !locked)
        }
        if (!locked) addMoveToHomeRow(menu, folder, member, applied)
        member.toLaunchableApp()?.let { app -> surfaces.appActions.addRows(menu, homeExtras(), app) }
        kit.addExportPngRow(menu, memberView, member.label)
        // Held, a row drags out onto the home screen or the dock, as from a
        // home icon's menu; the card closes as the drag starts.
        member.packageName?.let { pkg ->
            kit.addShortcuts(menu, pkg, member.userSerial, draggable = !locked) { shortcut ->
                closeFolder()
                surfaces.launchShortcut(pkg, shortcut.id, member.userSerial)
            }
        }
        return menu
    }

    // The app's widgets in the picker, for an app that has any. `first` runs
    // before the picker opens (an open folder closes).
    private fun addWidgetsRow(menu: SupernovaPopupMenu, packageName: String?, first: () -> Unit) {
        val pkg = packageName ?: return
        if (!WidgetPickerGroups.offersAny(activity, pkg)) return
        menu.addRow(activity.getString(R.string.widgets_row), R.drawable.ic_menu_widgets) {
            first()
            surfaces.widgets.showPickerFor(pkg)
        }
    }

    private fun addMoveToHomeRow(
        menu: SupernovaPopupMenu,
        folder: LauncherItem,
        member: LauncherItem,
        applied: (LauncherModel) -> Unit
    ) {
        menu.addRow(activity.getString(R.string.remove_from_folder), R.drawable.ic_menu_add) {
            TappedPlacement.place(viewModel, pageForMembers(folder)) { spot ->
                val before = viewModel.state.value.model
                // The move a drag onto that free cell makes, so a folder
                // left with one app gives way to it here too.
                viewModel.moveItemToWorkspace(member.id, spot.page, spot.cellX, spot.cellY, mergeFolders = false)
                applied(before)
            }
        }
    }

    // Where a member taken out of a folder goes: the folder's page. A docked
    // folder's page is left over from before it was docked and may be gone,
    // so its members land on the page in view instead.
    private fun pageForMembers(folder: LauncherItem): Int {
        val lastPage = (viewModel.state.value.model.pageCount - 1).coerceAtLeast(0)
        val page = if (folder.container == ContainerType.DOCK) {
            surfaces.workspaceAdapter.realPage(surfaces.binding.workspacePager.currentItem)
        } else {
            folder.page
        }
        return page.coerceIn(0, lastPage)
    }

    private fun homeExtras() = PopupRows.decode(viewModel.state.value.prefs.popupItemsHome, PopupRows.homeDefaults)
}
