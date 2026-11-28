package com.novaredux.launcher.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// A layout or drawer folder from before 28 Nov 2026, or from Supernova,
// with the old launcher-action links and "App icons" marker.
class RenamedMarkersTest {

    private val oldPick = AppOverride(iconPackPackage = "supernova:app", iconDrawableName = "0/com.x/com.x.Main")

    @Test
    fun aLayoutTakesTheNewLinksAndMarker() {
        val model = LauncherModel(
            items = listOf(
                LauncherItem(
                    id = "action", itemType = ItemType.SHORTCUT, intentUri = "supernova://action/JUMP_TO_PAGE?p=2"
                ),
                LauncherItem(
                    id = "app", itemType = ItemType.APP, packageName = "com.x", className = "com.x.Main",
                    swipeAction = "supernova://action/OPEN_DRAWER", swipeActionDown = "com.y/com.y.Main",
                    appEdit = oldPick
                ),
                LauncherItem(
                    id = "folder", itemType = ItemType.FOLDER,
                    customIconPack = "supernova:app", customIconName = "0/com.x/com.x.Main"
                ),
                LauncherItem(id = "shortcut", itemType = ItemType.SHORTCUT, shortcutEdit = oldPick)
            ),
            appOverrides = mapOf("0/com.x/com.x.Main" to oldPick)
        )
        val migrated = ModelMigrations.migrate(model)
        val items = migrated.items.associateBy { it.id }
        assertEquals("novaredux://action/JUMP_TO_PAGE?p=2", items.getValue("action").intentUri)
        assertEquals("novaredux://action/OPEN_DRAWER", items.getValue("app").swipeAction)
        assertEquals("com.y/com.y.Main", items.getValue("app").swipeActionDown)
        assertEquals(AppIconSource.PACK, items.getValue("app").appEdit?.iconPackPackage)
        assertEquals(AppIconSource.PACK, items.getValue("folder").customIconPack)
        assertEquals(AppIconSource.PACK, items.getValue("shortcut").shortcutEdit?.iconPackPackage)
        assertEquals(AppIconSource.PACK, migrated.appOverrides.getValue("0/com.x/com.x.Main").iconPackPackage)
        assertEquals("0/com.x/com.x.Main", items.getValue("folder").customIconName)
    }

    @Test
    fun aLayoutWithNothingToRenameIsTheSameLayout() {
        val model = LauncherModel(
            items = listOf(
                LauncherItem(id = "action", itemType = ItemType.SHORTCUT, intentUri = "novaredux://action/OPEN_DRAWER"),
                LauncherItem(id = "folder", itemType = ItemType.FOLDER, customIconPack = "com.pack")
            )
        )
        assertSame(model, RenamedMarkers.applied(model))
    }

    @Test
    fun aDrawerFolderTakesTheNewMarker() = runBlocking {
        val old = DrawerGroup(
            "Work", setOf("0/com.x/com.x.Main"), order = 3, type = DrawerGroupType.FOLDER,
            iconPack = "supernova:app", iconName = "0/com.x/com.x.Main"
        )
        val other = DrawerGroup("Games", setOf("0/com.g/com.g.Main"), iconPack = "com.pack", iconName = "games")
        val prefs = mutablePreferencesOf(Pref.drawerGroups.key to setOf(old.encode(), other.encode()))
        assertTrue(RenamedGroupIcons.shouldMigrate(prefs))
        val groups = RenamedGroupIcons.migrate(prefs)[Pref.drawerGroups.key].orEmpty()
        assertEquals(setOf(old.copy(iconPack = AppIconSource.PACK).encode(), other.encode()), groups)
        assertFalse(RenamedGroupIcons.shouldMigrate(RenamedGroupIcons.migrate(prefs)))
    }
}
