package com.novaredux.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The Drawer groups page: a drag reorders one type's groups or carries one
// to the other kind, Sort A-Z puts one type's back in name order, and Undo
// puts a deleted group back.
class DrawerGroupOrderTest {

    private val games = DrawerGroup("Games", setOf("0/a/A"), order = 0)
    private val social = DrawerGroup("Social", setOf("0/b/B"), order = 1, type = DrawerGroupType.FOLDER)
    private val travel = DrawerGroup("Travel", setOf("0/c/C"), order = 2)
    private val tools = DrawerGroup("Tools", setOf("0/d/D"), order = 3, type = DrawerGroupType.FOLDER)
    private val money = DrawerGroup("Money", setOf("0/e/E"), order = 4)
    private val all = listOf(games, social, travel, tools, money)

    private fun names(groups: List<DrawerGroup>, type: DrawerGroupType) =
        DrawerGroupOrder.sorted(groups).filter { it.type == type }.map { it.name }

    @Test
    fun aDragReordersTheTabsAndLeavesTheFoldersInPlace() {
        val moved = DrawerGroupOrder.reordered(all, DrawerGroupType.TAB, listOf("Travel", "Money", "Games"))
        assertEquals(listOf("Travel", "Money", "Games"), names(moved, DrawerGroupType.TAB))
        assertEquals(listOf("Social", "Tools"), names(moved, DrawerGroupType.FOLDER))
        assertEquals(listOf(0, 1, 2), listOf("Travel", "Money", "Games").map { name -> order(moved, name) })
        // The folders keep the orders they had.
        assertEquals(listOf(1, 3), listOf(order(moved, "Social"), order(moved, "Tools")))
        assertEquals(setOf("0/c/C"), moved.single { it.name == "Travel" }.members)
    }

    private fun order(groups: List<DrawerGroup>, name: String) = groups.single { it.name == name }.order

    // Folders with no order of their own, as made or imported: A-Z.
    private val zoo = DrawerGroup("Zoo", setOf("0/z/Z"), type = DrawerGroupType.FOLDER, id = "zoo")
    private val art = DrawerGroup("Art", setOf("0/r/R"), type = DrawerGroupType.FOLDER, id = "art")
    private val maps = DrawerGroup("Maps", setOf("0/m/M"), type = DrawerGroupType.FOLDER, id = "maps")
    private val unarranged = listOf(games, travel, zoo, art, maps)

    @Test
    fun foldersWithNoOrderShowAToZ() {
        assertEquals(listOf("Art", "Maps", "Zoo"), names(unarranged, DrawerGroupType.FOLDER))
        assertTrue(DrawerGroupOrder.showByName(unarranged, DrawerGroupType.FOLDER))
    }

    @Test
    fun aDragAmongTheTabsLeavesTheFoldersAToZ() {
        val moved =
            DrawerGroupOrder.arranged(unarranged, listOf("Travel", "Games"), listOf("art", "maps", "zoo"), retype)
        assertEquals(listOf("Travel", "Games"), names(moved, DrawerGroupType.TAB))
        assertTrue(moved.filter { it.type == DrawerGroupType.FOLDER }.all { it.order == Int.MAX_VALUE })
        // So a folder made next takes its place by name.
        val next = moved + DrawerGroup("Books", emptySet(), type = DrawerGroupType.FOLDER, id = "books")
        assertEquals(listOf("Art", "Books", "Maps", "Zoo"), names(next, DrawerGroupType.FOLDER))
    }

    @Test
    fun aFolderCarriedIntoTheTabsLeavesTheRestAToZ() {
        val moved = DrawerGroupOrder.arranged(
            unarranged, listOf("Games", "maps", "Travel"), listOf("art", "zoo"), retype
        )
        assertEquals(listOf("Games", "Maps", "Travel"), names(moved, DrawerGroupType.TAB))
        assertEquals(listOf("Art", "Zoo"), names(moved, DrawerGroupType.FOLDER))
        assertTrue(moved.filter { it.type == DrawerGroupType.FOLDER }.all { it.order == Int.MAX_VALUE })
    }

    @Test
    fun aFolderDraggedToAPlaceGivesTheFoldersAnOrder() {
        val moved =
            DrawerGroupOrder.arranged(unarranged, listOf("Games", "Travel"), listOf("zoo", "art", "maps"), retype)
        assertEquals(listOf("Zoo", "Art", "Maps"), names(moved, DrawerGroupType.FOLDER))
        assertFalse(DrawerGroupOrder.showByName(moved, DrawerGroupType.FOLDER))
        // The tabs, left as they were, keep their orders.
        assertEquals(listOf(0, 2), listOf(order(moved, "Games"), order(moved, "Travel")))
    }

    @Test
    fun aStepMovesAGroupAmongItsOwnType() {
        val moved = DrawerGroupOrder.stepped(unarranged, "zoo", -1)
        assertEquals(listOf("Art", "Zoo", "Maps"), names(moved, DrawerGroupType.FOLDER))
        assertEquals(listOf(0, 2), listOf(order(moved, "Games"), order(moved, "Travel")))
        // At either end, or for a group that isn't there, nothing moves.
        assertEquals(unarranged, DrawerGroupOrder.stepped(unarranged, "art", -1))
        assertEquals(unarranged, DrawerGroupOrder.stepped(unarranged, "zoo", 1))
        assertEquals(unarranged, DrawerGroupOrder.stepped(unarranged, "gone", 1))
    }

