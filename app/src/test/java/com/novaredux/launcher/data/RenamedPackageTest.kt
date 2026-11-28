package com.novaredux.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

// An item from before the rename that opens one of NovaRedux's own classes.
class RenamedPackageTest {

    private fun app(id: String, pkg: String, cls: String) = LauncherItem(
        id = id, itemType = ItemType.APP, packageName = pkg, className = cls
    )

    @Test
    fun ownClassesTakeTheirNewNames() {
        val model = LauncherModel(
            items = listOf(
                app("own", "io.github.revelationnow.novaredux.debug", "com.supernova.launcher.SettingsLauncherAlias"),
                // Supernova itself, from a Supernova backup, keeps its names.
                app("upstream", "com.supernova.launcher", "com.supernova.launcher.SettingsLauncherAlias"),
                app("other", "com.example.mail", "com.example.mail.Main")
            )
        )
        val classes = ModelMigrations.migrate(model).items.associate { it.id to it.className }
        assertEquals("com.novaredux.launcher.SettingsLauncherAlias", classes["own"])
        assertEquals("com.supernova.launcher.SettingsLauncherAlias", classes["upstream"])
        assertEquals("com.example.mail.Main", classes["other"])
    }

    @Test
    fun aLayoutWithNothingToRenameIsTheSameLayout() {
        val model = LauncherModel(items = listOf(app("other", "com.example.mail", "com.example.mail.Main")))
        assertSame(model, RenamedPackage.applied(model))
    }
}
