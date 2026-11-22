package com.supernova.launcher.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.supernova.launcher.R
import java.util.concurrent.TimeUnit

// Nova's root screen-lock method: the power key, pressed through `su`. A
// phone without root, or a root manager that refuses, is told so.
object RootLock {
    private const val WAIT_S = 10L

    fun lock(context: Context) {
        val app = context.applicationContext
        Thread {
            val pressed = runCatching {
                val su = ProcessBuilder("su", "-c", "input keyevent KEYCODE_POWER").redirectErrorStream(true).start()
                su.waitFor(WAIT_S, TimeUnit.SECONDS) && su.exitValue() == 0
            }.getOrDefault(false)
            if (!pressed) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(app, R.string.lock_root_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
