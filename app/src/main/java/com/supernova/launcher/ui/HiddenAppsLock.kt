package com.supernova.launcher.ui

import com.supernova.launcher.data.LauncherPrefs

// The lock on hidden apps (#4). While Lock hidden apps is on, hidden apps
// stay out of the drawer, search and the Hidden apps list until the phone's
// own lock is given. One unlock lasts until NovaRedux leaves the screen, so
// a phone handed over after a hidden app was opened shows nothing hidden.
// It only hides: private space is what keeps apps apart.
object HiddenAppsLock {

    @Volatile
    private var unlocked = false

    // Whether hidden apps may show now.
    fun isOpen(prefs: LauncherPrefs): Boolean = !prefs.hiddenAppsLock || unlocked

    fun unlock() {
        unlocked = true
    }

    // NovaRedux left the screen.
    fun relock() {
        unlocked = false
    }

    // Runs then once hidden apps may show: at once when they already may,
    // else after the phone's lock.
    fun whenOpen(prefs: LauncherPrefs, auth: DeviceAuth, title: CharSequence, then: () -> Unit) {
        if (isOpen(prefs)) {
            then()
            return
        }
        auth.confirm(title) {
            unlock()
            then()
        }
    }
}
