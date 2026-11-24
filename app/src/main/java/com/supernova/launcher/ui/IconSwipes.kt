package com.supernova.launcher.ui

import android.content.ComponentName
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.supernova.launcher.data.ItemType
import com.supernova.launcher.data.LaunchTarget
import com.supernova.launcher.data.LaunchableApp
import com.supernova.launcher.data.LauncherItem
import com.supernova.launcher.data.ShutterWidgets
import com.supernova.launcher.gesture.ActionShortcut
import com.supernova.launcher.gesture.GestureBinding
import com.supernova.launcher.gesture.HomeGestureDetector
import kotlin.math.abs

// An icon's own swipes: a short vertical flick on it runs what it is bound
// to, up and down independently (a swipe-to-open folder's swipe up opens
// it), while taps and long-presses keep working through the normal
// listeners.
class IconSwipes(private val surfaces: ItemSurfaces, private val host: Host) {
    interface Host {
        val gestures: HomeGestureDetector

        // The drawer pull armed on this down lets the stream go: an icon's
        // swipe up is the icon's.
        fun disarmDrawerPull()

        fun runAction(action: GestureBinding)
        fun launchIntentUri(uri: String)

        // One of the app's widgets, in a panel by the icon (#14).
        fun showShutter(item: LauncherItem, shutter: ShutterWidgets.Shutter, down: Boolean, anchor: View)
    }

    // `inFolder` is set for an app in an open folder (#224) and runs just
    // before the action (the folder's close on launch).
    fun attach(view: View, item: LauncherItem, inFolder: (() -> Unit)? = null) {
        // A swipe-to-open folder's swipe up is its own: it opens the folder,
        // whatever swipe-up action it still holds.
        val opensOnSwipe = item.itemType == ItemType.FOLDER && item.swipeToOpen
        val claimsUp = opensOnSwipe || item.swipeAction != null
        val claimsDown = item.swipeActionDown != null
        if (!claimsUp && !claimsDown) return
        var startY = 0f
        var startX = 0f
        // The swipe ran: the rest of the stream is the icon's, so the lift
        // can't also tap it.
        var swiped = false
        val hold = inFolder?.let { FolderTileSwipeHold(view, claimsUp, claimsDown) }
        // It only claims a swipe; a tap falls through to the icon's own
        // click handling, which accessibility services reach as usual.
        //noinspection ClickableViewAccessibility
        view.setOnTouchListener { v, event ->
            // In an open folder the grid is held off until the finger has
            // gone one way.
            hold?.onTouch(v, event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startX = event.rawX
                    swiped = false
                    // An open folder's window never feeds the home detector
                    // or the drawer pull.
                    if (hold == null) claimHomeSwipe(claimsUp, claimsDown)
                }
                // Decided as the finger crosses the home swipe's distance,
                // not on the lift: waiting for the lift needed twice that
                // distance, and a short flick still inside the icon read
                // as a tap, a slow one as a long-press.
                // The lift too, for a flick whose last move fell just short.
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> if (!swiped) {
                    val up = swipeAt(event.rawX - startX, startY - event.rawY, claimsUp, claimsDown)
                    if (up != null) {
                        swiped = true
                        runSwipe(v, event, item, up, inFolder)
                    }
                }
            }
            swiped
        }
    }

    // A swipe-to-open folder's tap: the first app its window shows, launched
    // from the folder's icon. A folder with nothing to launch opens instead.
    fun launchFolderFirstItem(folder: LauncherItem, anchor: View) {
        val first = surfaces.viewModel.folderFirstMember(folder.id)
        if (first == null) surfaces.folderCards.showFolder(anchor, folder) else surfaces.launchItem(first, anchor)
    }

    // The swipe on an icon: true up, false down, null for neither. dy is the
    // finger's rise; the distance is the home swipes' own
    // (HomeGestureDetector.minSwipeDistance).
    private fun swipeAt(dx: Float, dy: Float, claimsUp: Boolean, claimsDown: Boolean): Boolean? {
        val min = host.gestures.minSwipeDistance
        return when {
            claimsUp && dy >= min && dy > abs(dx) -> true
            claimsDown && -dy >= min && -dy > abs(dx) -> false
            else -> null
        }
    }

    private fun claimHomeSwipe(claimsUp: Boolean, claimsDown: Boolean) {
        // Both the icon's listener and the home detector decide on the same
        // lift, so the icon claims the directions it binds - otherwise one
        // swipe ran the icon's action AND the home binding. dispatchTouchEvent
        // feeds the detector before the press reaches the icon, so this lands
        // after the detector has cleared the flags for this gesture.
        host.gestures.suppressSwipe(up = claimsUp, down = claimsDown)
        // The drawer pull arms on the same down, before the press reaches the
        // icon, and takes the stream once the finger rises past the slop:
        // every icon's swipe up went to the drawer while a swipe up opens it.
        // The icon takes it back.
        if (claimsUp) host.disarmDrawerPull()
    }

    private fun runSwipe(v: View, event: MotionEvent, item: LauncherItem, up: Boolean, inFolder: (() -> Unit)?) {
        val opensOnSwipe = item.itemType == ItemType.FOLDER && item.swipeToOpen
        // The rest of the stream never reaches the icon's own handler, so its
        // press state and pending long-press are cancelled by hand; a folder
        // opening in place would keep the ripple.
        val cancel = MotionEvent.obtain(event)
        cancel.action = MotionEvent.ACTION_CANCEL
        v.onTouchEvent(cancel)
        cancel.recycle()
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        inFolder?.invoke()
        when {
            up && opensOnSwipe -> surfaces.folderCards.showFolder(v, item)
            up -> launchAction(v, item, item.swipeAction, item.swipeLabel, down = false)
            else -> launchAction(v, item, item.swipeActionDown, item.swipeLabelDown, down = true)
        }
    }

    private fun launchAction(v: View, item: LauncherItem, action: String?, label: String?, down: Boolean) {
        action ?: return
        val shutter = ShutterWidgets.of(action)
        val bound = ActionShortcut.decode(action)
        when {
            // A widget's panel opens from the icon, and a widget a restore
            // left unbound is bound to the swipe it belongs to (#14).
            shutter != null -> host.showShutter(item, shutter, down, v)
            // A launcher-action uri runs through the shared action dispatcher.
            bound != null -> host.runAction(bound)
            // An intent an app's shortcut screen made (a direct dial, a route).
            CreatedShortcuts.isIntentUri(action) -> host.launchIntentUri(action)
            else -> launchTarget(item, action, label)
        }
    }

    // Shortcut targets are stored "pkg|shortcutId"; anything else is a
    // flattened component, as written before shortcuts were supported.
    // Either carries its app's profile ("@serial"); older ones don't, and
    // open in the swiped icon's.
    private fun launchTarget(item: LauncherItem, action: String, label: String?) {
        val (target, storedSerial) = LaunchTarget.parse(action)
        val serial = storedSerial ?: item.userSerial
        if (target.contains('|')) {
            surfaces.launchShortcut(target.substringBefore('|'), target.substringAfter('|'), serial)
            return
        }
        val component = ComponentName.unflattenFromString(target) ?: return
        surfaces.launchApp(LaunchableApp(component.packageName, component.className, label.orEmpty(), serial))
    }
}
