package io.github.revelationnow.novaredux.connect

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenSearchSuggestionsTest {

    @Test
    fun readsTheSecondElement() {
        assertEquals(
            listOf("weather today", "weather tomorrow"),
            OpenSearchSuggestions.parse("""["weather",["weather today","weather tomorrow"]]""")
        )
    }

    // Google's answer carries its own metadata after the suggestions.
    @Test
    fun ignoresTheExtrasAfterIt() {
        val body = """["cat",["cats","cat videos"],["",""],[],{"google:suggesttype":["QUERY","QUERY"]}]"""
        assertEquals(listOf("cats", "cat videos"), OpenSearchSuggestions.parse(body))
    }

    @Test
    fun keepsOnlyNonBlankStrings() {
        val body = """["q",["one", 2, null, "  ", {"x":1}, " two "]]"""
        assertEquals(listOf("one", "two"), OpenSearchSuggestions.parse(body))
    }

    @Test
    fun decodesEscapes() {
        assertEquals(listOf("café \"au lait\""), OpenSearchSuggestions.parse("""["c",["café \"au lait\""]]"""))
    }

    @Test
    fun anythingElseIsNoSuggestions() {
        listOf(
            "",
            "<html><body>Sorry</body></html>",
            """{"suggestions":["a"]}""",
            """["only the query"]""",
            """["q","not a list"]""",
            """["q",["cut short"""
        ).forEach { body ->
            assertEquals(body, emptyList<String>(), OpenSearchSuggestions.parse(body))
        }
    }
}
