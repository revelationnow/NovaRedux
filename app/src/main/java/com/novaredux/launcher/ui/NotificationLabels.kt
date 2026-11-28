package com.novaredux.launcher.ui

import com.novaredux.launcher.data.ItemType
import com.novaredux.launcher.data.LauncherItem
import com.novaredux.launcher.data.LauncherPrefs

// Home screen icons whose label shows their app's newest notification in
// place of its name (#15), for the apps chosen under Notification badges.
// The name comes back when the notification goes.
object NotificationLabels {

    // How the setting holds an app, as Badged apps does.
    fun key(userSerial: Long, packageName: String, className: String): String =
        "$userSerial/$packageName/$className"

    fun chosen(item: LauncherItem, prefs: LauncherPrefs): Boolean {
        val pkg = item.packageName
        return item.itemType == ItemType.APP && pkg != null && prefs.notificationTextApps.isNotEmpty() &&
            key(item.userSerial, pkg, item.className.orEmpty()) in prefs.notificationTextApps
    }

    // Which apps' lines changed between two of the listener's states, by its
    // "$userHash/$packageName" keys; none while no app is chosen.
    fun changedKeys(prefs: LauncherPrefs, old: Map<String, String>, new: Map<String, String>): Set<String> =
        if (prefs.notificationTextApps.isEmpty()) emptySet() else BadgeDiff.changedValues(old, new)
}