    @Test
    fun sortAToZTakesTheFoldersOrderAwayAndUndoGivesItBack() {
        val arranged = DrawerGroupOrder.reordered(unarranged, DrawerGroupType.FOLDER, listOf("zoo", "maps", "art"))
        val sorted = DrawerGroupOrder.byName(arranged, DrawerGroupType.FOLDER)
        assertEquals(listOf("Art", "Maps", "Zoo"), names(sorted, DrawerGroupType.FOLDER))
        assertTrue(DrawerGroupOrder.showByName(sorted, DrawerGroupType.FOLDER))
        // The tabs are left as they were.
        assertEquals(listOf(0, 2), listOf(order(sorted, "Games"), order(sorted, "Travel")))
        val made = DrawerGroup("Books", emptySet(), type = DrawerGroupType.FOLDER, id = "books")
        val undone = DrawerGroupOrder.withOrders(sorted + made, arranged, DrawerGroupType.FOLDER)
        assertEquals(listOf("Zoo", "Maps", "Art", "Books"), names(undone, DrawerGroupType.FOLDER))
    }

    @Test
    fun foldersReorderAmongThemselves() {
        val moved = DrawerGroupOrder.reordered(all, DrawerGroupType.FOLDER, listOf("Tools", "Social"))
        assertEquals(listOf("Tools", "Social"), names(moved, DrawerGroupType.FOLDER))
        assertEquals(listOf("Games", "Travel", "Money"), names(moved, DrawerGroupType.TAB))
    }

    @Test
    fun aGroupTheDragNeverSawFollowsTheNamedOnes() {
        val moved = DrawerGroupOrder.reordered(all, DrawerGroupType.TAB, listOf("Money", "Games", "Gone"))
        assertEquals(listOf("Money", "Games", "Travel"), names(moved, DrawerGroupType.TAB))
    }

    @Test
    fun groupsThatNeverHadAnOrderGetOne() {
        val unordered = listOf(DrawerGroup("B", emptySet()), DrawerGroup("A", emptySet()))
        val moved = DrawerGroupOrder.reordered(unordered, DrawerGroupType.TAB, listOf("B", "A"))
        assertEquals(listOf("B", "A"), names(moved, DrawerGroupType.TAB))
        assertEquals(listOf(0, 1), DrawerGroupOrder.sorted(moved).map { it.order })
    }

    @Test
    fun undoPutsADeletedGroupBackAsTheUsers() {
        val smart = tools.copy(smart = true)
        val before = listOf(games, smart)
        val restored = DrawerGroupOrder.restored(listOf(games), before, "Tools")
        val back = restored.single { it.name == "Tools" }
        assertEquals(smart.members, back.members)
        assertEquals(smart.order, back.order)
        assertFalse(back.smart)
    }

    @Test
    fun undoGivesAFolderItsPlaceInItsTabsBack() {
        val key = TabFolders.keyOf("Social")
        val holder = games.copy(members = games.members + key)
        val before = listOf(holder, social, travel)
        // The delete took the folder and its key; a group made since stays.
        val now = listOf(games, travel, money)
        val restored = DrawerGroupOrder.restored(now, before, "Social")
        assertEquals(games.members + key, restored.single { it.name == "Games" }.members)
        assertEquals(travel, restored.single { it.name == "Travel" })
        assertEquals(money, restored.single { it.name == "Money" })
        assertEquals(4, restored.size)
    }

    @Test
    fun undoChangesNothingWhenTheNameIsTakenOrUnknown() {
        val now = listOf(games, social)
        assertEquals(now, DrawerGroupOrder.restored(now, all, "Social"))
        assertEquals(now, DrawerGroupOrder.restored(now, all, "Nope"))
    }

    @Test
    fun undoBringsBackTheGroupByIdBesideOneOfTheSameName() {
        val twin = DrawerGroup("Social", setOf("x"), type = DrawerGroupType.FOLDER, id = "#twin")
        val holder = games.copy(members = games.members + TabFolders.keyOf("#twin"))
        val restored = DrawerGroupOrder.restored(listOf(games, social), listOf(holder, social, twin), "#twin")
        assertEquals(listOf("Social", "Social"), restored.filter { it.type == DrawerGroupType.FOLDER }.map { it.name })
        assertEquals(holder.members, restored.single { it.id == "Games" }.members)
    }
    private val retype = { group: DrawerGroup, type: DrawerGroupType -> group.copy(type = type, smart = false) }

    @Test
    fun aFolderDraggedIntoTheTabsBecomesATabWhereItWasDropped() {
        val held = social.copy(members = social.members + TabFolders.keyOf("Tools"), smart = true)
        val moved = DrawerGroupOrder.arranged(
            listOf(games, held, travel, tools, money),
            listOf("Games", "Social", "Travel", "Money"),
            listOf("Tools"),
            retype
        )
        assertEquals(listOf("Games", "Social", "Travel", "Money"), names(moved, DrawerGroupType.TAB))
        assertEquals(listOf("Tools"), names(moved, DrawerGroupType.FOLDER))
        val social = moved.single { it.name == "Social" }
        assertFalse(social.smart)
        // The folder it held is now a folder in the tab.
        assertEquals(held.members, social.members)
    }

    @Test
    fun aTabDraggedIntoTheFoldersBecomesAFolder() {
        val moved =
            DrawerGroupOrder.arranged(all, listOf("Games", "Money"), listOf("Travel", "Social", "Tools"), retype)
        assertEquals(listOf("Games", "Money"), names(moved, DrawerGroupType.TAB))
        assertEquals(listOf("Travel", "Social", "Tools"), names(moved, DrawerGroupType.FOLDER))
    }
}
