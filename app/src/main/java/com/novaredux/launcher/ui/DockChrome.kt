package com.novaredux.launcher.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.isVisible
import com.novaredux.launcher.R
import com.novaredux.launcher.data.DockImage
import com.novaredux.launcher.data.LauncherPrefs
import com.novaredux.launcher.databinding.ActivityLauncherBinding
import com.novaredux.launcher.gesture.GestureDefaults
import com.novaredux.launcher.ui.theme.ResolvedSurface
import com.novaredux.launcher.ui.theme.Styles
import com.novaredux.launcher.ui.theme.SurfaceId
import com.novaredux.launcher.ui.theme.SurfaceShapes

// The dock and what lies around it on the home screen: whether it shows,
// the swipe arrow pointing at it, the scrims, its background and the Home
// surface's colour, with ChromeMargins placing the dock, the search bar, the
// workspace and the page indicator. Guarded, so a state emission that moves
// none of it rebuilds no drawables.
internal class DockChrome(
    private val context: Context,
    private val binding: ActivityLauncherBinding,
    geometry: DockGeometry,
    private val host: Host
) {
    interface Host {
        // System bar and cutout insets, the single source the chrome pads from.
        val systemInsets: Insets
        // The dock's laid-out height: 0 before its first layout.
        val dockHeightPx: Int
        val isLandscape: Boolean
        // Hidden for the moment: closed after a launch, or toggled off.
        val dockHidden: Boolean
        // The Replace widget in the search bar's slot, when one is set.
        fun bindSearchBarWidget(prefs: LauncherPrefs): View?
    }

    private val margins = ChromeMargins(context, binding, geometry, host)
    private var applied: List<Any>? = null

    // The next apply lays everything out again, whatever its guard says.
    fun invalidate() {
        applied = null
    }

    fun apply(prefs: LauncherPrefs) {
        val key = guardKey(prefs)
        if (applied == key) return
        applied = key
        binding.dockBar.isVisible = prefs.dockEnabled && !host.dockHidden
        syncArrow(prefs)
        binding.scrimTop.isVisible = prefs.showScrims
        binding.scrimBottom.isVisible = prefs.showScrims
        val arrow = if (arrowWanted(prefs)) {
            SwipeArrowDrawable(
                context.resources.displayMetrics.density,
                LabelStyler.parseColor(prefs.labelColor, context.getColor(R.color.onWallpaper))
            )
        } else {
            null
        }
        val placement = margins.place(prefs, arrow)
        // The Home surface: a colour over the wallpaper, under everything on
        // the home screen. Transparent until one is picked, so the wallpaper
        // shows.
        val styles = Styles.resolve(context, prefs)
        binding.root.setBackgroundColor(styles.of(SurfaceId.HOME).surface)
        val edgeToEdge = placement.behindNav || placement.fullBleed
        binding.dockBar.background = background(prefs, styles.of(SurfaceId.DOCK), squareBottom = edgeToEdge)
    }

    // The swipe indicator belongs to a swipe up that opens the drawer, and
    // points at the dock: it is only wanted with both.
    fun arrowWanted(prefs: LauncherPrefs): Boolean =
        prefs.drawerSwipeIndicator && prefs.dockEnabled && GestureDefaults.swipeUpOpensDrawer(prefs.gestureBindings)

    // It hides and comes back with the dock (auto-close, the dock toggle),
    // keeping its strip as the dock keeps its margins.
    fun syncArrow(prefs: LauncherPrefs) {
        binding.dockSwipeIndicator.isVisible = arrowWanted(prefs) && binding.dockBar.isVisible
    }

    private fun guardKey(prefs: LauncherPrefs): List<Any> = listOf(
        prefs.dockEnabled, prefs.dockBgStyle, prefs.dockBgOpacity, prefs.dockBgRadius,
        // The image by identity: the string is long, and only its
        // replacement matters.
        System.identityHashCode(prefs.dockBgImage),
        prefs.dockOverlay, prefs.showScrims,
        prefs.styleConfig, prefs.paletteSource, prefs.paletteSeed,
        prefs.searchBarConfig, prefs.searchBarWidgetId,
        prefs.dockPaddingH, prefs.dockPaddingV,
        prefs.dockBgBehindNav, prefs.dockBgPadding,
        // What a landscape dock's width is worked out from.
        prefs.dockCount, prefs.iconScale, prefs.dockIconScale, prefs.dockMatchDesktop,
        arrowWanted(prefs), prefs.labelColor,
        prefs.pageIndicatorStyle != IndicatorStyle.NONE, prefs.desktopPaddingV,
        host.systemInsets, host.dockHeightPx, host.isLandscape
    )

    // Nova's shapes, filled with the dock's colour or its image; a plain
    // rectangle with no image keeps the style engine's own background.
    // Behind-nav and un-inset square the bottom corners.
    private fun background(prefs: LauncherPrefs, resolved: ResolvedSurface, squareBottom: Boolean): Drawable? {
        val style = prefs.dockBgStyle
        val radius = if (style == DockBgStyle.ROUNDED) context.dp(prefs.dockBgRadius).toFloat() else 0f
        val bottom = if (squareBottom) 0f else radius
        val image = DockImage.decode(prefs.dockBgImage)
        val shaped = style == DockBgStyle.ARC || style == DockBgStyle.PLATFORM
        return when {
            style == DockBgStyle.NONE -> null
            image == null && !shaped -> SurfaceShapes.background(
                resolved.surface,
                if (squareBottom) floatArrayOf(radius, radius, 0f, 0f) else null,
                radius
            )
            else -> DockShapeDrawable(style, resolved.surface, image, floatArrayOf(radius, radius, bottom, bottom))
        }
    }
}
