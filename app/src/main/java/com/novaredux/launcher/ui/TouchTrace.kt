package com.novaredux.launcher.ui

import android.util.Log

// Where the home screen's touches go, for a look on an emulator or a phone:
// silent unless turned on with `adb shell setprop log.tag.NovaTouch DEBUG`
// (the Screens workflow does), read again each time the launcher resumes.
object TouchTrace {
    const val TAG = "NovaTouch"

    // Read once a resume, not once a touch: isLoggable reads a system
    // property, and the home screen sees a touch event every few milliseconds.
    @Volatile
    var on = false
        private set

    fun refresh() {
        on = Log.isLoggable(TAG, Log.DEBUG)
    }

    inline fun log(message: () -> String) {
        if (on) Log.d(TAG, message())
    }
}
