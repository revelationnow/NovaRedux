package com.supernova.launcher.ui

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.supernova.launcher.R
import com.supernova.launcher.data.LaunchableApp
import com.supernova.launcher.runLogged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// The opt-in rows of an app's menu (popupItemsHome, popupItemsDrawer): its
// store page, Close app, Relaunch and Save APK. Made with the activity, as
// Save APK's file picker has to be registered before the activity starts.
class AppActions(
    private val activity: AppCompatActivity,
    private val launchApp: (LaunchableApp) -> Unit
) {
    // Save APK: the source path waits here while the picker asks where the
    // copy goes.
    private class PendingApk(val sourceDir: String, val split: Boolean)
    private var pendingApk: PendingApk? = null

    private val saveApkLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        val pending = pendingApk
        pendingApk = null
        if (uri != null && pending != null) writeApk(uri, pending)
    }

    // The configurable extra rows. HIDE is added by the drawer menu itself
    // so it keeps its historical position.
    fun addRows(menu: SupernovaPopupMenu, extras: Set<PopupExtraRow>, app: LaunchableApp) {
        // Opens the app's market:// page, which any installed store can
        // take, so the row names no store.
        if (PopupExtraRow.PLAY_STORE in extras) {
            menu.addRow(activity.getString(R.string.popup_row_store), R.drawable.ic_play_store) {
                openStore(app.packageName)
            }
        }
        if (PopupExtraRow.CLOSE_APP in extras) {
            menu.addRow(activity.getString(R.string.popup_row_close_app), R.drawable.ic_menu_close_app) {
                close(app.packageName, app.label)
            }
        }
        if (PopupExtraRow.RELAUNCH in extras) {
            menu.addRow(activity.getString(R.string.popup_row_relaunch), R.drawable.ic_menu_relaunch) {
                close(app.packageName, app.label, toast = false)
                launchApp(app)
            }
        }
        if (PopupExtraRow.SAVE_APK in extras) {
            menu.addRow(activity.getString(R.string.popup_row_save_apk), R.drawable.ic_menu_save) {
                saveApk(app.packageName)
            }
        }
    }

    // The store's own page for the app, else the Play Store's web page.
    private fun openStore(packageName: String) {
        val opened = view("market://details?id=$packageName") ||
            view("https://play.google.com/store/apps/details?id=$packageName")
        if (!opened) Toast.makeText(activity, R.string.launch_failed, Toast.LENGTH_SHORT).show()
    }

    private fun view(uri: String): Boolean =
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri())) }.isSuccess

    // Android only lets a launcher end an app's background processes, and
    // from Android 14 on not even those of another app, so the toast says
    // what was asked rather than claiming the app closed (#169).
    private fun close(packageName: String, label: CharSequence, toast: Boolean = true) {
        runLogged("close app") {
            activity.getSystemService(ActivityManager::class.java)?.killBackgroundProcesses(packageName)
        }
        if (toast) {
            Toast.makeText(activity, activity.getString(R.string.app_closed, label), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveApk(packageName: String) {
        val info = runCatching { activity.packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
        if (info == null) {
            Toast.makeText(activity, R.string.apk_save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        pendingApk = PendingApk(info.sourceDir, !info.splitSourceDirs.isNullOrEmpty())
        runLogged("open APK save picker") { saveApkLauncher.launch("$packageName.apk") }
            .onFailure { pendingApk = null }
    }

    private fun writeApk(uri: Uri, pending: PendingApk) {
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runLogged("save APK") {
                    activity.contentResolver.openOutputStream(uri)?.use { output ->
                        File(pending.sourceDir).inputStream().use { it.copyTo(output) }
                    }
                }
            }
            val message = when {
                result.isFailure -> R.string.apk_save_failed
                // Split apps: base.apk alone is partial - say so honestly.
                pending.split -> R.string.apk_saved_base
                else -> R.string.apk_saved
            }
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }
}
