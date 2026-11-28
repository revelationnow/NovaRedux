package com.novaredux.launcher.ui

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
import com.novaredux.launcher.R
import com.novaredux.launcher.data.LaunchableApp
import com.novaredux.launcher.runLogged

// Opens an app pair (#12) from Android 12L on, where an app on the full
// screen may open another beside it (FLAG_ACTIVITY_LAUNCH_ADJACENT) and the
// screen splits. Asked from the launcher in the background, as the pair
// first did it, the flag no longer splits the screen: on an Android 15
// emulator the second app took the whole screen. So this screen comes up
// full screen for a moment, in a task of its own, opens the second app
// beside itself (the lower or right half), then the first app in its own
// half (the upper or left), and goes. Where the screen doesn't split after
// all, the first app opens over the second and a toast says so.
class AppPairActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private var started = false

    // The app for this screen's half, until it has opened.
    private var first: ComponentName? = null

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
        first = component(EXTRA_FIRST)
        val second = component(EXTRA_SECOND)
        if (first == null || second == null) {
            finish()
        } else {
            // Once this screen is up, so it is the app on the full screen.
            window.decorView.post { openBeside(second) }
        }
    }

    private fun openBeside(second: ComponentName) {
        val opened = runLogged("open the second app of a pair") {
            startActivity(AppPairLauncher.launchIntent(second).addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT))
        }.isSuccess
        if (opened) main.postDelayed(::notSplit, SPLIT_WAIT_MS) else openFirst()
    }

    // Moved into one half of the split screen: the first app takes it.
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (isInMultiWindowMode) openFirst()
    }

    private fun notSplit() {
        if (first == null) return
        Toast.makeText(applicationContext, R.string.pair_not_split, Toast.LENGTH_SHORT).show()
        openFirst()
    }

    private fun openFirst() {
        val app = first ?: return
        first = null
        main.removeCallbacksAndMessages(null)
        runLogged("open the first app of a pair") { startActivity(AppPairLauncher.launchIntent(app)) }
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

        // How long the screen gets to split once the second app is opening.
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
