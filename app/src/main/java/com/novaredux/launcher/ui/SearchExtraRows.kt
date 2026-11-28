package com.novaredux.launcher.ui

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.RelativeSizeSpan
import com.novaredux.launcher.R
import com.novaredux.launcher.search.SearchResult

// The text of search rows Nova never had: a calendar event's title over its
// day, time and place (#7).
internal object SearchExtraRows {

    private const val DETAIL_SIZE = 0.85f

    fun eventText(context: Context, event: SearchResult.Event): CharSequence {
        val dates = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
        // An all-day event's times are midnights in UTC.
        val flags = dates or if (event.allDay) DateUtils.FORMAT_UTC else DateUtils.FORMAT_SHOW_TIME
        val detail = listOfNotNull(DateUtils.formatDateRange(context, event.begin, event.end, flags), event.location)
            .joinToString(" · ")
        return SpannableStringBuilder(event.title.ifBlank { context.getString(R.string.event_untitled) })
            .append('\n')
            .append(detail, RelativeSizeSpan(DETAIL_SIZE), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
