package io.github.revelationnow.novaredux.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {

    // NovaRedux's web search engines, by the names its settings store, less
    // its custom address. The launcher's SuggestionSearchProviderTest pins
    // the same list on its side.
    @Test
    fun knowsEveryEngineNovaReduxOffers() {
        assertEquals(
            listOf("GOOGLE", "DUCKDUCKGO", "BRAVE", "BING", "ECOSIA", "STARTPAGE", "QWANT", "YAHOO", "YANDEX", "KAGI"),
            SuggestionEngine.entries.map { it.name }
        )
    }

    @Test
    fun findsEnginesByNameLeniently() {
        assertEquals(SuggestionEngine.DUCKDUCKGO, SuggestionEngine.fromName("DUCKDUCKGO"))
        assertEquals(SuggestionEngine.BRAVE, SuggestionEngine.fromName(" brave "))
        assertNull(SuggestionEngine.fromName("CUSTOM"))
        assertNull(SuggestionEngine.fromName(""))
        assertNull(SuggestionEngine.fromName(null))
    }

    @Test
    fun putsTheEncodedQueryLast() {
        assertEquals(
            "https://ac.duckduckgo.com/ac/?type=list&q=caf%C3%A9+%26+bar",
            SuggestionEngine.DUCKDUCKGO.url("café & bar")
        )
    }

    @Test
    fun everyEngineIsAskedOverHttps() {
        SuggestionEngine.entries.forEach { engine ->
            val url = engine.url("q")
            assertTrue(url, url.startsWith("https://") && url.endsWith("=q"))
        }
    }
}
