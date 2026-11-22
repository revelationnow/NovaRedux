package io.github.revelationnow.novaredux.connect

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

// Asks every engine for real, to catch one that has moved or changed its
// answer. Off the network it would only fail, so it runs only with
// NOVAREDUX_LIVE_SUGGEST=1: the Suggestion endpoints workflow sets it, and
// prints what each engine said.
class LiveEndpointsTest {

    @Test
    fun everyEngineSuggestsSomething() {
        assumeTrue(System.getenv("NOVAREDUX_LIVE_SUGGEST") == "1")
        val fetcher = SuggestionFetcher()
        val silent = SuggestionEngine.entries.filter { engine ->
            val answer = fetcher.ask(engine.url(QUERY), "en-US")
            println("${engine.name}: HTTP ${answer.status}, ${answer.suggestions.take(SHOWN)}")
            answer.suggestions.isEmpty()
        }
        assertTrue("No suggestions from: $silent", silent.isEmpty())
    }

    private companion object {
        const val QUERY = "weather"
        const val SHOWN = 5
    }
}
