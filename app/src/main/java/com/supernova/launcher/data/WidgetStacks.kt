package com.supernova.launcher.data

// Widget stacks (#10): several widgets in one widget item's cells, one
// showing at a time. The item's own widget is the stack's first; the rest
// follow in LauncherItem.stack. Pure, so the arithmetic is tested off the
// phone.
object WidgetStacks {

    fun size(item: LauncherItem): Int = 1 + item.stack.size

    // The widget showing, kept in range whatever was stored.
    fun shown(item: LauncherItem): Int = item.stackShown.mod(size(item))

    // Every host id the item holds, its own first.
    fun ids(item: LauncherItem): List<Int> = listOfNotNull(item.appWidgetId) + item.stack.mapNotNull { it.appWidgetId }

    // The widget at index as a stack member: 0 is the item's own.
    fun member(item: LauncherItem, index: Int): StackedWidget =
        if (index == 0) {
            StackedWidget(item.appWidgetId, item.widgetProvider, item.userSerial, item.label)
        } else {
            item.stack[index - 1]
        }

    // The item as if the widget at index were its own: what its menu's App
    // info, widget settings and export act on.
    fun asShown(item: LauncherItem, index: Int): LauncherItem {
        val member = member(item, index.mod(size(item)))
        return item.copy(
            appWidgetId = member.appWidgetId,
            widgetProvider = member.widgetProvider,
            userSerial = member.userSerial,
            label = member.label
        )
    }

    // One more widget on the stack, showing.
    fun added(item: LauncherItem, widget: StackedWidget): LauncherItem =
        item.copy(stack = item.stack + widget, stackShown = item.stack.size + 1)

    // The stack without the widget at index, and that widget's host id to
    // free. When the item's own goes, the next one takes its place. The
    // last widget left can't go this way: removing it removes the item.
    fun removed(item: LauncherItem, index: Int): Pair<LauncherItem, Int?> = when {
        item.stack.isEmpty() -> item to null
        index == 0 -> {
            val next = item.stack.first()
            item.copy(
                appWidgetId = next.appWidgetId,
                widgetProvider = next.widgetProvider,
                userSerial = next.userSerial,
                label = next.label,
                stack = item.stack.drop(1),
                stackShown = 0
            ) to item.appWidgetId
        }
        else -> {
            val at = (index - 1).coerceIn(item.stack.indices)
            item.copy(
                stack = item.stack.filterIndexed { i, _ -> i != at },
                stackShown = index - 1
            ) to item.stack[at].appWidgetId
        }
    }

    // A restored member bound to a new host id.
    fun withMemberId(item: LauncherItem, index: Int, appWidgetId: Int): LauncherItem =
        if (index == 0) {
            item.copy(appWidgetId = appWidgetId)
        } else {
            item.copy(
                stack = item.stack.mapIndexed { i, w -> if (i == index - 1) w.copy(appWidgetId = appWidgetId) else w }
            )
        }
}
