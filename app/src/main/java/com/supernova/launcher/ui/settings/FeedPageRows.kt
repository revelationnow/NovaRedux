package com.supernova.launcher.ui.settings

import android.content.Context
import com.supernova.launcher.R
import com.supernova.launcher.data.LauncherPrefs
import com.supernova.launcher.data.Pref
import com.supernova.launcher.ui.DiscoverClient
import com.supernova.launcher.ui.DiscoverTheme
import com.supernova.launcher.ui.LauncherViewModel

// Feed > Page: NovaRedux's own feed, or another app's page in its place, as
// Nova showed Google Discover (DiscoverClient): the Google app's, through a
// bridge app such as AIDL Bridge or Lawnfeed, or a feed app's. Then that
// page's theme, and how to get Discover.
internal object FeedPageRows {

    fun of(
        context: Context,
        viewModel: LauncherViewModel,
        rows: SettingsCatalogue.Rows,
        feedHidden: (LauncherPrefs) -> SettingItem.HiddenBy?
    ): List<SettingItem> {
        val pages = listOf("") + DiscoverClient.providers(context)
        val ownPage: (LauncherPrefs) -> SettingItem.HiddenBy? = { prefs ->
            feedHidden(prefs) ?: SettingItem.HiddenBy(R.string.feed_page, SettingItem.HiddenBy.Needs.OTHER)
                .takeIf { prefs.feedProvider.isEmpty() }
        }
        return listOf(
            rows.choiceText(R.string.feed_page, pages, { it.feedProvider }, { label(context, it) }) {
                viewModel.set(Pref.feedProvider, it)
            }.hiddenWhen(feedHidden),
            rows.choice(
                R.string.discover_theme, DiscoverTheme.entries, { it.discoverTheme }, { EnumLabels.of(it) }
            ) { viewModel.set(Pref.discoverTheme, it) }.hiddenWhen(ownPage).nested(),
            SettingItem.Row(
                R.string.feed_page_discover,
                subtitle = { context.getString(R.string.feed_page_discover_summary) },
                onClick = {},
                look = SettingItem.RowLook(end = 0, tappable = false)
            ).hiddenWhen(feedHidden)
        )
    }

    // NovaRedux's own feed for "", else the app's name.
    private fun label(context: Context, page: String): CharSequence {
        if (page.isEmpty()) return context.getString(R.string.feed_page_own)
        val pm = context.packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(page, 0)) }.getOrDefault(page)
    }
}
