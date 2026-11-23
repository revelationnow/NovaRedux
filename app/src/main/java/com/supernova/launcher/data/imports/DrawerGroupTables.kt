package com.supernova.launcher.data.imports

// The app drawer's tabs and folders inside another launcher's backup. Three
// shapes are known: a groups table whose rows say whether each is a tab or a
// folder, with a table of the apps each holds; drawer folders and the apps
// in them; and categories every app of the drawer is sorted into.
internal object DrawerGroupTables {

    val TABLES = GroupsAndApps.TABLES + DrawerFolders.TABLES + Categories.TABLES

    // Null where the backup holds none of these tables.
    fun read(contents: BackupContents): List<ForeignGroup>? {
        val tables = contents.tables
        return when {
            // The apps' table may be missing where the layout's rows hold
            // them (GroupsAndApps).
            GroupsAndApps.GROUPS in tables -> GroupsAndApps.read(tables)
            DrawerFolders.TABLES.all { it in tables } -> DrawerFolders.read(tables)
            Categories.TABLES.all { it in tables } -> Categories.read(tables)
            else -> null
        }
    }

    // "package/class#user", the user a number that may be missing.
    fun app(key: String?): ForeignApp? {
        val flat = key?.substringBeforeLast('#')
        val user = key?.takeIf { '#' in it }?.substringAfterLast('#')?.toLongOrNull()
        return IntentText.component(flat)?.let { (pkg, cls) -> ForeignApp(pkg, cls, BackupLayouts.userSerial(user)) }
    }
}

// Groups that are each a tab or a drawer folder, and a row per app a group
// holds. A tab can hold a drawer folder: its row names the folder by id in
// place of an activity. A group switched off keeps its row, flagged, and
// shows nowhere.
private object GroupsAndApps {
    const val GROUPS = "drawer_groups"
    const val MEMBERS = "appgroups"
    val TABLES = listOf(GROUPS, MEMBERS)

    // Nova 8 keeps a group's apps as rows of the layout table instead, each
    // in the group's own container: -200 less the group's id.
    private const val LAYOUT = "favorites"
    private const val GROUP_CONTAINERS = -200L

    private const val TAB = "TAB_APP_GROUP"
    private const val FOLDER = "FOLDER_APP_GROUP"
    private const val HELD_FOLDER = "FOLDER:"
    private const val SWITCHED_OFF = 128L

    fun read(tables: Map<String, List<Row>>): List<ForeignGroup> {
        val members = tables[MEMBERS].orEmpty().groupBy { it.long("groupId") }
        val laidOut = tables[LAYOUT].orEmpty().groupBy { row ->
            row.long("container")?.takeIf { it <= GROUP_CONTAINERS }?.let { GROUP_CONTAINERS - it }
        }
        return tables[GROUPS].orEmpty().filterNot(::switchedOff).mapNotNull { row ->
            val id = row.long("_id")
            val type = row.text("groupType")
            if (id == null || (type != TAB && type != FOLDER)) return@mapNotNull null
            val keys = members[id].orEmpty().mapNotNull { it.text("component") }
            ForeignGroup(
                id = id,
                title = row.text("title").orEmpty(),
                folder = type == FOLDER,
                order = row.int("tabOrder") ?: 0,
                // Its apps leave the main list, or stay there too.
                keepInMain = row.long("hideApps")?.let { it == 0L },
                apps = keys.filterNot(::isFolder).mapNotNull(DrawerGroupTables::app)
                    .ifEmpty { laidOutApps(laidOut[id]) },
                folders = keys.mapNotNull(::heldFolder),
                icon = row.blob("icon")
            )
        }
    }

    private fun switchedOff(row: Row): Boolean = ((row.long("flags") ?: 0L) and SWITCHED_OFF) != 0L

    // A group's apps as layout rows, in their order (#338).
    private fun laidOutApps(rows: List<Row>?): List<ForeignApp> =
        rows.orEmpty().filter { (it.int("itemType") ?: 0) == 0 }.sortedBy { it.int("rank") ?: 0 }.mapNotNull { row ->
            IntentText.parse(row.text("intent"))?.component?.let { (pkg, cls) ->
                ForeignApp(pkg, cls, BackupLayouts.userSerial(row.long("profileId")))
            }
        }

    private fun isFolder(key: String): Boolean = heldFolder(key) != null

    // The folder id in "package/FOLDER:id".
    private fun heldFolder(key: String): Long? =
        key.substringAfter('/', "").takeIf { it.startsWith(HELD_FOLDER) }
            ?.removePrefix(HELD_FOLDER)?.substringBefore('#')?.toLongOrNull()
}

