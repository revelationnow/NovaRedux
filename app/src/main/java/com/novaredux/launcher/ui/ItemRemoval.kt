package com.novaredux.launcher.ui

import android.view.View
import androidx.core.view.isVisible
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.novaredux.launcher.R
import com.novaredux.launcher.data.ContainerType
import com.novaredux.launcher.data.FolderMove
import com.novaredux.launcher.data.ItemRemovalModel
import com.novaredux.launcher.data.ItemType
import com.novaredux.launcher.data.LabelOrder
import com.novaredux.launcher.data.LauncherItem
import com.novaredux.launcher.data.LauncherModel

// Taking things off the home screen, each with its Undo bar: an item, a
// page and everything on it, a folder's order (Sort) and a folder moved to
// the drawer; and the empty pages left behind, which go once nothing could
// still bring something back to them.
class ItemRemoval(private val surfaces: ItemSurfaces, private val host: Host) {
    interface Host {
        val editModeOpen: Boolean
        val previewsShowing: Boolean
        // Edit mode's "+" asked for a page the model doesn't carry yet.
        val addingPage: Boolean

        // The pager jumped to a page showing what it already showed: no
        // scroll event tells the wallpaper or edit mode.
        fun onPageJumped(page: Int)
    }

    private val viewModel get() = surfaces.viewModel
    private val binding get() = surfaces.binding
    private val widgets get() = surfaces.widgets

    // Pages just removed from under the pager: once the adapter has the new
    // count, the pager goes to `page`, where what was in view now is.
    private class PageAfterRemoval(val pageCount: Int, val page: Int)
    private var pendingPage: PageAfterRemoval? = null

    // A removal's Undo is waiting: empty pages stay until it goes, so the
    // item it would put back still has its page to go back to.
    private var undoBar: Snackbar? = null

