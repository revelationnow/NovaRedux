package com.supernova.launcher.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.supernova.launcher.SupernovaAccessibilityService
import com.supernova.launcher.data.LaunchableApp
import com.supernova.launcher.runLogged

// Opens an app pair (#12). Android has no public call that opens two apps
// split, so it goes as the Splitz app does it: the first app opens as any
// app does, the accessibility service puts it into split screen, and the
// second opens beside it. The pauses give each step time to land; how well
// it goes varies from phone to phone. A service the system has bound may
// open apps from the background, which is what lets the second one in.
object AppPairLauncher {

    private const val SPLIT_AFTER_MS = 450L
    private const val SECOND_AFTER_MS = 550L

    // False when the accessibility service is off: the caller explains
    // instead of opening half a pair.
    fun launch(activity: Activity, second: LaunchableApp, launchFirst: () -> Unit): Boolean {
        if (!SupernovaAccessibilityService.isEnabled(activity)) return false
        launchFirst()
        val context = activity.applicationContext
        val main = Handler(Looper.getMainLooper())
        main.postDelayed({
            if (SupernovaAccessibilityService.toggleSplitScreen()) {
                main.postDelayed({ openBeside(context, second) }, SECOND_AFTER_MS)
            }
        }, SPLIT_AFTER_MS)
        return true
    }

    private fun openBeside(context: Context, app: LaunchableApp) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        runLogged("open the second app of a pair") { context.startActivity(intent) }
    }
}
