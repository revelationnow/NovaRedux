package com.novaredux.launcher.widget

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

// At a glance (#13): NovaRedux's own widget, with the date, the next
// calendar event, the next alarm and the weather, each opening its app.
// It needs nothing installed: the event shows once the calendar permission
// the feed's card asks for is given, and the weather with Breezy Weather
// and its permission. It is drawn again as the launcher comes to the front
// or the day changes, when the next alarm changes, and every half hour.
class GlanceWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // The weather read blocks; a receiver may run a little longer this way.
        val pending = goAsync()
        thread(name = "glance") {
            try {
                manager.updateAppWidget(ids, GlanceViews.build(context))
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) {
            val pending = goAsync()
            thread(name = "glance") {
                try {
                    updateAll(context)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        // Blocking (the weather): off the main thread. Costs nothing with
        // no glance widget placed.
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GlanceWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, GlanceViews.build(context))
        }
    }
}
