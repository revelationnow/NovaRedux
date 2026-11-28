package com.novaredux.launcher.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.novaredux.launcher.data.LauncherPrefs
import com.novaredux.launcher.databinding.ActivityLauncherBinding
import com.novaredux.launcher.ui.BottomChrome.BarSlot
import com.novaredux.launcher.ui.theme.Styles
import com.novaredux.launcher.ui.theme.SurfaceId

// Where the home screen's bottom chrome goes: the desktop search bar's slot,
// the dock's padding and margins, the bar's, and the workspace's and page
// indicator's insets around them, from the heights BottomChrome works out.
internal class ChromeMargins(
    private val context: Context,
    private val binding: ActivityLauncherBinding,
    private val geometry: DockGeometry,
    private val host: DockChrome.Host
) {
    // Where the bar and the dock went on this pass.
    class Placement(
        val slot: BarSlot?,
        val top: BottomChrome.Top?,
        val shared: DockGeometry.SharedRow?,
        // Behind-nav: the background slab runs flush to the window's bottom.
        val behindNav: Boolean,
        // Un-inset: the slab spans the width and rests on the navigation bar.
        val fullBleed: Boolean,
        // The landscape width cap, 0 for none.
        val cap: Int
    )

    // Lays the chrome out for [prefs], with the swipe [arrow] when one is
    // wanted, and says where the bar and the dock went.
    fun place(prefs: LauncherPrefs, arrow: Drawable?): Placement {
        val placement = placement(prefs)
        padDock(prefs, placement)
        val chrome = layout(prefs, placement, arrow?.intrinsicHeight)
        placeDock(prefs, placement, chrome)
        placeBar(placement, chrome)
        if (arrow != null) {
            binding.dockSwipeIndicator.setImageDrawable(arrow)
            binding.dockSwipeIndicator.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = chrome.arrowBottom
            }
        }
        placePager(placement, chrome)
        return placement
    }

    private fun placement(prefs: LauncherPrefs): Placement {
        val config = SearchBarConfig.decode(prefs.searchBarConfig).desktop
        val slot = slotFor(SearchBarPlacement.fromName(config.placement), prefs.dockEnabled)
        val top = if (slot == BarSlot.TOP) {
            BottomChrome.topBar(context.resources.displayMetrics.density, host.systemInsets.top)
        } else {
            null
        }
        showBar(prefs, config, slot)
        // A phone on its side shares one row between the bar (left) and the
        // dock (right): stacked, the two took a third of the short window
        // and the grid's icons shrank to half size to fit what was left.
        val sideBySide = slot != null && top == null && geometry.phoneSideBySide(prefs)
        val shared = if (sideBySide) geometry.sharedRowPx(prefs) else null
        // Neither edge-to-edge style fits a dock that has only part of a row.
        val styled = shared == null && prefs.dockBgStyle != DockBgStyle.NONE && slot != BarSlot.BELOW_DOCK
        // Behind-nav drops the side and bottom margins so the background slab
        // runs flush to the window's bottom edge. Un-inset is the weaker
        // version of the same thing, so behind-nav wins.
        val behindNav = styled && prefs.dockBgBehindNav
        val fullBleed = styled && !prefs.dockBgPadding && !behindNav
        return Placement(slot, top, shared, behindNav, fullBleed, geometry.landscapeChromeWidthPx())
    }

    // A Replace widget takes the bar's slot; the built-in bar steps aside but
    // keeps driving the slot's geometry.
    private fun showBar(prefs: LauncherPrefs, config: DesktopSearchBarConfig, slot: BarSlot?) {
        val barWidget = if (slot == null) null else host.bindSearchBarWidget(prefs)
        binding.desktopSearchWidget.isVisible = barWidget != null
        binding.desktopSearchBar.isVisible = slot != null && barWidget == null
        if (slot != null) {
            binding.desktopSearchBar.apply(config, Styles.resolve(context, prefs).of(SurfaceId.SEARCH_BAR))
        }
    }

    // DOCK_ABOVE/DOCK_BELOW only exist relative to a visible dock;
    // PERSISTENT degrades to the bare bottom when the dock is off. TOP is
    // pinned under the status bar whatever the dock does. WIDGET is a later
    // phase and renders nothing.
    private fun slotFor(placement: SearchBarPlacement, dockEnabled: Boolean): BarSlot? = when (placement) {
        SearchBarPlacement.NONE, SearchBarPlacement.WIDGET -> null
        SearchBarPlacement.DOCK_ABOVE -> if (dockEnabled) BarSlot.ABOVE_DOCK else null
        SearchBarPlacement.DOCK_BELOW -> if (dockEnabled) BarSlot.BELOW_DOCK else null
        SearchBarPlacement.PERSISTENT -> if (dockEnabled) BarSlot.BELOW_DOCK else BarSlot.BOTTOM
        SearchBarPlacement.TOP -> BarSlot.TOP
    }

    // A background gets room above and below the icons, so they don't touch
    // its edges; behind-nav folds the bars' insets into the padding.
    private fun padDock(prefs: LauncherPrefs, placement: Placement) {
        val insets = host.systemInsets
        val bgPadV = if (prefs.dockBgStyle == DockBgStyle.NONE) 0 else context.dp(DOCK_BG_PADDING_V_DP)
        val nav = placement.behindNav
        binding.dockBar.setPadding(
            context.dp(prefs.dockPaddingH) + if (nav) insets.left else 0,
            context.dp(prefs.dockPaddingV) + bgPadV,
            context.dp(prefs.dockPaddingH) + if (nav) insets.right else 0,
            context.dp(prefs.dockPaddingV) + bgPadV + if (nav) insets.bottom else 0
        )
    }

    // Heights above the window bottom for the dock, the bar, the arrow, the
    // page indicator and the workspace's floor (BottomChrome). Before the
    // dock's first layout the old fixed bar height stands in; behind-nav
    // folds the navigation bar into the dock's own padding, so the laid-out
    // height counts it already.
    private fun layout(prefs: LauncherPrefs, placement: Placement, arrowHeight: Int?): BottomChrome.Result {
        val density = context.resources.displayMetrics.density
        val landscape = host.isLandscape
        val laidOut = host.dockHeightPx
        return BottomChrome.layout(
            BottomChrome.Spec(
                density = density,
                landscape = landscape,
                navInset = host.systemInsets.bottom,
                dockEnabled = prefs.dockEnabled,
                dockOverlay = prefs.dockOverlay,
                dockBehindNav = placement.behindNav,
                dockFullBleed = placement.fullBleed,
                dockHeight = if (laidOut > 0) laidOut else context.dp(FALLBACK_DOCK_HEIGHT_DP),
                dockIconInset = binding.dockBar.paddingTop + context.dp(SLOT_PADDING_V_DP + ITEM_PADDING_V_DP),
                dockBare = prefs.dockBgStyle == DockBgStyle.NONE,
                slot = placement.slot,
                arrowWanted = arrowHeight != null,
                arrowHeight = arrowHeight ?: 0,
                indicatorOn = prefs.pageIndicatorStyle != IndicatorStyle.NONE,
                pagePadding = pagePaddingVPx(prefs.desktopPaddingV, density, landscape),
                sideBySide = placement.shared != null
            )
        )
    }

    // Landscape: the slots share the dock's width by weight, so a dock that
    // spans a window twice as wide leaves the same icons stranded a
    // half-screen apart. Cap it near its portrait width and centre it, so
    // the dock reads as one row rather than a scattered line. The
    // edge-to-edge background styles are the exception - a slab that stops
    // short of the edges is not the thing they are asking for.
    private fun placeDock(prefs: LauncherPrefs, placement: Placement, chrome: BottomChrome.Result) {
        val insets = host.systemInsets
        val params = binding.dockBar.layoutParams as ViewGroup.MarginLayoutParams
        params.bottomMargin = chrome.dockBottom
        val edgeToEdge = placement.behindNav || placement.fullBleed
        val sideMargin = geometry.sideMarginPx(prefs, edgeToEdge)
        params.leftMargin = if (placement.behindNav) 0 else sideMargin + insets.left
        params.rightMargin = if (placement.behindNav) 0 else sideMargin + insets.right
        val shared = placement.shared
        val gravity = when {
            shared != null -> {
                params.width = shared.dock
                // The bar's 16dp, mirrored at the other edge.
                params.rightMargin = context.dp(DockGeometry.SIDE_BY_SIDE_GAP_DP) + insets.right
                Gravity.BOTTOM or Gravity.END
            }
            placement.cap > 0 && !edgeToEdge -> {
                params.width = geometry.stackedDockWidthPx(prefs, placement.cap)
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            else -> {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                Gravity.BOTTOM
            }
        }
        (params as? CoordinatorLayout.LayoutParams)?.gravity = gravity
        binding.dockBar.layoutParams = params
    }

    // Same landscape cap the dock takes: a search field running the full
    // width of a landscape window is a very long, very empty box. Side by
    // side it takes what the dock leaves of the row, at the start.
    private fun placeBar(placement: Placement, chrome: BottomChrome.Result) {
        val insets = host.systemInsets
        val shared = placement.shared
        val barWidth = when {
            shared != null -> shared.bar
            placement.cap > 0 -> placement.cap
            else -> ViewGroup.LayoutParams.MATCH_PARENT
        }
        val edge = if (placement.top != null) Gravity.TOP else Gravity.BOTTOM
        val barGravity = when {
            shared != null -> edge or Gravity.START
            placement.cap > 0 -> edge or Gravity.CENTER_HORIZONTAL
            else -> edge
        }
        val bar = binding.desktopSearchBar.layoutParams as ViewGroup.MarginLayoutParams
        bar.bottomMargin = if (placement.top != null) 0 else chrome.barBottom
        bar.topMargin = placement.top?.barTop ?: 0
        bar.leftMargin = context.dp(BAR_SIDE_MARGIN_DP) + insets.left
        bar.rightMargin = context.dp(BAR_SIDE_MARGIN_DP) + insets.right
        bar.width = barWidth
        (bar as? CoordinatorLayout.LayoutParams)?.gravity = barGravity
        binding.desktopSearchBar.layoutParams = bar
        // A Replace widget sits where the bar would.
        val widget = binding.desktopSearchWidget.layoutParams as ViewGroup.MarginLayoutParams
        widget.bottomMargin = bar.bottomMargin
        widget.topMargin = bar.topMargin
        widget.leftMargin = bar.leftMargin
        widget.rightMargin = bar.rightMargin
        widget.width = barWidth
        (widget as? CoordinatorLayout.LayoutParams)?.gravity = barGravity
        binding.desktopSearchWidget.layoutParams = widget
    }

    private fun placePager(placement: Placement, chrome: BottomChrome.Result) {
        val insets = host.systemInsets
        val pager = binding.workspacePager.layoutParams as ViewGroup.MarginLayoutParams
        pager.bottomMargin = chrome.pagerBottom
        pager.topMargin = placement.top?.pagerTop ?: insets.top
        pager.leftMargin = insets.left
        pager.rightMargin = insets.right
        binding.workspacePager.layoutParams = pager
        val indicator = binding.pageIndicator.layoutParams as ViewGroup.MarginLayoutParams
        indicator.bottomMargin = chrome.indicatorBottom
        indicator.leftMargin = insets.left
        indicator.rightMargin = insets.right
        binding.pageIndicator.layoutParams = indicator
    }

    companion object {
        // A dock slot's padding above and below, and a dock item's.
        const val SLOT_PADDING_V_DP = 2
        const val ITEM_PADDING_V_DP = 4
        // The dock background's room above and below the icons.
        private const val DOCK_BG_PADDING_V_DP = 8
        // The dock's height before its first layout.
        private const val FALLBACK_DOCK_HEIGHT_DP = 72
        private const val BAR_SIDE_MARGIN_DP = 16
    }
}
