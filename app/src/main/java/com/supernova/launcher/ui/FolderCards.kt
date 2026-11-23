package com.supernova.launcher.ui

import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.lifecycle.lifecycleScope
import com.supernova.launcher.R
import com.supernova.launcher.data.DrawerGroup
import com.supernova.launcher.data.GroupPick
import com.supernova.launcher.data.ItemType
import com.supernova.launcher.data.LaunchableApp
import com.supernova.launcher.data.LauncherItem
import com.supernova.launcher.data.LauncherModel
import com.supernova.launcher.ui.theme.Styles
import com.supernova.launcher.ui.theme.SurfaceId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// A folder's card: a home folder's, whose members are home items, and a
// drawer folder's, fed from its apps through transient items - nothing there
// touches the model. One card is open at a time.
class FolderCards(private val surfaces: ItemSurfaces, private val host: Host) {
    interface Host {
        val folderPopup: FolderPopup

        // A card closed by the press the system's home swipe began with:
        // that swipe's home press counts it.
        val cardSwipeClose: CardSwipeClose

        // The dock's top on screen, which a card over the home screen stays
        // above; null with the dock hidden or the drawer over it.
        fun dockTopOnScreen(): Int?

        fun animateWallpaperZoom(target: Float)
    }

    // The open folder card, if any. A PopupWindow outlives a home press on
    // its own, so onNewIntent has to close it like every other overlay.
    var openCard: PopupWindow? = null

    // The open folder's members, so a badge moving redraws them too.
    var openMembers: FolderMemberAdapter? = null

    private val viewModel get() = surfaces.viewModel
    private val drag get() = surfaces.drag

    fun showFolder(anchor: View, folder: LauncherItem) = HomeCard(folder).open(anchor)

    fun showDrawerFolder(anchor: View, group: DrawerGroup, members: List<LaunchableApp>) =
        DrawerCard(group, anchor).open(members)

    // What sets the two cards apart.
    private class CardSpec(
        val title: String,
        val members: List<LauncherItem>,
        val adapter: FolderMemberAdapter,
        val onRename: (String) -> Unit,
        val onMenu: (View) -> Unit,
        val onDismiss: () -> Unit
    )

    // The members' grid, sized and labelled as the folder settings say, with
    // the badges the home screen draws.
    private fun memberAdapter(
        onClick: (LauncherItem) -> Unit,
        onLongPress: (View, LauncherItem) -> Unit
    ): FolderMemberAdapter {
        val prefs = viewModel.state.value.prefs
        val adapter = FolderMemberAdapter(
            appCatalog = viewModel.appCatalog(),
            showLabels = prefs.folderLabels,
            labelPrefs = prefs,
            iconScale = if (prefs.folderMatchDesktop) prefs.iconScale else prefs.folderIconScale,
            onClick = onClick,
            onLongPress = onLongPress
        )
        adapter.decorateIcon = { icon, member ->
            surfaces.itemIcons.withBadge(icon, member.packageName, member.className, member.userSerial)
        }
        return adapter
    }

    // The card, in the look the folder settings and the style give it.
    private fun showCard(anchor: View, spec: CardSpec, keepAboveScreenY: Int?): PopupWindow {
        val prefs = viewModel.state.value.prefs
        val surface = Styles.resolve(surfaces.activity, prefs).of(SurfaceId.FOLDER)
        return host.folderPopup.show(
            anchor = anchor,
            title = spec.title,
            members = spec.members,
            adapter = spec.adapter,
            columnsOverride = prefs.folderColumns,
            mode = prefs.folderMode,
            scroll = prefs.folderScroll,
            surfaceColor = surface.surface,
            textColor = surface.text,
            cornerRadiiDp = surface.cornerRadiiDp,
            showTitle = prefs.folderShowName,
            showCount = prefs.folderShowCount,
            shadow = prefs.folderShadow,
            animate = Animations.enabled(prefs),
            speed = Animations.factor(prefs),
            keepAboveScreenY = keepAboveScreenY,
            circle = prefs.folderAnimation == FolderAnimation.CIRCLE,
            alignBottom = prefs.folderAlignBottom,
            onRename = spec.onRename,
            onMenu = spec.onMenu,
            onDismiss = spec.onDismiss
        )
    }

