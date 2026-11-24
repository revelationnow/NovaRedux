package com.supernova.launcher.search

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.SystemClock
import android.os.UserHandle
import com.supernova.launcher.data.LaunchableApp

// App-shortcut results for the async tier: the shortcuts apps offer
// (dynamic and manifest) whose own name holds the query. It looked only at
// the shortcuts of the apps whose names matched, so a contact a messaging
// app offers, shown in the empty window, wasn't found by the contact's name
// (Supernova issue 324). Now any app's is, by a word of its name starting
// with the query; those of the apps the query names come first, and match
// anywhere in their names. Pinned-only shortcuts are home items, not the
// app's (AppCatalog.appShortcuts): a removed contact's pin kept showing.
// Shortcut queries are only allowed for the default home app; the
// SecurityException thrown otherwise degrades silently to no shortcut
// results.
class ShortcutSearchProvider(
    context: Context,
    private val matchedApps: (String) -> List<LaunchableApp>,
    // The apps a search may show: the shortcuts of any other (an app hidden
    // from search, say) stay out.
    private val searchableApps: () -> List<LaunchableApp>,
    private val userForSerial: (Long) -> UserHandle?
) : SearchProvider {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val densityDpi = context.resources.displayMetrics.densityDpi

    // Every app's shortcuts, asked for once in a while rather than at every
    // key typed: one call for each profile.
    private class Offered(val shortcut: ShortcutInfo, val label: String, val userSerial: Long) {
        val owner: Pair<String, Long> get() = shortcut.`package` to userSerial
    }
    private var offered: List<Offered> = emptyList()
    private var offeredAt = 0L
    private var offeredFor: Set<Long> = emptySet()

    override val id: String = ID

    override fun search(query: String): List<SearchResult> {
        val trimmed = query.trim()
        val apps = launcherApps
        if (trimmed.isEmpty() || apps == null) return emptyList()
        val named = matchedApps(trimmed).take(MAX_APPS).map { it.packageName to it.userSerial }
        val allowed = searchableApps().mapTo(HashSet()) { it.packageName to it.userSerial }
        val byName = trimmed.length >= MIN_NAME_QUERY
        val hits = offered(apps, allowed.mapTo(HashSet()) { it.second }).filter {
            it.owner in allowed && when {
                it.owner in named -> it.label.contains(trimmed, ignoreCase = true)
                else -> byName && nameRank(it.label, trimmed) != null
            }
        }
        return ranked(hits, trimmed, named, Offered::owner, Offered::label).take(MAX_RESULTS).map { hit ->
            // Icon load happens here, off the main thread in the async
            // tier, so binds only ever draw an already-resolved drawable.
            SearchResult.Shortcut(
                packageName = hit.shortcut.`package`,
                shortcutId = hit.shortcut.id,
                label = hit.label,
                userSerial = hit.userSerial,
                icon = runCatching { apps.getShortcutBadgedIconDrawable(hit.shortcut, densityDpi) }.getOrNull()
            )
        }
    }

    @Synchronized
    private fun offered(apps: LauncherApps, serials: Set<Long>): List<Offered> {
        val now = SystemClock.elapsedRealtime()
        if (offeredAt != 0L && serials == offeredFor && now - offeredAt < FRESH_MS) return offered
        val query = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
        )
        offered = serials.flatMap { serial ->
            val shortcuts = userForSerial(serial)?.let { user ->
                runCatching { apps.getShortcuts(query, user) }.getOrNull()
            }
            shortcuts.orEmpty().mapNotNull { shortcut ->
                (shortcut.shortLabel ?: shortcut.longLabel)?.toString()?.takeIf { it.isNotEmpty() }
                    ?.let { label -> Offered(shortcut, label, serial) }
            }
        }
        offeredAt = now
        offeredFor = serials
        return offered
    }

    companion object {
        const val ID = "shortcuts"
        private const val MAX_APPS = 3
        private const val MAX_RESULTS = 6

        // Another app's shortcut is found by its name from two letters on.
        private const val MIN_NAME_QUERY = 2

        // How long the shortcuts asked for are the ones searched: a window
        // open, and some typing in it.
        private const val FRESH_MS = 30_000L

        // A shortcut found by its own name: 0 when the label starts with the
        // query, 1 when one of its words does, null otherwise. Mid-word hits
        // are left to the named apps' own shortcuts, or every "a" would match.
        internal fun nameRank(label: String, query: String): Int? {
            val words = label.split(' ', '-', '_', '.', ',', '(', '/')
            return when {
                label.isEmpty() || query.isEmpty() -> null
                label.startsWith(query, ignoreCase = true) -> 0
                words.any { it.startsWith(query, ignoreCase = true) } -> 1
                else -> null
            }
        }

        // `hits` best first: the shortcuts of the `named` apps, in the order
        // the apps were found, then any other app's, those whose name starts
        // with the query ahead of those that hold it further in.
        internal fun <T> ranked(
            hits: List<T>,
            query: String,
            named: List<Pair<String, Long>>,
            owner: (T) -> Pair<String, Long>,
            label: (T) -> String
        ): List<T> = hits.sortedWith(
            compareBy<T>(
                { hit -> named.indexOf(owner(hit)).takeIf { it >= 0 } ?: named.size },
                { hit -> if (label(hit).startsWith(query, ignoreCase = true)) 0 else 1 }
            )
        )
    }
}
