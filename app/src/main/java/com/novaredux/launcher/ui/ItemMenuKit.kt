package com.novaredux.launcher.ui

import android.annotation.SuppressLint
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Process
import android.provider.DocumentsContract
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.drawToBitmap
import androidx.lifecycle.lifecycleScope
import com.novaredux.launcher.R
import com.novaredux.launcher.data.ContainerType
import com.novaredux.launcher.data.LARGE_ICON_SPAN
import com.novaredux.launcher.data.LauncherItem
import com.novaredux.launcher.data.isLargeIcon
import com.novaredux.launcher.notification.NovaReduxNotificationListener
import com.novaredux.launcher.runLogged
import com.novaredux.launcher.ui.theme.Styles
import com.novaredux.launcher.ui.theme.SurfaceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// What every item's menu is built from: the popup in the user's style, the
// notifications on top, an app's shortcuts (a tap, and a drag out onto the
// home screen), and the Large icon and Export PNG rows. Made with the
// activity, as Export PNG's file picker has to be registered before the
// activity starts.
class ItemMenuKit(private val surfaces: ItemSurfaces) {
    private val activity get() = surfaces.activity
    private val viewModel get() = surfaces.viewModel

    // Labs' Export PNG: the image is drawn when the row is tapped and waits
    // in the view model while the picker asks where it goes, so a recreate
    // behind the picker (a theme flip at sunset) keeps it. The picker has
    // made the file by the time it answers: when the process died meanwhile
    // and the image with it, that empty file goes again.
    private val exportPngLauncher = surfaces.activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val bitmap = viewModel.pendingExportPng
        viewModel.pendingExportPng = null
        if (uri != null) writePng(uri, bitmap)
    }

    // forItem: an app's, folder's or widget's own menu, whose actions can be
    // a row of icons (popupActionIcons); the smaller menus keep their labels.
    fun popup(forItem: Boolean = false): NovaReduxPopupMenu {
        val prefs = viewModel.state.value.prefs
        val menu = NovaReduxPopupMenu(
            activity, Styles.resolve(activity, prefs).of(SurfaceId.POPUP), Animations.enabled(prefs),
            cardBackground = prefs.popupCardBg,
            style = prefs.popupStyle,
            groupShortcuts = prefs.popupGroupShortcuts
        )
        menu.actionIcons = forItem && prefs.popupActionIcons
        // Same dismiss contract PopupMenu had; the armed-drag interplay
        // (activeItemPopup dismissal in routeItemGesture) relies on it.
        menu.setOnDismissListener { if (surfaces.drag.activeItemPopup === menu) surfaces.drag.activeItemPopup = null }
        return menu
    }

    // Up to two notification lines on top of the popup, gated by the pref and
    // by notification access (previews are empty without the listener).
    fun addNotifications(menu: NovaReduxPopupMenu, packageName: String?, userSerial: Long) {
        packageName ?: return
        if (!viewModel.state.value.prefs.popupNotifications) return
        val user = viewModel.appCatalog().userForSerial(userSerial) ?: Process.myUserHandle()
        NovaReduxNotificationListener.previewsFor(packageName, user.hashCode()).forEach { preview ->
            menu.addNotification(preview.title, preview.text) {
                // The app's own tap intent; a stale one just no-ops.
                runLogged("open notification") { preview.contentIntent?.let(PendingStarts::send) }
            }
        }
    }

    // The app's first few shortcuts. Held, a row drags out onto the home
    // screen or the dock, unless `draggable` is off (a locked desktop takes
    // nothing new); `onTap` is what a tap on one does.
    fun addShortcuts(
        menu: NovaReduxPopupMenu,
        packageName: String,
        userSerial: Long,
        draggable: Boolean,
        onTap: (ShortcutInfo) -> Unit
    ) {
        surfaces.shortcuts.published(packageName, userSerial).take(MENU_SHORTCUTS).forEach { shortcut ->
            menu.addShortcut(
                label(shortcut),
                surfaces.shortcuts.badgedIcon(shortcut),
                onLongPress = shortcutDrag(menu, packageName, shortcut, userSerial).takeIf { draggable }
            ) { onTap(shortcut) }
        }
    }

    // Large icon / Normal icon, for an icon or folder on the desktop: a large
    // one takes 2x2 cells and draws its icon twice the size. A grid too
    // small for one has no row; a page with no room says so.
    fun addIconSizeRow(menu: NovaReduxPopupMenu, item: LauncherItem) {
        if (item.container != ContainerType.WORKSPACE) return
        val prefs = viewModel.state.value.prefs
        val large = item.isLargeIcon
        if (!large && (prefs.gridCols < LARGE_ICON_SPAN || prefs.gridRows < LARGE_ICON_SPAN)) return
        val label = activity.getString(if (large) R.string.normal_icon else R.string.large_icon)
        menu.addRow(label, R.drawable.ic_menu_resize) {
            if (!viewModel.setIconSize(item.id, !large)) {
                Toast.makeText(activity, R.string.no_room_on_page, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // The item as it sits on the home screen, on a transparent background.
    fun addExportPngRow(menu: NovaReduxPopupMenu, view: View, name: String) {
        if (!viewModel.state.value.prefs.labsExportPng) return
        menu.addRow(activity.getString(R.string.export_png), R.drawable.ic_menu_export) {
            viewModel.pendingExportPng = runLogged("draw icon for PNG export") {
                view.drawToBitmap()
            }.getOrNull() ?: return@addRow
            val file = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "novaredux" }
            exportPngLauncher.launch("$file.png")
        }
    }

    private fun writePng(uri: Uri, bitmap: Bitmap?) {
        if (bitmap == null) {
            runLogged("delete the unwritten PNG") { DocumentsContract.deleteDocument(activity.contentResolver, uri) }
            Toast.makeText(activity, R.string.export_png_lost, Toast.LENGTH_LONG).show()
            return
        }
        activity.lifecycleScope.launch {
            runLogged("export icon as PNG") {
                withContext(Dispatchers.IO) {
                    activity.contentResolver.openOutputStream(uri)?.use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
                    } ?: error("no output stream")
                }
            }.onSuccess {
                Toast.makeText(activity, R.string.export_png_saved, Toast.LENGTH_SHORT).show()
            }.onFailure {
                val message = activity.getString(R.string.export_png_failed, it.message ?: "unknown")
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // A shortcut held in an item's menu drags out of it onto the home screen
    // or the dock, as one from search does. The menu's window carries the rest
    // of that gesture, so it hands its touches to the drag engine while the
    // drag is the engine's; the menu stays the active one until the drag
    // starts, and then hides and closes with the drop. Private space
    // shortcuts stay in the menu: on the home screen they would show while
    // the space is locked. The interceptor only hands a drag to the engine;
    // a tap still reaches the row as a click.
    @SuppressLint("ClickableViewAccessibility")
    private fun shortcutDrag(
        menu: NovaReduxPopupMenu,
        packageName: String,
        shortcut: ShortcutInfo,
        userSerial: Long
    ): ((View) -> Unit)? {
        if (viewModel.isPrivateSerial(userSerial)) return null
        val drag = surfaces.drag
        val window = menu.window
        window.setTouchInterceptor { _, ev ->
            drag.ownsGestureIn(window) && drag.routeGesture(ev, window)
        }
        return { row ->
            val payload = DragPayload.DrawerShortcut(
                packageName, shortcut.id, userSerial, label(shortcut).toString(),
                surfaces.shortcuts.badgedIcon(shortcut)
            )
            drag.arm(row, payload, window = window, menu = null)
            drag.activeItemPopup = menu
        }
    }

    private fun label(shortcut: ShortcutInfo): CharSequence = shortcut.shortLabel ?: shortcut.longLabel ?: shortcut.id

    private companion object {
        // How many of an app's shortcuts its menu lists.
        const val MENU_SHORTCUTS = 4
        const val PNG_QUALITY = 100
    }
}
