package com.supernova.launcher.data

// A backup's widget ids mean something only on the install that wrote it, and
// then only while they are still bound: any other is cleared, so it can't show
// someone else's widget. The provider stays, for WidgetController to bind a
// new id to.
internal object RestoredWidgets {

    // What the import's message counts: widgets and widget cards with no id.
    fun needingRebind(model: LauncherModel): Int =
        model.items.count { it.itemType == ItemType.WIDGET && it.appWidgetId == null } +
            model.items.sumOf { item -> item.stack.count { it.appWidgetId == null } } +
            model.items.sumOf { ShutterWidgets.needingRebind(it) } +
            model.feedCards.count { it.widgetProvider != null && it.appWidgetId == null }

    fun withStaleCleared(
        model: LauncherModel,
        widgetBound: (appWidgetId: Int, provider: String?) -> Boolean
    ): LauncherModel = model.copy(
        items = model.items.map { item ->
            val id = item.appWidgetId
            val own = if (item.itemType == ItemType.WIDGET && id != null && !widgetBound(id, item.widgetProvider)) {
                item.copy(appWidgetId = null)
            } else {
                item
            }
            // A stack's other widgets (#10) the same way, and the widgets
            // its swipes show (#14).
            val shutters = ShutterWidgets.withStaleCleared(own, widgetBound)
            shutters.copy(
                stack = shutters.stack.map { member ->
                    val memberId = member.appWidgetId
                    if (memberId != null && !widgetBound(memberId, member.widgetProvider)) {
                        member.copy(appWidgetId = null)
                    } else {
                        member
                    }
                }
            )
        },
        feedCards = model.feedCards.map { card ->
            val id = card.appWidgetId
            if (id != null && !widgetBound(id, card.widgetProvider)) card.copy(appWidgetId = null) else card
        }
    )
}
