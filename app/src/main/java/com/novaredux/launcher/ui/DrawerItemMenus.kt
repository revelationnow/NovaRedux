package com.novaredux.launcher.ui

import android.view.View
import android.widget.PopupWindow
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.novaredux.launcher.R
import com.novaredux.launcher.data.DrawerGroup
import com.novaredux.launcher.data.GalleryIconCodec
import com.novaredux.launcher.data.LaunchableApp

// The menus of what can go on the home screen: an app, a shortcut or a
// drawer folder held in the drawer, in search, or in a drawer folder's card.
// The long-presses of every new-item surface end here: the item's menu
// while the finger is held, and a drag to the home screen once it moves.
class DrawerItemMenus(private val surfaces: ItemSurfaces) {
    private val activity get() = surfaces.activity
    private val viewModel get() = surfaces.viewModel
    private val kit get() = surfaces.menuKit
    private val desktopLocked get() = viewModel.state.value.prefs.desktopLocked

    // The drawer's keyboard goes first: otherwise the menu opens over it and
    // only slides down a moment later, when the finger lifts and the menu
    // takes focus - the menu appearing to jump for no reason the user can
    // see. The drawer is adjustNothing, so nothing under the menu moves when
    // the keyboard goes.
    fun longPressApp(view: View, app: LaunchableApp) {
        surfaces.drawer.hideKeyboardIfShowing()
        // A private space app stays in the drawer: on the home screen it
        // would show while the space is locked.
        surfaces.drag.arm(view, DragPayload.DrawerApp(app), draggable = !app.privateSpace) { appMenu(view, app) }
    }

    fun longPressShortcut(view: View, shortcut: DragPayload.DrawerShortcut) {
        surfaces.drawer.hideKeyboardIfShowing()
        surfaces.drag.arm(view, shortcut, draggable = !viewModel.isPrivateSerial(shortcut.userSerial)) {
            shortcutMenu(view, shortcut)
        }
    }

    fun longPressFolder(view: View, group: DrawerGroup, apps: List<LaunchableApp>) {
        surfaces.drawer.hideKeyboardIfShowing()
        // Search finds a folder whose apps are all hidden too; lifted, it
        // closed the drawer only to be refused where it was dropped.
        surfaces.drag.arm(view, DragPayload.DrawerFolder(group, apps), draggable = apps.isNotEmpty()) {
            folderMenu(view, group, apps)
        }
    }

    // Drawer menu: Add to home, App info, Uninstall, Edit (+ Hide).
    // Placement into the dock, opening, and widgets are all drag/tap
    // gestures, so they leave the menu. Extra rows stay opt-in.
    //
    // source: the drawer folder's window the app was long-pressed in, if any.
    // It closes with the drawer when the app goes home, before Edit (as a
    // focusable window it would keep the sheet's field from the keyboard),
    // after Hide, which takes the app out of it, and after a shortcut the
    // menu opens on a locked desktop if the Close folder when launching
    // setting is on, as after the card's own launches.
    fun appMenu(anchor: View, app: LaunchableApp, source: PopupWindow? = null): NovaReduxPopupMenu {
        val menu = kit.popup(forItem = true)
        kit.addNotifications(menu, app.packageName, app.userSerial)
        val extras = PopupRows.decode(viewModel.state.value.prefs.popupItemsDrawer, PopupRows.drawerDefaults)
        // Nothing of a private space app goes home (longPressApp).
        val homeClosed = desktopLocked || app.privateSpace
        if (!homeClosed) {
            menu.addRow(activity.getString(R.string.add_to_home), R.drawable.ic_menu_add) {
                TappedPlacement.place(viewModel, viewModel.state.value.currentPage) { spot ->
                    viewModel.addAppToWorkspace(app, spot.page, spot.cellX, spot.cellY)
                    source?.dismiss()
                    surfaces.closeItemSources()
                }
            }
        }
        menu.addRow(activity.getString(R.string.app_info), R.drawable.ic_menu_info) {
            viewModel.appCatalog().actions.openAppInfo(app.packageName, app.className, app.userSerial)
        }
        if (PopupExtraRow.UNINSTALL in extras) {
            menu.addRow(activity.getString(R.string.uninstall), R.drawable.ic_menu_uninstall) {
                activity.startActivity(viewModel.appCatalog().actions.uninstallIntent(app.packageName, app.userSerial))
            }
        }
        menu.addRow(activity.getString(R.string.edit), R.drawable.ic_menu_edit) {
            source?.dismiss()
            val pickPhoto: PhotoPicker = surfaces.itemEdits::pickPhoto
            AppEditSheet.show(activity, activity.lifecycleScope, viewModel, app, pickPhoto, fromDrawer = true)
        }
        addHideRow(menu, app, PopupExtraRow.HIDE in extras, source)
        surfaces.appActions.addRows(menu, extras, app)
        addShortcutRows(menu, anchor, app, source, homeClosed)
        menu.show(anchor, surfaces.menuTouchX)
        return menu
    }

