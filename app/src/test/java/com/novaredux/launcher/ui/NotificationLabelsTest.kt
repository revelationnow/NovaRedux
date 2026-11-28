package com.novaredux.launcher.ui

import com.novaredux.launcher.data.ItemType
import com.novaredux.launcher.data.LauncherItem
import com.novaredux.launcher.data.LauncherPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Which home screen labels show their app's newest notification (#15).
class NotificationLabelsTest {

    private val chat = LauncherItem(
        id = "c", itemType = ItemType.APP, packageName = "com.chat", className = "com.chat.Main", userSerial = 0L
    )
    private val prefs = LauncherPrefs(notificationTextApps = setOf("0/com.chat/com.chat.Main"))

    @Test
    fun onlyTheChosenAppsInTheirOwnProfile() {
        assertTrue(NotificationLabels.chosen(chat, prefs))
        assertFalse(NotificationLabels.chosen(chat, LauncherPrefs()))
        assertFalse(NotificationLabels.chosen(chat.copy(userSerial = 10L), prefs))
        assertFalse(NotificationLabels.chosen(chat.copy(className = "com.chat.Other"), prefs))
    }

    @Test
    fun neverAFolderOrAShortcut() {
        assertFalse(NotificationLabels.chosen(chat.copy(itemType = ItemType.FOLDER), prefs))
        assertFalse(NotificationLabels.chosen(chat.copy(itemType = ItemType.SHORTCUT), prefs))
    }

    @Test
    fun changedLinesCountOnlyWhileAnAppIsChosen() {
        val old = mapOf("0/com.chat" to "Anna: Hi")
        val new = mapOf("0/com.chat" to "Anna: Where are you?", "0/com.mail" to "Bills")
        assertEquals(setOf("0/com.chat", "0/com.mail"), NotificationLabels.changedKeys(prefs, old, new))
        assertEquals(emptySet<String>(), NotificationLabels.changedKeys(LauncherPrefs(), old, new))
        assertEquals(emptySet<String>(), NotificationLabels.changedKeys(prefs, old, old))
    }
}
