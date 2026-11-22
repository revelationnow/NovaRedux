package io.github.revelationnow.novaredux.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestionCacheTest {

    private var now = 0L

    private fun cache(capacity: Int = 4, lifetime: Long = 1_000L) = SuggestionCache(capacity, lifetime) { now }

    @Test
    fun answersWhatItWasGiven() {
        val cache = cache()
        cache.put(SuggestionEngine.GOOGLE, "cat", listOf("cats"))
        assertEquals(listOf("cats"), cache.get(SuggestionEngine.GOOGLE, "cat"))
        assertNull(cache.get(SuggestionEngine.GOOGLE, "dog"))
    }

    @Test
    fun keepsEnginesApart() {
        val cache = cache()
        cache.put(SuggestionEngine.GOOGLE, "cat", listOf("cats"))
        assertNull(cache.get(SuggestionEngine.BING, "cat"))
    }

    @Test
    fun forgetsAfterItsLifetime() {
        val cache = cache(lifetime = 1_000L)
        cache.put(SuggestionEngine.GOOGLE, "cat", listOf("cats"))
        now = 1_000L
        assertEquals(listOf("cats"), cache.get(SuggestionEngine.GOOGLE, "cat"))
        now = 1_001L
        assertNull(cache.get(SuggestionEngine.GOOGLE, "cat"))
    }

    @Test
    fun dropsTheLeastRecentlyAskedWhenFull() {
        val cache = cache(capacity = 2)
        cache.put(SuggestionEngine.GOOGLE, "a", listOf("a1"))
        cache.put(SuggestionEngine.GOOGLE, "b", listOf("b1"))
        // "a" was asked for since, so "b" is the one to go.
        cache.get(SuggestionEngine.GOOGLE, "a")
        cache.put(SuggestionEngine.GOOGLE, "c", listOf("c1"))
        assertEquals(listOf("a1"), cache.get(SuggestionEngine.GOOGLE, "a"))
        assertNull(cache.get(SuggestionEngine.GOOGLE, "b"))
        assertEquals(listOf("c1"), cache.get(SuggestionEngine.GOOGLE, "c"))
    }
}
