package com.supernova.launcher.weather

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.util.zip.GZIPInputStream

// Breezy Weather (org.breezyweather, LGPL-3.0, on F-Droid) shares its weather
// through a content provider since 6.1, behind a permission the user grants
// NovaRedux. This reads the first location in Breezy's list, as its own
// widgets lead with. Nothing here goes online: Breezy fetches, NovaRedux reads.
object BreezyWeather {

    const val PACKAGE = "org.breezyweather"
    const val PERMISSION = "$PACKAGE.READ_PROVIDER"
    // Sent when Breezy has refreshed a location.
    const val UPDATED = "$PACKAGE.ACTION_UPDATE_NOTIFIER"
    const val STORE_PAGE = "https://f-droid.org/packages/$PACKAGE/"

    private const val AUTHORITY = "$PACKAGE.provider.weather"
    private const val ID = "id"
    private const val WEATHER = "weather"

    fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    fun permitted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun openIntent(context: Context): Intent =
        context.packageManager.getLaunchIntentForPackage(PACKAGE)
            ?: Intent(Intent.ACTION_VIEW, STORE_PAGE.toUri())

    // Blocking: call off the main thread. Null without Breezy, without the
    // permission, with no location set up, or on anything unexpected.
    fun read(context: Context): WeatherNow? = runCatching {
        if (!permitted(context)) return@runCatching null
        val resolver = context.contentResolver
        val locations = uri("locations").buildUpon().appendQueryParameter("limit", "1").build()
        val id = resolver.query(locations, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(ID)) else null
        } ?: return@runCatching null
        val weather = uri(WEATHER).buildUpon()
            .appendQueryParameter("withDaily", "false")
            .appendQueryParameter("withMinutely", "false")
            .appendQueryParameter("withAlerts", "false")
            .appendQueryParameter("withNormals", "false")
            .build()
        val blob = resolver.query(weather, null, "$ID=$id", null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getBlob(cursor.getColumnIndexOrThrow(WEATHER)) else null
        }
        val text = blob?.let { GZIPInputStream(it.inputStream()).bufferedReader().use { reader -> reader.readText() } }
        text?.let(BreezyWeatherJson::parse)
    }.getOrNull()

    private fun uri(path: String): Uri = "content://$AUTHORITY/$path".toUri()
}
