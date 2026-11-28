package com.novaredux.launcher.search

// One search source feeding the global search surface. Implementations must
// be callable off the main thread; in-memory providers (apps, calculator)
// should return synchronously and fast, binder/content-provider sources are
// invoked from a cancellable coroutine by the orchestrator.
interface SearchProvider {
    val id: String
    fun search(query: String): List<SearchResult>

    // How long the async tier waits after a keystroke before asking: for a
    // source that costs something per query (web suggestions go out to the
    // network), so a word typed quickly is asked about once.
    val debounceMillis: Long get() = 0L
}

// Result variants for the global search surface. Drawables ride along where
// the producing provider already resolved them off the main thread (shortcut
// and contact icons), so binds stay allocation-free.
sealed class SearchResult {
    data class Calculation(val expression: String, val display: String) : SearchResult()
    // highlight: the label ranges the query matched (AppMatcher), which
    // the search window emboldens.
    data class App(
        val app: com.novaredux.launcher.data.LaunchableApp,
        val highlight: List<IntRange> = emptyList()
    ) : SearchResult()
    data class Shortcut(
        val packageName: String,
        val shortcutId: String,
        val label: String,
        val userSerial: Long,
        val icon: android.graphics.drawable.Drawable?
    ) : SearchResult()
    data class Contact(
        val contactId: Long,
        val lookupKey: String,
        val name: String,
        val photo: android.graphics.drawable.Drawable?,
        // First phone number, resolved only when contact cards are on (D4).
        val phoneNumber: String? = null
    ) : SearchResult()
    // A launcher folder (workspace item or drawer folder-group); exactly one
    // of itemId / groupId is set. The icon is resolved at match time.
    // privateSpace: neither is set, and the row opens the private space's
    // card (PrivateSpaceSearchProvider).
    data class Folder(
        val itemId: String?,
        val groupId: String?,
        val label: String,
        val icon: android.graphics.drawable.Drawable?,
        val privateSpace: Boolean = false
    ) : SearchResult()

    // A calendar event matched by its title or place (#7), at its nearest
    // occurrence; it opens in the calendar app. color: its calendar's.
    data class Event(
        val eventId: Long,
        val title: String,
        val begin: Long,
        val end: Long,
        val allDay: Boolean,
        val location: String?,
        val color: Int?
    ) : SearchResult()

    // A phone number, web address or email address typed into search (#9),
    // with what can be done with it.
    data class Action(val kind: TypedText.Kind, val text: String) : SearchResult()

    // A custom card matched by name; rendered as the full card.
    data class Card(val card: com.novaredux.launcher.feed.FeedCard) : SearchResult()

    data class Web(val query: String, val provider: WebEngine) : SearchResult()
    data class ProviderChips(val query: String) : SearchResult()

    // A search the web engine suggests for what was typed (query), asked
    // through NovaRedux Connect; a tap searches the web for text.
    data class Suggestion(val text: String, val query: String, val engine: WebEngine) : SearchResult()

    companion object {
        // What Enter opens: the first row the results list shows that one tap
        // opens, in the list's own order. A calculation only copies, and a
        // card's actions are its own.
        fun firstOpenable(results: List<SearchResult>): SearchResult? =
            results.firstOrNull { it is App }
                ?: results.firstOrNull { it is Folder }
                ?: results.firstOrNull { it is Shortcut }
                ?: results.firstOrNull { it is Contact }
    }
}

class MathSearchProvider : SearchProvider {
    override val id: String = ID

    override fun search(query: String): List<SearchResult> {
        val result = Calculator.evaluate(query) ?: return emptyList()
        return listOf(SearchResult.Calculation(query, result.display))
    }

    companion object {
        const val ID = "math"
    }
}