// Drawer folders, and a row per app in one, each in its place.
private object DrawerFolders {
    const val FOLDERS = "folders"
    const val ITEMS = "folderitems"
    val TABLES = listOf(FOLDERS, ITEMS)

    fun read(tables: Map<String, List<Row>>): List<ForeignGroup> {
        val items = tables[ITEMS].orEmpty().groupBy { it.long("folderId") }
        return tables[FOLDERS].orEmpty().mapNotNull { row ->
            row.long("id")?.let { id ->
                ForeignGroup(
                    id = id,
                    title = row.text("title").orEmpty(),
                    folder = true,
                    order = row.int("rank") ?: 0,
                    // Hidden: its apps are out of the main list.
                    keepInMain = if (row.long("hide") == 1L) false else null,
                    apps = items[id].orEmpty().sortedBy { it.int("rank") ?: 0 }
                        .mapNotNull { DrawerGroupTables.app(it.text("item_info")) }
                )
            }
        }
    }
}

// Categories the drawer sorts every app into, each shown as a tab, and the
// folders a category's apps may be gathered into, each held by its tab. The
// launcher's own categories have no label stored, only their id.
private object Categories {
    const val TABLE = "category"
    const val APPS = "draweritem"
    val TABLES = listOf(TABLE, APPS)

    private val NAMES = mapOf(
        "phone" to "Contacts",
        "internet" to "Internet",
        "games" to "Games",
        "media" to "Media",
        "utility" to "Utilities",
        "settings" to "Settings"
    )

    fun read(tables: Map<String, List<Row>>): List<ForeignGroup> {
        // An app's shortcut is no member of a tab or a folder.
        val rows = tables[APPS].orEmpty().filter { it.text("deepShortcutId").isNullOrEmpty() }
        val inside = rows.groupBy { it.long("parentId") }
        // A folder launches nothing itself, and holds the rows naming it.
        val folderIds: Set<Long?> = rows.filter { it.text("packageName").isNullOrEmpty() }
            .mapNotNull { it.long("id") }.filter { it in inside }.toSet()
        return tabs(tables[TABLE].orEmpty(), inside[null].orEmpty(), folderIds) + folders(rows, inside, folderIds)
    }

    private fun tabs(categories: List<Row>, loose: List<Row>, folderIds: Set<Long?>): List<ForeignGroup> {
        val apps = loose.filterNot { it.long("id") in folderIds }.groupBy { it.text("categoryId") }
        val folders = loose.filter { it.long("id") in folderIds }.groupBy { it.text("categoryId") }
        // A category the launcher made that nothing is sorted into would be
        // an empty tab.
        val filled = apps.keys + folders.keys
        return categories
            .filter { it.long("enabled") != 0L && (it.text("id") in filled || !it.text("customLabel").isNullOrBlank()) }
            .mapIndexedNotNull { index, row ->
                row.text("id")?.let { id ->
                    ForeignGroup(
                        // Below zero, as the folders have the table's own ids.
                        id = -1L - index,
                        title = row.text("customLabel")?.takeIf { it.isNotBlank() } ?: name(id),
                        folder = false,
                        order = row.int("position") ?: index,
                        // Every app is in some category, so the main list
                        // keeps them all.
                        keepInMain = true,
                        apps = apps[id].orEmpty().mapNotNull(::app),
                        folders = folders[id].orEmpty().mapNotNull { it.long("id") }
                    )
                }
            }
    }

    // Each folder with its apps in their order. They show in it, not loose
    // in the main list beside it, as they did there.
    private fun folders(rows: List<Row>, inside: Map<Long?, List<Row>>, folderIds: Set<Long?>): List<ForeignGroup> =
        rows.filter { it.long("id") in folderIds }.mapNotNull { row ->
            row.long("id")?.let { id ->
                val held = inside[id].orEmpty().sortedBy { it.int("position") ?: 0 }
                ForeignGroup(
                    id = id,
                    title = row.text("label").orEmpty(),
                    folder = true,
                    order = row.int("position") ?: 0,
                    keepInMain = false,
                    apps = held.filterNot { it.long("id") in folderIds }.mapNotNull(::app),
                    folders = held.mapNotNull { it.long("id") }.filter { it in folderIds }
                )
            }
        }

    private fun app(row: Row): ForeignApp? = row.text("packageName")?.takeIf { it.isNotEmpty() }?.let {
        val activity = row.text("activityName")?.takeIf(String::isNotEmpty)
        ForeignApp(it, activity, BackupLayouts.userSerial(row.long("userId")))
    }

    private fun name(id: String): String = NAMES[id] ?: id.replaceFirstChar { it.uppercaseChar() }
}