    // A widget's host id is kept while the Undo bar shows, so Undo brings the
    // same widget back; it is deleted once the bar goes without an Undo.
    // `window` is a view in the window the bar shows in (null: the
    // activity's), as an open folder covers the activity's; `onUndo` is
    // handed the model from before the Undo, to redraw that folder.
    fun remove(item: LauncherItem, window: View? = null, onUndo: ((LauncherModel) -> Unit)? = null) {
        val widget = item.itemType == ItemType.WIDGET
        if (widget) widgets.onWidgetItemRemoved(item)
        // Capture before removal: the exact item plus folder members, so undo
        // restores the same ids into the same cells (or the nearest free).
        val members = if (item.itemType == ItemType.FOLDER) viewModel.folderMembers(item.id) else emptyList()
        // An app whose folder gives way as it goes (one app left in it):
        // Undo brings back the folder, every app in it, as it was.
        val folder = ItemRemovalModel.givingWayFolder(viewModel.state.value.model, item)
        viewModel.removeItem(item.id)
        if (!viewModel.state.value.prefs.undoRemove) {
            if (widget) widgets.releaseWidget(item)
            removeEmptyPages(emptied = item.desktopPage())
            return
        }
        val bar = Snackbar.make(window ?: binding.root, R.string.item_removed, UNDO_DURATION_MS)
            .setAction(R.string.undo) {
                val before = viewModel.state.value.model
                if (folder != null) viewModel.replaceItems(folder) else viewModel.restoreItems(listOf(item) + members)
                onUndo?.invoke(before)
            }
        overDock(bar, window)
        bar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                // Before the check below: a bar replaced by the next removal's
                // is final too.
                if (widget && event != DISMISS_EVENT_ACTION) widgets.releaseWidget(item)
                if (undoBar !== transientBottomBar) return
                undoBar = null
                if (event != DISMISS_EVENT_ACTION) removeEmptyPages(emptied = item.desktopPage())
            }
        })
        undoBar = bar
        bar.show()
    }

    // A folder's Sort rewrites its stored order, so the order it had stays an
    // Undo away (#136). `window` is a view in the window the bar shows in
    // (null: the activity's); `refresh` is handed the model from before each
    // change, to redraw an open folder once that change is saved.
    fun sortFolder(folderId: String, window: View? = null, refresh: ((LauncherModel) -> Unit)? = null) {
        val members = viewModel.folderMembers(folderId)
        val manual = members.map { it.id }
        val model = viewModel.state.value.model
        viewModel.sortFolder(folderId)
        refresh?.invoke(model)
        // Already A-Z: nothing to take back.
        if (LabelOrder.sorted(members) { it.label }.map { it.id } == manual) return
        val bar = Snackbar.make(window ?: binding.root, R.string.folder_sorted, UNDO_DURATION_MS)
            .setAction(R.string.undo) {
                val sorted = viewModel.state.value.model
                viewModel.reorderFolder(folderId, manual)
                refresh?.invoke(sorted)
            }
        overDock(bar, window)
        bar.show()
    }

    // A page goes with everything on it (#215). Its widgets keep their host
    // ids while the Undo bar shows, so Undo brings the same widgets back;
    // they go once the bar ends without one. `onRemoved` runs once the page
    // has gone, `onUndo` once Undo is tapped.
    fun removePage(page: Int, onUndo: () -> Unit = {}, onRemoved: () -> Unit = {}) {
        val count = surfaces.workspaceAdapter.realPageCount
        viewModel.removePageWithItems(page) { removed ->
            onRemoved()
            removed.widgets.forEach(widgets::onWidgetItemRemoved)
            if (!viewModel.state.value.prefs.undoRemove) {
                removed.widgets.forEach(widgets::releaseWidget)
                return@removePageWithItems
            }
            val bar = Snackbar.make(binding.root, R.string.page_removed, UNDO_DURATION_MS)
                .setAction(R.string.undo) {
                    viewModel.restorePage(removed)
                    followRemovedPages(count, removed.page)
                    onUndo()
                }
            // Over the Desktop previews scrim, so Undo isn't a tap on the scrim.
            if (host.previewsShowing) bar.view.elevation = surfaces.activity.dp(OVER_PREVIEWS_ELEVATION_DP)
            // In edit mode, over the peeking Widgets sheet rather than under it.
            when {
                host.editModeOpen -> bar.anchorView = binding.editWidgetSheet
                binding.dockBar.isVisible -> bar.anchorView = binding.dockBar
            }
            bar.addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event != DISMISS_EVENT_ACTION) removed.widgets.forEach(widgets::releaseWidget)
                    if (undoBar === transientBottomBar) undoBar = null
                }
            })
            undoBar = bar
            bar.show()
        }
    }

    // Says what stayed on the home screen, and offers the move back.
    fun showFolderMoved(move: FolderMove) {
        val left = move.leftBehind
        val text = if (left == 0) {
            surfaces.activity.getString(R.string.moved_to_drawer)
        } else {
            surfaces.activity.resources.getQuantityString(R.plurals.moved_to_drawer_left_behind, left, left)
        }
        val bar = Snackbar.make(binding.root, text, UNDO_DURATION_MS)
            .setAction(R.string.undo) { viewModel.undoFolderMove(move) }
        overDock(bar, null)
        val page = move.items.first().desktopPage()
        bar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                if (undoBar !== transientBottomBar) return
                undoBar = null
                if (event != DISMISS_EVENT_ACTION) removeEmptyPages(emptied = page)
            }
        })
        undoBar = bar
        bar.show()
    }

    // Remove empty pages: every page left with nothing on it goes, but the
    // home page and the one in view - so a page being filled (edit mode's
    // "+", a widget's setup screen) is never taken from under it. Runs when
    // a drag ends, when the pager comes to rest and when an Undo times out;
    // not while anything is still moving items or pages about.
    //
    // emptied: a page the user just emptied (its last item removed or
    // dragged away). That one goes even while in view - it is not a page
    // being filled - and the pager steps back onto the page before it.
    fun removeEmptyPages(emptied: Int? = null) {
        if (!emptyPagesRemovable()) return
        val adapter = surfaces.workspaceAdapter
        val inView = adapter.realPage(binding.workspacePager.currentItem)
        val count = adapter.realPageCount
        val keep = if (emptied == inView) emptySet() else setOf(inView)
        viewModel.removeEmptyPages(keep) { removed ->
            val remaining = count - removed.size
            val shifted = inView - removed.count { it < inView }
            val page = if (inView in removed) shifted - 1 else shifted
            followRemovedPages(remaining, page.coerceIn(0, remaining - 1))
        }
    }

    // Pages before the one in view went, so it is further left now. The
    // adapter may have the new count already (then the pager moves at once)
    // or not yet (then the emission bringing it does; see onPageCountChanged).
    fun followRemovedPages(pageCount: Int, page: Int) {
        if (surfaces.workspaceAdapter.realPageCount == pageCount) {
            showPage(page)
        } else {
            pendingPage = PageAfterRemoval(pageCount, page)
        }
    }

    // The model's pages changed: a removal waiting for the adapter's new
    // count follows it now.
    fun onPageCountChanged() {
        val pending = pendingPage ?: return
        pendingPage = null
        if (surfaces.workspaceAdapter.realPageCount == pending.pageCount) showPage(pending.page)
    }

    private fun emptyPagesRemovable(): Boolean {
        val drag = surfaces.drag
        val pager = binding.workspacePager
        val adapter = surfaces.workspaceAdapter
        val busy = drag.isArmed || drag.isActive || drag.settlingItemId != null ||
            host.editModeOpen || host.previewsShowing || host.addingPage || undoBar != null
        return viewModel.state.value.prefs.removeEmptyPages && !busy &&
            pager.scrollState == ViewPager2.SCROLL_STATE_IDLE &&
            adapter.realPageCount > 1 && !adapter.isAddPage(pager.currentItem)
    }

    // No scroll event tells the indicator and the wallpaper when the pager
    // jumps to a page showing what it already showed.
    private fun showPage(page: Int) {
        binding.workspacePager.setCurrentItem(surfaces.workspaceAdapter.centerPosition(page), false)
        viewModel.setCurrentPage(page)
        binding.pageIndicator.onPageScrolled(page, 0f)
        host.onPageJumped(page)
    }

    // The bar rides above the dock in the activity's own window.
    private fun overDock(bar: Snackbar, window: View?) {
        if (window == null && binding.dockBar.isVisible) bar.anchorView = binding.dockBar
    }

    private companion object {
        // Undo snackbar lifetime after a removal.
        const val UNDO_DURATION_MS = 4000
        // Above the Desktop previews scrim (24dp).
        const val OVER_PREVIEWS_ELEVATION_DP = 26f
    }
}

private fun LauncherItem.desktopPage(): Int? = page.takeIf { container == ContainerType.WORKSPACE }
