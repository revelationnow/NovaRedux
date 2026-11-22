package com.supernova.launcher.ui

import android.animation.ValueAnimator
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View

// Fades a view to grey and back (Nova 7's drawer behind a windowed folder):
// its saturation, drawn through a hardware layer while it is anything but
// full colour, and no layer at all once it is back.
class GreyFade(private val view: View) {

    // 0 is full colour, 1 is grey.
    private var level = 0f
    private var animator: ValueAnimator? = null

    fun fadeTo(grey: Boolean, durationMs: Long) {
        animator?.cancel()
        animator = null
        val target = if (grey) 1f else 0f
        if (durationMs <= 0L || level == target) {
            apply(target)
            return
        }
        animator = ValueAnimator.ofFloat(level, target).apply {
            duration = durationMs
            addUpdateListener { apply(it.animatedValue as Float) }
            start()
        }
    }

    private fun apply(value: Float) {
        level = value
        if (value <= 0f) {
            view.setLayerType(View.LAYER_TYPE_NONE, null)
            return
        }
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1f - value) })
        }
        if (view.layerType == View.LAYER_TYPE_HARDWARE) {
            view.setLayerPaint(paint)
        } else {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        }
    }
}
