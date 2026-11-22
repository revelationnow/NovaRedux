package com.supernova.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.supernova.launcher.R
import com.supernova.launcher.runLogged
import com.supernova.launcher.weather.BreezyWeather
import com.supernova.launcher.weather.WeatherCondition
import com.supernova.launcher.weather.WeatherNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The home bar's weather button, Nova's weather in the search bar: Breezy
// Weather's current conditions, read when home comes to the front and when
// Breezy says it has refreshed. A tap opens Breezy; before the permission is
// given it asks for it, and with no Breezy installed it opens Breezy's page.
class WeatherChip(
    private val activity: ComponentActivity,
    private val bar: SearchBarView,
    // Whether the home bar shows the button at all; nothing is read if not.
    private val wanted: () -> Boolean
) : DefaultLifecycleObserver {

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (registered) return
        ContextCompat.registerReceiver(
            activity, receiver, IntentFilter(BreezyWeather.UPDATED), ContextCompat.RECEIVER_EXPORTED
        )
        registered = true
    }

    override fun onResume(owner: LifecycleOwner) = refresh()

    override fun onStop(owner: LifecycleOwner) {
        if (!registered) return
        runCatching { activity.unregisterReceiver(receiver) }
        registered = false
    }

    fun refresh() {
        if (!wanted()) return
        activity.lifecycleScope.launch {
            val now = withContext(Dispatchers.IO) { BreezyWeather.read(activity) }
            bar.showWeather(now)
        }
    }

    // For SearchBarView.onChip: true when it took the tap.
    fun onChip(chip: SearchBarChip): Boolean {
        if (chip != SearchBarChip.WEATHER) return false
        when {
            !BreezyWeather.installed(activity) || BreezyWeather.permitted(activity) ->
                runLogged("open weather") { activity.startActivity(BreezyWeather.openIntent(activity)) }
            else -> ActivityCompat.requestPermissions(activity, arrayOf(BreezyWeather.PERMISSION), PERMISSION_REQUEST)
        }
        return true
    }

    private companion object {
        const val PERMISSION_REQUEST = 0x57
    }
}

// How the weather button looks: the condition's icon before the
// temperature, both in the bar's colour; a dash until there is a reading.
object WeatherLook {

    private const val PLACEHOLDER = "–"
    private const val ICON_GAP_DP = 4

    fun dress(view: TextView, now: WeatherNow?, iconColor: Int) {
        view.text = now?.text() ?: PLACEHOLDER
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(now), 0, 0, 0)
        view.compoundDrawablePadding = view.dp(ICON_GAP_DP)
        TextViewCompat.setCompoundDrawableTintList(view, ColorStateList.valueOf(iconColor))
    }

    private fun icon(now: WeatherNow?): Int {
        val day = now?.daylight != false
        return when (now?.condition) {
            null, WeatherCondition.CLOUDY -> R.drawable.ic_weather_cloudy
            WeatherCondition.CLEAR -> if (day) R.drawable.ic_weather_clear_day else R.drawable.ic_weather_clear_night
            WeatherCondition.PARTLY_CLOUDY ->
                if (day) R.drawable.ic_weather_partly_cloudy_day else R.drawable.ic_weather_partly_cloudy_night
            WeatherCondition.RAIN -> R.drawable.ic_weather_rain
            WeatherCondition.SNOW -> R.drawable.ic_weather_snow
            WeatherCondition.THUNDER -> R.drawable.ic_weather_thunder
            WeatherCondition.FOG -> R.drawable.ic_weather_fog
            WeatherCondition.WIND -> R.drawable.ic_weather_wind
        }
    }
}
