package com.supernova.launcher.ui

import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.format.DateUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isNotEmpty
import androidx.recyclerview.widget.RecyclerView
import com.supernova.launcher.R
import com.supernova.launcher.databinding.ItemFeedAddBinding
import com.supernova.launcher.databinding.ItemFeedCalendarBinding
import com.supernova.launcher.databinding.ItemFeedContactsBinding
import com.supernova.launcher.databinding.ItemFeedCustomBinding
import com.supernova.launcher.databinding.ItemFeedEmptyBinding
import com.supernova.launcher.databinding.ItemFeedMediaBinding
import com.supernova.launcher.databinding.ItemFeedScreenTimeBinding
import com.supernova.launcher.feed.CalendarCardSource
import com.supernova.launcher.feed.ContactsCardSource
import com.supernova.launcher.feed.FeedCard
import com.supernova.launcher.feed.FeedOrder
import com.supernova.launcher.feed.MediaCardSource
import com.supernova.launcher.feed.ScreenTimeCardSource
import com.supernova.launcher.runLogged

// Renders the feed pane's card list. One view type per card kind; the list is
// tiny (a handful of cards), so submits rebuild wholesale and the pane's
// itemAnimator is null - no diffing, no cross-fades, no churn.
class FeedAdapter(
    private val callbacks: Callbacks,
    // Custom cards render through the shared binder (feed, drawer and search
    // draw the same card); its callbacks carry the launch and menu routes.
    private val cardBinder: CardViewBinder
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class Callbacks(
        val onGrantCalendar: () -> Unit,
        val onGrantContacts: () -> Unit,
        val onOpenEvent: (Long) -> Unit,
        val onOpenContact: (ContactsCardSource.Starred, View) -> Unit,
        // The footer's Add card (the add sheet) and Feed settings; the empty
        // page's two kinds of card, and a ready-made card turned on by its
        // order key.
        val onAddCard: () -> Unit,
        val onFeedSettings: () -> Unit,
        val onAddWidget: () -> Unit,
        val onAddShortcuts: () -> Unit,
        val onShowBuiltIn: (String) -> Unit,
        // The screen time card's ask (#17): Settings' usage access page.
        val onGrantUsageAccess: () -> Unit
    )

    // key and group place a card in the user's order (FeedOrder); the
    // footer and the empty page have neither and never move.
    sealed class Row(val key: String?, val group: FeedOrder.Group?) {
        class Media(val state: MediaCardSource.MediaState) : Row(FeedOrder.MEDIA, FeedOrder.Group.REST)
        class CalendarRow(val state: CalendarCardSource.CalendarState) : Row(FeedOrder.CALENDAR, FeedOrder.Group.REST)
        class Contacts(val state: ContactsCardSource.ContactsState) : Row(FeedOrder.CONTACTS, FeedOrder.Group.REST)
        // Today's screen time (#17); apps are the ones it names, as the
        // launcher draws them.
        class ScreenTime(
            val state: ScreenTimeCardSource.ScreenTimeState,
            val apps: List<ShownApp>
        ) : Row(FeedOrder.SCREEN_TIME, FeedOrder.Group.REST)
        class Custom(val card: FeedCard, cardGroup: FeedOrder.Group) : Row(card.id, cardGroup)
        object Footer : Row(null, null)
        // No card at all: the page explains itself. readyMade holds the
        // order keys of the built-in cards that are turned off.
        class Empty(val readyMade: List<String>) : Row(null, null)
    }

    // An app the screen time card names: its name and icon, and its time.
    class ShownApp(val label: String, val icon: Drawable?, val timeMs: Long)

    private val rows = ArrayList<Row>()

    // The rows as shown, in order: what a move up or down is measured
    // against.
    val rowsShown: List<Row> get() = rows

    // A card dragged past a neighbour: the two change places on screen.
    // The order is stored when the card is let go.
    fun moveRow(from: Int, to: Int) {
        if (from !in rows.indices || to !in rows.indices || from == to) return
        rows.add(to, rows.removeAt(from))
        notifyItemMoved(from, to)
    }

    // Resolved style ints, applied at bind time (binds are rare: feed-open
    // and source emissions only).
    private var cardColor: Int = 0xF0202124.toInt()
    private var textColor: Int = Color.WHITE
    private var accentColor: Int = Color.WHITE
    // Add card sits on the pane's dim, not on a card.
    private var paneTextColor: Int = Color.WHITE

    // Shown rows are bound again, so a color picked while the pane stays
    // open (Settings over it) reaches the cards already on screen.
    @Suppress("NotifyDataSetChanged")
    fun setAppearance(card: Int, text: Int, accent: Int, paneText: Int) {
        val changed = listOf(card, text, accent, paneText) != listOf(cardColor, textColor, accentColor, paneTextColor)
        cardColor = card
        textColor = text
        accentColor = accent
        paneTextColor = paneText
        cardBinder.setAppearance(card, text, accent)
        if (changed && rows.isNotEmpty()) notifyDataSetChanged()
    }

    @Suppress("NotifyDataSetChanged")
    fun submit(newRows: List<Row>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Media -> TYPE_MEDIA
        is Row.CalendarRow -> TYPE_CALENDAR
        is Row.Contacts -> TYPE_CONTACTS
        is Row.ScreenTime -> TYPE_SCREEN_TIME
        is Row.Custom -> TYPE_CUSTOM
        Row.Footer -> TYPE_ADD
        is Row.Empty -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        density = parent.resources.displayMetrics.density
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_MEDIA -> MediaHolder(ItemFeedMediaBinding.inflate(inflater, parent, false))
            TYPE_CALENDAR -> CalendarHolder(ItemFeedCalendarBinding.inflate(inflater, parent, false))
            TYPE_CONTACTS -> ContactsHolder(ItemFeedContactsBinding.inflate(inflater, parent, false))
            TYPE_SCREEN_TIME -> ScreenTimeHolder(ItemFeedScreenTimeBinding.inflate(inflater, parent, false))
            TYPE_CUSTOM -> CustomHolder(ItemFeedCustomBinding.inflate(inflater, parent, false))
            TYPE_EMPTY -> EmptyHolder(ItemFeedEmptyBinding.inflate(inflater, parent, false))
            else -> AddHolder(ItemFeedAddBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Media -> (holder as MediaHolder).bind(row.state)
            is Row.CalendarRow -> (holder as CalendarHolder).bind(row.state)
            is Row.Contacts -> (holder as ContactsHolder).bind(row.state)
            is Row.ScreenTime -> (holder as ScreenTimeHolder).bind(row)
            is Row.Custom -> (holder as CustomHolder).bind(row.card)
            Row.Footer -> (holder as AddHolder).bind()
            is Row.Empty -> (holder as EmptyHolder).bind(row.readyMade)
        }
    }

    private fun tintCard(view: View) {
        (view.background?.mutate() as? GradientDrawable)?.setColor(cardColor)
    }

    private inner class MediaHolder(private val binding: ItemFeedMediaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(state: MediaCardSource.MediaState) {
            tintCard(binding.root)
            binding.mediaTitle.setTextColor(textColor)
            binding.mediaArtist.setTextColor(textColor)
            binding.mediaTitle.text = state.title
            binding.mediaArtist.text = state.artist
            if (state.artwork != null) {
                binding.mediaArt.setImageBitmap(state.artwork)
                binding.mediaArt.visibility = View.VISIBLE
            } else {
                binding.mediaArt.setImageDrawable(null)
                binding.mediaArt.visibility = View.GONE
            }
            binding.mediaPlayPause.setImageResource(
                if (state.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
            )
            val controls = state.controller.transportControls
            binding.mediaPlayPause.setOnClickListener {
                runLogged("media play or pause") { if (state.isPlaying) controls.pause() else controls.play() }
            }
            binding.mediaNext.setOnClickListener { runLogged("media next") { controls.skipToNext() } }
            binding.mediaPrev.setOnClickListener { runLogged("media previous") { controls.skipToPrevious() } }
        }
    }

    private inner class CalendarHolder(private val binding: ItemFeedCalendarBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(state: CalendarCardSource.CalendarState) {
            tintCard(binding.root)
            binding.calendarHeader.setTextColor(accentColor)
            binding.calendarEvents.removeAllViews()
            when (state) {
                CalendarCardSource.CalendarState.NeedsPermission -> {
                    binding.calendarPermissionText.visibility = View.VISIBLE
                    binding.calendarPermissionText.setTextColor(textColor)
                    binding.calendarGrantButton.visibility = View.VISIBLE
                    binding.calendarGrantButton.setOnClickListener { callbacks.onGrantCalendar() }
                }
                is CalendarCardSource.CalendarState.Events -> {
                    binding.calendarPermissionText.visibility = View.GONE
                    binding.calendarGrantButton.visibility = View.GONE
                    val context = binding.root.context
                    state.events.forEach { event ->
                        val row = FadingTextView(context).apply {
                            text = buildString {
                                append(dayLabel(event.beginMs))
                                if (!event.allDay) {
                                    append("  ")
                                    append(
                                        DateUtils.formatDateTime(
                                            context, event.beginMs, DateUtils.FORMAT_SHOW_TIME
                                        )
                                    )
                                }
                                append("  ")
                                append(event.title)
                            }
                            setTextColor(textColor)
                            textSize = 14f
                            maxLines = 1
                            setPadding(0, dp(6), 0, dp(6))
                            setOnClickListener { callbacks.onOpenEvent(event.eventId) }
                        }
                        binding.calendarEvents.addView(row)
                    }
                }
            }
        }

        // Today / Tomorrow / weekday, like the spec's event labels.
        private fun dayLabel(timeMs: Long): String {
            val context = binding.root.context
            return when {
                DateUtils.isToday(timeMs) -> context.getString(R.string.today)
                DateUtils.isToday(timeMs - DateUtils.DAY_IN_MILLIS) ->
                    context.getString(R.string.tomorrow)
                else -> DateUtils.formatDateTime(
                    context, timeMs,
                    DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
                )
            }
        }
    }

    private inner class ContactsHolder(private val binding: ItemFeedContactsBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(state: ContactsCardSource.ContactsState) {
            tintCard(binding.root)
            binding.contactsHeader.setTextColor(accentColor)
            binding.contactsRow.removeAllViews()
            when (state) {
                ContactsCardSource.ContactsState.NeedsPermission -> {
                    binding.contactsPermissionText.visibility = View.VISIBLE
                    binding.contactsPermissionText.setTextColor(textColor)
                    binding.contactsGrantButton.visibility = View.VISIBLE
                    binding.contactsGrantButton.setOnClickListener { callbacks.onGrantContacts() }
                }
                is ContactsCardSource.ContactsState.Contacts -> {
                    binding.contactsPermissionText.visibility = View.GONE
                    binding.contactsGrantButton.visibility = View.GONE
                    val context = binding.root.context
                    val size = dp(48)
                    state.contacts.forEach { contact ->
                        val tile: View = if (contact.photo != null) {
                            ImageView(context).apply {
                                setImageBitmap(contact.photo)
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                outlineProvider = CircleOutline
                                clipToOutline = true
                            }
                        } else {
                            TextView(context).apply {
                                text = contact.displayName.take(1).uppercase()
                                gravity = Gravity.CENTER
                                textSizeOf(R.dimen.text_title)
                                setTextColor(textColor)
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.OVAL
                                    setColor((0x33 shl 24) or (accentColor and 0xFFFFFF))
                                }
                            }
                        }
                        tile.contentDescription = contact.displayName
                        tile.setOnClickListener { callbacks.onOpenContact(contact, it) }
                        binding.contactsRow.addView(
                            tile,
                            LinearLayout.LayoutParams(size, size).apply { rightMargin = dp(10) }
                        )
                    }
                }
            }
        }
    }

    private inner class ScreenTimeHolder(private val binding: ItemFeedScreenTimeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: Row.ScreenTime) {
            tintCard(binding.root)
            binding.screenTimeHeader.setTextColor(accentColor)
            binding.screenTimeApps.removeAllViews()
            val today = row.state as? ScreenTimeCardSource.ScreenTimeState.Today
            val asking = today == null
            binding.screenTimePermissionText.visibility = if (asking) View.VISIBLE else View.GONE
            binding.screenTimeGrantButton.visibility = if (asking) View.VISIBLE else View.GONE
            binding.screenTimeTotal.visibility = if (asking) View.GONE else View.VISIBLE
            binding.screenTimeUnlocks.visibility = if (asking) View.GONE else View.VISIBLE
            binding.screenTimePermissionText.setTextColor(textColor)
            binding.screenTimeGrantButton.setOnClickListener { callbacks.onGrantUsageAccess() }
            today ?: return
            val context = binding.root.context
            binding.screenTimeTotal.setTextColor(textColor)
            binding.screenTimeTotal.text =
                context.getString(R.string.feed_screen_time_total, ScreenTimeText.duration(context, today.totalMs))
            binding.screenTimeUnlocks.setTextColor(textColor)
            binding.screenTimeUnlocks.text =
                context.resources.getQuantityString(R.plurals.feed_screen_time_unlocks, today.unlocks, today.unlocks)
            row.apps.forEach { binding.screenTimeApps.addView(appRow(it)) }
        }

        // An app's icon, name and time on one line.
        private fun appRow(app: ShownApp): View {
            val context = binding.root.context
            val size = dp(APP_ICON_DP)
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(APP_ROW_PADDING_DP), 0, dp(APP_ROW_PADDING_DP))
                addView(ImageView(context).apply { setImageDrawable(app.icon) }, LinearLayout.LayoutParams(size, size))
                addView(
                    TextView(context).apply {
                        text = app.label
                        setTextColor(textColor)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPaddingRelative(dp(APP_ROW_PADDING_DP * 2), 0, dp(APP_ROW_PADDING_DP), 0)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                addView(
                    TextView(context).apply {
                        text = ScreenTimeText.duration(context, app.timeMs)
                        setTextColor(textColor)
                    }
                )
            }
        }
    }

    // A custom card's own menu (its ⋮'s), for a card put down unmoved;
    // false for any other row, whose menu the feed builds itself.
    fun openCardMenu(holder: RecyclerView.ViewHolder): Boolean {
        val custom = holder as? CustomHolder
        val card = custom?.card
        return card != null && cardBinder.openMenu(custom.binding, card)
    }

    private inner class CustomHolder(val binding: ItemFeedCustomBinding) :
        RecyclerView.ViewHolder(binding.root) {

        var card: FeedCard? = null
            private set

        fun bind(card: FeedCard) {
            this.card = card
            cardBinder.bind(binding, card)
        }
    }

    private inner class AddHolder(private val binding: ItemFeedAddBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            // Same fill as the action pills, so the button reads as one of them.
            (binding.feedAddCard.background?.mutate() as? GradientDrawable)
                ?.setColor((0x29 shl 24) or (paneTextColor and 0xFFFFFF))
            binding.feedAddCard.setTextColor(paneTextColor)
            binding.feedAddCard.setOnClickListener { callbacks.onAddCard() }
            binding.feedSettings.setTextColor(paneTextColor)
            binding.feedSettings.setOnClickListener { callbacks.onFeedSettings() }
        }
    }

    private inner class EmptyHolder(private val binding: ItemFeedEmptyBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(readyMade: List<String>) {
            val muted = (0xD9 shl 24) or (paneTextColor and 0xFFFFFF)
            binding.feedEmptyTitle.setTextColor(paneTextColor)
            binding.feedEmptyText.setTextColor(muted)
            binding.feedEmptyReadyLabel.setTextColor(muted)
            binding.feedEmptySettings.setTextColor(paneTextColor)
            binding.feedEmptySettings.setOnClickListener { callbacks.onFeedSettings() }
            // The two choices sit on card plates, in the cards' colours.
            for (choice in listOf(binding.feedEmptyWidget, binding.feedEmptyShortcuts)) tintCard(choice)
            binding.feedEmptyWidgetTitle.setTextColor(textColor)
            binding.feedEmptyShortcutsTitle.setTextColor(textColor)
            val cardMuted = (0xB3 shl 24) or (textColor and 0xFFFFFF)
            binding.feedEmptyWidgetText.setTextColor(cardMuted)
            binding.feedEmptyShortcutsText.setTextColor(cardMuted)
            binding.feedEmptyWidget.setOnClickListener { callbacks.onAddWidget() }
            binding.feedEmptyShortcuts.setOnClickListener { callbacks.onAddShortcuts() }
            val flow = binding.feedEmptyReady
            val context = binding.root.context
            flow.removeAllViews()
            readyMade.forEach { key ->
                val label = BUILT_IN_LABELS[key] ?: return@forEach
                flow.addView(
                    CardViewBinder.pill(context, null, context.getString(label), paneTextColor) {
                        callbacks.onShowBuiltIn(key)
                    }
                )
            }
            val any = flow.isNotEmpty()
            binding.feedEmptyReadyLabel.visibility = if (any) View.VISIBLE else View.GONE
            flow.visibility = if (any) View.VISIBLE else View.GONE
        }
    }

    private object CircleOutline : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }

    private var density = 1f

    private fun dp(value: Int): Int = (value * density).toInt()

    companion object {
        private const val TYPE_MEDIA = 0
        private const val TYPE_CALENDAR = 1
        private const val TYPE_CONTACTS = 2
        private const val TYPE_CUSTOM = 3
        private const val TYPE_ADD = 4
        private const val TYPE_EMPTY = 5
        private const val TYPE_SCREEN_TIME = 6

        // The screen time card's app lines.
        private const val APP_ICON_DP = 24
        private const val APP_ROW_PADDING_DP = 6

        // The ready-made cards as the empty page names them.
        val BUILT_IN_LABELS = mapOf(
            FeedOrder.MEDIA to R.string.cards_media_title,
            FeedOrder.CALENDAR to R.string.feed_calendar_title,
            FeedOrder.CONTACTS to R.string.feed_contacts_title,
            FeedOrder.SCREEN_TIME to R.string.feed_screen_time_title
        )
    }
}
