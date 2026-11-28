package com.novaredux.launcher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import androidx.core.graphics.withClip
import com.novaredux.launcher.data.AppItemEdits
import com.novaredux.launcher.data.CustomShapeSpec
import com.novaredux.launcher.data.DynamicAppIcons
import com.novaredux.launcher.data.IconGlass
import com.novaredux.launcher.data.IconShape
import com.novaredux.launcher.data.LaunchableApp
import com.novaredux.launcher.data.LauncherItem
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

// Nova's adaptive icon animation, after Launcher3's DragView: while an app's
// adaptive icon is carried, its two layers move under the icon's shape. The
// finger's motion pulls them the other way, the foreground further than the
// background, on a spring, and they settle back when the finger rests.
class AdaptiveDragIcon(
    private val background: Drawable?,
    private val foreground: Drawable?,
    // The icon's shape for a `size` x `size` icon; the layers are cut to it.
    private val mask: Path,
    private val size: Float,
    // How far past each side of the icon its layers reach, as a share of its
    // size: what the layers can move by before an edge would show.
    private val bleed: Float
) : Drawable() {

    private val shaped = Path()
    private val matrix = Matrix()
    private val layerBounds = Rect()
    private val springX = Spring()
    private val springY = Spring()
    private var lastFrame = 0L

    // The finger moved by (dx, dy) since the last move: the layers are
    // pulled against it, at most as far as their bleed leaves to spare.
    fun pull(dx: Float, dy: Float) {
        val most = bounds.width() * bleed * REACH
        springX.target = (-dx * PULL).coerceIn(-most, most)
        springY.target = (-dy * PULL).coerceIn(-most, most)
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        matrix.setScale(bounds.width() / size, bounds.height() / size)
        matrix.postTranslate(bounds.left.toFloat(), bounds.top.toFloat())
        mask.transform(matrix, shaped)
    }

    override fun draw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / MS_PER_S).coerceAtMost(MAX_STEP_S)
        lastFrame = now
        // Both axes step, whichever still moves.
        val moving = springX.step(dt) or springY.step(dt)
        canvas.withClip(shaped) {
            drawLayer(this, background, BACKGROUND_SHARE)
            drawLayer(this, foreground, 1f)
        }
        if (moving) invalidateSelf() else lastFrame = 0L
    }

    private fun drawLayer(canvas: Canvas, layer: Drawable?, share: Float) {
        layer ?: return
        val area = bounds
        val reachX = (area.width() * bleed).roundToInt()
        val reachY = (area.height() * bleed).roundToInt()
        val dx = (springX.value * share).roundToInt()
        val dy = (springY.value * share).roundToInt()
        layerBounds.set(area.left - reachX, area.top - reachY, area.right + reachX, area.bottom + reachY)
        layerBounds.offset(dx, dy)
        layer.bounds = layerBounds
        layer.draw(canvas)
    }

    // The lifted icon's shadow follows its shape where the platform can
    // cast one from a path.
    override fun getOutline(outline: Outline) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            outline.setPath(shaped)
        } else if (shaped.isConvex) {
            @Suppress("DEPRECATION")
            outline.setConvexPath(shaped)
        }
    }

    override fun setAlpha(alpha: Int) {
        background?.alpha = alpha
        foreground?.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        background?.colorFilter = colorFilter
        foreground?.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    // One axis of the layers' motion: a damped spring after a target that
    // itself relaxes to rest, so the layers settle once the finger stops.
    private class Spring {
        var value = 0f
        var target = 0f
        private var velocity = 0f

        // True while it still moves.
        fun step(dt: Float): Boolean {
            target *= exp(-dt / TARGET_RELAX_S)
            velocity += (-STIFFNESS * (value - target) - DAMPING * velocity) * dt
            value += velocity * dt
            val resting = abs(value) < REST && abs(velocity) < REST && abs(target) < REST
            if (resting) {
                value = 0f
                velocity = 0f
                target = 0f
            }
            return !resting
        }
    }

    companion object {
        // How much of the bleed the layers may use, and how far a finger's
        // move pulls them (pixels of offset per pixel moved).
        private const val REACH = 0.8f
        private const val PULL = 1.2f
        // The background moves half as far: the layers part, for depth.
        private const val BACKGROUND_SHARE = 0.5f
        // A spring with a little give (about half critical damping).
        private const val STIFFNESS = 600f
        private const val DAMPING = 27f
        private const val TARGET_RELAX_S = 0.09f
        private const val REST = 0.05f
        private const val MS_PER_S = 1000f
        private const val MAX_STEP_S = 1f / 30f
    }
}

// The adaptive drag icon for an app, when the picture on screen is the
// app's own adaptive icon with nothing laid over it: no icon pack, themed
// icons, glass or icon theme bypass, no icon of its own picked in Edit, not
// a work app (its badge), not a calendar's icon of the day, and animations
// on. Null otherwise, and the drag carries the picture as it is.
class AdaptiveDragIcons(context: Context, private val viewModel: LauncherViewModel) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val density = context.resources.displayMetrics.densityDpi
    private val dynamicIcons = DynamicAppIcons(context.packageManager, density)

    fun forApp(app: LaunchableApp, placed: LauncherItem?): AdaptiveDragIcon? {
        val prefs = viewModel.state.value.prefs
        val user = viewModel.appCatalog().userForSerial(app.userSerial)?.takeIf { plain(app, placed) }
        val info = user?.let {
            runCatching { launcherApps.getActivityList(app.packageName, it) }.getOrNull()
                ?.firstOrNull { activity -> activity.componentName.className == app.className }
        }
        val icon = info?.takeUnless(dynamicIcons::has)
            ?.let { runCatching { it.getIcon(density) }.getOrNull() } as? AdaptiveIconDrawable
            ?: return null
        val shape = prefs.iconShape
        val mask = shape.pathFor(MASK_SIZE, CustomShapeSpec.decode(prefs.iconShapeCustom)) ?: run {
            icon.setBounds(0, 0, MASK_SIZE.roundToInt(), MASK_SIZE.roundToInt())
            Path(icon.iconMask)
        }
        // As the renderer draws them: a chosen shape overfills a little, the
        // platform's mask by the platform's own quarter.
        val bleed = if (shape == IconShape.SYSTEM) PLATFORM_BLEED else SHAPE_BLEED
        return AdaptiveDragIcon(icon.background, icon.foreground, mask, MASK_SIZE, bleed)
    }

    // Whether the icon on screen is the app's own adaptive icon as it ships.
    private fun plain(app: LaunchableApp, placed: LauncherItem?): Boolean {
        val prefs = viewModel.state.value.prefs
        val ownIcon = with(AppItemEdits) {
            placed?.appEdit?.hasIcon() == true ||
                viewModel.appCatalog().overrideFor(app.userSerial, app.packageName, app.className)?.hasIcon() == true
        }
        return prefs.iconPack.isBlank() && !prefs.themedIcons && prefs.iconGlass == IconGlass.OFF &&
            !prefs.labsBypassIconTheme && !app.work && !ownIcon && Animations.enabled(prefs)
    }

    private companion object {
        const val MASK_SIZE = 192f
        const val SHAPE_BLEED = 0.12f
        const val PLATFORM_BLEED = 0.25f
    }
}
