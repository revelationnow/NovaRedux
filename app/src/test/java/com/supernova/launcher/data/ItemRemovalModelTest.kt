package com.supernova.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Remove on an app in an open folder: a folder left with one app gives way
// to it, as one an app was dragged out of does (#345), and Undo brings the
// folder back as it was.
class ItemRemovalModelTest {

    private fun app(id: String, container: ContainerType, rank: Int = 0, folder: String? = null) =
        LauncherItem(
            id = id, itemType = ItemType.APP, container = container, rank = rank,
            folderId = folder, packageName = "p.$id", className = "c.$id"
        )

    private fun LauncherModel.item(id: String) = items.first { it.id == id }

    private val folder = LauncherItem(
        id = "f", itemType = ItemType.FOLDER, page = 1, cellX = 2, cellY = 3, label = "F"
    )

    private fun model(vararg members: String) = LauncherModel(
        items = listOf(app("w", ContainerType.WORKSPACE), folder) +
            members.mapIndexed { rank, id -> app(id, ContainerType.FOLDER, rank, "f") },
        pageCount = 2
    )

    @Test
    fun theOtherAppOfTwoTakesTheFoldersPlace() {
        val before = model("a", "b")
        val after = ItemRemovalModel.applied(before, "a")
        assertEquals(listOf("w", "b"), after.items.map { it.id })
        val last = after.item("b")
        assertEquals(ContainerType.WORKSPACE, last.container)
        assertNull(last.folderId)
        assertEquals(1, last.page)
        assertEquals(2, last.cellX)
        assertEquals(3, last.cellY)
    }

    @Test
    fun undoBringsTheFolderBackWithBothApps() {
        val before = model("a", "b")
        val putBack = ItemRemovalModel.givingWayFolder(before, before.item("a"))
        assertEquals(listOf("f", "a", "b"), putBack?.map { it.id })
        // replaceItems: what took the folder's place gives way to it.
        val after = ItemRemovalModel.applied(before, "a")
        val ids = putBack.orEmpty().mapTo(HashSet()) { it.id }
        val undone = LauncherRepository.restored(
            after.copy(items = after.items.filterNot { it.id in ids }), putBack.orEmpty(), 4, 5, dockSlots = 5
        )
        assertEquals(before.items.toSet(), undone.items.toSet())
    }

    @Test
    fun aFolderWithAppsToSpareStays() {
        val before = model("a", "b", "c")
        val after = ItemRemovalModel.applied(before, "a")
        assertEquals(listOf("w", "f", "b", "c"), after.items.map { it.id })
        assertEquals("f", after.item("b").folderId)
        assertNull(ItemRemovalModel.givingWayFolder(before, before.item("a")))
    }

    @Test
    fun theLastAppTakesTheFolderWithIt() {
        val before = model("a")
        assertEquals(listOf("w"), ItemRemovalModel.applied(before, "a").items.map { it.id })
        assertEquals(listOf("f", "a"), ItemRemovalModel.givingWayFolder(before, before.item("a"))?.map { it.id })
    }

    @Test
    fun aFolderGoesWithEveryAppInIt() {
        val before = model("a", "b", "c")
        assertEquals(listOf("w"), ItemRemovalModel.applied(before, "f").items.map { it.id })
        assertNull(ItemRemovalModel.givingWayFolder(before, folder))
    }

    @Test
    fun aHomeItemGoesAlone() {
        val before = model("a", "b")
        val after = ItemRemovalModel.applied(before, "w")
        assertTrue(after.items.none { it.id == "w" })
        assertEquals(before.items.drop(1), after.items)
        assertNull(ItemRemovalModel.givingWayFolder(before, before.item("w")))
        assertEquals(before, ItemRemovalModel.applied(before, "missing"))
    }
}
