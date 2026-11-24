package com.supernova.launcher.ui

import android.content.Intent
import android.view.View
import com.supernova.launcher.R
import com.supernova.launcher.SettingsActivity
import com.supernova.launcher.data.DrawerGroup
import com.supernova.launcher.data.DrawerGroupType
import com.supernova.launcher.data.ItemType
import com.supernova.launcher.data.LaunchableApp
import com.supernova.launcher.data.LauncherItem

// What goes in a group: the Select apps pickers of a home folder, a drawer
// group and the hidden apps, a drawer tab's menu, and the way into
// Settings > Drawer groups.
class GroupPickers(private val surfaces: ItemSurfaces) {
    private val activity get() = surfaces.activity
    private val viewModel get() = surfaces.viewModel

    // A home folder's checklist: every visible app, checked = member. Newly
    // checked apps become fresh items inside the folder; unchecked members
    // move out to a free workspace cell (or are removed when no page has
    // room).
    fun folderApps(folder: LauncherItem) {
        val apps = viewModel.visibleApps()
        if (apps.isEmpty()) return
        fun keyOf(pkg: String?, cls: String?, serial: Long) = "$serial/$pkg/$cls"
        val memberByKey = HashMap<String, LauncherItem>()
        // App icons only. A pinned shortcut carries its app's package and
        // class, and ticked the app as if it were in the folder - unticking
        // it then took the shortcut out.
        viewModel.folderMembers(folder.id).filter { it.itemType == ItemType.APP }.forEach { member ->
            memberByKey.putIfAbsent(keyOf(member.packageName, member.className, member.userSerial), member)
        }
        val checked = apps
            .map { keyOf(it.packageName, it.className, it.userSerial) in memberByKey }
            .toBooleanArray()

        val checklist = AppChecklist(
            activity, apps, checked.copyOf(), viewModel.appCatalog()::iconFor, LaunchableApp::label
        )
        checklist.show(folder.label.ifBlank { activity.getString(R.string.folder) }) { ticks ->
            val added = ArrayList<LaunchableApp>()
            val removed = HashSet<String>()
            apps.forEachIndexed { index, app ->
                val member = memberByKey[keyOf(app.packageName, app.className, app.userSerial)]
                when {
                    ticks[index] && member == null -> added += app
                    !ticks[index] && member != null -> removed += member.id
                }
            }
            if (added.isNotEmpty() || removed.isNotEmpty()) {
                viewModel.applyFolderChecklist(folder.id, added, removed)
            }
        }
    }

    fun groupApps(group: DrawerGroup, name: String? = null) {
        // A folder on the personal list offers no work apps while they have
        // their own tab.
        val personalOnly = group.type == DrawerGroupType.FOLDER && viewModel.workTabActive()
        val apps = if (personalOnly) viewModel.personalApps() else viewModel.visibleApps()
        GroupChecklist(activity, viewModel, surfaces.itemIcons).show(group, apps, name)
    }

    // Hidden apps behind the phone's lock while Lock hidden apps is on (#4).
    private fun unlockHidden(then: () -> Unit) = HiddenAppsLock.whenOpen(
        viewModel.state.value.prefs, surfaces.deviceAuth, activity.getString(R.string.hidden_apps_unlock), then
    )

    // Settings' Hidden apps picker, from the All tab's menu.
    fun hiddenApps() {
        val apps = viewModel.state.value.apps
        val checked = apps.map { viewModel.isAppHidden(it) }.toBooleanArray()
        val checklist = AppChecklist(
            activity, apps, checked.copyOf(), viewModel.appCatalog()::iconFor, LaunchableApp::label
        )
        checklist.show(activity.getString(R.string.hidden_apps)) { ticks ->
            apps.indices.filter { ticks[it] != checked[it] }.forEach { viewModel.setAppHidden(apps[it], ticks[it]) }
        }
    }

    // Settings > Drawer groups, on the group's own page (by its id), or the list.
    fun openGroupSettings(group: String?) {
        activity.startActivity(
            Intent(activity, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_DRAWER_GROUP, group.orEmpty())
        )
    }

    // A drawer tab held, or tapped while it shows. A group tab: Edit tab (its
    // page in Settings > Drawer groups), Select apps, and Group settings (the
    // Drawer groups list). The All tab: its hidden apps, shown in its list for
    // a moment (Show hidden apps) or picked (Hide apps), and Group settings.
    // Handed back so a drag of the tab can close it.
    fun tabMenu(anchor: View, group: DrawerGroup?): SupernovaPopupMenu {
        val drawer = surfaces.drawer
        drawer.hideKeyboardIfShowing()
        val menu = surfaces.menuKit.popup()
        if (group == null) {
            // Says what it will do: shown, the hidden apps go back out of sight.
            if (drawer.hiddenAppsShown) {
                menu.addRow(activity.getString(R.string.hide_hidden_apps), R.drawable.ic_menu_hide) {
                    drawer.toggleHiddenApps()
                }
            } else {
                menu.addRow(activity.getString(R.string.show_hidden_apps), R.drawable.ic_menu_show) {
                    unlockHidden { drawer.toggleHiddenApps() }
                }
            }
            menu.addRow(activity.getString(R.string.hide_apps), R.drawable.ic_menu_hide) {
                unlockHidden { hiddenApps() }
            }
        } else {
            // Same rows, same order as a drawer folder's menu (#241).
            menu.addRow(activity.getString(R.string.select_apps), R.drawable.ic_menu_select_apps) { groupApps(group) }
            menu.addRow(activity.getString(R.string.edit), R.drawable.ic_menu_edit) { openGroupSettings(group.id) }
        }
        menu.addRow(activity.getString(R.string.group_settings), R.drawable.ic_tab_settings) { openGroupSettings(null) }
        // Tracked as the item menus are, so back and home close it.
        surfaces.drag.activeItemPopup = menu
        menu.show(anchor)
        return menu
    }
}
