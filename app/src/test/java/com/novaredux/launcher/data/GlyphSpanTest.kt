package com.novaredux.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Every app's themed glyph drawn at one size (#332): how far a glyph's ink
// reaches, and the scale that brings it to the size wanted.
class GlyphSpanTest {

    private val size = 20
    private val ink = 0xFF000000.toInt()

    // A square of ink `side` pixels across, its top left corner at `x`, `y`.
    private fun square(side: Int, x: Int = (size - side) / 2, y: Int = (size - side) / 2) =
        IntArray(size * size) { index ->
            val px = index % size
            val py = index / size
            if (px in x until x + side && py in y until y + side) ink else 0
        }

    @Test
    fun aCentredGlyphSpansItsOwnWidth() {
        assertEquals(0.5f, GlyphSpan.of(square(10), size)!!, 0.001f)
        assertEquals(1f, GlyphSpan.of(square(20), size)!!, 0.001f)
    }

    @Test
    fun anOffCentreGlyphSpansTwiceItsFarthestReach() {
        // Ink from 12 to 17 reaches 8 past the middle (10): a span of 16/20.
        assertEquals(0.8f, GlyphSpan.of(square(6, x = 12, y = 9), size)!!, 0.001f)
    }

    @Test
    fun faintPixelsAndNoInkAreNotMeasured() {
        assertNull(GlyphSpan.of(IntArray(size * size), size))
        val faint = square(20).map { if (it == ink) 0x20000000 else it }.toIntArray()
        assertNull(GlyphSpan.of(faint, size))
    }

    @Test
    fun theScaleIsHeldToItsRangeAndLeftAloneWhenClose() {
        assertEquals(0.8f, GlyphSpan.scaleFor(0.5f, 0.4f), 0.001f)
        assertEquals(1.6f, GlyphSpan.scaleFor(0.1f, 0.4f), 0.001f)
        assertEquals(0.6f, GlyphSpan.scaleFor(1f, 0.4f), 0.001f)
        assertEquals(1f, GlyphSpan.scaleFor(0.41f, 0.4f), 0.001f)
    }
}
