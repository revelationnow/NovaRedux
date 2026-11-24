package com.supernova.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// App pairs (#12): how a pair keeps its second app, and how it parts.
class AppPairsTest {

    private val maps = LauncherItem(
        id = "a", itemType = ItemType.APP, packageName = "com.maps", className = "com.maps.Main", label = "Maps"
    )
    private val music = LaunchableApp("com.music", "com.music.Player", "Music", userSerial = 10L)

    @Test
    fun aPairKeepsItsSecondAppWithItsProfile() {
        val pair = AppPairs.paired(maps, music, "Maps + Music")
        assertEquals("com.music/com.music.Player@10", pair.pairWith)
        assertEquals("Maps + Music", pair.label)
        val second = AppPairs.second(pair)
        assertEquals("com.music", second?.packageName)
        assertEquals("com.music.Player", second?.className)
        assertEquals(10L, second?.userSerial)
    }

    @Test
    fun aPlainAppHasNoSecond() {
        assertNull(AppPairs.second(maps))
        assertNull(AppPairs.second(maps.copy(pairWith = "not a component")))
    }

    @Test
    fun partingGivesTheAppItsOwnNameBack() {
        val parted = AppPairs.parted(AppPairs.paired(maps, music, "Maps + Music"), "Maps")
        assertNull(parted.pairWith)
        assertEquals("Maps", parted.label)
    }
}
