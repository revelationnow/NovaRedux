package com.supernova.launcher.search

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Instances

// Calendar events whose title or place holds the query (#7). Read from the
// phone's calendar provider, so whatever calendar app syncs there is
// searched, offline. It needs the calendar permission the feed's calendar
// card asks for, and reads nothing without it.
class CalendarSearchProvider(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis
) : SearchProvider {

    override val id: String = ID

    override fun search(query: String): List<SearchResult> {
        val trimmed = query.trim()
        val found = if (trimmed.length < MIN_QUERY || !granted()) emptyList() else instances(trimmed)
        return EventRanking.nearest(found, now(), MAX_RESULTS)
    }

    private fun granted(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun instances(query: String): List<SearchResult.Event> {
        val from = now() - PAST_MS
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, from + PAST_MS + AHEAD_MS)
        }.build()
        val like = "%" + EventRanking.escapeLike(query) + "%"
        return context.contentResolver.query(
            uri, PROJECTION, SELECTION, arrayOf(like, like), "${Instances.BEGIN} ASC"
        )?.use { rows ->
            buildList {
                while (size < SCAN_LIMIT && rows.moveToNext()) {
                    add(
                        SearchResult.Event(
                            eventId = rows.getLong(COL_ID),
                            title = rows.getString(COL_TITLE).orEmpty(),
                            begin = rows.getLong(COL_BEGIN),
                            end = rows.getLong(COL_END),
                            allDay = rows.getInt(COL_ALL_DAY) != 0,
                            location = rows.getString(COL_PLACE)?.takeIf { it.isNotBlank() },
                            color = if (rows.isNull(COL_COLOR)) null else rows.getInt(COL_COLOR)
                        )
                    )
                }
            }
        }.orEmpty()
    }

    companion object {
        const val ID = "calendar"

        // One letter matches nearly every event.
        private const val MIN_QUERY = 2
        private const val MAX_RESULTS = 4

        // Occurrences read at most; a daily event fills a year with them.
        private const val SCAN_LIMIT = 500
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val PAST_MS = 30 * DAY_MS
        private const val AHEAD_MS = 365 * DAY_MS

        private val PROJECTION = arrayOf(
            Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END,
            Instances.ALL_DAY, Instances.EVENT_LOCATION, Instances.DISPLAY_COLOR
        )
        private const val COL_ID = 0
        private const val COL_TITLE = 1
        private const val COL_BEGIN = 2
        private const val COL_END = 3
        private const val COL_ALL_DAY = 4
        private const val COL_PLACE = 5
        private const val COL_COLOR = 6
        private const val SELECTION =
            "${Instances.TITLE} LIKE ? ESCAPE '\\' OR ${Instances.EVENT_LOCATION} LIKE ? ESCAPE '\\'"
    }
}
