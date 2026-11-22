package com.supernova.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import kotlin.math.max

// The dock's background in Nova's shapes: a rectangle (rounded by radii),
// Arc, rising from its sides to a curved top highest in the middle, or
// Platform, a shelf seen from the front with its top edge narrower than its
// bottom and a darker front edge. Filled with the dock's colour, or with the
// dock's image (Dock background image) cropped to fill it, at the colour's
// opacity.
class DockShapeDrawable(
    private val shape: DockBgStyle,
    fill: Int,
    private val image: Bitmap? = null,
    // A rectangle's corners in px: top left, top right, bottom right, bottom
    // left.
    private val radii: FloatArray = FloatArray(CORNERS)
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fill
        if (image != null) {
            shader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            alpha = android.graphics.Color.alpha(fill)
        }
    }
    private val front = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (image != null) {
            FRONT_OVER_IMAGE
        } else {
            ColorUtils.blendARGB(fill, android.graphics.Color.BLACK, FRONT_SHADE)
        }
        alpha = (alpha * android.graphics.Color.alpha(fill)) / OPAQUE
    }
    private val path = Path()
    private val frontPath = Path()

    override fun onBoundsChange(bounds: Rect) {
        path.reset()
        frontPath.reset()
        when (shape) {
            DockBgStyle.ARC -> arc(bounds)
            DockBgStyle.PLATFORM -> platform(bounds)
            else -> path.addRoundRect(
                RectF(bounds), FloatArray(CORNERS * 2) { radii.getOrElse(it / 2) { 0f } }, Path.Direction.CW
            )
        }
        image?.let { cover(it, bounds) }
    }

    // A quadratic through the shoulders peaks halfway to its control.
    private fun arc(bounds: Rect) {
        val height = bounds.height().toFloat()
        val shoulder = bounds.top + height * ARC_SHOULDER
        path.moveTo(bounds.left.toFloat(), bounds.bottom.toFloat())
        path.lineTo(bounds.left.toFloat(), shoulder)
        path.quadTo(bounds.exactCenterX(), bounds.top - height * ARC_SHOULDER, bounds.right.toFloat(), shoulder)
        path.lineTo(bounds.right.toFloat(), bounds.bottom.toFloat())
        path.close()
    }

    private fun platform(bounds: Rect) {
        val inset = bounds.width() * PLATFORM_INSET
        val edge = bounds.bottom - bounds.height() * PLATFORM_FRONT
        path.moveTo(bounds.left.toFloat(), edge)
        path.lineTo(bounds.left + inset, bounds.top.toFloat())
        path.lineTo(bounds.right - inset, bounds.top.toFloat())
        path.lineTo(bounds.right.toFloat(), edge)
        path.lineTo(bounds.right.toFloat(), bounds.bottom.toFloat())
        path.lineTo(bounds.left.toFloat(), bounds.bottom.toFloat())
        path.close()
        frontPath.addRect(
            bounds.left.toFloat(), edge, bounds.right.toFloat(), bounds.bottom.toFloat(), Path.Direction.CW
        )
    }

    // The image scaled to cover the dock, its middle in the middle.
    private fun cover(bitmap: Bitmap, bounds: Rect) {
        val scale = max(bounds.width() / bitmap.width.toFloat(), bounds.height() / bitmap.height.toFloat())
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(
            bounds.left + (bounds.width() - bitmap.width * scale) / 2,
            bounds.top + (bounds.height() - bitmap.height * scale) / 2
        )
        paint.shader?.setLocalMatrix(matrix)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
        if (!frontPath.isEmpty) canvas.drawPath(frontPath, front)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        front.alpha = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = paint.alpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        front.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val CORNERS = 4
        const val OPAQUE = 255
        // Where Arc's sides meet its curve, down from the top.
        const val ARC_SHOULDER = 0.35f
        // How far in Platform's top corners sit, of the width.
        const val PLATFORM_INSET = 0.06f
        // Platform's front edge, of the height, and how much darker it is:
        // the colour a quarter of the way to black, or a shade over an image.
        const val PLATFORM_FRONT = 0.12f
        const val FRONT_SHADE = 0.25f
        const val FRONT_OVER_IMAGE = 0x40000000
    }
}
