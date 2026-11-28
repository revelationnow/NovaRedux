package com.novaredux.launcher.ui

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import com.novaredux.launcher.runLogged
import java.io.File

// A font file the user picked for icon labels (#6), kept in NovaRedux's own
// storage. The label_font_file pref holds its name and when it came in, so a
// new file under the same name still redraws every label; the typeface is
// read once per stamp. Restored on a phone without the file, labels fall back
// to the default font.
object FontFiles {

    // What the system file picker offers. Many file managers call a font
    // file plain binary, so that is offered too and the file checked after.
    val pickerTypes = arrayOf(
        "font/*", "application/font-sfnt", "application/x-font-ttf", "application/x-font-otf",
        "application/vnd.ms-opentype", "application/octet-stream"
    )

    private const val DIR = "fonts"
    private const val LABEL = "label"
    private const val MAX_BYTES = 32L * 1024 * 1024
    private const val STAMP_MARK = '|'

    @Volatile
    private var loaded: Pair<String, Typeface?>? = null

    fun stamp(name: String, at: Long): String = "$name$STAMP_MARK$at"

    // The file's name, as settings show it.
    fun nameOf(stamp: String): String = stamp.substringBeforeLast(STAMP_MARK)

    // Copies the picked file in if Android reads it as a font: its name, or
    // null when it isn't one (or is too big to be a sane label font).
    fun importLabelFont(context: Context, uri: Uri): String? {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val incoming = File(dir, "$LABEL.new")
        val copied = runLogged("copy a font file") {
            context.contentResolver.openInputStream(uri)?.use { input ->
                incoming.outputStream().use { input.copyTo(it) }
            }
        }.getOrNull() != null
        val font = copied && incoming.length() in 1..MAX_BYTES && readable(incoming)
        if (!font || !incoming.renameTo(File(dir, LABEL))) {
            incoming.delete()
            return null
        }
        loaded = null
        return displayName(context, uri)
    }

    // The label typeface for this stamp, or null without a usable file.
    fun labelTypeface(context: Context, stamp: String): Typeface? {
        loaded?.takeIf { it.first == stamp }?.let { return it.second }
        val file = File(File(context.filesDir, DIR), LABEL)
        val face = if (stamp.isNotEmpty() && file.isFile) {
            runCatching { Typeface.Builder(file).build() }.getOrNull()
        } else {
            null
        }
        loaded = stamp to face
        return face
    }

    private fun readable(file: File): Boolean = runCatching { Typeface.Builder(file).build() }.getOrNull() != null

    private fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { row ->
                if (row.moveToFirst()) row.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: LABEL
}
