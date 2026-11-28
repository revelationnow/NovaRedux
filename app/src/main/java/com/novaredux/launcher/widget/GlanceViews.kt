package com.novaredux.launcher.widget

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Instances
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import com.novaredux.launcher.R
import com.novaredux.launcher.search.EventRanking
import com.novaredux.launcher.search.SearchResult
import com.novaredux.launcher.weather.BreezyWeather

// What the glance widget shows (#13), built fresh each time it is drawn.
internal object GlanceViews {

    private const val HOUR_MS = 60L * 60 * 1000
    private const val EVENTS_AHEAD_MS = 36 * HOUR_MS
    private const val ALARMS_AHEAD_MS = 24 * HOUR_MS
    private const val SCAN_LIMIT = 20
    private const val CODE_DATE = 1
    private const val CODE_EVENT = 2
    private const val CODE_WEATHER = 3
    private const val COL_ID = 0
    private const val COL_TITLE = 1
    private const val COL_BEGIN = 2
    private const val COL_END = 3
    private const val COL_ALL_DAY = 4

    fun build(context: Context): RemoteViews {
        val now = System.currentTimeMillis()
        val views = RemoteViews(context.packageName, R.layout.widget_glance)
        val date = DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
        views.setTextViewText(R.id.glanceDate, DateUtils.formatDateTime(context, now, date))
        views.setOnClickPendingIntent(R.id.glanceDate, open(context, CODE_DATE, calendarAt(now)))

        val event = nextEvent(context, now)
        show(views, R.id.glanceEvent, event?.let { eventText(context, it, now) })
        event?.let { views.setOnClickPendingIntent(R.id.glanceEvent, open(context, CODE_EVENT, eventIntent(it))) }

        val alarm = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock
            ?.takeIf { it.triggerTime - now in 0..ALARMS_AHEAD_MS }
        val alarmTime = alarm?.let { DateUtils.formatDateTime(context, it.triggerTime, DateUtils.FORMAT_SHOW_TIME) }
        show(views, R.id.glanceAlarm, alarmTime?.let { context.getString(R.string.glance_alarm, it) })
        alarm?.showIntent?.let { views.setOnClickPendingIntent(R.id.glanceAlarm, it) }

        val weather = BreezyWeather.read(context)
        show(views, R.id.glanceWeather, weather?.text())
        if (weather != null) {
            val breezy = open(context, CODE_WEATHER, BreezyWeather.openIntent(context))
            views.setOnClickPendingIntent(R.id.glanceWeather, breezy)
        }
        return views
    }

    private fun show(views: RemoteViews, id: Int, text: CharSequence?) {
        views.setViewVisibility(id, if (text == null) View.GONE else View.VISIBLE)
        views.setTextViewText(id, text ?: "")
    }

    private fun open(context: Context, code: Int, intent: Intent): PendingIntent = PendingIntent.getActivity(
        context, code, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun calendarAt(time: Long): Intent = Intent(
        Intent.ACTION_VIEW,
        CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also { ContentUris.appendId(it, time) }.build()
    )

    private fun eventIntent(event: SearchResult.Event): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)

    // The event under way or next, within a day and a half.
    private fun nextEvent(context: Context, now: Long): SearchResult.Event? {
        val calendar = context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
        if (calendar != PackageManager.PERMISSION_GRANTED) return null
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, now + EVENTS_AHEAD_MS)
        }.build()
        val found = runCatching {
            context.contentResolver.query(uri, PROJECTION, null, null, "${Instances.BEGIN} ASC")?.use { rows ->
                buildList {
                    while (size < SCAN_LIMIT && rows.moveToNext()) {
                        add(
                            SearchResult.Event(
                                rows.getLong(COL_ID), rows.getString(COL_TITLE).orEmpty(), rows.getLong(COL_BEGIN),
                                rows.getLong(COL_END), rows.getInt(COL_ALL_DAY) != 0, null, null
                            )
                        )
                    }
                }
            }
        }.getOrNull().orEmpty()
        return EventRanking.nearest(found, now, 1).firstOrNull()?.takeIf { it.end >= now }
    }

    // "Dentist · 15:00", or with the day when it isn't today.
    private fun eventText(context: Context, event: SearchResult.Event, now: Long): String {
        val today = DateUtils.isToday(event.begin) || event.begin <= now
        val day = if (today) 0 else DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
        val flags = if (event.allDay) {
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_UTC
        } else {
            day or DateUtils.FORMAT_SHOW_TIME
        }
        val title = event.title.ifBlank { context.getString(R.string.event_untitled) }
        return "$title · ${DateUtils.formatDateTime(context, event.begin, flags)}"
    }

    private val PROJECTION = arrayOf(
        Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY
    )
}
