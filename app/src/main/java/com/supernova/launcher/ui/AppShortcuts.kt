package com.supernova.launcher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Process
import com.supernova.launcher.data.GalleryIconCodec
import com.supernova.launcher.data.LaunchableApp
import com.supernova.launcher.runLogged

// An app's shortcuts as the launcher reads them (LauncherApps): the ones an
// item's menu lists, their icons, and the pin that keeps one on the home
// screen working.
class AppShortcuts(private val context: Context, private val viewModel: LauncherViewModel) {
    private val launcherApps: LauncherApps? get() = context.getSystemService(LauncherApps::class.java)

    private fun userFor(userSerial: Long) =
        viewModel.appCatalog().userForSerial(userSerial) ?: Process.myUserHandle()

    // Every shortcut the app offers: dynamic, manifest and pinned.
    fun forApp(packageName: String, userSerial: Long): List<ShortcutInfo> = query(packageName, userSerial, ANY)

    // The ones the app itself offers (dynamic and manifest), as an item's
    // menu lists them: a pin outlives the app's own entry, so a removed
    // contact's shortcut no longer comes back (Supernova issue 324).
    fun published(packageName: String, userSerial: Long): List<ShortcutInfo> =
        query(packageName, userSerial, PUBLISHED)

    private fun query(packageName: String, userSerial: Long, flags: Int): List<ShortcutInfo> {
        val apps = launcherApps ?: return emptyList()
        val query = LauncherApps.ShortcutQuery().setPackage(packageName).setQueryFlags(flags)
        return runCatching { apps.getShortcuts(query, userFor(userSerial)) ?: emptyList() }.getOrElse { emptyList() }
    }

    fun find(packageName: String, shortcutId: String, userSerial: Long): ShortcutInfo? {
        val apps = launcherApps ?: return null
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setShortcutIds(listOf(shortcutId))
            .setQueryFlags(ANY)
        return runCatching { apps.getShortcuts(query, userFor(userSerial))?.firstOrNull() }.getOrNull()
    }

    // The icon a menu row shows, with the work profile's badge.
    fun badgedIcon(shortcut: ShortcutInfo): Drawable? = runCatching {
        launcherApps?.getShortcutBadgedIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)
    }.getOrNull()

    // The shortcut's own icon, as a home item keeps it.
    fun ownIcon(shortcut: ShortcutInfo): Drawable? = runCatching {
        launcherApps?.getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)
    }.getOrNull()

    // A shortcut on the home screen is pinned to this launcher, as a pin
    // request's is and as Launcher3 pins its own: an app can drop a dynamic
    // shortcut at any time, and an unpinned home item then no longer starts.
    // pinShortcuts sets the package's whole pinned list, so it is the list
    // already pinned plus this one. Never unpinned here: an undone removal
    // would find a pin-only shortcut gone.
    fun pin(packageName: String, shortcutId: String, userSerial: Long) {
        val apps = launcherApps ?: return
        val user = userFor(userSerial)
        runLogged("query app shortcuts") {
            val query = LauncherApps.ShortcutQuery()
                .setPackage(packageName)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            val pinned = apps.getShortcuts(query, user).orEmpty().map { it.id }
            if (shortcutId !in pinned) apps.pinShortcuts(packageName, pinned + shortcutId, user)
        }
    }

    // A drawer shortcut's home item, worked out as it is placed: filed under
    // the activity the shortcut belongs to (else its app), with the
    // shortcut's own icon kept inline as a pin request's is
    // (handleCreateShortcutResult) - without it the item drew its app's icon
    // - and pinned to this launcher (pin).
    fun homeItem(shortcut: DragPayload.DrawerShortcut): ShortcutItem {
        val info = find(shortcut.packageName, shortcut.shortcutId, shortcut.userSerial)
        val className = info?.activity?.className
            ?: installedApp(shortcut.packageName, shortcut.userSerial)?.className.orEmpty()
        val icon = info?.let(::ownIcon) ?: shortcut.icon
        pin(shortcut.packageName, shortcut.shortcutId, shortcut.userSerial)
        return ShortcutItem(
            LaunchableApp(shortcut.packageName, className, shortcut.label, shortcut.userSerial),
            icon?.let { GalleryIconCodec.encode(it) }
        )
    }

    private fun installedApp(packageName: String, userSerial: Long): LaunchableApp? =
        viewModel.state.value.apps.firstOrNull { it.packageName == packageName && it.userSerial == userSerial }

    private companion object {
        const val PUBLISHED = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
        const val ANY = PUBLISHED or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
    }
}
