package com.novaredux.launcher.data.imports

// A folder on the home screen that the backup holds nothing in, beside a
// drawer folder of the same name: a drawer folder put on the home screen,
// whose apps the backup keeps with the drawer folder alone. Left empty, it
// went at the import, and every such folder was missing from the home
// screen (#338). It gets the drawer folder's apps.
internal object PlacedDrawerFolders {

    fun members(
        folder: ForeignItem,
        groups: List<ForeignGroup>?,
        appLabel: (packageName: String, className: String?, userSerial: Long) -> String?
    ): List<ForeignItem> {
        val name = folder.title.trim()
        val group = groups.orEmpty().firstOrNull {
            folder.kind == ForeignKind.FOLDER && it.folder && name.isNotEmpty() &&
                it.title.trim().equals(name, ignoreCase = true)
        }
        return group?.apps.orEmpty().mapIndexed { rank, app ->
            ForeignItem(
                id = folder.id,
                kind = ForeignKind.APP,
                place = ForeignPlace.FOLDER,
                parentId = folder.id,
                rank = rank,
                title = appLabel(app.packageName, app.className, app.userSerial).orEmpty(),
                intent = IntentText.launching(app.packageName, app.className),
                userSerial = app.userSerial
            )
        }
    }
}
