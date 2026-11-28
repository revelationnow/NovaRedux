package com.novaredux.launcher.data

// NovaRedux's classes were in com.supernova.launcher until 28 Nov 2026
// (docs/NAMES.md). An item stored or backed up before then that opens one
// of them, as the NovaRedux Settings icon the first run plants does, still
// names it there: it opens the class under its new name. Supernova's own
// app's items (com.supernova.launcher, in a Supernova backup) keep theirs.
object RenamedPackage {
    private const val OLD = "com.supernova.launcher."
    private const val NEW = "com.novaredux.launcher."
    private const val SUPERNOVA = "com.supernova.launcher"

    fun className(packageName: String?, className: String?): String? =
        if (packageName != SUPERNOVA && className?.startsWith(OLD) == true) {
            NEW + className.removePrefix(OLD)
        } else {
            className
        }

    fun applied(model: LauncherModel): LauncherModel {
        if (model.items.none { className(it.packageName, it.className) != it.className }) return model
        return model.copy(items = model.items.map { it.copy(className = className(it.packageName, it.className)) })
    }
}
