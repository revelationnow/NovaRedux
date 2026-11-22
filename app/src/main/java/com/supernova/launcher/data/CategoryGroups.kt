package com.supernova.launcher.data

// Nova 7's category tabs: a drawer group with a category holds that
// category's apps, kept up as apps install and go. The drawer folders it
// holds are the user's and stay; only its apps follow the category.
object CategoryGroups {

    // The group with its apps set to its category's (byCategory: each
    // category's app keys). A group without a category comes back as it is.
    fun refreshed(group: DrawerGroup, byCategory: Map<SmartCategory, Set<String>>): DrawerGroup {
        val category = group.category ?: return group
        val folders = group.members.filterTo(mutableSetOf()) { TabFolders.idOf(it) != null }
        return group.copy(members = folders + byCategory[category].orEmpty())
    }

    // Whether the launcher keeps this group's apps to its category: a
    // category group of the user's. A Smart Folder is made again whole by
    // its own pass instead.
    fun follows(group: DrawerGroup): Boolean = group.category != null && !group.smart
}
