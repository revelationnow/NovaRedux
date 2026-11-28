package com.novaredux.launcher.ui

import android.content.Context
import android.content.Intent

// A stored intent made safe to start from NovaRedux (#21, from a security
// review). It may be an item's, a swipe's or a gesture's: picked from the
// Activities list, made by another app's shortcut screen, or brought in with
// a backup from anywhere, so it is treated as anyone's:
// - no URI grant flags, and no clip data, which can carry them too: started
//   from NovaRedux, a grant would hand whatever it names NovaRedux's own
//   files (its file provider's);
// - never one of NovaRedux's own screens that other apps can't open, which
//   an intent started from inside NovaRedux could otherwise reach.
object SafeIntents {

    private const val GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

    // The intent, scrubbed in place, or null where it would open one of
    // NovaRedux's unexported screens.
    fun forLaunch(context: Context, intent: Intent): Intent? {
        scrub(intent)
        intent.selector?.let(::scrub)
        return intent.takeUnless { opensOwnHidden(context, it) }
    }

    private fun scrub(intent: Intent) {
        intent.flags = intent.flags and GRANTS.inv()
        intent.clipData = null
    }

    // A selector, where there is one, is what Android resolves.
    private fun opensOwnHidden(context: Context, intent: Intent): Boolean {
        val target = intent.selector ?: intent
        val packages = context.packageManager
        val activity = target.component?.let { runCatching { packages.getActivityInfo(it, 0) }.getOrNull() }
            ?: packages.resolveActivity(target, 0)?.activityInfo
        return activity != null && activity.packageName == context.packageName && !activity.exported
    }
}
