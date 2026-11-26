package io.github.revelationnow.novaredux.connect

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.Charset

// Asks an engine for suggestions over HTTPS, as plainly as it can be asked:
// no cookies, no identifiers, this app's own name as the user agent, and the
// phone's language so the suggestions come in it. Answers are kept a few
// minutes (SuggestionCache). Only HTTPS, and no redirects: the query goes to
// the engine's own address and nowhere it might point to.
class SuggestionFetcher(
    private val cache: SuggestionCache = SuggestionCache(),
    private val open: (String) -> HttpURLConnection = { openHttps(it) }
) {

    // What one request came back with: the HTTP status (0 when it never got
    // one) and the suggestions in it.
    class Answer(val status: Int, val suggestions: List<String>)

    // onCancel hands over the way to stop the request midway: the
    // provider's cancellation signal calls it when NovaRedux stops waiting.
    fun fetch(
        engine: SuggestionEngine,
        query: String,
        language: String,
        onCancel: (stop: () -> Unit) -> Unit = {}
    ): List<String> =
        cache.get(engine, query) ?: ask(engine, query, language, onCancel).suggestions.also { found ->
            if (found.isNotEmpty()) cache.put(engine, query, found)
        }

    internal fun ask(
        engine: SuggestionEngine,
        query: String,
        language: String,
        onCancel: (stop: () -> Unit) -> Unit = {}
    ): Answer {
        val connection = open(engine.url(query))
        onCancel { connection.disconnect() }
        return runCatching {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", ACCEPT)
            if (language.isNotEmpty()) connection.setRequestProperty("Accept-Language", language)
            val status = connection.responseCode
            val suggestions = if (status == HttpURLConnection.HTTP_OK) {
                val charset = charsetOf(connection.contentType)
                connection.inputStream.use { engine.format.parse(readCapped(it, charset)) }
            } else {
                emptyList()
            }
            Answer(status, suggestions)
        }.getOrDefault(Answer(0, emptyList())).also { connection.disconnect() }
    }

    internal companion object {
        const val TIMEOUT_MS = 2_000

        fun openHttps(url: String): HttpURLConnection {
            val uri = URI(url)
            require(uri.scheme == "https") { "suggestions go over HTTPS only" }
            return uri.toURL().openConnection() as HttpURLConnection
        }
        const val USER_AGENT = "NovaReduxConnect/1 (Android)"
        const val ACCEPT = "application/x-suggestions+json, application/json;q=0.9, */*;q=0.1"

        // Far more than any list of suggestions: a page that size is not one.
        const val MAX_BYTES = 64 * 1024
        private const val BUFFER_BYTES = 8 * 1024

        // The charset a Content-Type names, or UTF-8 (JSON's own).
        fun charsetOf(contentType: String?): Charset {
            val name = contentType?.split(';')
                ?.map(String::trim)
                ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
                ?.substringAfter('=')?.trim('"', ' ')
            return name?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        }

        // At most max bytes of input, decoded; a longer answer is cut short
        // (and so won't parse).
        fun readCapped(input: InputStream, charset: Charset, max: Int = MAX_BYTES): String {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            while (out.size() < max) {
                val read = input.read(buffer, 0, minOf(buffer.size, max - out.size()))
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            return String(out.toByteArray(), charset)
        }
    }
}
