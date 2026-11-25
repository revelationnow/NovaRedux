package com.supernova.launcher.ui

import android.app.Activity
import android.database.ContentObserver
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import com.supernova.launcher.R

// Smart Launcher's Rotate on place (#20): with it on and the home screen
// set to auto-rotate, the launcher stays in portrait, and as the phone turns
// on its side each icon, folder and widget on the home screen, in the dock
// and in the app drawer turns upright where it is, rather than the grid
// being laid out again for a wide screen. A widget is laid out on its cells'
// width and height swapped and turned (WorkspaceCanvas.turn).
//
// A view that turns is marked (mark): it takes the turn whenever it is
// attached, so pages, tiles and rows made or recycled later turn too, and a
// change of turn reaches the ones attached under the roots. Nothing turns
// while the phone's own auto-rotate is off.
class RotateInPlace(
    private val activity: Activity,
    private val roots: () -> List<View>,
    private val animate: () -> Boolean
) {
    private var on = false
    private var resumed = false
    // The phone's own turn, a quarter (0 to 3) clockwise from upright.
    private var quarter = 0

    // For FeatureTour: the phone's turn in degrees, in place of the sensor's.
    var heldDegrees: Int? = null
        set(value) {
            field = value
            sensed(value ?: 0)
            refresh()
        }

    private val sensor = object : OrientationEventListener(activity, SensorManager.SENSOR_DELAY_UI) {
        override fun onOrientationChanged(orientation: Int) {
            if (heldDegrees == null) sensed(orientation)
        }
    }

    private val autoRotate = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    // On while the setting is on and the home screen auto-rotates.
    fun set(enabled: Boolean) {
        if (on == enabled) return
        on = enabled
        listen()
        refresh()
    }

    fun onResume() {
        resumed = true
        listen()
        refresh()
    }

    fun onPause() {
        resumed = false
        listen()
    }

    // The sensor and the auto-rotate switch, only while on and in view.
    private fun listen() {
        val resolver = activity.contentResolver
        if (on && resumed) {
            if (sensor.canDetectOrientation()) sensor.enable()
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, autoRotate
            )
        } else {
            sensor.disable()
            resolver.unregisterContentObserver(autoRotate)
        }
    }

    private fun sensed(degrees: Int) {
        val next = Turns.quarter(degrees, quarter)
        if (next == quarter) return
        quarter = next
        refresh()
    }

    private fun refresh() {
        val target = target()
        if (target == current) return
        current = target
        val animated = animate()
        roots().forEach { turn(it, animated) }
    }

    private fun target(): Int {
        val phoneTurns = heldDegrees != null || Settings.System.getInt(
            activity.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0
        ) == 1
        if (!on || !phoneTurns) return 0
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.rotation ?: 0
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.rotation
        }
        return Turns.forViews(quarter, display)
    }

    private fun turn(view: View, animated: Boolean) {
        when {
            view is WorkspaceCanvas -> {
                view.turn = current
                (0 until view.childCount).forEach { turn(view.getChildAt(it), animated) }
            }
            view.getTag(R.id.tag_turns) == true -> turnOne(view, animated)
            view is ViewGroup -> (0 until view.childCount).forEach { turn(view.getChildAt(it), animated) }
        }
    }

    private fun turnOne(view: View, animated: Boolean) {
        val degrees = current.toFloat()
        view.animate().cancel()
        if (animated && view.isAttachedToWindow && view.isShown) {
            view.animate().rotation(degrees).setDuration(TURN_MS).start()
        } else {
            view.rotation = degrees
        }
    }

    companion object {
        private const val TURN_MS = 250L

        // The turn every marked view takes, in View.rotation's degrees.
        var current = 0
            private set

        // A view that turns in place, from when it is next attached: an
        // icon or a tile turns whole; a page's canvas turns its widgets.
        fun mark(view: View) {
            if (view.getTag(R.id.tag_turns) == true) return
            view.setTag(R.id.tag_turns, true)
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    if (v is WorkspaceCanvas) {
                        v.turn = current
                    } else if (v.rotation != current.toFloat()) {
                        v.animate().cancel()
                        v.rotation = current.toFloat()
                    }
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            })
        }
    }
}

// The arithmetic of a turn, apart from the views, for its tests.
object Turns {

    // How far either side of a quarter's middle the phone has to be before
    // it counts as turned to it, so it doesn't flicker near 45 degrees.
    private const val SETTLE = 30
    private const val QUARTER = 90
    private const val WHOLE = 360
    private const val UPSIDE_DOWN = 2
    private const val QUARTERS = 4

    // The phone's turn as a quarter (0 upright, 1 on its side with its left
    // edge up, 3 with its right edge up), from OrientationEventListener's
    // degrees; `current` kept until the phone is well into another, and
    // while it lies flat (a negative reading). Upside down counts as
    // upright, as phones' own auto-rotate mostly has it.
    fun quarter(degrees: Int, current: Int): Int {
        if (degrees < 0) return current
        val nearest = ((degrees + QUARTER / 2) / QUARTER) % QUARTERS
        val off = Math.floorMod(degrees - nearest * QUARTER + WHOLE / 2, WHOLE) - WHOLE / 2
        return when {
            kotlin.math.abs(off) > SETTLE -> current
            nearest == UPSIDE_DOWN -> 0
            else -> nearest
        }
    }

    // The views' turn in View.rotation's degrees (clockwise) for the phone
    // turned `quarter` and the window turned `displayRotation`
    // (Surface.ROTATION_*, which runs the other way): what the window
    // doesn't already make up for. A phone turned clockwise turns its
    // picture with it, so the views turn back the other way.
    fun forViews(quarter: Int, displayRotation: Int): Int {
        val clockwise = Math.floorMod(-(quarter + displayRotation) * QUARTER, WHOLE)
        return if (clockwise > WHOLE / 2) clockwise - WHOLE else clockwise
    }
}
