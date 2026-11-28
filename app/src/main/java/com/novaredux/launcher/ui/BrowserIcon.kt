package com.novaredux.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.net.toUri

// The icon of the app a web link opens in: the default browser, which is
// where the search bar's web searches go. Null with no default chosen (the
// system's chooser would answer) or nothing installed to open links.
// The drawable is kept per package, so a bar restyled with the same browser
// gets the same object back and needn't redraw.
object BrowserIcon {

    private var cached: Pair<String, Drawable>? = null

    fun of(context: Context): Drawable? {
        val pm = context.packageManager
        val info = runCatching {
            pm.resolveActivity(Intent(Intent.ACTION_VIEW, PROBE.toUri()), PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()?.activityInfo?.takeIf { it.packageName != SYSTEM_CHOOSER } ?: return null
        val known = cached?.takeIf { it.first == info.packageName }?.second
        return known ?: runCatching { info.loadIcon(pm) }.getOrNull()?.also { cached = info.packageName to it }
    }

    private const val PROBE = "https://example.com"
    private const val SYSTEM_CHOOSER = "android"
}
