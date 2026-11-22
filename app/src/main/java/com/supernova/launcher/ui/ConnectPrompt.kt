package com.supernova.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.supernova.launcher.R
import com.supernova.launcher.connect.NovaConnect
import com.supernova.launcher.runLogged

// Web suggestions need NovaRedux Connect, the separate app that goes online
// for NovaRedux. Turned on without it, what it is and why it is separate are
// said first, with the way to get it.
object ConnectPrompt {

    fun explainIfMissing(activity: Activity) {
        if (NovaConnect.installed(activity)) return
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.connect_title)
            .setMessage(R.string.connect_message)
            .setPositiveButton(R.string.connect_get) { _, _ -> open(activity) }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    // Its own page (what it sends, and to whom) once installed; until then,
    // the page to get it from.
    fun open(context: Context) {
        val intent = if (NovaConnect.installed(context)) {
            Intent(Intent.ACTION_APPLICATION_PREFERENCES).setPackage(NovaConnect.PACKAGE)
        } else {
            NovaConnect.downloadIntent()
        }
        runLogged("open NovaRedux Connect") { context.startActivity(intent) }
    }
}
