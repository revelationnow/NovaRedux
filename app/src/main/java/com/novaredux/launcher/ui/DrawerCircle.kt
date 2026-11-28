package com.novaredux.launcher.ui

import android.animation.Animator
import android.view.View
import android.view.ViewAnimationUtils
import kotlin.math.hypot

// The drawer's Circle transition, as Nova had it: the drawer grows out of a
// circle at the bottom middle of the screen, where the swipe up starts and
// the dock's drawer button sits, and shrinks back into it on the way out.
object DrawerCircle {

    // The reveal for the sheet, opening (out from nothing) or closing (in to
    // nothing); null while the sheet has no size to reveal yet, its first
    // open, which then fades in instead.
    fun reveal(sheet: View, opening: Boolean): Animator? {
        if (!sheet.isAttachedToWindow || sheet.width == 0 || sheet.height == 0) return null
        val centreX = sheet.width / 2
        val centreY = sheet.height
        val full = hypot(centreX.toFloat(), centreY.toFloat())
        return ViewAnimationUtils.createCircularReveal(
            sheet, centreX, centreY, if (opening) 0f else full, if (opening) full else 0f
        )
    }
}
