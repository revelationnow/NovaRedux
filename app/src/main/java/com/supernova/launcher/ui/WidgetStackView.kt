package com.supernova.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import kotlin.math.abs

// A widget stack (#10): its widgets in one spot, one showing and the others
// a sideways swipe away, with dots along the bottom for how many there are
// and which is up. A sideways move on it is the stack's, so it doesn't turn
// the page; an upright one is left to the widget's own scrolling and to the
// home gestures. The stack goes round: past the last comes the first.
class WidgetStackView(context: Context) : FrameLayout(context) {

    // A swipe or TalkBack brought another widget up.
    var onShownChanged: (Int) -> Unit = {}

    var shown = 0
        private set

    private val dots = StackDots(context)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val flingSpeed = ViewConfiguration.get(context).scaledMinimumFlingVelocity * FLING_FACTOR
    private var downX = 0f
    private var downY = 0f
    private var swiping = false
    private var velocity: VelocityTracker? = null

    private val widgetCount: Int get() = (childCount - 1).coerceAtLeast(0)

    fun setWidgets(views: List<View>, index: Int) {
        removeAllViews()
        views.forEach { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        addView(dots, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        show(index)
    }

    // The widgets' views in stack order, the dots left out.
    fun widgetViews(): List<View> = (0 until widgetCount).map(::getChildAt)

    fun show(index: Int) {
        if (widgetCount == 0) return
        shown = index.mod(widgetCount)
        widgetViews().forEachIndexed { i, view ->
            view.animate().cancel()
            view.translationX = 0f
            // Invisible rather than gone: each keeps its size, which its
            // widget was told.
            view.visibility = if (i == shown) VISIBLE else INVISIBLE
        }
        dots.set(widgetCount, shown)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (widgetCount < 2) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                swiping = false
                // Sideways is the stack's until the move says otherwise.
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                if (!swiping && dy > slop && dy > dx) parent?.requestDisallowInterceptTouchEvent(false)
                if (!swiping && dx > slop && dx > dy) {
                    swiping = true
                    velocity = VelocityTracker.obtain().also { it.addMovement(ev) }
                }
            }
        }
        return swiping
    }

    // Only a swipe the intercept took ends up here.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!swiping) return false
        velocity?.addMovement(ev)
        val dx = ev.x - downX
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> getChildAt(shown)?.translationX = dx
            MotionEvent.ACTION_UP -> finishSwipe(dx)
            MotionEvent.ACTION_CANCEL -> finishSwipe(0f)
        }
        return true
    }

    private fun finishSwipe(dx: Float) {
        val tracker = velocity
        tracker?.computeCurrentVelocity(VELOCITY_UNITS)
        val fling = tracker != null && abs(tracker.xVelocity) > flingSpeed
        val far = abs(dx) > width * SWIPE_SHARE || fling && abs(dx) > slop
        tracker?.recycle()
        velocity = null
        swiping = false
        parent?.requestDisallowInterceptTouchEvent(false)
        if (far) step(if (dx < 0) 1 else -1) else getChildAt(shown)?.animate()?.translationX(0f)?.start()
    }

    // The next widget (+1) or the one before (-1) slides in from that side.
    private fun step(direction: Int) {
        val outgoing = getChildAt(shown) ?: return
        val next = (shown + direction).mod(widgetCount)
        val incoming = getChildAt(next) ?: return
        val travel = width.toFloat() * direction
        incoming.translationX = travel
        incoming.visibility = VISIBLE
        incoming.animate().translationX(0f).setDuration(SLIDE_MS).start()
        outgoing.animate().translationX(-travel).setDuration(SLIDE_MS).withEndAction {
            outgoing.visibility = if (shown == indexOfChild(outgoing)) VISIBLE else INVISIBLE
            outgoing.translationX = 0f
        }.start()
        shown = next
        dots.set(widgetCount, shown)
        onShownChanged(shown)
    }

    // TalkBack moves through the stack as through a list it can scroll.
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (widgetCount > 1) {
            info.isScrollable = true
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when {
        widgetCount < 2 -> super.performAccessibilityAction(action, arguments)
        action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> {
            show(shown + 1)
            onShownChanged(shown)
            true
        }
        action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> {
            show(shown - 1)
            onShownChanged(shown)
            true
        }
        else -> super.performAccessibilityAction(action, arguments)
    }

    private companion object {
        const val SWIPE_SHARE = 0.2f
        const val FLING_FACTOR = 4
        const val VELOCITY_UNITS = 1000
        const val SLIDE_MS = 220L
    }
}

// The stack's dots: one per widget, the showing one brighter. Over any
// widget, so white with a soft shadow, as page dots on the wallpaper are.
private class StackDots(context: Context) : View(context) {

    private var count = 0
    private var current = 0
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(SHADOW_DP * density, 0f, 0f, SHADOW)
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun set(count: Int, current: Int) {
        this.count = count
        this.current = current
        visibility = if (count > 1) VISIBLE else GONE
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = ((DOT_DP + BOTTOM_DP * 2) * density).toInt()
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        val radius = DOT_DP * density / 2
        val gap = GAP_DP * density
        val total = count * radius * 2 + (count - 1) * gap
        var x = (width - total) / 2 + radius
        val y = height - BOTTOM_DP * density - radius
        repeat(count) { i ->
            paint.alpha = if (i == current) BRIGHT else DIM
            canvas.drawCircle(x, y, radius, paint)
            x += radius * 2 + gap
        }
    }

    private companion object {
        const val DOT_DP = 6f
        const val GAP_DP = 5f
        const val BOTTOM_DP = 4f
        const val SHADOW_DP = 1.5f
        const val SHADOW = 0x66000000
        const val BRIGHT = 235
        const val DIM = 110
    }
}
