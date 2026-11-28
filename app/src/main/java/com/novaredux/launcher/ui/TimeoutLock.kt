package com.novaredux.launcher.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.novaredux.launcher.R
import com.novaredux.launcher.runLogged

// Nova's timeout screen-lock method, for phones with neither the
// accessibility service nor device admin to lock with: the screen goes
// black and the phone's screen timeout drops to its shortest, so the screen
// turns itself off in a moment; the timeout is put back as soon as it has
// (or a tap cancels). It needs the permission to modify system settings.
object TimeoutLock {
    private const val STORE = "timeout_lock"
    private const val SAVED = "screen_off_timeout"
    // The system holds a timeout to its own minimum, a few seconds.
    private const val SHORTEST_MS = 1_000

    fun canLock(context: Context): Boolean = Settings.System.canWrite(context)

    fun lock(activity: Activity) {
        if (canLock(activity)) {
            runLogged("timeout lock") { activity.startActivity(Intent(activity, TimeoutLockActivity::class.java)) }
        } else {
            explainPermission(activity)
        }
    }

    // Why the permission, then the system screen that grants it.
    fun explainPermission(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.lock_method_timeout)
            .setMessage(R.string.lock_timeout_permission)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${activity.packageName}".toUri())
                runLogged("open modify system settings") { activity.startActivity(intent) }
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    // The timeout as it was is kept until it is put back, even across a
    // restart of the app.
    fun shorten(context: Context) {
        val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        runLogged("shorten the screen timeout") {
            val resolver = context.contentResolver
            if (!store.contains(SAVED)) {
                store.edit(commit = true) {
                    putInt(SAVED, Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT))
                }
            }
            Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, SHORTEST_MS)
        }
    }

    fun restore(context: Context) {
        val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        if (!store.contains(SAVED)) return
        runLogged("put the screen timeout back") {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(
                    context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, store.getInt(SAVED, 0)
                )
            }
        }
        store.edit { remove(SAVED) }
    }
}

// The black screen of the timeout lock: as dark as the screen goes, over
// everything, until the screen turns off or a tap cancels it.
class TimeoutLockActivity : Activity() {

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
        }
        setContentView(
            View(this).apply {
                setBackgroundColor(Color.BLACK)
                contentDescription = getString(R.string.lock_timeout_cancel)
                setOnClickListener { finish() }
            }
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        // The system's own broadcast reaches a receiver that isn't exported.
        ContextCompat.registerReceiver(
            this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        TimeoutLock.shorten(this)
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        TimeoutLock.restore(this)
        super.onDestroy()
    }
}
