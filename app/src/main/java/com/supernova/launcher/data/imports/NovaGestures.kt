package com.supernova.launcher.data.imports

import com.supernova.launcher.data.LauncherPrefs
import com.supernova.launcher.gesture.ActionShortcut
import com.supernova.launcher.gesture.Gesture
import com.supernova.launcher.gesture.GestureAction
import com.supernova.launcher.gesture.GestureBinding
import com.supernova.launcher.gesture.GestureDefaults
import com.supernova.launcher.gesture.HomeButtonAction

// Nova's home-screen gestures, its Home button and its icon swipe actions.
// Nova keeps each as "Label$#Intent;...;end": one of its own actions (an
// extra names it), its own settings screen, an app, or any other intent; an
// empty value is Nothing.
internal object NovaGestures {

    fun applyTo(prefs: LauncherPrefs, nova: NovaValues): LauncherPrefs {
        val bound = GESTURES.mapNotNull { (key, gesture) -> nova.raw(key)?.let { gesture to binding(it) } }.toMap()
        val kept = prefs.gestureBindings.filterNot { entry -> entry.substringBefore(':') in bound.keys.map { it.name } }
        val home = nova.raw("home_button_intent")?.let(::binding)?.action?.takeIf { it in HomeButtonAction.choices }
        return prefs.copy(
            gestureBindings = kept.toSet() +
                bound.map { (gesture, binding) -> GestureDefaults.encode(gesture, binding) },
            homeButtonAction = home?.name ?: prefs.homeButtonAction,
            homeButtonOnMainPage = nova.bool("home_button_action_default_page_only") ?: prefs.homeButtonOnMainPage
        )
    }

    // An icon's swipe-up action as LauncherItem.swipeAction keeps it, with its
    // label: a launcher action's uri, an app's "pkg/cls", or an intent uri.
    // Null for none, or for an action NovaRedux has no twin of.
    fun swipeAction(value: String?): Pair<String, String?>? {
        val binding = value?.takeIf { it.isNotBlank() }?.let(::binding) ?: return null
        val action = when (binding.action) {
            GestureAction.NONE -> null
            GestureAction.LAUNCH_APP, GestureAction.LAUNCH_INTENT -> binding.payload
            else -> ActionShortcut.encode(binding.action)
        }
        return action?.let { it to binding.label }
    }

    fun binding(value: String): GestureBinding {
        val split = value.indexOf(INTENT_MARKER)
        val label = if (split > 0) value.substring(0, split) else null
        val intent = IntentText.parse(if (split >= 0) value.substring(split + 1) else value)
            ?: return GestureBinding(GestureAction.NONE)
        val own = intent.extras[OWN_ACTION]
        val component = intent.component
        return when {
            own != null -> GestureBinding(LayoutConverter.ownAction(own) ?: GestureAction.NONE)
            component?.first == NOVA && component.second.contains(SETTINGS) ->
                GestureBinding(GestureAction.OPEN_SETTINGS)
            intent.action == ACTION_MAIN && component != null ->
                GestureBinding(GestureAction.LAUNCH_APP, "${component.first}/${component.second}", label)
            else -> GestureBinding(GestureAction.LAUNCH_INTENT, intent.uri, label)
        }
    }

    private const val INTENT_MARKER = "\$#Intent;"
    private const val OWN_ACTION = "LAUNCHER_ACTION"
    private const val NOVA = "com.teslacoilsw.launcher"
    private const val SETTINGS = "Settings"
    private const val ACTION_MAIN = "android.intent.action.MAIN"

    // Gesture names as Nova writes them. Those not seen in a backup yet
    // follow the same pattern; one that doesn't match is simply not read.
    private val GESTURES = mapOf(
        "gesture_swipe_up" to Gesture.SWIPE_UP,
        "gesture_swipe_down" to Gesture.SWIPE_DOWN,
        "gesture_double_tap" to Gesture.DOUBLE_TAP,
        "gesture_pinch" to Gesture.PINCH_IN,
        "gesture_pinch_out" to Gesture.PINCH_OUT,
        "gesture_two_finger_scroll_up" to Gesture.TWO_FINGER_SWIPE_UP,
        "gesture_two_finger_scroll_down" to Gesture.TWO_FINGER_SWIPE_DOWN,
        "gesture_double_tap_swipe_up" to Gesture.DOUBLE_TAP_SWIPE_UP,
        "gesture_double_tap_swipe_down" to Gesture.DOUBLE_TAP_SWIPE_DOWN,
        "gesture_two_finger_rotate_cw" to Gesture.TWO_FINGER_ROTATE_CW,
        "gesture_two_finger_rotate_ccw" to Gesture.TWO_FINGER_ROTATE_CCW
    )
}
