package com.supernova.launcher.search

// Actions on what was typed (#9): one row for a phone number, a web address
// or an email address, at the top of the results.
class TypedActionSearchProvider : SearchProvider {

    override val id: String = ID

    override fun search(query: String): List<SearchResult> =
        TypedText.of(query)?.let { listOf(SearchResult.Action(it.kind, it.text)) }.orEmpty()

    companion object {
        const val ID = "typed"
    }
}
