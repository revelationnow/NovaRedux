package com.novaredux.launcher.data

// The Drawer groups page's edits of the stored list as a whole: a drag that
// reorders the tabs or the folders (or carries a group from one to the
// other), Sort A-Z, and a deleted group brought back.
//
// A group with no order of its own (Int.MAX_VALUE, DrawerGroup's default)
// goes after the ordered ones, by name. Folders start that way, made here or
// imported, and show A-Z as Nova shows them; a drag among them gives them
// an order, which Sort A-Z takes away again. Tabs and folders are separate
// lists (the tab bar, the drawer's folders), so a change to one never
// numbers the other.
object DrawerGroupOrder {

    // The order every surface shows groups in: by their stored order, then
    // by name.
    fun sorted(groups: List<DrawerGroup>): List<DrawerGroup> =
        groups.sortedWith(compareBy<DrawerGroup> { it.order }.then(LabelOrder.by { it.name }))

    // The groups of one type in the order ids gives, numbered from the top;
    // the other type's keep their orders. A group of the type that ids
    // leaves out (one made meanwhile) follows the named ones.
    fun reordered(groups: List<DrawerGroup>, type: DrawerGroupType, ids: List<String>): List<DrawerGroup> {
        val sorted = sorted(groups)
        val ofType = sorted.filter { it.type == type }
        val named = ids.distinct().mapNotNull { id -> ofType.firstOrNull { it.id == id } }
        val queue = ArrayDeque(
            (named + ofType.filterNot { it in named }).mapIndexed { position, group -> group.copy(order = position) }
        )
        return sorted.map { if (it.type == type) queue.removeFirst() else it }
    }

    // A drag across the Drawer groups page's two cards (#267): the tabs and
    // the folders each in the order their ids give, a group dragged into the
    // other card changed by retype on the way (LauncherViewModel.retyped).
    // A card the drag left as it was keeps its orders, so folders still
    // showing A-Z go on doing so.
    fun arranged(
        groups: List<DrawerGroup>,
        tabs: List<String>,
        folders: List<String>,
        retype: (DrawerGroup, DrawerGroupType) -> DrawerGroup
    ): List<DrawerGroup> {
        val retyped = groups.map {
            when {
                it.id in tabs && it.type != DrawerGroupType.TAB -> retype(it, DrawerGroupType.TAB)
                it.id in folders && it.type != DrawerGroupType.FOLDER -> retype(it, DrawerGroupType.FOLDER)
                else -> it
            }
        }
        val withTabs = if (moved(groups, DrawerGroupType.TAB, tabs)) {
            reordered(retyped, DrawerGroupType.TAB, tabs)
        } else {
            retyped
        }
        return if (moved(groups, DrawerGroupType.FOLDER, folders)) {
            reordered(withTabs, DrawerGroupType.FOLDER, folders)
        } else {
            withTabs
        }
    }

    // Whether a drop put one card's groups in another order or brought one
    // in; a card a group only left keeps the order it had.
    private fun moved(groups: List<DrawerGroup>, type: DrawerGroupType, ids: List<String>): Boolean =
        sorted(groups).filter { it.type == type && it.id in ids }.map { it.id } != ids

    // A group a step up or down among its own type's, as a screen reader
    // moves it; unchanged at either end.
    fun stepped(groups: List<DrawerGroup>, id: String, delta: Int): List<DrawerGroup> {
        val type = groups.firstOrNull { it.id == id }?.type
        val ids = sorted(groups).filter { it.type == type }.map { it.id }.toMutableList()
        val index = ids.indexOf(id)
        val target = index + delta
        if (type == null || target !in ids.indices) return groups
        ids[index] = ids[target].also { ids[target] = ids[index] }
        return reordered(groups, type, ids)
    }

    // Sort A-Z: one type's groups without an order of their own, so they
    // show by name and a new one takes its place among them.
    fun byName(groups: List<DrawerGroup>, type: DrawerGroupType): List<DrawerGroup> =
        groups.map { if (it.type == type) it.copy(order = Int.MAX_VALUE) else it }

    // Whether one type's groups show A-Z already, so Sort A-Z would change
    // nothing on the page.
    fun showByName(groups: List<DrawerGroup>, type: DrawerGroupType): Boolean {
        val shown = sorted(groups).filter { it.type == type }
        return shown == shown.sortedWith(LabelOrder.by<DrawerGroup> { it.name })
    }

    // Sort A-Z undone: one type's groups back at the orders they had in
    // before; one made since keeps its own.
    fun withOrders(now: List<DrawerGroup>, before: List<DrawerGroup>, type: DrawerGroupType): List<DrawerGroup> {
        val orders = before.filter { it.type == type }.associate { it.id to it.order }
        return now.map { group ->
            orders[group.id]?.takeIf { group.type == type }?.let { group.copy(order = it) } ?: group
        }
    }

    // A deleted group put back: the group as it was in before, and its place
    // in every tab that held it then and still exists. Everything stored
    // since stays. The user asked for it back, so it is theirs: a smart
    // folder's marker goes, or the next regeneration would drop it again.
    // Unchanged when before has no such group, or one with its id exists now.
    fun restored(now: List<DrawerGroup>, before: List<DrawerGroup>, id: String): List<DrawerGroup> {
        val group = before.firstOrNull { it.id == id }
        if (group == null || now.any { it.id == id }) return now
        val key = TabFolders.keyOf(id)
        val holders = before.filter { key in it.members }.map { it.id }.toSet()
        return now.map { if (it.id in holders) it.copy(members = it.members + key) else it } +
            group.copy(smart = false)
    }
}
