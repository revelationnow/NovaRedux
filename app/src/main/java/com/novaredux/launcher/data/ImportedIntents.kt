package com.novaredux.launcher.data

// What a backup brings in, before it is laid out (#21, from a security
// review): a stored intent that would place a call (a Direct dial shortcut,
// or anything posing as one) opens the dialer with the number instead. A
// backup is a file from anywhere and an icon can say anything; the dialer
// shows the number before it is called. Shortcuts made on this phone are
// untouched, and every stored intent is scrubbed again as it starts
// (ui/SafeIntents).
//
// On the text of the intent (Intent.toUri's), which holds its action as
// ";action=...;" whatever comes before it, and keeps it so inside a
// gesture's stored binding.
object ImportedIntents {
    private const val CALL = ";action=android.intent.action.CALL;"
    private const val DIAL = ";action=android.intent.action.DIAL;"

    fun calmed(uri: String?): String? = uri?.replace(CALL, DIAL)

    fun calmed(model: LauncherModel): LauncherModel = model.copy(
        items = model.items.map { item ->
            item.copy(
                intentUri = calmed(item.intentUri),
                swipeAction = calmed(item.swipeAction),
                swipeActionDown = calmed(item.swipeActionDown)
            )
        }
    )

    fun calmed(prefs: LauncherPrefs): LauncherPrefs =
        prefs.copy(gestureBindings = prefs.gestureBindings.mapTo(LinkedHashSet()) { it.replace(CALL, DIAL) })
}
