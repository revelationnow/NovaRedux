package com.supernova.launcher.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Process
import android.util.Log
import android.view.WindowManager
import androidx.core.net.toUri
import com.supernova.launcher.ui.LauncherOverlayBinder as Overlay

// The page to the left of home drawn by another app, as Nova showed Google
// Discover. NovaRedux hands its window to an app that serves a launcher
// overlay - the Google app, through a bridge app such as AIDL Bridge or
// Lawnfeed, or a feed app such as Neo Feed - drives it as the first page is
// pulled, and hears how far it is open. NovaRedux stays offline: the page is
// the other app's own window over the home screen.
//
// After Launcher3's launcher client: bind to the provider's overlay service,
// attach the activity's window to it, keep it told whether the activity is
// started and resumed, and forward a pull's progress; the page settles open
// or shut on its own when the pull ends.
class DiscoverClient(
    private val activity: Activity,
    // How far the page is open (0..1), as it moves.
    private val onProgress: (Float) -> Unit
) {
    private var provider: String? = null
    private var theme = DiscoverTheme.AUTO
    private var overlay: IBinder? = null
    private var bound = false
    private var serviceVersion = 1
    private var windowAttrs: WindowManager.LayoutParams? = null
    private var activityState = 0
    private val main = Handler(Looper.getMainLooper())

    // The provider has a page to show, and how far that page is open.
    var ready = false
        private set
    var progress = 0f
        private set
    val showing: Boolean get() = progress > 0f

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            overlay = service
            attach()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            overlay = null
            ready = false
            moved(0f)
        }
    }

    private val callback = Overlay.Callback(
        onScroll = { value -> main.post { moved(value) } },
        onStatus = { status -> main.post { ready = status and STATUS_HAS_PAGE != 0 } }
    )

    // The provider's package, or null for none (NovaRedux's own feed), and
    // the page's theme. A new provider is bound afresh; a new theme is sent
    // with the window again.
    fun configure(packageName: String?, theme: DiscoverTheme) {
        val themeChanged = theme != this.theme
        this.theme = theme
        if (packageName != provider) {
            unbind()
            provider = packageName
            if (packageName != null) bind(packageName)
        } else if (themeChanged) {
            attach()
        }
    }

    // The activity's window was attached (or detached): the page draws over
    // it, on its token.
    fun windowAttached(attached: Boolean) {
        if (attached) {
            windowAttrs = activity.window.attributes
            attach()
        } else {
            windowAttrs = null
            overlay.send(Overlay.WINDOW_DETACHED) { writeInt(if (activity.isChangingConfigurations) 1 else 0) }
        }
    }

    // Started and resumed, as the activity goes: the page pauses with it.
    fun activityState(started: Boolean, resumed: Boolean) {
        activityState = (if (started) STATE_STARTED else 0) or (if (resumed) STATE_RESUMED else 0)
        overlay.sendState(activityState, serviceVersion)
    }

    // A pull from the first page: its start, how far it has come (0..1),
    // and its end.
    enum class Pull { START, MOVE, END }

    fun pull(phase: Pull, progress: Float = 0f) = when (phase) {
        Pull.START -> overlay.send(Overlay.START_SCROLL)
        Pull.MOVE -> overlay.send(Overlay.ON_SCROLL) { writeFloat(progress.coerceIn(0f, 1f)) }
        Pull.END -> overlay.send(Overlay.END_SCROLL)
    }

    // Opens or closes the page, animated or at once.
    fun show(open: Boolean, animate: Boolean = true) {
        val flags = if (animate) FLAG_ANIMATE else 0
        overlay.send(if (open) Overlay.OPEN_OVERLAY else Overlay.CLOSE_OVERLAY) { writeInt(flags) }
    }

    private fun bind(packageName: String) {
        val intent = serviceIntent(activity, packageName)
        serviceVersion = runCatching {
            @Suppress("DEPRECATION")
            activity.packageManager.resolveService(intent, PackageManager.GET_META_DATA)
                ?.serviceInfo?.metaData?.getInt(SERVICE_VERSION_KEY, 1)
        }.getOrNull() ?: 1
        bound = runCatching {
            activity.bindService(intent, connection, Context.BIND_AUTO_CREATE or Context.BIND_ADJUST_WITH_ACTIVITY)
        }.getOrDefault(false)
        if (!bound) Log.w(TAG, "$packageName serves no launcher overlay")
    }

    private fun unbind() {
        overlay.send(Overlay.WINDOW_DETACHED) { writeInt(0) }
        if (bound) runCatching { activity.unbindService(connection) }
        bound = false
        overlay = null
        ready = false
        if (progress != 0f) moved(0f)
    }

    // The window and how the client works, then the activity's state. A
    // provider older than windowAttached2 takes the window alone.
    private fun attach() {
        val attrs = windowAttrs ?: return
        if (serviceVersion < WINDOW_ATTACHED2_VERSION) {
            overlay.send(Overlay.WINDOW_ATTACHED) {
                writeTypedObject(attrs, 0)
                writeStrongBinder(callback)
                writeInt(CLIENT_OPTIONS)
            }
        } else {
            val window = Bundle().apply {
                putParcelable("layout_params", attrs)
                putParcelable("configuration", themed(activity.resources.configuration, theme))
                putInt("client_options", CLIENT_OPTIONS)
            }
            overlay.send(Overlay.WINDOW_ATTACHED_2) {
                writeTypedObject(window, 0)
                writeStrongBinder(callback)
            }
        }
        overlay.sendState(activityState, serviceVersion)
    }

    private fun moved(value: Float) {
        progress = value.coerceIn(0f, 1f)
        onProgress(progress)
    }

    companion object {
        private const val TAG = "DiscoverClient"
        const val ACTION = "com.android.launcher3.WINDOW_OVERLAY"
        private const val SERVICE_VERSION_KEY = "service.api.version"
        private const val CLIENT_VERSION = 7
        private const val CLIENT_SERVICE_VERSION = 9
        private const val WINDOW_ATTACHED2_VERSION = 3
        const val ACTIVITY_STATE_VERSION = 4
        // Feed, search, hotword and the rest, as the Pixel launcher asks.
        private const val CLIENT_OPTIONS = 15
        private const val STATE_STARTED = 1
        const val STATE_RESUMED = 2
        private const val STATUS_HAS_PAGE = 1
        private const val FLAG_ANIMATE = 1

        // The service a provider serves the overlay from; with no package,
        // what every provider's service answers (for providers()).
        fun serviceIntent(context: Context, packageName: String?): Intent =
            Intent(ACTION).setPackage(packageName).setData(
                "app://${context.packageName}:${Process.myUid()}".toUri().buildUpon()
                    .appendQueryParameter("v", CLIENT_VERSION.toString())
                    .appendQueryParameter("cv", CLIENT_SERVICE_VERSION.toString())
                    .build()
            )

        // Every installed app that serves a launcher overlay, by package.
        fun providers(context: Context): List<String> = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(serviceIntent(context, null), 0)
                .map { it.serviceInfo.packageName }
                .distinct()
        }.getOrDefault(emptyList())

        // The launcher's configuration with its night mode as the theme asks
        // (Auto leaves it as it is): the page takes its look from it.
        fun themed(base: Configuration, theme: DiscoverTheme): Configuration {
            val night = when (theme) {
                DiscoverTheme.AUTO -> return base
                DiscoverTheme.LIGHT -> Configuration.UI_MODE_NIGHT_NO
                DiscoverTheme.DARK -> Configuration.UI_MODE_NIGHT_YES
            }
            return Configuration(base).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            }
        }
    }
}

// A call to the provider, when there is one; a provider gone away or failing
// is logged and changes nothing here.
private inline fun IBinder?.send(code: Int, crossinline write: Parcel.() -> Unit = {}) {
    val service = this ?: return
    runCatching { service.overlayCall(code) { write() } }
        .onFailure { Log.w("DiscoverClient", "launcher overlay call $code failed", it) }
}

// The activity's state whole, or as a pause or a resume to a provider older
// than setActivityState.
private fun IBinder?.sendState(state: Int, serviceVersion: Int) {
    when {
        serviceVersion >= DiscoverClient.ACTIVITY_STATE_VERSION ->
            send(Overlay.SET_ACTIVITY_STATE) { writeInt(state) }
        state and DiscoverClient.STATE_RESUMED != 0 -> send(Overlay.ON_RESUME)
        else -> send(Overlay.ON_PAUSE)
    }
}
