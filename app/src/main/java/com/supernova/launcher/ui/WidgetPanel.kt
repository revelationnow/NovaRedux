package com.supernova.launcher.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import com.supernova.launcher.R
import com.supernova.launcher.data.LauncherItem
import com.supernova.launcher.data.LauncherPrefs
import com.supernova.launcher.data.ShutterWidgets
import com.supernova.launcher.ui.theme.Styles
import com.supernova.launcher.ui.theme.SurfaceId
import com.supernova.launcher.ui.theme.SurfaceShapes
import com.supernova.launcher.widget.WidgetSizing

// A swipe's widget (#14) in a panel by its icon over the home screen, as
// Action Launcher's Shutters: the widget at its default size, on the folder
// window's colour and corners. A tap outside it, Back, Home or the launcher
// going out of view closes it.
class WidgetPanel(
    private val activity: AppCompatActivity,
    private val viewModel: LauncherViewModel,
    private val widgets: () -> WidgetController,
    private val cellSize: () -> Pair<Int, Int>
) {
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    private var window: PopupWindow? = null

    fun show(item: LauncherItem, shutter: ShutterWidgets.Shutter, down: Boolean, anchor: View) {
        val id = shutter.appWidgetId?.takeIf { manager.getAppWidgetInfo(it) != null }
        val view = id?.let { widgets().panelWidgetView(it) }
        if (id != null && view != null) {
            open(id, view, anchor)
            return
        }
        // Its id went with a restore, or with its app's update: the widget
        // is bound to the swipe again, then shown.
        val rebinding = widgets().rebindShutter(item, down, shutter.provider) { newId, _ ->
            widgets().panelWidgetView(newId)?.let { open(newId, it, anchor) }
        }
        if (!rebinding) Toast.makeText(activity, R.string.widget_provider_missing, Toast.LENGTH_SHORT).show()
    }

    // Whether one was up.
    fun dismiss(): Boolean {
        val wasUp = window?.isShowing == true
        window?.dismiss()
        window = null
        return wasUp
    }

    private fun open(widgetId: Int, widget: View, anchor: View) {
        dismiss()
        val info = manager.getAppWidgetInfo(widgetId) ?: return
        val prefs = viewModel.state.value.prefs
        val (width, height) = panelSize(info, prefs)
        val inner = 2 * activity.dp(PADDING_DP)
        (widget as? AppWidgetHostView)?.let { hostView ->
            WidgetSizing.reportSize(hostView, width - inner, height - inner, activity.resources.displayMetrics.density)
        }
        val card = card(widget, prefs)
        val scrim = FrameLayout(activity).apply {
            setOnClickListener { dismiss() }
            addView(card, FrameLayout.LayoutParams(width, height))
        }
        val (x, y) = PanelPlacement.place(iconArea(anchor), width, height, room(), activity.dp(GAP_DP))
        // Where the window itself sits is known once it is laid out.
        scrim.doOnLayout {
            val at = IntArray(2).also(scrim::getLocationOnScreen)
            card.translationX = (x - at[0]).toFloat()
            card.translationY = (y - at[1]).toFloat()
        }
        val popup = PopupWindow(scrim, MATCH, MATCH, true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.animationStyle = if (Animations.enabled(prefs)) R.style.FolderPopupAnimation else 0
        // The widget's view is kept for next time, off this panel.
        popup.setOnDismissListener { card.removeAllViews() }
        popup.showAtLocation(activity.window.decorView, Gravity.NO_GRAVITY, 0, 0)
        window = popup
    }

    // The widget's default cells, and the panel's padding around them, kept
    // to the screen.
    private fun panelSize(info: AppWidgetProviderInfo, prefs: LauncherPrefs): Pair<Int, Int> {
        val (cellWidth, cellHeight) = cellSize()
        val spans = WidgetSizing.defaultSpans(activity, info, cellWidth, cellHeight, prefs.gridCols, prefs.gridRows)
        val root = activity.window.decorView
        val inner = 2 * activity.dp(PADDING_DP)
        val width = (spans.first * cellWidth + inner).coerceAtMost(root.width - 2 * activity.dp(MARGIN_DP))
        val height = (spans.second * cellHeight + inner).coerceAtMost((root.height * MAX_HEIGHT_SHARE).toInt())
        return Pair(width, height)
    }

    private fun card(widget: View, prefs: LauncherPrefs): FrameLayout {
        val surface = Styles.resolve(activity, prefs).of(SurfaceId.FOLDER)
        val density = activity.resources.displayMetrics.density
        val padding = activity.dp(PADDING_DP)
        return FrameLayout(activity).apply {
            background = SurfaceShapes.background(
                surface.surface,
                surface.cornerRadiiDp?.let { radii -> FloatArray(radii.size) { radii[it] * density } },
                activity.dp(CORNER_DP).toFloat()
            )
            clipToOutline = true
            elevation = if (prefs.folderShadow) activity.dp(ELEVATION_DP).toFloat() else 0f
            setPadding(padding, padding, padding, padding)
            // A tap on its edge isn't one outside it.
            isClickable = true
            addView(widget, FrameLayout.LayoutParams(MATCH, MATCH))
        }
    }

    // The icon on the screen; none once it has gone (an open folder's icon,
    // with the folder closed by the swipe).
    private fun iconArea(anchor: View): PanelPlacement.Area? = anchor.takeIf { it.isAttachedToWindow }?.let { icon ->
        val at = IntArray(2).also(icon::getLocationOnScreen)
        PanelPlacement.Area(at[0], at[1], at[0] + icon.width, at[1] + icon.height)
    }

    // The screen between the bars, less a margin.
    private fun room(): PanelPlacement.Area {
        val root = activity.window.decorView
        val at = IntArray(2).also(root::getLocationOnScreen)
        val bars = ViewCompat.getRootWindowInsets(root)?.getInsets(WindowInsetsCompat.Type.systemBars()) ?: Insets.NONE
        val margin = activity.dp(MARGIN_DP)
        return PanelPlacement.Area(
            at[0] + bars.left + margin,
            at[1] + bars.top + margin,
            at[0] + root.width - bars.right - margin,
            at[1] + root.height - bars.bottom - margin
        )
    }

    private companion object {
        const val PADDING_DP = 8
        const val GAP_DP = 8
        const val MARGIN_DP = 16
        const val CORNER_DP = 24
        const val ELEVATION_DP = 8
        const val MAX_HEIGHT_SHARE = 0.6f
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
