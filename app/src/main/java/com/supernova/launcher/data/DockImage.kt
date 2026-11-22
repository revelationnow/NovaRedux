package com.supernova.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max

// The dock's background image (dockBgImage), as Nova let one be picked from
// the gallery or an icon pack: kept in the settings as an inline WebP, so a
// backup carries it, no wider than a screen and well under the size a
// settings value can comfortably be.
object DockImage {
    private const val MAX_WIDTH = 1440
    private const val MAX_HEIGHT = 720
    private const val QUALITY = 85
    // A picture with no size of its own: a strip as wide as the widest kept.
    private const val FALLBACK_HEIGHT = 360

    // The picked image, scaled down and encoded; null when it can't be read.
    fun encode(context: Context, uri: Uri): String? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = max(info.size.width / MAX_WIDTH.toFloat(), info.size.height / MAX_HEIGHT.toFloat())
            if (scale > 1f) decoder.setTargetSize((info.size.width / scale).toInt(), (info.size.height / scale).toInt())
            // A bitmap in software memory, so it can be compressed.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(webp(), QUALITY, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    // A picture from an icon pack or a dock theme, drawn at its own size
    // (within the same bounds) and encoded as a picked one is.
    fun encode(drawable: Drawable): String? = runCatching {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: MAX_WIDTH
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: FALLBACK_HEIGHT
        val scale = max(1f, max(width / MAX_WIDTH.toFloat(), height / MAX_HEIGHT.toFloat()))
        val bitmap = drawable.toBitmap((width / scale).toInt(), (height / scale).toInt())
        val out = ByteArrayOutputStream()
        bitmap.compress(webp(), QUALITY, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun webp(): Bitmap.CompressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        Bitmap.CompressFormat.WEBP
    }

    // Decoded once per image: the dock asks again on every restyle.
    private var decodedFrom: String? = null
    private var decoded: Bitmap? = null

    @Synchronized
    fun decode(encoded: String): Bitmap? {
        if (encoded.isEmpty()) return null
        if (encoded != decodedFrom) {
            decoded = runCatching {
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
            decodedFrom = encoded
        }
        return decoded
    }
}
