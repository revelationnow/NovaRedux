package com.supernova.launcher.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.supernova.launcher.R
import com.supernova.launcher.data.AppOverride
import com.supernova.launcher.data.ItemType
import com.supernova.launcher.data.LaunchableApp
import com.supernova.launcher.data.LauncherItem
import com.supernova.launcher.gesture.ActionShortcut
import com.supernova.launcher.gesture.GestureAction
import com.supernova.launcher.runLogged

// An item's Edit: its sheet (name, icon, swipes), the picker an icon's
// swipe is bound with, and the photo picker a custom icon comes from. Made
// with the activity, as the pickers have to be registered before the
// activity starts.
class ItemEdits(private val surfaces: ItemSurfaces) {
    private val activity get() = surfaces.activity
    private val viewModel get() = surfaces.viewModel

    // An icon swipe waiting on an app's shortcut screen (the action picker's
    // Shortcuts tab), and where its answer goes.
    private class PendingSwipeShortcut(val itemId: String, val down: Boolean, val onPicked: (String?) -> Unit)
    private var pendingSwipeShortcut: PendingSwipeShortcut? = null

    private val createSwipeShortcutLauncher =
        surfaces.activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val pending = pendingSwipeShortcut ?: return@registerForActivityResult
            pendingSwipeShortcut = null
            if (result.resultCode == Activity.RESULT_OK) saveCreatedShortcut(pending, result.data)
        }

    // Gallery picker for per-app custom icons. The launcher must be registered
    // up front; the sheet that wants the result parks its callback here.
    private var photoPickCallback: ((Uri?) -> Unit)? = null
    private val pickPhotoLauncher =
        surfaces.activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { photoPicked(it) }

    // The system file picker reaches every storage provider and file manager,
    // where the photo picker shows only the media library.
    private val pickPhotoFileLauncher =
        surfaces.activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { photoPicked(it) }

    // The sheets' PhotoPicker.
    fun pickPhoto(fromFiles: Boolean, onPicked: (Uri?) -> Unit) {
        photoPickCallback = onPicked
        runLogged("open photo picker") {
            if (fromFiles) {
                pickPhotoFileLauncher.launch(arrayOf("image/*"))
            } else {
                pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }.onFailure { Toast.makeText(activity, R.string.launch_failed, Toast.LENGTH_SHORT).show() }
    }

    // Edit for a placed workspace item: the app-level fields (label, icon)
    // plus per-item swipe gestures, so the popup menu stays minimal.
    fun showItemSheet(item: LauncherItem, allowSwipe: Boolean) {
        // A launcher action has a name and an icon of its own, as a folder has.
        if (ActionShortcut.decode(item.intentUri) != null) showFolderSheet(item) else showAppSheet(item, allowSwipe)
    }

    // Folder Edit: name, icon, Swipe to open folder and the swipe actions in
    // one sheet, as an app's Edit holds its label, icon and swipes. A
    // launcher action's Edit is this one too.
    fun showFolderSheet(item: LauncherItem) {
        FolderEditSheet.show(
            activity, activity.lifecycleScope, viewModel, item, ::pickPhoto,
            onEditSwipe = { down, onPicked -> showSwipePicker(item, down) { onPicked(it) } }
        )
    }

    private fun showAppSheet(item: LauncherItem, allowSwipe: Boolean) {
        val app = item.toLaunchableApp()
        // Anything but an app icon edits its own name, label and icon, not
        // its app's.
        val shortcut = if (item.itemType == ItemType.APP) null else shortcutEdit(item, app)
        if (app == null && shortcut == null) return
        // An app icon's Edit is this copy's own, not the drawer's.
        val placed = item.takeIf { shortcut == null }
        val scope = activity.lifecycleScope
        if (!allowSwipe) {
            AppEditSheet.show(activity, scope, viewModel, app, ::pickPhoto, shortcut = shortcut, placed = placed)
            return
        }
        AppEditSheet.show(
            activity, scope, viewModel, app, ::pickPhoto, shortcut = shortcut, placed = placed,
            // Read live so a re-open (or a just-committed pick) shows the
            // current binding, not the snapshot captured when the menu opened.
            swipeLabels = { down ->
                val live = viewModel.state.value.model.items.firstOrNull { it.id == item.id }
                if (down) live?.swipeLabelDown else live?.swipeLabel
            },
            onEditSwipe = { down, onPicked -> showSwipePicker(item, down) { onPicked(it) } }
        )
    }

    private fun shortcutEdit(item: LauncherItem, app: LaunchableApp?) = AppEditSheet.ShortcutEdit(
        item.label, item.shortcutEdit ?: AppOverride(), pinnedLabel(item),
        // Drawn as the item is on home, with the edit in the sheet.
        preview = { edit ->
            surfaces.itemIcons.intentItemIcon(item.copy(shortcutEdit = edit))
                ?: app?.let { viewModel.appCatalog().iconFor(it) }
        }
    ) { label, edit -> viewModel.editShortcut(item.id, label, edit) }

    // The name an app gives its pinned shortcut, for Edit's Reset; null when
    // the item isn't one or the app no longer has it.
    private fun pinnedLabel(item: LauncherItem): String? {
        val id = item.shortcutId
        val info = item.packageName?.takeIf { id != null }
            ?.let { pkg -> surfaces.shortcuts.forApp(pkg, item.userSerial).firstOrNull { it.id == id } }
        return (info?.shortLabel ?: info?.longLabel)?.toString()?.takeIf { it.isNotBlank() }
    }

    // The action picker for an icon swipe: the launcher's actions (Jump to
    // page included), any app or one of its shortcuts, or an app's shortcut
    // screen. Targets are stored as LaunchTarget strings, action uris
    // (ActionShortcut) or intent URIs (CreatedShortcuts). onPicked reports
    // the committed label (null = cleared) so the Edit sheet can refresh its
    // row without reading the async model write back.
    fun showSwipePicker(item: LauncherItem, down: Boolean, onPicked: (String?) -> Unit = {}) {
        val actions = ActionShortcut.choices + GestureAction.JUMP_TO_PAGE + GestureAction.SHOW_WIDGET
        val title = activity.getString(if (down) R.string.set_swipe_down_action else R.string.set_swipe_up_action)
        ActionPickerSheet.show(activity, viewModel, title, actions) { pick ->
            swipePicked(item, down, pick, onPicked)
        }
    }

    private fun swipePicked(item: LauncherItem, down: Boolean, pick: ActionPick, onPicked: (String?) -> Unit) {
        fun save(target: String?, label: String?) {
            viewModel.setSwipeAction(item.id, target, label, down)
            onPicked(label)
        }
        when (pick) {
            ActionPick.None -> save(null, null)
            // A widget is picked and set up first; the Edit sheet hears of it
            // once it is saved (#14).
            is ActionPick.Action -> if (pick.action == GestureAction.SHOW_WIDGET) {
                surfaces.widgets.pickShutter(item, down) { _, label -> onPicked(label) }
            } else {
                save(ActionShortcut.encode(pick.action), pick.label)
            }
            is ActionPick.Page ->
                save(ActionShortcut.encode(GestureAction.JUMP_TO_PAGE, pick.index.toString()), pick.label)
            // With the app's profile: a swipe can open a work app from a
            // personal icon, and used to open its personal copy.
            is ActionPick.App -> {
                val component = ComponentName(pick.app.packageName, pick.app.className).flattenToString()
                save("$component@${pick.app.userSerial}", pick.app.label)
            }
            is ActionPick.Shortcut ->
                save("${pick.app.packageName}|${pick.shortcutId}@${pick.app.userSerial}", pick.label)
            is ActionPick.Legacy -> {
                pendingSwipeShortcut = PendingSwipeShortcut(item.id, down, onPicked)
                val create = Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(pick.component)
                runLogged("open shortcut creator") { createSwipeShortcutLauncher.launch(create) }.onFailure {
                    pendingSwipeShortcut = null
                    Toast.makeText(activity, R.string.launch_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveCreatedShortcut(pending: PendingSwipeShortcut, data: Intent?) {
        val created = data?.let { CreatedShortcuts.read(activity, it) }
        if (created == null) {
            Toast.makeText(activity, R.string.shortcut_unusable, Toast.LENGTH_SHORT).show()
            return
        }
        val target = when (created) {
            is CreatedShortcut.Pinned -> created.target
            is CreatedShortcut.Legacy -> created.intentUri
        }
        viewModel.setSwipeAction(pending.itemId, target, created.label, pending.down)
        pending.onPicked(created.label)
    }

    private fun photoPicked(uri: Uri?) {
        photoPickCallback?.invoke(uri)
        photoPickCallback = null
    }
}
