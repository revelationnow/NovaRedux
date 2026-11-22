package com.supernova.launcher.ui

import android.content.Context
import android.view.View
import androidx.core.graphics.Insets
import com.supernova.launcher.data.DeviceProfile
import com.supernova.launcher.data.LauncherPrefs

// How the bottom chrome shares the window's width: how wide a landscape dock
// or search bar may grow, whether a phone on its side puts the two in one
// row, and the width the dock's slots share between them.
class DockGeometry(
    private val context: Context,
    private val dockBar: View,
    // System bar and cutout insets, the single source the chrome pads from.
    private val insets: () -> Insets,
    private val landscape: () -> Boolean
) {
    class SharedRow(val bar: Int, val dock: Int)

    // How wide the bottom chrome is allowed to grow in landscape: the
    // display's short edge (the width this same chrome has in portrait) with
    // some slack, so it uses the extra room without spreading across it. 0 in
    // portrait, and whenever the window is already narrower than the cap.
    fun landscapeChromeWidthPx(): Int {
        if (!landscape()) return 0
        val metrics = context.resources.displayMetrics
        val cap = (minOf(metrics.widthPixels, metrics.heightPixels) * LANDSCAPE_CHROME_WIDTH).toInt()
        return if (cap in 1 until availableWidth()) cap else 0
    }

    // A phone (not a tablet) on its side with a dock: the dock and a search
    // bar share one row, when the dock's icons fit beside a usable bar.
    fun phoneSideBySide(prefs: LauncherPrefs): Boolean =
        landscape() && !DeviceProfile.isWide(context) && prefs.dockEnabled

    // The shared row's split: the dock as wide as its icons need at a roomy
    // spacing (tighter only to leave the bar its minimum), the bar the rest.
    // Splitting the row in half crammed a seven-icon dock and spread three
    // icons far apart. Null when even tight slots leave the bar too short:
    // the two stack instead.
    fun sharedRowPx(prefs: LauncherPrefs): SharedRow? {
        val available = availableWidth() - context.dp(SIDE_BY_SIDE_GAP_DP) * SIDE_BY_SIDE_GAPS
        val padding = 2 * context.dp(prefs.dockPaddingH)
        val count = prefs.dockCount.coerceAtLeast(1)
        val slot = ((available - context.dp(SHARED_BAR_MIN_DP) - padding) / count)
            .coerceAtMost(slotPx(prefs, tight = false))
        if (slot < slotPx(prefs, tight = true)) return null
        val dock = slot * count + padding
        return SharedRow(available - dock, dock)
    }

    // A landscape dock on a row of its own: no wider than roomy slots need
    // and no wider than the cap near its portrait width, so three icons
    // don't spread across the screen; but never so narrow its icons lose
    // their tight slots, up to the window.
    // A tablet keeps the cap itself: its dock has room, and its bar matches.
    fun stackedDockWidthPx(prefs: LauncherPrefs, cap: Int): Int {
        if (DeviceProfile.isWide(context)) return cap
        val padding = 2 * context.dp(prefs.dockPaddingH)
        val roomy = prefs.dockCount * slotPx(prefs, tight = false) + padding
        val tight = prefs.dockCount * slotPx(prefs, tight = true) + padding
        return maxOf(minOf(cap, roomy), tight).coerceAtMost(availableWidth())
    }

    // The dock's margin at each side. A dock without a background spans the
    // width, so its slots stand on the grid's columns; a background floats
    // 10dp in from the edges unless it runs edge to edge.
    fun sideMarginPx(prefs: LauncherPrefs, edgeToEdge: Boolean): Int =
        if (edgeToEdge || prefs.dockBgStyle == DockBgStyle.NONE) 0 else context.dp(FLOATING_MARGIN_DP)

    // Width the dock has for its slots: its laid-out width once it has one,
    // else the window minus the margins DockChrome is about to give it.
    fun contentWidth(prefs: LauncherPrefs): Int {
        val laidOut = dockBar.width - dockBar.paddingLeft - dockBar.paddingRight
        // A landscape dock's width follows its icon count and size, so the
        // last layout's width is stale the moment one of those changes.
        if (laidOut > 0 && !landscape()) return laidOut
        val side = insets().left + insets().right
        val sideMargins = 2 * (sideMarginPx(prefs, false) + context.dp(prefs.dockPaddingH)) + side
        val placement = SearchBarPlacement.fromName(SearchBarConfig.decode(prefs.searchBarConfig).desktop.placement)
        val barShown = placement != SearchBarPlacement.NONE && placement != SearchBarPlacement.WIDGET &&
            placement != SearchBarPlacement.TOP
        val shared = if (barShown && phoneSideBySide(prefs)) sharedRowPx(prefs) else null
        val content = if (shared != null) {
            shared.dock - 2 * context.dp(prefs.dockPaddingH)
        } else {
            val cap = landscapeChromeWidthPx()
            val window = if (cap > 0) {
                stackedDockWidthPx(prefs, cap) + side
            } else {
                context.resources.displayMetrics.widthPixels
            }
            window - sideMargins
        }
        return content.coerceAtLeast(1)
    }

    private fun availableWidth(): Int =
        context.resources.displayMetrics.widthPixels - insets().left - insets().right

    // The dock's icon box before any slot squeezes it.
    private fun boxPx(prefs: LauncherPrefs): Int =
        (context.dp(ICON_BASE_DP) * if (prefs.dockMatchDesktop) prefs.iconScale else prefs.dockIconScale).toInt()

    // A dock slot's width: roomy, about a portrait dock's spacing, or as
    // tight as an icon (and its label) still reads at.
    private fun slotPx(prefs: LauncherPrefs, tight: Boolean): Int {
        val box = boxPx(prefs)
        return if (tight) {
            maxOf(context.dp(SLOT_TIGHT_DP), box + context.dp(SLOT_TIGHT_EXTRA_DP))
        } else {
            maxOf(context.dp(SLOT_ROOMY_DP), box + context.dp(SLOT_ROOMY_EXTRA_DP))
        }
    }

    companion object {
        // The gap between a side-by-side bar and dock, and at either edge:
        // start, middle and end.
        const val SIDE_BY_SIDE_GAP_DP = 16
        private const val SIDE_BY_SIDE_GAPS = 3
        // An icon's box at 100%, as the activity sizes it.
        private const val ICON_BASE_DP = 48
        // Landscape cap on the dock and desktop search bar, as a multiple of
        // the display's short edge: wide enough to use the extra room, narrow
        // enough that five dock icons still read as a row.
        private const val LANDSCAPE_CHROME_WIDTH = 1.35f
        // The shortest bar the shared row keeps; a dock that leaves less stacks.
        private const val SHARED_BAR_MIN_DP = 200
        // Dock slots in landscape: a roomy one, and the tightest kept.
        private const val SLOT_ROOMY_DP = 72
        private const val SLOT_ROOMY_EXTRA_DP = 24
        private const val SLOT_TIGHT_DP = 56
        private const val SLOT_TIGHT_EXTRA_DP = 8
        private const val FLOATING_MARGIN_DP = 10
    }
}
