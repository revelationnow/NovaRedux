package com.supernova.launcher.ui

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import com.supernova.launcher.data.LauncherPrefs

// Nova's card drawer: the app list sits on a rounded card that stops short of
// the sheet's sides, over the drawer's own background, which may be see-
// through. The search row and the tabs stay on that background, outside the
// card. The card is the list container's background, clipped to, so the
// list, its results and the fast scroller ride inside it; it runs down to the
// sheet's bottom edge, under the navigation bar, where the list already comes
// to rest above that bar.
object DrawerCard {

    private const val SIDE_DP = 8
    private const val RADIUS_DP = 20
    private const val OPAQUE = 0xFF shl 24

    // `surface` is the drawer's resolved background; the card is it made
    // opaque, unless drawerCardColor picks a color of its own.
    fun apply(container: View, prefs: LauncherPrefs, surface: Int) {
        val params = container.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val side = if (prefs.drawerCard) container.dp(SIDE_DP) else 0
        if (params.leftMargin != side || params.rightMargin != side) {
            params.leftMargin = side
            params.rightMargin = side
            container.layoutParams = params
        }
        container.background = if (prefs.drawerCard) card(container, prefs, surface) else null
        container.clipToOutline = prefs.drawerCard
    }

    // What the card takes off the grid's width.
    fun sideMargins(container: View): Int =
        (container.layoutParams as? ViewGroup.MarginLayoutParams)?.let { it.leftMargin + it.rightMargin } ?: 0

    private fun card(container: View, prefs: LauncherPrefs, surface: Int) = GradientDrawable().apply {
        cornerRadius = container.dp(RADIUS_DP.toFloat())
        setColor(LabelStyler.parseColor(prefs.drawerCardColor, surface or OPAQUE))
    }
}
