package io.github.revelationnow.novaredux.connect

// The last answers, in memory for a few minutes: typing a word and then
// taking letters back would otherwise ask again for what was just answered.
// Nothing is written anywhere; it goes with the app's process.
class SuggestionCache(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val lifetimeMillis: Long = DEFAULT_LIFETIME_MS,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI }
) {
    private class Entry(val suggestions: List<String>, val at: Long)

    // In access order, so the one asked for longest ago comes first.
    private val entries = LinkedHashMap<String, Entry>(capacity, LOAD_FACTOR, true)

    @Synchronized
    fun get(engine: SuggestionEngine, query: String): List<String>? {
        val key = key(engine, query)
        val entry = entries[key]?.takeIf { clock() - it.at <= lifetimeMillis }
        if (entry == null) entries.remove(key)
        return entry?.suggestions
    }

    @Synchronized
    fun put(engine: SuggestionEngine, query: String, suggestions: List<String>) {
        entries[key(engine, query)] = Entry(suggestions, clock())
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }

    private fun key(engine: SuggestionEngine, query: String) = "${engine.name}\n$query"

    private companion object {
        const val DEFAULT_CAPACITY = 64
        const val DEFAULT_LIFETIME_MS = 5 * 60 * 1000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val LOAD_FACTOR = 0.75f
    }
}
