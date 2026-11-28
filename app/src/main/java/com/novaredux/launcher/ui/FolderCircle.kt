package com.novaredux.launcher.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.ViewAnimationUtils
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import kotlin.math.hypot
import kotlin.math.max

// Folder animation Circle (FolderAnimation.CIRCLE): the folder card widens
// out of its icon as a circle, and narrows back into it on close. The card's
// pivot, set by the zoom's own reckoning, is where the icon sits on it.
object FolderCircle {

    // Where the circle starts and ends, kept from the open for the close.
    class Origin(val x: Int, val y: Int, val radius: Float)

    // Null when the card can't take a reveal; the zoom plays instead.
    fun open(card: View, icon: View, durationMs: Long): Origin? {
        if (!card.isAttachedToWindow || card.width == 0 || card.height == 0) return null
        val origin = Origin(card.pivotX.toInt(), card.pivotY.toInt(), icon.width / 2f)
        card.scaleX = 1f
        card.scaleY = 1f
        ViewAnimationUtils.createCircularReveal(card, origin.x, origin.y, origin.radius, farthest(card, origin))
            .apply {
                duration = durationMs
                interpolator = DecelerateInterpolator()
            }
            .start()
        return origin
    }

    // False when the card can't take a reveal any more; the zoom closes it.
    fun close(card: View, origin: Origin, durationMs: Long, end: () -> Unit): Boolean {
        if (!card.isAttachedToWindow || card.width == 0) return false
        ViewAnimationUtils.createCircularReveal(card, origin.x, origin.y, farthest(card, origin), origin.radius)
            .apply {
                duration = durationMs
                interpolator = AccelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        // The reveal lets go of its clip at the end; hidden
                        // first, the card doesn't flash whole for a frame.
                        card.visibility = View.INVISIBLE
                        end()
                    }
                })
            }
            .start()
        return true
    }

    // The card's corner farthest from the centre: the radius that shows it all.
    private fun farthest(card: View, origin: Origin): Float {
        val dx = max(origin.x, card.width - origin.x).toFloat()
        val dy = max(origin.y, card.height - origin.y).toFloat()
        return hypot(dx, dy)
    }
}
