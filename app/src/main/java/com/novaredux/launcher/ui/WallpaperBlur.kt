package com.novaredux.launcher.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Build
import com.novaredux.launcher.data.LauncherPrefs
import kotlin.math.roundToInt

// The wallpaper blurred behind the drawer and open folders (#11): Android
// 12's background blur on NovaRedux's own window, ramped in step with the
// wallpaper zoom. A phone that leaves window blur out, or turns it off for a
// while (battery saver), shows the usual backgrounds instead. The blur shows
// wherever what NovaRedux draws lets the wallpaper through.
class WallpaperBlur(private val activity: Activity) {

    private var animator: ValueAnimator? = null
    private var level = 0f

    // target: 1 with the drawer or a folder open, 0 without.
    fun to(target: Float, prefs: LauncherPrefs) {
        if (!supported) return
        val goal = if (prefs.blurWallpaper) target else 0f
        animator?.cancel()
        val radius = prefs.blurStrength * MAX_RADIUS_DP * activity.resources.displayMetrics.density / PERCENT
        if (goal == level || !Animations.enabled(prefs)) {
            level = goal
            apply(level * radius)
            return
        }
        animator = ValueAnimator.ofFloat(level, goal).apply {
            duration = Animations.duration(prefs, RAMP_MS)
            addUpdateListener { ramp ->
                level = ramp.animatedValue as Float
                apply(level * radius)
            }
            start()
        }
    }

    private fun apply(radius: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val allowed = activity.windowManager.isCrossWindowBlurEnabled
        activity.window.setBackgroundBlurRadius(if (allowed) radius.roundToInt() else 0)
    }

    companion object {
        const val MIN_STRENGTH = 10
        const val MAX_STRENGTH = 100
        const val DEFAULT_STRENGTH = 50
        private const val MAX_RADIUS_DP = 40f
        private const val PERCENT = 100f
        private const val RAMP_MS = 250L

        val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}
