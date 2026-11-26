package io.github.revelationnow.novaredux.connect

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionFetcherTest {

    // A connection that answers without a network, and remembers what it
    // was asked.
    private class FakeConnection(
        url: String,
        private val status: Int,
        private val body: String,
        private val type: String? = "application/x-suggestions+json; charset=utf-8",
        private val fails: Boolean = false
    ) : HttpURLConnection(URI(url).toURL()) {
        var disconnected = false

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy() = false

        override fun getResponseCode(): Int = if (fails) throw IOException("offline") else status

        override fun getContentType(): String? = type

        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
    }

    private val opened = mutableListOf<FakeConnection>()

    private fun fetcher(
        status: Int = HttpURLConnection.HTTP_OK,
        body: String = """["cat",["cats","cat videos"]]""",
        fails: Boolean = false
    ) = SuggestionFetcher(SuggestionCache()) { url ->
        FakeConnection(url, status, body, fails = fails).also(opened::add)
    }

    @Test
    fun asksTheEngineAndReadsItsAnswer() {
        val found = fetcher().fetch(SuggestionEngine.DUCKDUCKGO, "cat", "en-GB")

        assertEquals(listOf("cats", "cat videos"), found)
        val connection = opened.single()
        assertEquals("https://ac.duckduckgo.com/ac/?type=list&q=cat", connection.url.toString())
        assertEquals(SuggestionFetcher.USER_AGENT, connection.getRequestProperty("User-Agent"))
        assertEquals("en-GB", connection.getRequestProperty("Accept-Language"))
        assertEquals(null, connection.getRequestProperty("Cookie"))
        assertTrue(connection.disconnected)
    }

    @Test
    fun asksOnceForTheSameWords() {
        val subject = fetcher()
        subject.fetch(SuggestionEngine.GOOGLE, "cat", "en")
        subject.fetch(SuggestionEngine.GOOGLE, "cat", "en")
        assertEquals(1, opened.size)
    }

    @Test
    fun anErrorOrAFailureIsNoSuggestions() {
        assertEquals(emptyList<String>(), fetcher(status = 429).fetch(SuggestionEngine.GOOGLE, "cat", "en"))
        assertEquals(emptyList<String>(), fetcher(fails = true).fetch(SuggestionEngine.GOOGLE, "cat", "en"))
        assertTrue(opened.all { it.disconnected })
    }

    @Test
    fun nothingFoundIsAskedAgain() {
        val subject = fetcher(body = """["zzzz",[]]""")
        subject.fetch(SuggestionEngine.GOOGLE, "zzzz", "en")
        subject.fetch(SuggestionEngine.GOOGLE, "zzzz", "en")
        assertEquals(2, opened.size)
    }

    @Test
    fun cancellingDisconnects() {
        var stop: (() -> Unit)? = null
        fetcher().fetch(SuggestionEngine.GOOGLE, "cat", "en") { stop = it }
        opened.single().disconnected = false
        stop?.invoke()
        assertTrue(opened.single().disconnected)
    }

    @Test
    fun readsTheCharsetItIsTold() {
        assertEquals(Charsets.ISO_8859_1, SuggestionFetcher.charsetOf("text/javascript; charset=ISO-8859-1"))
        assertEquals(Charsets.UTF_8, SuggestionFetcher.charsetOf("application/json; charset=\"utf-8\""))
        assertEquals(Charsets.UTF_8, SuggestionFetcher.charsetOf("application/json"))
        assertEquals(Charsets.UTF_8, SuggestionFetcher.charsetOf("application/json; charset=no-such-thing"))
        assertEquals(Charsets.UTF_8, SuggestionFetcher.charsetOf(null))
    }

    @Test
    fun readsNoMoreThanItsCap() {
        val text = "x".repeat(10_000)
        fun read(max: Int) = SuggestionFetcher.readCapped(ByteArrayInputStream(text.toByteArray()), Charsets.UTF_8, max)
        assertEquals(100, read(100).length)
        assertEquals(text, read(SuggestionFetcher.MAX_BYTES))
    }

    // A redirect would take the query somewhere other than the engine's own
    // address; cleartext isn't asked at all.
    @Test
    fun followsNoRedirectAndOpensHttpsOnly() {
        fetcher().fetch(SuggestionEngine.DUCKDUCKGO, "dog", "en")
        assertFalse(opened.single().instanceFollowRedirects)
        assertThrows(IllegalArgumentException::class.java) { SuggestionFetcher.openHttps("http://example.org/?q=dog") }
    }
}
