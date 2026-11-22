package com.supernova.launcher.ui

import android.os.Binder
import android.os.IBinder
import android.os.Parcel

// The launcher overlay interface that the Google app - and the bridge and
// feed apps built on it - serves to launchers for the page to the left of
// home, by hand: its interface names and its calls' transaction codes, which
// are its methods' places in the interface (com.google.android.libraries
// .launcherclient.ILauncherOverlay) and must stay as the services expect.
// Every call is one-way. See DiscoverClient.
internal object LauncherOverlayBinder {
    const val OVERLAY = "com.google.android.libraries.launcherclient.ILauncherOverlay"
    const val CALLBACK = "com.google.android.libraries.launcherclient.ILauncherOverlayCallback"

    // ILauncherOverlay's calls, in its order.
    const val START_SCROLL = IBinder.FIRST_CALL_TRANSACTION
    const val ON_SCROLL = IBinder.FIRST_CALL_TRANSACTION + 1
    const val END_SCROLL = IBinder.FIRST_CALL_TRANSACTION + 2
    const val WINDOW_ATTACHED = IBinder.FIRST_CALL_TRANSACTION + 3
    const val WINDOW_DETACHED = IBinder.FIRST_CALL_TRANSACTION + 4
    const val CLOSE_OVERLAY = IBinder.FIRST_CALL_TRANSACTION + 5
    const val ON_PAUSE = IBinder.FIRST_CALL_TRANSACTION + 6
    const val ON_RESUME = IBinder.FIRST_CALL_TRANSACTION + 7
    const val OPEN_OVERLAY = IBinder.FIRST_CALL_TRANSACTION + 8
    const val WINDOW_ATTACHED_2 = IBinder.FIRST_CALL_TRANSACTION + 13
    const val SET_ACTIVITY_STATE = IBinder.FIRST_CALL_TRANSACTION + 15

    // ILauncherOverlayCallback's two.
    const val SCROLL_CHANGED = IBinder.FIRST_CALL_TRANSACTION
    const val STATUS_CHANGED = IBinder.FIRST_CALL_TRANSACTION + 1

    // What the launcher hands the service to hear back on: how far the page
    // is open, and its status. Called on a binder thread.
    class Callback(
        private val onScroll: (Float) -> Unit,
        private val onStatus: (Int) -> Unit
    ) : Binder() {
        init {
            attachInterface(null, CALLBACK)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != SCROLL_CHANGED && code != STATUS_CHANGED) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(CALLBACK)
            if (code == SCROLL_CHANGED) onScroll(data.readFloat()) else onStatus(data.readInt())
            return true
        }
    }
}

// A one-way call to the overlay service: its interface name, then what
// `write` puts after it.
internal inline fun IBinder.overlayCall(code: Int, write: Parcel.() -> Unit = {}) {
    val data = Parcel.obtain()
    try {
        data.writeInterfaceToken(LauncherOverlayBinder.OVERLAY)
        data.write()
        transact(code, data, null, IBinder.FLAG_ONEWAY)
    } finally {
        data.recycle()
    }
}
