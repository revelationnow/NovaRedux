package com.supernova.launcher.data

import com.supernova.launcher.gesture.ActionShortcut
import com.supernova.launcher.gesture.GestureAction

// An icon swipe that shows one of its app's widgets in a panel by the icon,
// as Action Launcher's Shutters do (#14). The swipe's target is a SHOW_WIDGET
// action whose payload is "<host id>|<provider>": the provider is what lets
// a restore tell the widget from a stale id, and bind it again.
object ShutterWidgets {

    data class Shutter(val appWidgetId: Int?, val provider: String?)

    private const val SEPARATOR = '|'

    fun target(appWidgetId: Int?, provider: String?): String =
        ActionShortcut.encode(GestureAction.SHOW_WIDGET, "${appWidgetId ?: ""}$SEPARATOR${provider.orEmpty()}")

    // The widget a swipe's target shows; null for any other target.
    fun of(swipeAction: String?): Shutter? =
        ActionShortcut.decode(swipeAction)
            ?.takeIf { it.action == GestureAction.SHOW_WIDGET }
            ?.payload
            ?.let { payload ->
                Shutter(
                    payload.substringBefore(SEPARATOR).toIntOrNull(),
                    payload.substringAfter(SEPARATOR, "").ifEmpty { null }
                )
            }

    // The host ids an item's swipes hold, which the orphan sweep keeps.
    fun ids(item: LauncherItem): List<Int> =
        listOfNotNull(of(item.swipeAction)?.appWidgetId, of(item.swipeActionDown)?.appWidgetId)

    // A restore: a swipe's widget that isn't bound here to its provider loses
    // its id and keeps the provider, so it can't show someone else's widget.
    fun withStaleCleared(item: LauncherItem, widgetBound: (Int, String?) -> Boolean): LauncherItem {
        fun cleared(action: String?): String? {
            val shutter = of(action) ?: return action
            val id = shutter.appWidgetId
            return if (id == null || widgetBound(id, shutter.provider)) action else target(null, shutter.provider)
        }
        return item.copy(swipeAction = cleared(item.swipeAction), swipeActionDown = cleared(item.swipeActionDown))
    }

    // An item's swipes whose widget waits on a new id.
    fun needingRebind(item: LauncherItem): Int =
        listOf(item.swipeAction, item.swipeActionDown).count { action ->
            of(action)?.let { it.appWidgetId == null && it.provider != null } == true
        }
}
