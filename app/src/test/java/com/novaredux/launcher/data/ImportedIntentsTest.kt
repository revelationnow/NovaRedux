package com.novaredux.launcher.data

import com.novaredux.launcher.gesture.Gesture
import com.novaredux.launcher.gesture.GestureAction
import com.novaredux.launcher.gesture.GestureBinding
import com.novaredux.launcher.gesture.GestureDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportedIntentsTest {

    private val call = "intent:tel:5550123#Intent;action=android.intent.action.CALL;end"
    private val dial = "intent:tel:5550123#Intent;action=android.intent.action.DIAL;end"

    @Test
    fun aCallFromABackupOpensTheDialerInstead() {
        assertEquals(dial, ImportedIntents.calmed(call))
        // Nova keeps its intents without the scheme in front.
        assertEquals(
            "#Intent;action=android.intent.action.DIAL;S.x=y;end",
            ImportedIntents.calmed("#Intent;action=android.intent.action.CALL;S.x=y;end")
        )
    }

    @Test
    fun everythingElseIsLeftAsItCame() {
        val view = "intent:https://example.org#Intent;action=android.intent.action.VIEW;end"
        assertEquals(view, ImportedIntents.calmed(view))
        assertEquals("supernova://action/OPEN_DRAWER", ImportedIntents.calmed("supernova://action/OPEN_DRAWER"))
        assertEquals(null, ImportedIntents.calmed(null as String?))
    }

    @Test
    fun itemsAndTheirSwipesAreCalmed() {
        val item = LauncherItem(
            id = "a", itemType = ItemType.SHORTCUT, container = ContainerType.WORKSPACE,
            intentUri = call, swipeAction = call, swipeActionDown = call
        )
        val calmed = ImportedIntents.calmed(LauncherModel(items = listOf(item))).items.single()
        assertEquals(listOf(dial, dial, dial), listOf(calmed.intentUri, calmed.swipeAction, calmed.swipeActionDown))
    }

    @Test
    fun aGestureBoundToACallDialsAndStillReadsBack() {
        val stored = setOf(
            GestureDefaults.encode(Gesture.DOUBLE_TAP, GestureBinding(GestureAction.LAUNCH_INTENT, call, "Mum"))
        )
        val calmed = ImportedIntents.calmed(LauncherPrefs(gestureBindings = stored)).gestureBindings
        val binding = GestureDefaults.decode(calmed).getValue(Gesture.DOUBLE_TAP)
        assertEquals(GestureBinding(GestureAction.LAUNCH_INTENT, dial, "Mum"), binding)
    }
}
