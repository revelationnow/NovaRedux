package com.supernova.launcher.data

import kotlin.math.max

// How far a glyph's ink reaches across its canvas, for drawing every app's
// themed glyph at one size (#332): twice its farthest reach from the middle,
// along either axis, over the canvas's side. Measured from the middle, as the
// glyph is scaled about it.
internal object GlyphSpan {

    // Null with no ink to measure.
    fun of(pixels: IntArray, size: Int): Float? {
        val centre = size / 2f
        var reach = -1f
        for (index in pixels.indices) {
            if ((pixels[index] ushr ALPHA_SHIFT) < INK_ALPHA) continue
            val x = index % size
            val y = index / size
            // The pixel's far edge, on whichever side of the middle it is.
            val across = max(centre - x, x + 1 - centre)
            val down = max(centre - y, y + 1 - centre)
            reach = max(reach, max(across, down))
        }
        return if (reach <= 0f) null else (reach * 2f / size).coerceAtMost(1f)
    }

    // The scale that brings a glyph of `span` to `target`, held to a range:
    // a glyph that measures oddly (a thin line, a single dot) isn't blown up
    // or crushed. 1 where it is near enough already.
    fun scaleFor(span: Float, target: Float): Float {
        val scale = (target / span).coerceIn(MIN_SCALE, MAX_SCALE)
        return if (kotlin.math.abs(scale - 1f) < TOLERANCE) 1f else scale
    }

    private const val ALPHA_SHIFT = 24
    // Faint edge pixels of anti-aliasing aren't ink.
    private const val INK_ALPHA = 64
    private const val MIN_SCALE = 0.6f
    private const val MAX_SCALE = 1.6f
    private const val TOLERANCE = 0.03f
}
