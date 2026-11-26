package io.github.revelationnow.novaredux.connect

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import java.util.Locale

// Answers NovaRedux's question, what an engine suggests for what was typed
// (SuggestionsContract), and only NovaRedux's, signed with this app's key:
// any other app is refused (Callers).
// Read-only. The request runs on the binder thread the query came in on,
// and stops when NovaRedux stops waiting (its cancellation signal).
class SuggestionsProvider : ContentProvider() {

    private val fetcher by lazy { SuggestionFetcher() }

    override fun onCreate(): Boolean = true

    // Every query since Android 8 arrives here, with the cancellation signal.
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?
    ): Cursor = answer(uri, cancellationSignal)

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor = answer(uri, null)

    private fun answer(uri: Uri, signal: CancellationSignal?): Cursor {
        val context = context ?: throw SecurityException(REFUSED)
        if (!Callers.mayAsk(context, callingPackage)) throw SecurityException(REFUSED)
        val cursor = MatrixCursor(arrayOf(SuggestionsContract.COLUMN))
        val query = uri.getQueryParameter(SuggestionsContract.PARAM_QUERY)?.trim().orEmpty()
        val engine = SuggestionEngine.fromName(uri.getQueryParameter(SuggestionsContract.PARAM_ENGINE))
        val asked = uri.pathSegments == listOf(SuggestionsContract.PATH) && query.length in 1..MAX_QUERY_CHARS
        if (asked && engine != null) {
            fetcher.fetch(engine, query, Locale.getDefault().toLanguageTag()) { stop ->
                signal?.setOnCancelListener { stop() }
            }.forEach { cursor.addRow(arrayOf<Any>(it)) }
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException(READ_ONLY)

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException(READ_ONLY)

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException(READ_ONLY)

    private companion object {
        // A search, not a page of text: anything longer isn't sent.
        const val MAX_QUERY_CHARS = 200
        const val READ_ONLY = "Suggestions are read-only"
        const val REFUSED = "Only NovaRedux may ask for suggestions"
    }
}
