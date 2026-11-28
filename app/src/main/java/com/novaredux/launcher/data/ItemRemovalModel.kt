package com.novaredux.launcher.data

// One item taken off the home screen or the dock: a folder goes with every
// app in it. An app removed from an open folder leaves it as one dragged
// out of it does: a folder left with one app gives way to it, which takes
// the folder's place, and an empty one goes (#345).
internal object ItemRemovalModel {
    fun applied(current: LauncherModel, itemId: String): LauncherModel {
        val removed = current.items.firstOrNull { it.id == itemId } ?: return current
        val removeIds = mutableSetOf(itemId)
        if (removed.itemType == ItemType.FOLDER) {
            current.items.filter { it.folderId == itemId }.mapTo(removeIds) { it.id }
        }
        val next = current.copy(items = current.items.filterNot { it.id in removeIds })
        return if (removed.container == ContainerType.FOLDER) {
            LauncherRepository.withSparseFolderDissolved(next, removed.folderId)
        } else {
            next
        }
    }

    // What the removal's Undo puts back when taking `item` out of its folder
    // makes the folder give way: the folder as it was, with every app it
    // held. Null when the folder stays, and the item alone goes back.
    fun givingWayFolder(current: LauncherModel, item: LauncherItem): List<LauncherItem>? {
        val folder = current.items.firstOrNull {
            item.container == ContainerType.FOLDER && it.id == item.folderId && it.itemType == ItemType.FOLDER
        } ?: return null
        val members = current.items.filter { it.container == ContainerType.FOLDER && it.folderId == folder.id }
        return (listOf(folder) + members).takeIf { members.size <= 2 }
    }
}
