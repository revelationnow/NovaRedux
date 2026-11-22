package com.supernova.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionSearchProviderTest {

    private val asked = mutableListOf<Pair<String, WebProvider>>()

    private fun provider(
        engine: WebEngine = WebEngine(WebProvider.DUCKDUCKGO),
        answer: List<String> = listOf("cats", "cat videos")
    ) = SuggestionSearchProvider({ engine }) { query, provider ->
        asked += query to provider
        answer
    }

    @Test
    fun asksTheChosenEngine() {
        val engine = WebEngine(WebProvider.DUCKDUCKGO)
        val results = provider(engine).search(" cat ")

        assertEquals(listOf("cat" to WebProvider.DUCKDUCKGO), asked)
        assertEquals(
            listOf(
                SearchResult.Suggestion("cats", "cat", engine),
                SearchResult.Suggestion("cat videos", "cat", engine)
            ),
            results
        )
    }

    @Test
    fun dropsWhatWasTypedBlanksAndRepeats() {
        val answer = listOf("cat", "Cats", " ", "cats", "cat food", "cat toys", "cat names", "cat breeds")
        val texts = provider(answer = answer).search("cat").map { (it as SearchResult.Suggestion).text }
        assertEquals(listOf("Cats", "cat food", "cat toys", "cat names"), texts)
        assertEquals(SuggestionSearchProvider.MAX_SUGGESTIONS, texts.size)
    }

    @Test
    fun asksNothingForOneLetterOrACustomAddress() {
        assertTrue(provider().search("c").isEmpty())
        assertTrue(provider(WebEngine(WebProvider.CUSTOM, "https://example.org/?q=%s")).search("cat").isEmpty())
        assertTrue(asked.isEmpty())
    }

    @Test
    fun waitsForTheTypingToPause() {
        assertTrue(provider().debounceMillis > 0)
    }

    @Test
    fun boldsWhatTheEngineAdded() {
        assertEquals(listOf(3..7), SuggestionSearchProvider.completion("cat toys", "cat"))
        assertEquals(listOf(3..7), SuggestionSearchProvider.completion("Cat toys", "cat"))
        assertEquals(emptyList<IntRange>(), SuggestionSearchProvider.completion("wild cats", "cat"))
        assertEquals(emptyList<IntRange>(), SuggestionSearchProvider.completion("cat", "cat"))
    }

    // NovaRedux Connect knows the engines by these names (its
    // SuggestionEngine, which pins the same list): a new engine here needs
    // its suggestion address there.
    @Test
    fun connectKnowsEveryEngineByName() {
        assertEquals(
            listOf("GOOGLE", "DUCKDUCKGO", "BRAVE", "BING", "ECOSIA", "STARTPAGE", "QWANT", "YAHOO", "YANDEX", "KAGI"),
            WebProvider.entries.filter { it != WebProvider.CUSTOM }.map { it.name }
        )
    }
}
