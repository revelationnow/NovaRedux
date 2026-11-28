package com.novaredux.launcher.data

// App pairs (#12): an app item that opens a second app beside it in split
// screen. The second is kept as a launch target, "pkg/cls@userSerial".
object AppPairs {

    fun target(app: LaunchableApp): String = "${app.packageName}/${app.className}@${app.userSerial}"

    // The second app, or null for an item that isn't a pair.
    fun second(item: LauncherItem): LaunchableApp? {
        val (component, serial) = LaunchTarget.split(item.pairWith ?: return null)
        val pkg = component.substringBefore('/')
        val cls = component.substringAfter('/', "")
        return if (pkg.isEmpty() || cls.isEmpty()) null else LaunchableApp(pkg, cls, "", serial)
    }

    fun paired(item: LauncherItem, second: LaunchableApp, label: String): LauncherItem =
        item.copy(pairWith = target(second), label = label)

    fun parted(item: LauncherItem, label: String): LauncherItem = item.copy(pairWith = null, label = label)
}
