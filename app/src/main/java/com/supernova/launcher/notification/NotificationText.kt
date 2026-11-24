package com.supernova.launcher.notification

// The line a home screen label shows for its app's newest notification
// (#15): who and what ("Anna: See you at 7"), or whichever of the two it
// has, on one line and kept short. The label ellipsizes what doesn't fit.
object NotificationText {

    private const val MAX_CHARS = 80
    private val SPACES = Regex("\\s+")

    fun line(title: CharSequence?, text: CharSequence?): String? {
        val who = title?.toString()?.replace(SPACES, " ")?.trim().orEmpty()
        val what = text?.toString()?.replace(SPACES, " ")?.trim().orEmpty()
        val line = when {
            who.isEmpty() -> what
            what.isEmpty() -> who
            else -> "$who: $what"
        }
        return clipped(line).ifEmpty { null }
    }

    // At most MAX_CHARS, never cutting a character drawn with two (an emoji).
    private fun clipped(line: String): String {
        if (line.length <= MAX_CHARS) return line
        val end = if (line[MAX_CHARS - 1].isHighSurrogate()) MAX_CHARS - 1 else MAX_CHARS
        return line.substring(0, end)
    }
}
