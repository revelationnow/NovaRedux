package com.novaredux.launcher.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.novaredux.launcher.R
import java.util.concurrent.TimeUnit

// Nova's root screen-lock method: the power key, pressed through `su`. A
// phone without root, or a root manager that refuses, is told so.
//
// The one command NovaRedux runs as root, fixed here, and only when a lock
// is asked for with Root picked as the method; picking it says first what a
// root grant gives (lock_root_warning). Nothing else in the app calls su.
object RootLock {
    private const val WAIT_S = 10L
    private val COMMAND = listOf("su", "-c", "input keyevent KEYCODE_POWER")

    fun lock(context: Context) {
        val app = context.applicationContext
        Thread {
            val pressed = runCatching {
                val su = ProcessBuilder(COMMAND).redirectErrorStream(true).start()
                // A root manager still waiting on its prompt isn't left
                // holding a shell.
                val done = su.waitFor(WAIT_S, TimeUnit.SECONDS)
                if (!done) su.destroy()
                done && su.exitValue() == 0
            }.getOrDefault(false)
            if (!pressed) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(app, R.string.lock_root_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
