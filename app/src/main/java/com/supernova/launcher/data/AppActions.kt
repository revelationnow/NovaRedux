package com.supernova.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import androidx.core.net.toUri

// What the launcher does with an app beyond drawing it: launches it, opens
// its info and uninstall pages, and asks whether it is installed. Through
// LauncherApps, for whichever profile the app is in (by its user serial).
class AppActions(
    private val context: Context,
    private val launcherApps: LauncherApps,
    private val userManager: UserManager,
    private val userForSerial: (Long) -> UserHandle?
) {
    fun launch(
        app: LaunchableApp,
        sourceBounds: android.graphics.Rect? = null,
        options: android.os.Bundle? = null
    ) {
        val user = requireNotNull(userForSerial(app.userSerial)) { "Unknown user ${app.userSerial}" }
        launcherApps.startMainActivity(
            ComponentName(app.packageName, app.className), user, sourceBounds, options
        )
    }

    fun openAppInfo(packageName: String, className: String?, userSerial: Long) {
        val user = userForSerial(userSerial)
        if (user != null && className != null) {
            launcherApps.startAppDetailsActivity(ComponentName(packageName, className), user, null, null)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:$packageName".toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }

    fun uninstallIntent(packageName: String, userSerial: Long): Intent {
        return Intent(Intent.ACTION_DELETE).apply {
            data = "package:$packageName".toUri()
            userForSerial(userSerial)?.let { putExtra(Intent.EXTRA_USER, it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    // The activity an icon brought in from elsewhere should launch: the one
    // it names while the app still has it, else the app's first launcher
    // activity. An app that is not here keeps what it names.
    fun launchActivity(packageName: String, className: String?, userSerial: Long): String? {
        val user = userForSerial(userSerial) ?: return className
        val activities = runCatching { launcherApps.getActivityList(packageName, user) }.getOrDefault(emptyList())
            .map { it.componentName.className }
        return className?.takeIf { activities.isEmpty() || it in activities } ?: activities.firstOrNull()
    }

    fun isInstalled(packageName: String, userSerial: Long): Boolean {
        val user = userForSerial(userSerial) ?: return false
        return runCatching { launcherApps.isPackageEnabled(packageName, user) }.getOrDefault(false)
    }

    // Whether the app is surely gone for that user (see InstalledApps).
    fun isUninstalled(packageName: String, userSerial: Long): Boolean =
        userForSerial(userSerial)?.let { InstalledApps.isGone(launcherApps, userManager, packageName, it) } == true
}
