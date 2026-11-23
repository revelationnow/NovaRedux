package com.supernova.launcher.ui

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.supernova.launcher.databinding.ActivityLauncherBinding

// What the item menus, the edit sheets and the removals reach the home
// screen through: the activity, its controllers and the pieces they share.
// The launcher activity is the one implementation; everything here is read
// when it is used, never while the activity is being made.
interface ItemSurfaces {
    val activity: AppCompatActivity
    val viewModel: LauncherViewModel
    val binding: ActivityLauncherBinding
    val workspaceAdapter: WorkspacePagerAdapter
    val drag: LiveDragController
    val widgets: WidgetController
    val drawer: DrawerController
    val itemIcons: ItemIcons
    val shortcuts: AppShortcuts
    val menuKit: ItemMenuKit
    val itemEdits: ItemEdits
    val appActions: AppActions
    val removal: ItemRemoval
    val moveToDrawer: FolderToDrawerPrompt
    val pickers: GroupPickers

    // X of the last touch-down, in window coordinates: an item's menu opens
    // there.
    val menuTouchX: Float

    fun launchShortcut(packageName: String, shortcutId: String, userSerial: Long, anchor: View? = null)

    // What a new item's menu closes as it sends the item home: the drawer,
    // the search window, the feed that search was opened over, and a folder
    // card no drag is carried by.
    fun closeItemSources()

    fun hideSearch()
}
