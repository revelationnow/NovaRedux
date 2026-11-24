package com.supernova.launcher.search

import com.supernova.launcher.runLogged
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Two-tier fan-out for the global search overlay. The immediate tier (apps,
// calculator, web fallback) runs synchronously on the caller so
// every keystroke paints without a frame of latency; the async tier
// (shortcuts, contacts and web suggestions - binder, content-provider and
// network backed) runs in a cancel-and-restart coroutine, each provider on
// its own, and re-delivers the merged list as each answers. A new query
// cancels the previous async pass; a stale pass that already finished is
// dropped by the generation check on the main dispatcher.
class SearchOrchestrator(
    private val immediateProviders: List<SearchProvider>,
    private val asyncProviders: List<SearchProvider>,
    private val scope: CoroutineScope,
    // IO: the async providers block on binder calls and, through the
    // companion app, on the network.
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    // Per-provider enable flags, keyed by SearchProvider.id; the controller
    // backs this with the search prefs.
    private val isEnabled: (String) -> Boolean = { true }
) {

    private var asyncJob: Job? = null
    private var generation = 0

    // Must be called from the main thread (the generation guard relies on
    // callbacks and searches sharing the main dispatcher).
    fun search(query: String, onResults: (List<SearchResult>) -> Unit) {
        asyncJob?.cancel()
        generation++
        val startedGeneration = generation

        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            onResults(emptyList())
            return
        }

        val immediate = collect(immediateProviders, trimmed)
        onResults(sectioned(immediate))

        val active = asyncProviders.filter { isEnabled(it.id) }
        if (active.isEmpty()) return
        // A slow provider (web suggestions) never holds back the ones that
        // answer at once. The slots keep the providers' own order, whatever
        // order they answer in.
        val answers = arrayOfNulls<List<SearchResult>>(active.size)
        asyncJob = scope.launch(mainDispatcher) {
            active.forEachIndexed { index, provider ->
                launch {
                    if (provider.debounceMillis > 0) delay(provider.debounceMillis)
                    val found = withContext(backgroundDispatcher) {
                        runLogged("search provider") { provider.search(trimmed) }.getOrDefault(emptyList())
                    }
                    if (found.isNotEmpty() && generation == startedGeneration) {
                        answers[index] = found
                        onResults(sectioned(immediate + answers.filterNotNull().flatten()))
                    }
                }
            }
        }
    }

    fun cancel() {
        asyncJob?.cancel()
        asyncJob = null
        // Orphans any async pass already past its ensureActive check.
        generation++
    }

    private fun collect(providers: List<SearchProvider>, query: String): List<SearchResult> =
        providers.filter { isEnabled(it.id) }.flatMap { provider ->
            runLogged("search provider") { provider.search(query) }.getOrDefault(emptyList())
        }

    // Stable sort groups results into display sections regardless of which
    // tier produced them, keeping each provider's own ordering within a group.
    private fun sectioned(results: List<SearchResult>): List<SearchResult> =
        results.sortedBy(::sectionRank)

    private fun sectionRank(result: SearchResult): Int = when (result) {
        is SearchResult.Calculation -> Section.CALCULATION
        is SearchResult.Action -> Section.ACTIONS
        is SearchResult.App -> Section.APPS
        is SearchResult.Card -> Section.CARDS
        is SearchResult.Folder -> Section.FOLDERS
        is SearchResult.Shortcut -> Section.SHORTCUTS
        is SearchResult.Contact -> Section.CONTACTS
        is SearchResult.Event -> Section.EVENTS
        is SearchResult.Web -> Section.WEB
        is SearchResult.Suggestion -> Section.SUGGESTIONS
        is SearchResult.ProviderChips -> Section.PROVIDERS
    }.ordinal

    // The display sections, top to bottom.
    private enum class Section {
        CALCULATION, ACTIONS, APPS, CARDS, FOLDERS, SHORTCUTS, CONTACTS, EVENTS, WEB, SUGGESTIONS, PROVIDERS
    }
}
