package com.novaredux.launcher.data

// "App icons" in the icon picker: any installed app's own icon, picked for
// an app, a folder or a launcher action. A pick
// is kept the way a pack icon is, in the pack and drawable-name fields, under
// a pack name no installed package can have, so backups, drawer groups and
// Reset custom icons carry it as they carry a pack icon. The name is the
// source app's "userSerial/pkg/cls". An app that has gone falls back to the
// default icon, as a vanished pack's icon does.
object AppIconSource {

    // A package name holds no ':'.
    const val PACK = "novaredux:app"

    // Its name before 28 Nov 2026 (docs/NAMES.md), and Supernova's: still
    // read, from older layouts, drawer groups and Supernova backups.
    const val SUPERNOVA_PACK = "supernova:app"

    fun isAppIcon(pack: String?): Boolean = pack == PACK || pack == SUPERNOVA_PACK

    fun nameFor(app: LaunchableApp): String = "${app.userSerial}/${app.packageName}/${app.className}"

    // The source app of a picked name (label left empty); null for a
    // malformed one.
    fun appOf(name: String): LaunchableApp? {
        val parts = name.split('/', limit = 3)
        val serial = parts.getOrNull(0)?.toLongOrNull()
        val pkg = parts.getOrNull(1).orEmpty()
        val cls = parts.getOrNull(2).orEmpty()
        return if (serial == null || pkg.isEmpty() || cls.isEmpty()) {
            null
        } else {
            LaunchableApp(pkg, cls, label = "", userSerial = serial)
        }
    }
}
