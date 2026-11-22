package com.supernova.launcher.connect

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.OperationCanceledException
import android.os.SystemClock
import androidx.core.net.toUri
import com.supernova.launcher.runLogged
import java.util.concurrent.atomic.AtomicReference

// NovaRedux Connect: the small companion app (the connect module) that does
// what needs the internet on NovaRedux's behalf - today, web suggestions
// while typing a search - so NovaRedux itself never holds the permission.
// It answers through a content provider, and only to NovaRedux's own
// packages. The contract below (authority, path, parameters and column) is
// the connect module's SuggestionsContract; the two change together.
object NovaConnect {
    const val PACKAGE = "io.github.revelationnow.novaredux.connect"
    private const val AUTHORITY = "$PACKAGE.suggestions"
    private const val PATH = "suggestions"
    private const val PARAM_QUERY = "q"
    private const val PARAM_ENGINE = "engine"
    private const val COLUMN = "suggestion"

    // Where it is published until a store carries it.
    private const val DOWNLOAD_PAGE = "https://github.com/revelationnow/NovaRedux/releases"

    // How long a search waits for suggestions before going on without.
    private const val TIMEOUT_MS = 2_500L

    // Whether it is installed is asked again at most this often: a search
    // asks on every pause in the typing.
    private const val RECHECK_MS = 5_000L

    @Volatile private var checkedAt = Long.MIN_VALUE
    @Volatile private var present = false

    // The ask still waiting, which a newer one cancels.
    private val inFlight = AtomicReference<CancellationSignal?>()
    private val timer by lazy { Handler(Looper.getMainLooper()) }

    fun installed(context: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (checkedAt == Long.MIN_VALUE || now - checkedAt > RECHECK_MS) {
            present = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess
            checkedAt = now
        }
        return present
    }

    // The page to get it from.
    fun downloadIntent(): Intent =
        Intent(Intent.ACTION_VIEW, DOWNLOAD_PAGE.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // Blocking, so off the main thread: what engine (a WebProvider name)
    // suggests for query, best first. Empty without the app, past the
    // timeout, or when a newer ask has taken over.
    fun suggestions(context: Context, query: String, engine: String): List<String> {
        if (!installed(context)) return emptyList()
        val uri = Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(PATH)
            .appendQueryParameter(PARAM_QUERY, query)
            .appendQueryParameter(PARAM_ENGINE, engine)
            .build()
        val signal = CancellationSignal()
        inFlight.getAndSet(signal)?.cancel()
        val timeout = Runnable { signal.cancel() }
        timer.postDelayed(timeout, TIMEOUT_MS)
        return try {
            runLogged("web suggestions") { ask(context, uri, signal) }.getOrDefault(emptyList())
        } finally {
            timer.removeCallbacks(timeout)
            inFlight.compareAndSet(signal, null)
        }
    }

    private fun ask(context: Context, uri: Uri, signal: CancellationSignal): List<String> = try {
        context.contentResolver.query(uri, arrayOf(COLUMN), null, null, null, signal)?.use(::column).orEmpty()
    } catch (expected: OperationCanceledException) {
        // Too slow, or the typing went on: no suggestions this time.
        emptyList()
    }

    private fun column(cursor: Cursor): List<String> = buildList {
        val index = cursor.getColumnIndex(COLUMN)
        if (index < 0) return@buildList
        while (cursor.moveToNext()) cursor.getString(index)?.let(::add)
    }
}
