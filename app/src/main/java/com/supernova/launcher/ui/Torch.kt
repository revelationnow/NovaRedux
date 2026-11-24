package com.supernova.launcher.ui

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import com.supernova.launcher.runLogged

// The Torch action (#5): the camera's flash on or off, as the quick settings
// tile does it. Android's torch needs no camera permission. Its state is
// followed from the first use on, so a toggle after the tile turned it on
// turns it off.
object Torch {

    @Volatile
    private var on = false

    @Volatile
    private var cameraId: String? = null
    private var watching = false

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) on = enabled
        }
    }

    // False when the phone has no flash, or another app holds the camera.
    fun toggle(context: Context): Boolean {
        val (camera, id) = flash(context) ?: return false
        if (!watching) {
            watching = true
            camera.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        }
        return runLogged("toggle the torch") { camera.setTorchMode(id, !on) }.isSuccess
    }

    private fun flash(context: Context): Pair<CameraManager, String>? {
        val camera = context.applicationContext.getSystemService(CameraManager::class.java) ?: return null
        val id = cameraId ?: runCatching {
            camera.cameraIdList.firstOrNull { id ->
                camera.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
        cameraId = id
        return id?.let { camera to it }
    }
}
