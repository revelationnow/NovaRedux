package com.supernova.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView

// The drawer's row of recent apps, a grid row to a page: the newest at the
// left as before, and a sideways swipe turns to the apps opened before
// those, a page at a time. It was one fixed row, which a phone tester tried
// to swipe. Each page is a FrequentAppsRowView, its cells lined up with the
// grid under it. A swipe that starts here turns the row's pages while there
// are pages that way; past them the drawer's tab swipe takes it
// (TabSwipeDrag).
class RecentAppsPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    private val pages = PageAdapter()

    init {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        adapter = pages
        // A rebind swaps the pages whole, with no fade.
        itemAnimator = null
        PagerSnapHelper().attachToRecyclerView(this)
    }

    // As FrequentAppsRowView.bindLabeled, `columns` cells to a page. A new
    // set of apps starts back at the first page; keepPage leaves the page
    // showing (the same apps, their late icons in).
    fun bindLabeled(
        entries: List<FrequentAppsRowView.Entry>,
        textColor: Int,
        columns: Int,
        look: FrequentAppsRowView.CellLook,
        keepPage: Boolean = false
    ) {
        pages.bind(entries.chunked(columns.coerceAtLeast(1)), PageLook(textColor, columns, look))
        if (!keepPage) scrollToPosition(0)
    }

    private class PageLook(val textColor: Int, val columns: Int, val cell: FrequentAppsRowView.CellLook)

    private class PageAdapter : RecyclerView.Adapter<PageAdapter.Holder>() {

        private var pages: List<List<FrequentAppsRowView.Entry>> = emptyList()
        private var look: PageLook? = null

        class Holder(val row: FrequentAppsRowView) : RecyclerView.ViewHolder(row)

        // Every page changes with the row's apps or looks.
        @SuppressLint("NotifyDataSetChanged")
        fun bind(pages: List<List<FrequentAppsRowView.Entry>>, look: PageLook) {
            this.pages = pages
            this.look = look
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(
                FrequentAppsRowView(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
            )

        override fun getItemCount(): Int = pages.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val look = look ?: return
            holder.row.bindLabeled(pages[position], look.textColor, look.columns, sidePaddingDp = 0, look = look.cell)
        }
    }
}
