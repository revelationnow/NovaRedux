package com.novaredux.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryGroupsTest {

    private val byCategory = mapOf(
        SmartCategory.GAMES to setOf("0/chess/Main", "0/go/Main"),
        SmartCategory.SOCIAL to setOf("0/chat/Main")
    )

    @Test
    fun aCategoryGroupsAppsBecomeTheCategorysAndItsFoldersStay() {
        val folder = TabFolders.keyOf("#f1")
        val tab = DrawerGroup("Games", setOf("0/gone/Main", folder), category = SmartCategory.GAMES)
        val refreshed = CategoryGroups.refreshed(tab, byCategory)
        assertEquals(setOf(folder, "0/chess/Main", "0/go/Main"), refreshed.members)
    }

    @Test
    fun aCategoryWithNoAppsLeavesOnlyItsFolders() {
        val tab = DrawerGroup("News", setOf("0/paper/Main"), category = SmartCategory.NEWS)
        assertEquals(emptySet<String>(), CategoryGroups.refreshed(tab, byCategory).members)
    }

    @Test
    fun aPlainGroupIsLeftAsItIs() {
        val tab = DrawerGroup("Mine", setOf("0/chess/Main"))
        assertSame(tab, CategoryGroups.refreshed(tab, byCategory))
    }

    @Test
    fun onlyTheUsersCategoryGroupsAreKeptUp() {
        val category = DrawerGroup("Games", emptySet(), category = SmartCategory.GAMES)
        assertTrue(CategoryGroups.follows(category))
        // A Smart Folder is made again whole by its own pass.
        assertFalse(CategoryGroups.follows(category.copy(smart = true)))
        assertFalse(CategoryGroups.follows(DrawerGroup("Mine", emptySet())))
    }
}
