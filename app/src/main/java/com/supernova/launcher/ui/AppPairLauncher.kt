package com.supernova.launcher.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.supernova.launcher.SupernovaAccessibilityService
import com.supernova.launcher.data.LaunchableApp
import com.supernova.launcher.runLogged

// Opens an app pair (#12): the first app opens as any app does, and the
// second opens beside it. From Android 12L an app opened that way
// (FLAG_ACTIVITY_LAUNCH_ADJACENT) splits the screen with the full-screen
// one by itself. Before that it took the accessibility service to put the
// first app into split screen, as the Splitz app did; 12L dropped that
// action, which an emulator run of FeatureTour showed. The pauses give each
// step time to land, and how well it goes varies from phone to phone.
object AppPairLauncher {

    private const val SPLIT_AFTER_MS = 450L
    private const val SECOND_AFTER_MS = 550L
    private const val BESIDE_AFTER_MS = 650L

    // False when it needs the accessibility service and that is off: the
    // caller explains instead of opening half a pair.
    fun launch(activity: Activity, second: LaunchableApp, launchFirst: () -> Unit): Boolean {
        val splitsByItself = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2
        if (!splitsByItself && !SupernovaAccessibilityService.isEnabled(activity)) return false
        launchFirst()
        val context = activity.applicationContext
        val main = Handler(Looper.getMainLooper())
        if (splitsByItself) {
            main.postDelayed({ openBeside(context, second) }, BESIDE_AFTER_MS)
        } else {
            main.postDelayed({
                if (SupernovaAccessibilityService.toggleSplitScreen()) {
                    main.postDelayed({ openBeside(context, second) }, SECOND_AFTER_MS)
                }
            }, SPLIT_AFTER_MS)
        }
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