    // A home folder's card. An app in it is a home icon like any other: its
    // menu while the finger is held, and a drag once it moves - to another
    // place in the folder, or out past the card's edge onto the home screen
    // or the dock. The card is its own window, so the gesture's touches come
    // in through its interceptor, not the activity's dispatchTouchEvent.
    private inner class HomeCard(private val folder: LauncherItem) {
        private var popup: PopupWindow? = null
        private lateinit var adapter: FolderMemberAdapter

        // Moving an app inside the card, before it is taken out past the
        // card's edge (FolderReorderDrag).
        private var reorder: FolderReorderDrag? = null

        // A member's long-press menu, closed with the folder: Back, Home, the
        // screen going off and rotation all close the folder, and the menu
        // used to float on over the home screen, its rows still aimed at the
        // closed folder.
        private var memberMenu: SupernovaPopupMenu? = null

        fun open(anchor: View) {
            adapter = memberAdapter(::launchMember, ::hold)
            adapter.shortcutIcon = { member ->
                member.takeIf { it.itemType == ItemType.SHORTCUT }?.let(surfaces.itemIcons::intentItemIcon)
            }
            // An app's swipes work in the open folder too, as on the home
            // screen.
            adapter.attachSwipe = { tile, member -> surfaces.iconSwipes.attach(tile, member, ::closeOnLaunch) }
            host.animateWallpaperZoom(1f)
            val spec = CardSpec(
                title = folder.label,
                members = viewModel.folderMembers(folder.id),
                adapter = adapter,
                onRename = { viewModel.renameFolder(folder.id, it) },
                onMenu = ::showTitleMenu,
                onDismiss = ::closed
            )
            val card = showCard(anchor, spec, host.dockTopOnScreen())
            popup = card
            // Every touch on the card goes through the item gesture first: an
            // app held there, the drag it becomes, and the press that closes
            // its menu once modal, swallowed so it doesn't also launch the app
            // it lands on. A modal menu leaves outside taps to its host (see
            // makeModal), and the card's window is the host here: the
            // activity never sees taps in a folder. Taps pass through to the
            // card's own views (false unless a drag takes the stream), which
            // accessibility services click as usual.
            host.cardSwipeClose.onCardShown()
            //noinspection ClickableViewAccessibility
            card.setTouchInterceptor { _, ev ->
                host.cardSwipeClose.onTouch(ev.actionMasked)
                drag.routeGesture(ev, card)
            }
            reorder = (card.contentView as? ViewGroup)?.let { content ->
                FolderReorderDrag(content, adapter, { viewModel.state.value.prefs }) { order ->
                    viewModel.reorderFolder(folder.id, order)
                }
            }
            openCard = card
            openMembers = adapter
        }

        private fun launchMember(member: LauncherItem) {
            closeOnLaunch()
            surfaces.launchItem(member)
        }

        private fun closeOnLaunch() {
            if (viewModel.state.value.prefs.folderCloseOnLaunch) popup?.dismiss()
        }

        private fun hold(memberView: View, member: LauncherItem) {
            memberMenu?.dismiss()
            drag.arm(memberView, DragPayload.ExistingItem(member.id), window = popup) {
                surfaces.workspaceMenus.folderMember(folder, member, memberView, { popup?.dismiss() }, ::applied)
                    .also {
                        it.show(memberView, surfaces.menuTouchX)
                        memberMenu = it
                    }
            }
            reorder?.let(drag::armWindowDrag)
        }

        // The folder shows its members again once a change is saved - waited
        // for, not guessed at with a delay.
        private fun applied(before: LauncherModel) {
            surfaces.activity.lifecycleScope.launch {
                withTimeoutOrNull(SAVE_WAIT_MS) { viewModel.state.first { it.model !== before } }
                val remaining = viewModel.folderMembers(folder.id)
                if (remaining.isEmpty()) popup?.dismiss() else adapter.submit(remaining)
            }
        }

        // The title row's overflow: Select apps and Sort, as the folder's
        // long-press has them. Opened by a tap, so modal at once, and the
        // card's touch interceptor closes it on a tap outside it.
        private fun showTitleMenu(button: View) {
            memberMenu?.dismiss()
            // Every row changes the layout, as the long-press menu's locked
            // state has none of them.
            if (DesktopLock.refused(surfaces.activity, viewModel)) return
            val activity = surfaces.activity
            val menu = surfaces.menuKit.popup()
            menu.addRow(activity.getString(R.string.select_apps), R.drawable.ic_menu_select_apps) {
                // The folder closes first: as a focusable window it stays the
                // IME target even under the dialog, so the checklist's rename
                // field could never summon the keyboard while it was up.
                popup?.dismiss()
                surfaces.pickers.folderApps(folder)
            }
            menu.addRow(activity.getString(R.string.sort), R.drawable.ic_menu_sort) {
                // The folder's window covers the activity's, so its Undo bar
                // shows in the folder's own window.
                surfaces.removal.sortFolder(folder.id, popup?.contentView, ::applied)
            }
            surfaces.moveToDrawer.addRow(menu, folder) { popup?.dismiss() }
            drag.activeItemPopup = menu
            menu.show(button)
            menu.makeModal()
            memberMenu = menu
        }

        private fun closed() {
            // A shortcut being dragged out of a member's menu closes the card
            // too, and that close is the drag's as well.
            val menuCarried = memberMenu?.let { drag.ownsGestureIn(it.window) } == true
            host.cardSwipeClose.onCardClosed(
                SystemClock.uptimeMillis(), carried = popup?.let(drag::ownsGestureIn) == true || menuCarried
            )
            // That menu carries the rest of the gesture, and closes with its
            // drop.
            if (!menuCarried) memberMenu?.dismiss()
            memberMenu = null
            // Closed under a held app or its drag (a home press, Back, a
            // rotation): the rest of that gesture would only ever have come
            // through this card, so it ends here.
            drag.onWindowDismissed(popup)
            openCard = null
            openMembers = null
            host.animateWallpaperZoom(0f)
        }
    }

