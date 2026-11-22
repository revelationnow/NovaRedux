package io.github.revelationnow.novaredux.connect

import org.junit.Assert.assertEquals
import org.junit.Test

class BingSuggestionsTest {

    @Test
    fun readsEveryGroupsSuggestions() {
        val body = """{"AS":{"Query":"weather","FullResults":1,"Results":[""" +
            """{"Type":"AS","Suggests":[{"Txt":"weather","Type":"LS","Sk":"","HCS":1},""" +
            """{"Txt":"weather channel","Type":"LS","Sk":"LS1"}]},""" +
            """{"Type":"AS","Suggests":[{"Txt":" weather radar ","Type":"LS"},{"Type":"LS"},{"Txt":7}]}]}}"""
        assertEquals(listOf("weather", "weather channel", "weather radar"), BingSuggestions.parse(body))
    }

    @Test
    fun anythingElseIsNoSuggestions() {
        listOf("", "[]", """{"AS":{}}""", """{"AS":{"Results":"none"}}""", """["q",["a"]]""").forEach { body ->
            assertEquals(body, emptyList<String>(), BingSuggestions.parse(body))
        }
    }

    @Test
    fun bingIsReadInItsOwnFormat() {
        assertEquals(AnswerFormat.BING, SuggestionEngine.BING.format)
        assertEquals(AnswerFormat.OPENSEARCH, SuggestionEngine.GOOGLE.format)
    }
}