    // A hidden app only gets here through search or Show hidden apps,
    // whatever the row setting: Unhide. Otherwise Hide, when the setting
    // has it.
    private fun addHideRow(menu: NovaReduxPopupMenu, app: LaunchableApp, offered: Boolean, source: PopupWindow?) {
        if (viewModel.isAppHidden(app)) {
            menu.addRow(activity.getString(R.string.unhide_app), R.drawable.ic_menu_hide) {
                viewModel.setAppHidden(app, false)
                source?.dismiss()
            }
            return
        }
        if (!offered) return
        menu.addRow(activity.getString(R.string.hide_app), R.drawable.ic_menu_hide) {
            viewModel.setAppHidden(app, true)
            source?.dismiss()
            // Where it went, and the way back.
            // Raised over the drawer and the search window it was hidden
            // from: at its own elevation it drew under them, and a tap
            // on Undo opened the app beneath (#137).
            Snackbar.make(
                surfaces.binding.root, activity.getString(R.string.app_hidden, app.label), Snackbar.LENGTH_LONG
            )
                .setAction(R.string.undo) { viewModel.setAppHidden(app, false) }
                .apply { view.elevation = activity.dp(OVER_OVERLAYS_ELEVATION_DP) }
                .show()
        }
    }

    // Drawer semantics: tapping a shortcut pins it to the workspace. A
    // locked desktop takes nothing new, so there the row opens the shortcut
    // instead, as a home icon's menu does, and the card or search window it
    // came from closes as after any launch from them.
    private fun addShortcutRows(
        menu: NovaReduxPopupMenu,
        anchor: View,
        app: LaunchableApp,
        source: PopupWindow?,
        homeClosed: Boolean
    ) {
        kit.addShortcuts(menu, app.packageName, app.userSerial, draggable = !homeClosed) { shortcut ->
            if (homeClosed) {
                surfaces.launchShortcut(app.packageName, shortcut.id, app.userSerial, anchor)
                if (viewModel.state.value.prefs.folderCloseOnLaunch) source?.dismiss()
                surfaces.hideSearch()
                return@addShortcuts
            }
            TappedPlacement.place(viewModel, viewModel.state.value.currentPage) { spot ->
                val icon = surfaces.shortcuts.ownIcon(shortcut)
                viewModel.addShortcutToWorkspace(
                    app = app,
                    shortcutId = shortcut.id,
                    label = shortcut.shortLabel?.toString() ?: app.label,
                    page = spot.page,
                    cellX = spot.cellX,
                    cellY = spot.cellY,
                    iconBase64 = icon?.let { GalleryIconCodec.encode(it) }
                )
                surfaces.shortcuts.pin(app.packageName, shortcut.id, app.userSerial)
                source?.dismiss()
                surfaces.closeItemSources()
            }
        }
    }

    // An app shortcut long-pressed in search: Add to Home and its app's App
    // info. A drag pins it where it lands.
    fun shortcutMenu(anchor: View, shortcut: DragPayload.DrawerShortcut): NovaReduxPopupMenu {
        val menu = kit.popup()
        if (!desktopLocked && !viewModel.isPrivateSerial(shortcut.userSerial)) {
            menu.addRow(activity.getString(R.string.add_to_home), R.drawable.ic_menu_add) {
                TappedPlacement.place(viewModel, viewModel.state.value.currentPage) { spot ->
                    val item = surfaces.shortcuts.homeItem(shortcut)
                    viewModel.addShortcutToWorkspace(
                        item.owner, shortcut.shortcutId, shortcut.label, spot.page, spot.cellX, spot.cellY,
                        iconBase64 = item.iconBase64
                    )
                    surfaces.closeItemSources()
                }
            }
        }
        viewModel.state.value.apps
            .firstOrNull { it.packageName == shortcut.packageName && it.userSerial == shortcut.userSerial }
            ?.let { app ->
                menu.addRow(activity.getString(R.string.app_info), R.drawable.ic_menu_info) {
                    viewModel.appCatalog().actions.openAppInfo(app.packageName, app.className, app.userSerial)
                }
            }
        menu.show(anchor, surfaces.menuTouchX)
        return menu
    }

    // A drawer folder long-pressed: Add to Home puts a copy of it on the
    // home screen (a drag does too, where it lands); Select apps picks its
    // apps; Edit opens its page in Settings > Drawer groups, and Group
    // settings that list.
    fun folderMenu(anchor: View, group: DrawerGroup, apps: List<LaunchableApp>): NovaReduxPopupMenu {
        val menu = kit.popup()
        val pickers = surfaces.pickers
        if (!desktopLocked && apps.isNotEmpty()) {
            menu.addRow(activity.getString(R.string.add_to_home), R.drawable.ic_menu_add) {
                TappedPlacement.place(viewModel, viewModel.state.value.currentPage) { spot ->
                    viewModel.addFolderToWorkspace(group, apps, spot.page, spot.cellX, spot.cellY)
                    surfaces.closeItemSources()
                }
            }
        }
        // A drawer folder lists its apps in the drawer's order, so it has no
        // order of its own to Sort.
        menu.addRow(activity.getString(R.string.select_apps), R.drawable.ic_menu_select_apps) {
            pickers.groupApps(group)
        }
        // Its page in Settings > Drawer groups: name, icon, tab or folder,
        // apps, and Delete.
        menu.addRow(activity.getString(R.string.edit), R.drawable.ic_menu_edit) { pickers.openGroupSettings(group.id) }
        // The Drawer groups list, as a tab's menu has it.
        menu.addRow(activity.getString(R.string.group_settings), R.drawable.ic_tab_settings) {
            pickers.openGroupSettings(null)
        }
        menu.show(anchor, surfaces.menuTouchX)
        return menu
    }

    private companion object {
        // Above the drawer (12dp), the edit sheet (14dp) and the search
        // window (16dp); below the drop targets (20dp).
        const val OVER_OVERLAYS_ELEVATION_DP = 18f
    }
}