    // A drawer folder's card: its apps, and the drawer folders inside it
    // (#229) as folder tiles. An app in it is a drawer icon like any other:
    // its menu, and a drag to the home screen once the finger moves.
    private inner class DrawerCard(private val group: DrawerGroup, private val anchor: View) {
        private var popup: PopupWindow? = null

        // An app's menu open over the card, if any: it closes with the card.
        private var memberMenu: SupernovaPopupMenu? = null

        // The group's name as of now: a rename typed into the title is saved
        // as the popup closes, which Edit does before opening the checklist.
        private var groupName = group.name

        // The folders inside, by the id of the tile that stands for each.
        private val held = HashMap<String, DrawerGroup>()
        private val heldIcons = HashMap<String, Drawable>()

        fun open(members: List<LaunchableApp>) {
            val adapter = memberAdapter(::tap, ::hold)
            adapter.shortcutIcon = { member ->
                held[member.id]?.let { child ->
                    heldIcons.getOrPut(member.id) {
                        surfaces.itemIcons.drawerFolderIcon(child, viewModel.drawerFolderApps(child), nested = false)
                    }
                }
            }
            val spec = CardSpec(
                title = group.name,
                members = items(members),
                adapter = adapter,
                onRename = { groupName = viewModel.renameGroup(group, it) },
                // The group editor rewrites the members pref; the drawer
                // rebuilds from the emission, so the stale card closes. The
                // editor is titled with the name the group has now: a rename
                // in the title may not be stored yet.
                onMenu = {
                    popup?.dismiss()
                    surfaces.pickers.groupApps(group, groupName)
                },
                onDismiss = ::closed
            )
            val card = showCard(anchor, spec, keepAboveScreenY = null)
            popup = card
            // A windowed folder greys the drawer out behind it, as Nova's did.
            surfaces.drawer.greyBehindFolder(true)
            // Every touch on the card goes through the item gesture first: an
            // app held there, the drag it becomes, and the press that closes
            // its menu once modal (without also launching the app it lands
            // on). Accessibility services click the card's views directly,
            // past this.
            //noinspection ClickableViewAccessibility
            card.setTouchInterceptor { _, ev -> drag.routeGesture(ev, card) }
            openCard = card
            openMembers = adapter
        }

        // Its apps as transient APP items, and the folders inside it as
        // FOLDER items, each put in held by its item id.
        private fun items(members: List<LaunchableApp>): List<LauncherItem> =
            viewModel.drawerFolderContents(group, members).map { pick ->
                when (pick) {
                    is GroupPick.App -> LauncherItem(
                        id = "drawer-folder/${pick.app.userSerial}/${pick.app.packageName}/${pick.app.className}",
                        itemType = ItemType.APP,
                        label = pick.app.label,
                        packageName = pick.app.packageName,
                        className = pick.app.className,
                        userSerial = pick.app.userSerial
                    )
                    is GroupPick.Folder -> {
                        val id = "drawer-folder/folder/${pick.group.id}"
                        held[id] = pick.group
                        LauncherItem(id = id, itemType = ItemType.FOLDER, label = pick.group.name)
                    }
                }
            }

        // A folder inside opens in this card's place; Back then closes it,
        // not back to this one.
        private fun tap(item: LauncherItem) {
            val child = held[item.id]
            if (child != null || viewModel.state.value.prefs.folderCloseOnLaunch) popup?.dismiss()
            child?.let { showDrawerFolder(anchor, it, viewModel.drawerFolderApps(it)) }
            item.toLaunchableApp()?.let { surfaces.launchApp(it) }
        }

        private fun hold(memberView: View, item: LauncherItem) {
            // A folder inside: the drawer folder's menu, and a drag of its
            // apps to the home screen.
            held[item.id]?.let { child ->
                val apps = viewModel.drawerFolderApps(child)
                val payload = DragPayload.DrawerFolder(child, apps)
                drag.arm(memberView, payload, window = popup, draggable = apps.isNotEmpty()) {
                    surfaces.drawerMenus.folderMenu(memberView, child, apps).also { memberMenu = it }
                }
            }
            item.toLaunchableApp()?.let { app ->
                drag.arm(memberView, DragPayload.DrawerApp(app), window = popup) {
                    surfaces.drawerMenus.appMenu(memberView, app, source = popup).also { memberMenu = it }
                }
            }
        }

        private fun closed() {
            // A menu a shortcut is being dragged out of carries the rest of
            // that gesture, and closes with its drop.
            memberMenu?.takeUnless { drag.ownsGestureIn(it.window) }?.dismiss()
            memberMenu = null
            // Closed under a held app or its drag (a home press, Back, a
            // rotation): the rest of that gesture would only ever have come
            // through this card, so it ends here.
            drag.onWindowDismissed(popup)
            openCard = null
            openMembers = null
            surfaces.drawer.greyBehindFolder(false)
            // The drawer sheet is usually still open underneath; only release
            // the wallpaper zoom when it isn't.
            if (!surfaces.drawer.isExpanded) host.animateWallpaperZoom(0f)
        }
    }

    private companion object {
        // How long a member's change is waited for before the card shows its
        // members again.
        const val SAVE_WAIT_MS = 1_000L
    }
}
