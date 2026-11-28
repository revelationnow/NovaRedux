package com.novaredux.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Test

// A shortcut is found by its own name, whichever app offers it (#324): the
// apps the query names keep their shortcuts first, then the closer matches.
class ShortcutRankingTest {

    private data class Hit(val pkg: String, val label: String, val serial: Long = 0L)

    private fun ranked(hits: List<Hit>, query: String, vararg named: String) =
        ShortcutSearchProvider.ranked(
            hits, query, named.map { it to 0L }, owner = { it.pkg to it.serial }, label = { it.label }
        ).map { it.label }

    @Test
    fun theNamedAppsComeFirstInTheirOrder() {
        val hits = listOf(
            Hit("chat", "Mom"),
            Hit("notes", "New momentum"),
            Hit("mail", "Mom at work"),
            Hit("phone", "Call Mom")
        )
        assertEquals(
            listOf("Call Mom", "New momentum", "Mom", "Mom at work"),
            ranked(hits, "mom", "phone", "notes")
        )
    }

    @Test
    fun aNameStartingWithTheQueryComesAheadOfOneHoldingItLater() {
        val hits = listOf(Hit("dial", "Joanna"), Hit("chat", "Ann Lee"), Hit("chat", "Anna"))
        assertEquals(listOf("Ann Lee", "Anna", "Joanna"), ranked(hits, "ann"))
    }

    @Test
    fun aWorkProfilesCopyIsAnotherApp() {
        val hits = listOf(Hit("chat", "Sam", serial = 10L), Hit("chat", "Sam"))
        val ranked = ShortcutSearchProvider.ranked(
            hits, "sam", listOf("chat" to 0L), owner = { it.pkg to it.serial }, label = { it.label }
        )
        assertEquals(listOf(0L, 10L), ranked.map { it.serial })
    }
}
