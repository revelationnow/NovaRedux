package com.supernova.launcher.data

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.withScale

// Draws an icon's themed look in the plate and glyph colours: its shipped
// monochrome layer, or one generated from its picture (see ThemedGlyph).
// `normalize` draws a shipped layer's glyph at one size (#332).
internal class ThemedIconPainter(
    private val resources: Resources,
    private val plate: Int,
    private val glyph: Int,
    private val normalize: Boolean = false
) {

    // Any icon's themed look: its monochrome layer in the themed colours
    // when it ships one, else the glyph `pick` reads off its picture (see
    // ThemedGlyph); null when `pick` finds none.
    fun themedLayer(icon: Drawable, pick: (IntArray, Int, Int) -> IntArray?): Drawable? {
        val mono = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            (icon as? AdaptiveIconDrawable)?.monochrome
        } else {
            null
        }
        val fg = if (mono != null) {
            (mono.constantState?.newDrawable()?.mutate() ?: mono).apply { setTint(glyph) }
                .let { if (normalize) evened(it) else it }
        } else {
            generatedGlyph(icon, pick) ?: return null
        }
        return AdaptiveIconDrawable(ThemedPlate(plate), fg)
    }

    // A shipped glyph at the size every other one is drawn at: its ink
    // measured off a reading of it, then the glyph, still the app's own
    // drawing, scaled about the middle to span GLYPH_SPAN of the canvas.
    private fun evened(layer: Drawable): Drawable {
        val span = GlyphSpan.of(layersPixels(listOf(layer), inset = 0), GLYPH_PX) ?: return layer
        val scale = GlyphSpan.scaleFor(span, GLYPH_SPAN)
        return if (scale == 1f) layer else ScaledGlyph(layer, scale)
    }

    // Lets `pick` choose a glyph's alpha off the icon's foreground (see
    // iconPixels) and draws it in the themed glyph colour.
    private fun generatedGlyph(icon: Drawable, pick: (IntArray, Int, Int) -> IntArray?): Drawable? {
        val alpha = pick(iconPixels(icon, withBackground = false), GLYPH_PX, GLYPH_PX) ?: return null
        val rgb = glyph and RGB_BITS
        return pixelsDrawable(IntArray(alpha.size) { (alpha[it] shl ALPHA_SHIFT) or rgb }, glyphScale(icon))
    }

    // An icon with no glyph to read, its whole picture in the themed
    // colours on the themed plate (see ThemedGlyph.tinted). A background of
    // one plain colour (RAR's white) is left out: the plate stands in for it.
    fun tintedCopy(icon: Drawable): Drawable {
        val back = (icon as? AdaptiveIconDrawable)?.background
        val plainBack = back != null && ThemedGlyph.isPlain(layersPixels(listOf(back), inset = 0))
        val pixels = ThemedGlyph.tinted(iconPixels(icon, withBackground = !plainBack), plate, glyph)
        return AdaptiveIconDrawable(
            ThemedPlate(plate),
            pixelsDrawable(pixels, glyphScale(icon))
        )
    }

    // The icon's picture on the adaptive icon's full canvas: its foreground
    // (with its background too, for `withBackground`), or the whole legacy
    // icon, inset toward the safe zone so the mask doesn't crop it.
    private fun iconPixels(icon: Drawable, withBackground: Boolean): IntArray {
        val adaptive = icon as? AdaptiveIconDrawable
            ?: return layersPixels(listOf(icon), inset = (GLYPH_PX * LEGACY_INSET).toInt())
        val layers = listOfNotNull(adaptive.background.takeIf { withBackground }, adaptive.foreground)
        return layersPixels(layers, inset = 0)
    }

    private fun layersPixels(layers: List<Drawable>, inset: Int): IntArray {
        val size = GLYPH_PX
        val picture = createBitmap(size, size)
        val canvas = Canvas(picture)
        layers.forEach {
            it.setBounds(inset, inset, size - inset, size - inset)
            it.draw(canvas)
        }
        val pixels = IntArray(size * size).also { picture.getPixels(it, 0, size, 0, 0, size, size) }
        picture.recycle()
        return pixels
    }

    // A legacy icon is read at the size its tile and ink are judged at, then
    // drawn smaller: read off, it spans nine tenths of the system shape and
    // lost its corners to it. Drawn at this scale a square one clears a round
    // mask and sits near the size of a shipped monochrome glyph.
    private fun glyphScale(icon: Drawable): Float =
        if (icon is AdaptiveIconDrawable) 1f else LEGACY_GLYPH_SCALE

    private fun pixelsDrawable(pixels: IntArray, scale: Float): Drawable {
        val read = Bitmap.createBitmap(pixels, GLYPH_PX, GLYPH_PX, Bitmap.Config.ARGB_8888)
        if (scale == 1f) return read.toDrawable(resources)
        val drawn = createBitmap(GLYPH_PX, GLYPH_PX)
        val inset = GLYPH_PX * (1f - scale) / 2f
        Canvas(drawn).drawBitmap(
            read, null, RectF(inset, inset, GLYPH_PX - inset, GLYPH_PX - inset),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        read.recycle()
        return drawn.toDrawable(resources)
    }

    private companion object {
        // An ARGB int's alpha byte, and its colour without it.
        const val ALPHA_SHIFT = 24
        const val RGB_BITS = 0xFFFFFF

        // Generated themed icons: the canvas they're read off and drawn at,
        // how far a legacy icon sits in from its edges when read, and how
        // much smaller it is then drawn.
        const val GLYPH_PX = 192
        const val LEGACY_INSET = 0.2f
        const val LEGACY_GLYPH_SCALE = 0.8f

        // The share of the canvas a normalized glyph spans: 43dp of the
        // adaptive icon's 108, about three fifths of the 72 that show.
        const val GLYPH_SPAN = 0.4f
    }
}

// A glyph drawn `scale` times its size about its middle; the adaptive icon
// sizes it, and it draws on within that.
private class ScaledGlyph(private val glyph: Drawable, private val scale: Float) : Drawable() {

    override fun onBoundsChange(bounds: Rect) {
        glyph.bounds = bounds
    }

    override fun draw(canvas: Canvas) {
        canvas.withScale(scale, scale, bounds.exactCenterX(), bounds.exactCenterY()) { glyph.draw(this) }
    }

    override fun setAlpha(alpha: Int) {
        glyph.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        glyph.colorFilter = colorFilter
    }

    override fun setTintList(tint: ColorStateList?) {
        glyph.setTintList(tint)
    }

    @Deprecated("Deprecated in Drawable", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getConstantState(): ConstantState? = glyph.constantState?.let { State(it, scale) }

    private class State(private val glyph: ConstantState, private val scale: Float) : ConstantState() {
        override fun newDrawable(): Drawable = ScaledGlyph(glyph.newDrawable().mutate(), scale)

        override fun getChangingConfigurations(): Int = glyph.changingConfigurations
    }
}
