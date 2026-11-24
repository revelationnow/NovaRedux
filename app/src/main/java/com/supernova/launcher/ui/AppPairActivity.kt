package com.supernova.launcher.ui

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.window.SplashScreen
import com.supernova.launcher.R
import com.supernova.launcher.data.LaunchableApp
import com.supernova.launcher.runLogged

// Opens an app pair (#12) from Android 12L on, where an app on the full
// screen may open another beside it (FLAG_ACTIVITY_LAUNCH_ADJACENT) and the
// screen splits. Asked from the launcher in the background, as the pair
// first did it, the flag no longer splits the screen: on an Android 15
// emulator the second app took the whole screen. So this screen comes up
// full screen for a moment, in a task of its own, opens the first app beside
// itself, then the second app in its own half, and goes. Where the screen
// doesn't split after all, the first app is left open on its own and a
// toast says so.
class AppPairActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private var started = false

    // The app still to open, until it has been (or the split never came).
    private var second: ComponentName? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Brought back after the system let it go: the pair opened then, or
        // won't now.
        if (savedInstanceState != null) finish()
    }

    override fun onResume() {
        super.onResume()
        if (started || isFinishing) return
        started = true
        val first = component(EXTRA_FIRST)
        second = component(EXTRA_SECOND)
        if (first == null || second == null) {
            finish()
        } else {
            // Once this screen is up, so it is the app on the full screen.
            window.decorView.post { openFirst(first) }
        }
    }

    private fun openFirst(first: ComponentName) {
        val opened = runLogged("open the first app of a pair") {
            startActivity(AppPairLauncher.launchIntent(first).addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT))
        }.isSuccess
        if (opened) main.postDelayed(::notSplit, SPLIT_WAIT_MS) else finish()
    }

    // Moved into one half of the split screen: the second app takes it.
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        val app = second?.takeIf { isInMultiWindowMode } ?: return
        second = null
        main.removeCallbacksAndMessages(null)
        runLogged("open the second app of a pair") { startActivity(AppPairLauncher.launchIntent(app)) }
        finish()
    }

    private fun notSplit() {
        if (second == null) return
        second = null
        Toast.makeText(applicationContext, R.string.pair_not_split, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun component(key: String): ComponentName? =
        intent.getStringExtra(key)?.let(ComponentName::unflattenFromString)

    companion object {
        private const val EXTRA_FIRST = "first"
        private const val EXTRA_SECOND = "second"

        // How long the screen gets to split once the first app is opening.
        private const val SPLIT_WAIT_MS = 2_000L

        fun open(activity: Activity, first: LaunchableApp, second: LaunchableApp) {
            val intent = Intent(activity, AppPairActivity::class.java)
                .putExtra(EXTRA_FIRST, ComponentName(first.packageName, first.className).flattenToString())
                .putExtra(EXTRA_SECOND, ComponentName(second.packageName, second.className).flattenToString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            // A plain colour while it starts, not NovaRedux's icon.
            val options = ActivityOptions.makeBasic().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    splashScreenStyle = SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR
                }
            }
            runLogged("open an app pair") { activity.startActivity(intent, options.toBundle()) }
        }
    }
}
