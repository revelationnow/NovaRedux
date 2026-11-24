package com.supernova.launcher.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

// An app pair's icon (#12): the first app's icon up and to the left and the
// second's down and to the right, each a little over half the size. Each is
// drawn from its own copy, since the icon cache hands out shared drawables
// and a drawable has one set of bounds.
class AppPairIcon(first: Drawable, second: Drawable) : Drawable() {

    private val first = first.constantState?.newDrawable()?.mutate() ?: first
    private val second = second.constantState?.newDrawable()?.mutate() ?: second

    override fun draw(canvas: Canvas) {
        val box = bounds
        val size = (box.width() * SHARE).toInt()
        first.setBounds(box.left, box.top, box.left + size, box.top + size)
        second.setBounds(box.right - size, box.bottom - size, box.right, box.bottom)
        first.draw(canvas)
        second.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        first.alpha = alpha
        second.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        first.colorFilter = colorFilter
        second.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = first.intrinsicWidth

    override fun getIntrinsicHeight(): Int = first.intrinsicHeight

    private companion object {
        const val SHARE = 0.68f
    }
}
