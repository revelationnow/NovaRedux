package com.novaredux.launcher.search

// Web suggestions for what was typed, from the user's own search engine,
// asked through NovaRedux Connect: the separate app that alone goes online
// (NovaRedux itself has no internet permission). fetch is that app's answer
// for a query and an engine; it is empty when the app isn't installed, the
// engine is one it can't ask, or the answer is too slow.
class SuggestionSearchProvider(
    private val engine: () -> WebEngine,
    private val fetch: (query: String, engine: WebProvider) -> List<String>
) : SearchProvider {

    override val id: String = ID

    // A word typed quickly is asked about once, when the typing pauses.
    override val debounceMillis: Long = DEBOUNCE_MS

    override fun search(query: String): List<SearchResult> {
        val trimmed = query.trim()
        val engine = engine()
        // A custom search address has no known way to ask for suggestions.
        if (trimmed.length < MIN_CHARS || engine.provider == WebProvider.CUSTOM) return emptyList()
        return fetch(trimmed, engine.provider)
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.equals(trimmed, ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .take(MAX_SUGGESTIONS)
            .map { SearchResult.Suggestion(it, trimmed, engine) }
    }

    companion object {
        const val ID = "suggestions"
        const val MIN_CHARS = 2
        const val MAX_SUGGESTIONS = 4
        const val DEBOUNCE_MS = 250L

        // What the engine added to the typed words, which the row shows in
        // bold as the engines do: nothing when the suggestion doesn't start
        // with them.
        fun completion(text: String, query: String): List<IntRange> =
            if (query.isNotEmpty() && text.length > query.length && text.startsWith(query, ignoreCase = true)) {
                listOf(query.length until text.length)
            } else {
                emptyList()
            }
    }
}
