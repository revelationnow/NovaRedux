package com.novaredux.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.novaredux.launcher.R
import com.novaredux.launcher.connect.NovaConnect
import com.novaredux.launcher.runLogged

// Web suggestions need NovaRedux Connect, the separate app that goes online
// for NovaRedux. Turned on without it, what it is and why it is separate are
// said first, with the way to get it.
object ConnectPrompt {

    fun explainIfMissing(activity: Activity) {
        val state = NovaConnect.state(activity)
        if (state == NovaConnect.State.READY) return
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.connect_title)
            .setMessage(if (state == NovaConnect.State.FOREIGN) R.string.connect_foreign else R.string.connect_message)
            .setPositiveButton(R.string.connect_get) { _, _ -> open(activity) }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    // Its own page (what it sends, and to whom) once installed; until then,
    // or for one signed elsewhere, the page to get it from.
    fun open(context: Context) {
        val intent = if (NovaConnect.state(context) == NovaConnect.State.READY) {
            Intent(Intent.ACTION_APPLICATION_PREFERENCES).setPackage(NovaConnect.PACKAGE)
        } else {
            NovaConnect.downloadIntent()
        }
        runLogged("open NovaRedux Connect") { context.startActivity(intent) }
    }
}
