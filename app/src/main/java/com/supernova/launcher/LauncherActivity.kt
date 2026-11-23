package com.supernova.launcher

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.supernova.launcher.ui.AppActions
import com.supernova.launcher.ui.AppShortcuts
import com.supernova.launcher.ui.CardSwipeClose
import com.supernova.launcher.ui.FolderCards
import com.supernova.launcher.ui.IconSwipes
import com.supernova.launcher.ui.DrawerItemMenus
import com.supernova.launcher.ui.FolderToDrawerPrompt
import com.supernova.launcher.ui.GroupPickers
import com.supernova.launcher.ui.ItemEdits
import com.supernova.launcher.ui.ItemIcons
import com.supernova.launcher.ui.ItemMenuKit
import com.supernova.launcher.ui.ItemRemoval
import com.supernova.launcher.ui.ItemSurfaces
import com.supernova.launcher.ui.WorkspaceItemMenus
import com.supernova.launcher.ui.dp
import com.supernova.launcher.ui.showTracked
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.view.drawToBitmap
import androidx.core.view.isVisible
import com.supernova.launcher.ui.DragPayload
import com.supernova.launcher.ui.LiveDragController
import com.supernova.launcher.ui.WidgetController
import com.supernova.launcher.ui.currentWorkspaceCanvas
import com.supernova.launcher.ui.screenRect
import com.supernova.launcher.ui.toLaunchableApp
import com.supernova.launcher.ui.WorkspaceCanvas
import com.supernova.launcher.data.ContainerType
import com.supernova.launcher.data.DrawerGroup
import com.supernova.launcher.data.ItemType
import com.supernova.launcher.data.LauncherPrefs
import com.supernova.launcher.data.LaunchableApp
import com.supernova.launcher.data.LARGE_ICON_SPAN
import com.supernova.launcher.data.LauncherItem
import com.supernova.launcher.data.isLargeIcon
import com.supernova.launcher.data.LauncherRepository
import com.supernova.launcher.data.WorkspaceGeometry
import com.supernova.launcher.databinding.ActivityLauncherBinding
import com.supernova.launcher.databinding.ItemIconBinding
import com.supernova.launcher.gesture.Gesture
import com.supernova.launcher.gesture.GestureAction
import com.supernova.launcher.gesture.GestureBinding
import com.supernova.launcher.gesture.GestureDefaults
import com.supernova.launcher.gesture.HomeButtonAction
import com.supernova.launcher.gesture.HomeGestureDetector
import com.supernova.launcher.notification.SupernovaNotificationListener
import com.supernova.launcher.ui.Animations
import com.supernova.launcher.ui.DesktopPreviewsOverlay
import com.supernova.launcher.ui.LaunchAnimation
import com.supernova.launcher.ui.HomeOrientation
import com.supernova.launcher.ui.WallpaperScrollMode
import com.supernova.launcher.ui.SupernovaPopupMenu
import com.supernova.launcher.ui.BadgeDiff
import com.supernova.launcher.ui.BadgeStyle
import com.supernova.launcher.ui.LabelStyler
import com.supernova.launcher.ui.FolderIconDrawable
import com.supernova.launcher.ui.FolderMemberAdapter
import com.supernova.launcher.ui.FolderPopup
import com.supernova.launcher.ui.PausedIcon
import com.supernova.launcher.ui.LauncherUiState
import com.supernova.launcher.ui.LauncherViewModel
import com.supernova.launcher.ui.PageTransformers
import com.supernova.launcher.ui.TouchTrace
import com.supernova.launcher.ui.DrawerController
import com.supernova.launcher.ui.FeedController
import com.supernova.launcher.ui.SearchBarConfig
import com.supernova.launcher.ui.SearchController
import com.supernova.launcher.ui.ScrollEffect
import com.supernova.launcher.ui.WorkspacePagerAdapter
import com.supernova.launcher.ui.theme.AccentColor
import com.supernova.launcher.ui.theme.ThemeEngine
import com.supernova.launcher.widget.WidgetResizeFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class LauncherActivity :
    AppCompatActivity(),
    WorkspacePagerAdapter.Host,
    LiveDragController.Host,
    WidgetController.Host,
    com.supernova.launcher.ui.VoiceSearch.Host {

    private lateinit var binding: ActivityLauncherBinding
    private val viewModel: LauncherViewModel by viewModels { LauncherViewModel.Factory(appGraph) }

    override lateinit var workspaceAdapter: WorkspacePagerAdapter
    private lateinit var drawer: DrawerController
    private lateinit var drag: LiveDragController
    private lateinit var widgets: WidgetController
    private lateinit var search: SearchController
    private lateinit var feed: FeedController
    // Swipe-up drawer pull (a finger-tracking open): armed on a down
    // that may become a swipe-up, active once it does. See trackDrawerPull.
    private var drawerPullArmed = false
    private var drawerPullActive = false
    private var drawerPullDownX = 0f
    private var drawerPullDownY = 0f
    private var drawerPullVelocity: android.view.VelocityTracker? = null
    // A drawer left open behind a launched app because drawerAutoClose is off.
    // Screen-off resets the home screen, but honours that pref: locking the
    // phone while inside the launched app must not close the drawer the user
    // is coming back to. Cleared once the launcher is resumed.
    private var drawerKeptBehindLaunch = false
    // Circle's and Adaptive icon's launcher-side part (launchWithAnimation).
    private val launchPrelude by lazy { com.supernova.launcher.ui.LaunchPrelude(binding.root) }
    // Custom cards on every surface that shows them: one look, one way to
    // launch, edit and pin.
    private val cards by lazy {
        com.supernova.launcher.ui.FeedCardRoutes(
            this, viewModel, { actions }, { widgets },
            requestCalendar = { requestFeedCalendarPermission.launch(android.Manifest.permission.READ_CALENDAR) },
            requestContacts = { requestFeedContactsPermission.launch(android.Manifest.permission.READ_CONTACTS) }
        )
    }
    // A home swipe turns the page or swipes up or down, never both (#82).
    private val pageSwipeGuard by lazy { com.supernova.launcher.ui.PageSwipeGuard(binding.workspacePager) }

    private val gestureDetector by lazy {
        HomeGestureDetector(
            this,
            {
                TouchTrace.log { "gesture $it" }
                actions.runGesture(it)
            },
            ::workspaceCanScroll,
            ::pressOnItem,
            pageTookSwipe = { pageSwipeGuard.pageTookStream }
        )
    }

    // What a gesture, the home button, an action shortcut or a feed card is
    // bound to, carried out on this screen's surfaces.
    private val actions by lazy {
        com.supernova.launcher.ui.ActionRunner(
            this, viewModel,
            object : com.supernova.launcher.ui.ActionRunner.Host {
                override val root: View get() = binding.root
                override fun openDrawer() = this@LauncherActivity.openDrawer()
                override fun expandNotificationShade() = this@LauncherActivity.expandNotificationShade()
                override fun showPage(page: Int) = binding.workspacePager.setCurrentItem(
                    workspaceAdapter.centerPosition(page), Animations.scrollPages(viewModel.state.value.prefs)
                )
                override fun openSearch(query: String?) = if (query == null) search.open() else search.open(query)
                override fun launchApp(app: LaunchableApp) = this@LauncherActivity.launchApp(app)
                override fun launchShortcut(packageName: String, shortcutId: String, userSerial: Long) =
                    this@LauncherActivity.launchShortcut(packageName, shortcutId, userSerial)
                override fun launchIntentUri(uri: String) = this@LauncherActivity.launchIntentUri(uri, null)
                override fun toggleDock() = this@LauncherActivity.toggleDock()
                override fun showMainDockPage() =
                    this@LauncherActivity.showMainDockPage(Animations.scrollPages(viewModel.state.value.prefs))
                override fun showDesktopPreviews() = desktopPreviews.show()
                override fun openFeed() = feed.open()
                override fun openPrivateSpace(anchor: View?) = privateSpaceCard.open(anchor)
            }
        )
    }

    // The Private space action's card (#75), a folder card of the space's
    // apps over the home screen.
    private val privateSpaceCard by lazy {
        com.supernova.launcher.ui.PrivateSpaceCard(
            this, viewModel, folderPopup,
            object : com.supernova.launcher.ui.PrivateSpaceCard.Host {
                override val homeAnchor: View get() = binding.workspacePager

                override fun dockTopOnScreen(): Int? = this@LauncherActivity.dockTopOnScreen()

                override fun homeIsClear(): Boolean =
                    !drawer.isVisible && !search.isOpen && !feed.isOpen && !desktopPreviews.isShowing &&
                        folderCards.openCard == null && drag.activeItemPopup == null && homeMenuDialog == null &&
                        !(::editMode.isInitialized && editMode.isOpen)

                override fun closeOtherCards() {
                    folderCards.openCard?.dismiss()
                    folderCards.openCard = null
                }

                override fun launchApp(app: LaunchableApp) = this@LauncherActivity.launchApp(app)

                override fun holdApp(view: View, app: LaunchableApp, card: android.widget.PopupWindow?) {
                    drag.arm(view, DragPayload.DrawerApp(app), window = card, draggable = false) {
                        drawerMenus.appMenu(view, app, source = card)
                    }
                }

                override fun decorate(icon: android.graphics.drawable.Drawable?, item: LauncherItem) =
                    itemIcons.withBadge(icon, item.packageName, item.className, item.userSerial)

                override fun showMenu(anchor: View, rows: (SupernovaPopupMenu) -> Unit) {
                    drag.activeItemPopup?.dismiss()
                    val menu = menuKit.popup()
                    rows(menu)
                    drag.activeItemPopup = menu
                    menu.show(anchor)
                    menu.makeModal()
                }

                // The interceptor routes the item gesture; taps still reach
                // the card's own click listeners.
                @android.annotation.SuppressLint("ClickableViewAccessibility")
                override fun onShown(card: android.widget.PopupWindow, adapter: FolderMemberAdapter) {
                    animateWallpaperZoom(1f)
                    // As in a folder: a held app and its menu go through the
                    // item gesture, the card being a window of its own.
                    cardSwipeClose.onCardShown()
                    card.setTouchInterceptor { _, ev ->
                        cardSwipeClose.onTouch(ev.actionMasked)
                        drag.routeGesture(ev, card)
                    }
                    folderCards.openCard = card
                    folderCards.openMembers = adapter
                }

                override fun onClosed(card: android.widget.PopupWindow?) {
                    cardSwipeClose.onCardClosed(
                        SystemClock.uptimeMillis(), carried = card != null && drag.ownsGestureIn(card)
                    )
                    // A menu open over the card closes with it.
                    drag.activeItemPopup?.dismiss()
                    drag.activeItemPopup = null
                    drag.onWindowDismissed(card)
                    if (folderCards.openCard === card) {
                        folderCards.openCard = null
                        folderCards.openMembers = null
                    }
                    animateWallpaperZoom(0f)
                }
            }
        )
    }

    private val launchStartMs = SystemClock.elapsedRealtime()

    private lateinit var editMode: com.supernova.launcher.ui.HomeEditController

    // A screen reader's way to move a dock or desktop icon, in place of the
    // drag it can't perform.
    private val itemMoves = com.supernova.launcher.ui.ItemMoveActions(
        object : com.supernova.launcher.ui.ItemMoveActions.Host {
            override fun state(): LauncherUiState = viewModel.state.value

            override fun moveToCell(itemId: String, target: com.supernova.launcher.data.AccessibleMoves.Target.Cell) =
                viewModel.moveItemToWorkspace(itemId, target.page, target.cellX, target.cellY, mergeFolders = false)

            override fun moveToDock(itemId: String, rank: Int) {
                viewModel.moveItemToDock(itemId, rank)
            }

            override fun placeWidget(
                itemId: String,
                rect: com.supernova.launcher.data.WorkspaceGeometry.CellRect,
                done: (Boolean) -> Unit
            ) = viewModel.resizeWidget(itemId, rect, onRefused = { done(false) }, onSaved = { done(true) })

            override fun announce(message: Int) = binding.root.announceForAccessibility(getString(message))
        }
    )
    private lateinit var pageDrag: com.supernova.launcher.ui.PageDragController
    // What a drag over a page previews and what its drop does there.
    private val workspaceDrops by lazy {
        com.supernova.launcher.ui.WorkspaceDrops(
            this, viewModel,
            object : com.supernova.launcher.ui.WorkspaceDrops.Host {
                override fun forEachWorkspaceIcon(action: (Int, View) -> Unit) = drag.forEachWorkspaceIcon(action)
                override fun shortcutItem(shortcut: DragPayload.DrawerShortcut) = shortcuts.homeItem(shortcut)
                override fun bindWidget(
                    info: android.appwidget.AppWidgetProviderInfo,
                    target: WidgetController.Target
                ) = widgets.bind(info, target)
                override fun placeShortcut(
                    target: com.supernova.launcher.ui.ShortcutTarget,
                    cell: com.supernova.launcher.ui.ShortcutPlacer.DropCell
                ) = shortcutPlacer.pick(target, cell)
            }
        )
    }
    private var homeMenuDialog: com.google.android.material.bottomsheet.BottomSheetDialog? = null
    private val folderPopup by lazy { FolderPopup(this) }
    // A home folder's card, or the private space's, closed by the press the
    // system's home swipe began with: that swipe's home press counts it.
    private val cardSwipeClose = CardSwipeClose()
    // True from onStart until the activity is resumed. Pressing home in
    // another app restarts the launcher, so onStart runs before the home
    // intent is delivered; pressing home on the launcher itself only pauses
    // and resumes it, with no onStart in between. That difference is the only
    // reliable read of "was the user already looking at the home screen" by
    // the time onNewIntent runs - the Lifecycle state is STARTED either way,
    // because the temporary pause has already been dispatched.
    private var startedSinceResume = false

    // When the launcher's window last gained focus (uptime ms): a home press
    // only counts as made on the home screen once it has held focus a moment.
    private var focusGainedAt = 0L
    private var appliedScrollEffect: ScrollEffect? = null

    // X of the last touch-down, in window coordinates - dispatchTouchEvent
    // sees every event, and the activity's own view IS the window, so ev.x
    // needs no conversion. Read by the item menus when they open. A drawer
    // folder's card, its own full-screen window, sets it too (routeItemGesture).
    private var menuTouchX = 0f
    // Edit mode's "+" page asked for a real page, maybe with a pick from the
    // sheet to land on it. Once the model carries the page, edit mode closes
    // there; the pick waits one emission more, for the current page to be
    // the new one (observeState).
    private class PendingEditAddPage(val page: Int, val then: (() -> Unit)?) {
        var landed = false
    }
    private var pendingEditAddPage: PendingEditAddPage? = null
    // Transient dock hide from the TOGGLE_DOCK action; mirrors dockAutoClosed
    // (visibility only, reserved margins stay), never persisted.
    private var dockToggledOff = false

    // Guard so system-chrome window calls run only when the prefs change.
    private var appliedSystemChrome: List<Any>? = null

    private var appliedAccent: AccentColor = AccentColor.BLUE
    private var lastRenderedState: LauncherUiState? = null
    // What the dock currently shows; see dockSignature. Null forces a rebuild.
    private var renderedDock: List<Any?>? = null
    // The badge state the icons were last drawn from, so a replay of the same
    // state - which arrives on every return to the home screen - does nothing.
    private var renderedBadges: com.supernova.launcher.notification.BadgeState? = null
    // Every icon the home screen and the drawer draw beyond an app's own:
    // folder previews, shortcut pictures, and the badges on all of them.
    private val itemIcons by lazy { com.supernova.launcher.ui.ItemIcons(this, viewModel) }
    // The LauncherApps side of an app's shortcuts.
    private val shortcuts: AppShortcuts by lazy { AppShortcuts(this, viewModel) }
    // What the item menus, the edit sheets and the removals reach the screen
    // through.
    private val surfaces: ItemSurfaces = object : ItemSurfaces {
        override val activity: AppCompatActivity get() = this@LauncherActivity
        override val viewModel: LauncherViewModel get() = this@LauncherActivity.viewModel
        override val binding: ActivityLauncherBinding get() = this@LauncherActivity.binding
        override val workspaceAdapter: WorkspacePagerAdapter get() = this@LauncherActivity.workspaceAdapter
        override val drag: LiveDragController get() = this@LauncherActivity.drag
        override val widgets: WidgetController get() = this@LauncherActivity.widgets
        override val drawer: DrawerController get() = this@LauncherActivity.drawer
        override val itemIcons: ItemIcons get() = this@LauncherActivity.itemIcons
        override val shortcuts: AppShortcuts get() = this@LauncherActivity.shortcuts
        override val menuKit: ItemMenuKit get() = this@LauncherActivity.menuKit
        override val itemEdits: ItemEdits get() = this@LauncherActivity.itemEdits
        override val appActions: AppActions get() = this@LauncherActivity.appActions
        override val removal: ItemRemoval get() = this@LauncherActivity.removal
        override val moveToDrawer: FolderToDrawerPrompt get() = this@LauncherActivity.moveToDrawer
        override val pickers: GroupPickers get() = this@LauncherActivity.pickers
        override val workspaceMenus: WorkspaceItemMenus get() = this@LauncherActivity.workspaceMenus
        override val drawerMenus: DrawerItemMenus get() = this@LauncherActivity.drawerMenus
        override val folderCards: FolderCards get() = this@LauncherActivity.folderCards
        override val iconSwipes: IconSwipes get() = this@LauncherActivity.iconSwipes
        override val menuTouchX: Float get() = this@LauncherActivity.menuTouchX

        override fun launchApp(app: LaunchableApp, anchor: View?) = this@LauncherActivity.launchApp(app, anchor)

        override fun launchItem(item: LauncherItem, anchor: View?) = this@LauncherActivity.launchItem(item, anchor)

        override fun launchShortcut(packageName: String, shortcutId: String, userSerial: Long, anchor: View?) =
            this@LauncherActivity.launchShortcut(packageName, shortcutId, userSerial, anchor)

        override fun closeItemSources() = this@LauncherActivity.closeItemSources()

        override fun hideSearch() {
            if (::search.isInitialized && search.isOpen) search.hide()
        }
    }
    // Made with the activity: each registers a picker of its own.
    private val menuKit: ItemMenuKit = ItemMenuKit(surfaces)
    private val itemEdits: ItemEdits = ItemEdits(surfaces)
    private val appActions: AppActions = AppActions(this) { launchApp(it) }
    private val moveToDrawer: FolderToDrawerPrompt by lazy { FolderToDrawerPrompt(surfaces) }
    private val pickers: GroupPickers by lazy { GroupPickers(surfaces) }
    private val workspaceMenus: WorkspaceItemMenus by lazy { WorkspaceItemMenus(surfaces) }
    private val drawerMenus: DrawerItemMenus by lazy { DrawerItemMenus(surfaces) }
    private val folderCards: FolderCards by lazy {
        FolderCards(
            surfaces,
            object : FolderCards.Host {
                override val folderPopup: FolderPopup get() = this@LauncherActivity.folderPopup
                override val cardSwipeClose: CardSwipeClose get() = this@LauncherActivity.cardSwipeClose

                override fun dockTopOnScreen(): Int? = this@LauncherActivity.dockTopOnScreen()

                override fun animateWallpaperZoom(target: Float) = this@LauncherActivity.animateWallpaperZoom(target)
            }
        )
    }
    private val iconSwipes: IconSwipes by lazy {
        IconSwipes(
            surfaces,
            object : IconSwipes.Host {
                override val gestures: HomeGestureDetector get() = gestureDetector

                override fun disarmDrawerPull() {
                    drawerPullArmed = false
                }

                override fun runAction(action: GestureBinding) {
                    actions.run(action)
                }

                override fun launchIntentUri(uri: String) = this@LauncherActivity.launchIntentUri(uri, null)
            }
        )
    }
    private val removal: ItemRemoval by lazy {
        ItemRemoval(
            surfaces,
            object : ItemRemoval.Host {
                override val editModeOpen: Boolean get() = ::editMode.isInitialized && editMode.isOpen
                override val previewsShowing: Boolean get() = desktopPreviews.isShowing
                override val addingPage: Boolean get() = pendingEditAddPage != null

                override fun onPageJumped(page: Int) {
                    updateWallpaperOffset(page.toFloat())
                    if (::editMode.isInitialized) editMode.onPageChanged()
                }
            }
        )
    }
    private var reportedFullyDrawn = false
    private var lastWallpaperPages = -1

    // Which dock page is showing (transient).
    // Page 0 is the main one, drawn in the middle (DockPages).
    private var dockPage = 0
    private val dockSlide by lazy { com.supernova.launcher.ui.DockSlide(binding.dockBar) }
    private val dockGeometry by lazy {
        com.supernova.launcher.ui.DockGeometry(this, binding.dockBar, { systemInsets }, { isLandscape })
    }
    private val dockChrome by lazy {
        com.supernova.launcher.ui.DockChrome(this, binding, dockGeometry, dockChromeHost)
    }
    private val dockChromeHost = object : com.supernova.launcher.ui.DockChrome.Host {
        override val systemInsets: Insets get() = this@LauncherActivity.systemInsets
        override val dockHeightPx: Int get() = this@LauncherActivity.dockHeightPx
        override val isLandscape: Boolean get() = this@LauncherActivity.isLandscape
        override val dockHidden: Boolean get() = dockAutoClosed || dockToggledOff
        override fun bindSearchBarWidget(prefs: LauncherPrefs): View? = widgets.bindSearchBarWidget(prefs)
    }

    // The window shape the chrome was last laid out against. The activity
    // handles orientation and size changes itself (see the manifest's
    // configChanges), and onConfigurationChanged fires for changes that move
    // none of these - so the relayout is gated on one of them actually moving.
    private var lastConfigOrientation = 0
    private var lastConfigWidthDp = 0
    private var lastConfigHeightDp = 0
    // The dock's laid-out height. It grows with the icon scale and the label
    // line(s) rather than sitting at a constant, so everything stacked above
    // it measures off this; the layout listener in setupDock keeps it current
    // and the chrome guard carries it. 0 until the first layout, which reads
    // as the old design height so the first pass is spaced sensibly - and
    // re-spaced from the real height before it draws.
    private var dockHeightPx = 0
    // Width an icon column gets in the dock: the slot the LinearLayout hands
    // each item, which is the dock's width split dockCount ways. A dock full
    // of icons can make that narrower than the icon box, so viewForItem sizes
    // dock icons against it instead of letting them overflow the slot.
    private var dockSlotWidthPx = 0
    // System bar + cutout insets. A launcher draws its wallpaper edge to edge
    // and pads its own chrome out of the bars; this is the single source those
    // paddings read, so a bar appearing or hiding re-lays everything out.
    private var systemInsets: Insets = Insets.NONE
    private var dockTouchDownX = Float.NaN
    private var dockTouchDownY = Float.NaN
    private var dockTouchDownAt = 0L
    // Overlay auto-close: hidden after a launch until home is re-entered.
    private var dockAutoClosed = false

    // Wallpaper zoom-out while the drawer or a folder is open (API 30+).
    private var wallpaperZoomAnimator: android.animation.ValueAnimator? = null
    private var wallpaperZoomTarget = 0f
    private var wallpaperZoomValue = 0f
    // Some OEM wallpaper services throw; stop calling after the first failure.
    private var wallpaperZoomBroken = false

    // Shortcuts picked from the add sheet, placed on the page in view.
    private val shortcutPlacer = com.supernova.launcher.ui.ShortcutPlacer(this) { viewModel }

    // A search bar's mic: what was said goes into the search window, over
    // whichever page the bar was on.
    private val voiceSearchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val said = com.supernova.launcher.ui.VoiceSearch.heard(it.data)
        if (it.resultCode != RESULT_OK || said == null) return@registerForActivityResult
        if (::drawer.isInitialized && drawer.isVisible) hideDrawer()
        if (feed.isOpen) feed.setBehindSearch(true)
        search.open(said)
    }

    override fun startVoiceSearch() {
        // No recognizer to answer: the phone's own voice search instead.
        if (runCatching { voiceSearchLauncher.launch(com.supernova.launcher.ui.VoiceSearch.listenIntent()) }.isFailure) {
            com.supernova.launcher.ui.VoiceSearch.webSearch(this)
        }
    }

    private val requestHomeRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Result is informational; the user may decline and we never re-prompt automatically.
    }

    // A wallpaper image from any folder: the system file picker reaches
    // every storage provider and file manager, where the photo picker shows
    // only the media library.
    private val pickWallpaperFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) openWallpaperCrop(uri)
        }

    // Desktop previews overlay (DESKTOP_PREVIEWS action). Lazy: costs nothing
    // until the action first runs; views exist only while shown.
    private val desktopPreviews: DesktopPreviewsOverlay by lazy {
        DesktopPreviewsOverlay(this, binding.root, object : DesktopPreviewsOverlay.Host {
            override fun pageCount(): Int = workspaceAdapter.realPageCount
            override fun currentPage(): Int = viewModel.state.value.currentPage
            override fun defaultPage(): Int = viewModel.state.value.model.defaultPage
            override fun pageViewFor(page: Int): View? = attachedPageView(page)
            override fun accentColor(): Int =
                ThemeEngine.accentColor(this@LauncherActivity, viewModel.state.value.prefs.accentColor)
            override fun jumpToPage(page: Int) {
                binding.workspacePager.setCurrentItem(
                    workspaceAdapter.centerPosition(page), Animations.scrollPages(viewModel.state.value.prefs)
                )
            }
            // Lock layout refuses here as it does in edit mode.
            override fun addPage() {
                if (com.supernova.launcher.ui.DesktopLock.refused(this@LauncherActivity, viewModel)) return
                viewModel.addPage()
                refreshPreviewsSoon()
            }
            override fun removePage(page: Int) {
                if (com.supernova.launcher.ui.DesktopLock.refused(this@LauncherActivity, viewModel)) return
                if (viewModel.workspaceItems(page).isEmpty()) {
                    viewModel.removePage(page)
                } else {
                    removal.removePage(page, onUndo = ::refreshPreviewsSoon)
                }
                refreshPreviewsSoon()
            }
            override fun movePage(from: Int, to: Int) {
                if (com.supernova.launcher.ui.DesktopLock.refused(this@LauncherActivity, viewModel)) return
                viewModel.movePage(from, to)
                refreshPreviewsSoon()
            }
            override fun setDefaultPage(page: Int) {
                if (com.supernova.launcher.ui.DesktopLock.refused(this@LauncherActivity, viewModel)) return
                viewModel.setDefaultPage(page)
                refreshPreviewsSoon()
            }
        })
    }

    // Page ops round-trip through DataStore; rebuild once the model lands.
    private fun refreshPreviewsSoon() {
        lifecycleScope.launch {
            delay(200)
            if (desktopPreviews.isShowing) desktopPreviews.rebuild()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        liveLaunchers.removeAll { it.get() == null }
        liveLaunchers += java.lang.ref.WeakReference(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        viewModel.onDisplayDensity(resources.displayMetrics.densityDpi)
        appliedAccent = viewModel.state.value.prefs.accentColor
        ThemeEngine.applyAccent(this, appliedAccent)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        rememberConfiguration(resources.configuration)
        setUpEdgeToEdge()
        // A theme flip recreates this activity with no pref changing.
        viewModel.refreshThemedIcons()

        widgets = WidgetController(this, binding, viewModel, host = this)

        // Without this, back falls through to the system's task-root handling,
        // which re-delivers a home intent - a full onNewIntent reset just to
        // close the drawer. A launcher always consumes back itself.
        onBackPressedDispatcher.addCallback(this) {
            when {
                // A long-press menu first: it is not focusable, so back
                // reaches here, and closing the drawer under it left the
                // menu floating over the home screen.
                drag.activeItemPopup?.isShowing == true -> drag.activeItemPopup?.dismiss()
                // A widget's resize frame next: it sits on the page, under
                // whatever else is open.
                widgets.activeResizeFrame != null -> widgets.dismissResizeFrame()
                ::editMode.isInitialized && editMode.isOpen -> editMode.exit()
                desktopPreviews.isShowing -> desktopPreviews.dismiss()
                ::search.isInitialized && search.isOpen -> search.hide()
                // Feed before drawer: the pane overlays the workspace, so back
                // must close it before it can ever reach the (hidden) drawer.
                ::feed.isInitialized && feed.isOpen -> feed.close()
                // A query typed in the drawer goes first: the full list
                // comes back, and the next Back closes the drawer.
                ::drawer.isInitialized && drawer.isExpanded -> if (!drawer.clearQuery()) drawer.hide()
                else -> Unit
            }
        }

        setupWorkspace()
        setupDrawer()
        setupSearch()
        setupFeed()
        setupDock()
        drag = LiveDragController(binding, viewModel, host = this, drops = workspaceDrops)
        setupEditMode()
        observeState()
        maybeShowOnboarding()
        widgets.sweepOrphanIds()
        observeBadges()
        restoreDockPage()

        // Wallpaper-derived palettes go stale without any prefs emission, so
        // re-resolve and re-apply the styled chrome when the wallpaper changes.
        WallpaperManager.getInstance(this).addOnColorsChangedListener(
            wallpaperColorsListener, android.os.Handler(mainLooper)
        )

        // Registered for the activity's whole lifetime rather than between
        // onStart and onStop: the broadcast races onStop when the screen turns
        // off on the launcher itself, and a lock made from inside a launched
        // app has to reach this too.
        androidx.core.content.ContextCompat.registerReceiver(
            this, screenOffReceiver,
            android.content.IntentFilter(Intent.ACTION_SCREEN_OFF),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    // Short-and-wide window. Read from the live configuration rather than
    // cached, so a rotation handled in place is reflected immediately.
    private val isLandscape: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    override fun landscape(): Boolean = isLandscape

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val moved = newConfig.orientation != lastConfigOrientation ||
            newConfig.screenWidthDp != lastConfigWidthDp ||
            newConfig.screenHeightDp != lastConfigHeightDp
        rememberConfiguration(newConfig)
        if (!moved) return
        relayoutForConfiguration()
    }

    private fun rememberConfiguration(config: Configuration) {
        lastConfigOrientation = config.orientation
        lastConfigWidthDp = config.screenWidthDp
        lastConfigHeightDp = config.screenHeightDp
    }

    // A rotation (or a multi-window resize) with the activity kept alive.
    // Everything below is either measured in pixels against the old window or
    // anchored to a view that has just moved, so it is torn down and rebuilt
    // rather than left pointing at the previous geometry.
    private fun relayoutForConfiguration() {
        // Overlays positioned against the old window. A PopupWindow does not
        // reposition itself, and a resize frame's span limits were worked out
        // in the old cells.
        homeMenuDialog?.dismiss()
        homeMenuDialog = null
        drag.activeItemPopup?.dismiss()
        drag.activeItemPopup = null
        folderCards.openCard?.dismiss()
        folderCards.openCard = null
        widgets.dismissResizeFrame()
        if (desktopPreviews.isShowing) desktopPreviews.dismiss()
        // Edit mode's page zoom, sheet height and chip row were all worked
        // out for the old window; left open, the page sat off-centre under
        // a sheet covering half a landscape screen.
        if (::editMode.isInitialized && editMode.isOpen) editMode.exit()
        // A drag in flight holds cell arithmetic from the old grid; the
        // preview offsets it applied are cleared with it.
        workspaceDrops.cancelReorderPreview(animate = false)

        // Cached pixel geometry, and every guard that would otherwise skip
        // the re-apply because the prefs themselves did not change.
        dockHeightPx = 0
        dockSlotWidthPx = 0
        dockChrome.invalidate()
        renderedDock = null
        appliedSystemChrome = null
        widgets.clearReportedSizes()

        val state = viewModel.state.value
        applySystemChrome(state.prefs)
        applyWindowInsets()
        renderDock(state)
        // Icons are placed from normalized coordinates read off the live
        // canvas, and widgets from cell sizes; both change with the window.
        workspaceAdapter.invalidateAll()
        if (::drawer.isInitialized) drawer.onWindowResized()
        if (::search.isInitialized) search.onWindowResized()
        // The wallpaper's scroll step is per-page across the new width.
        lastWallpaperPages = -1
        binding.workspacePager.post {
            updateWallpaperOffset(binding.workspacePager.currentItem.toFloat())
        }
    }

    private fun observeBadges() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SupernovaNotificationListener.badgeState.collect { badges ->
                    val previous = renderedBadges
                    // Recorded even with badges off, so switching them on
                    // diffs against what is really on screen rather than
                    // against nothing.
                    renderedBadges = badges
                    // A StateFlow replays its value to every new collector,
                    // and this one is re-collected on every onStart: without
                    // this, every return to the home screen tore the whole
                    // workspace down and re-inflated it, during the
                    // return-home animation.
                    if (previous == badges) return@collect
                    val prefs = viewModel.state.value.prefs
                    if (!prefs.badges) return@collect
                    val changed = BadgeDiff.changedKeys(previous?.counts.orEmpty(), badges.counts).toMutableSet()
                    // A new notification can change a Dynamic badge's
                    // picture without changing its count.
                    if (BadgeStyle.effective(prefs) == BadgeStyle.DYNAMIC) {
                        changed += BadgeDiff.changedValues(previous?.faces.orEmpty(), badges.faces)
                    }
                    if (changed.isEmpty()) return@collect
                    applyBadgeChanges(changed)
                }
            }
        }
    }

    // Redraw only what these apps' badges are actually on. "changed" holds the
    // listener's "$userHash/$packageName" keys, which is also how withBadge
    // looks a count up.
    private fun applyBadgeChanges(changed: Set<String>) {
        val prefs = viewModel.state.value.prefs

        fun carries(item: LauncherItem): Boolean {
            val pkg = item.packageName ?: return false
            // Per-app badge filter: an item excluded there never shows one,
            // so its count moving changes nothing on screen.
            if (prefs.badgeApps.isNotEmpty() &&
                "${item.userSerial}/$pkg/${item.className.orEmpty()}" !in prefs.badgeApps
            ) {
                return false
            }
            val user = viewModel.appCatalog().userForSerial(item.userSerial) ?: Process.myUserHandle()
            return "${user.hashCode()}/$pkg" in changed
        }

        // A folder sums its members' counts, so a member changing is the
        // folder changing - but only while folder badges are on.
        fun affected(item: LauncherItem): Boolean =
            if (item.itemType == ItemType.FOLDER) {
                prefs.folderBadges && viewModel.folderMembers(item.id).any(::carries)
            } else {
                carries(item)
            }

        // Redrawn in place: the icon's view stays, with a press on it.
        val pages = binding.workspacePager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
        if (pages != null) {
            workspaceAdapter.redrawItemsWith(pages, ::affected, ::redrawItemView)
        } else {
            workspaceAdapter.invalidatePagesWith(::affected)
        }
        val dockHits = viewModel.dockItems().filter(::affected)
        if (dockHits.isNotEmpty() && !redrawDockItems(dockHits)) {
            renderDock(viewModel.state.value, force = true)
        }
        drawer.onBadgeChanged()
        folderCards.openMembers?.refreshIcons()
    }

    // Phase 8 first-run gate. GRANDFATHERING: existing users must never see
    // the wizard - an install that already has layout items or has been
    // through the old home prompt predates onboarding, so onboarding_done is
    // written silently and control falls through to the normal home-role
    // prompt. Items the wizard planted itself as it opened don't count. Until
    // the wizard finishes or is skipped (both write the flag), this re-checks
    // on every launcher create, so pressing Home mid-wizard isn't a trap: it
    // simply re-opens next launch.
    private fun maybeShowOnboarding() {
        if (startedAsHome()) {
            // The first run that just made this the home app finishes from
            // here, rather than this opening a second; the launcher it was
            // opened over goes once its writes are in.
            val handover = com.supernova.launcher.onboarding.OnboardingHomeHandover
            if (handover.finishForHome(this, ::closeOtherLaunchers)) return
            closeOtherLaunchers()
        }
        lifecycleScope.launch {
            if (!viewModel.onboardingDone()) {
                val predates = viewModel.hasModelItems() && !viewModel.defaultLayoutSeeded()
                if (predates || viewModel.homePromptShown()) {
                    viewModel.markOnboardingDone()
                } else {
                    startActivity(
                        Intent(this@LauncherActivity, com.supernova.launcher.onboarding.OnboardingActivity::class.java)
                    )
                    return@launch
                }
            }
            maybePromptDefaultHome()
        }
    }

    private fun startedAsHome(): Boolean = intent?.hasCategory(Intent.CATEGORY_HOME) == true

    // Opened from another launcher's icon, this runs in an ordinary task;
    // once it becomes the home app, the system starts the home screen in a
    // task of its own, and the first copy stayed alive behind it, with its
    // own view model, for as long as the process lived. The home copy closes
    // it as it starts.
    private fun closeOtherLaunchers() {
        liveLaunchers.mapNotNull { it.get() }
            .filter { it !== this && !it.isFinishing && !it.startedAsHome() }
            .forEach { it.finishAndRemoveTask() }
    }

    private fun maybePromptDefaultHome() {
        if (!HomeRole.isNeeded(this)) return
        val request = HomeRole.requestIntent(this) ?: return
        lifecycleScope.launch {
            // The onboarding wizard owns the ROLE_HOME ask until it completes;
            // finishing or skipping it writes homePromptShown too, so this
            // prompt can never double-ask afterwards.
            if (!viewModel.onboardingDone()) return@launch
            if (viewModel.homePromptShown()) return@launch
            viewModel.markHomePromptShown()
            requestHomeRoleLauncher.launch(request)
        }
    }

    private val wallpaperColorsListener =
        WallpaperManager.OnColorsChangedListener { _, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                com.supernova.launcher.ui.theme.Styles.onWallpaperChanged()
                // Based-on-wallpaper status icons; the guard skips it when
                // their colour stays.
                applySystemChrome(viewModel.state.value.prefs)
                dockChrome.invalidate()
                // Palette-derived colors moved under everything the dock
                // draws, and none of that is in its signature.
                renderedDock = null
                dockChrome.apply(viewModel.state.value.prefs)
                if (::drawer.isInitialized) drawer.invalidateStyle()
                if (::search.isInitialized) search.invalidateStyle()
                if (::feed.isInitialized) feed.invalidateStyle()
                workspaceAdapter.invalidateAll()
            }
        }

    override fun onDestroy() {
        liveLaunchers.removeAll { it.get() == null || it.get() === this }
        // A recreate (a theme flip, an accent change) can come with the feed
        // showing; its media and calendar listeners are registered with the
        // system and would keep this activity alive for good.
        if (::feed.isInitialized) feed.release()
        discover.configure(null, com.supernova.launcher.ui.DiscoverTheme.AUTO)
        WallpaperManager.getInstance(this).removeOnColorsChangedListener(wallpaperColorsListener)
        runCatching { unregisterReceiver(screenOffReceiver) }
        // It takes itself off once the dock has settled; a destroy can come
        // first, and the window it is on may be handed to the next instance.
        if (::binding.isInitialized) binding.root.viewTreeObserver.removeOnPreDrawListener(dockChromeWatch)
        super.onDestroy()
    }

    // Refreshes dynamic calendar icons when the day rolls over while the
    // launcher is visible; a no-op for packs without calendar icons.
    private val dateChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) = redrawForNewDay()
    }

    // The day the calendar icons were drawn for. The receiver only listens
    // while the launcher is started, and midnight usually passes with the
    // screen off, so onStart catches up when this has fallen behind.
    private var iconsDrawnFor: java.time.LocalDate = java.time.LocalDate.now()

    private fun redrawForNewDay() {
        iconsDrawnFor = java.time.LocalDate.now()
        // Only date icons change with the day. Without any, there is
        // nothing to redraw: this rebound every page on the morning's first
        // return home.
        if (!viewModel.appCatalog().onDayChanged()) return
        workspaceAdapter.invalidateAll()
        // onDayChanged evicts the calendar icons without touching the
        // prefs, the model or the icon generation, so the dock's
        // signature is unmoved and it has to be told.
        renderDock(viewModel.state.value, force = true)
        drawer.notifyContentChanged()
    }

    // Screen off: close everything over the home screen, so unlocking lands on
    // the home screen rather than on whatever was open when the screen went
    // dark, as Launcher3 does. The page stays where it was (#317), unless
    // Return to default page is on.
    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            privateSpaceCard.cancelPending()
            val keepPage = !viewModel.state.value.prefs.homeReturnToDefaultPage
            resetToHome(animate = false, keepDrawer = drawerKeptBehindLaunch, keepPage = keepPage)
            if (drawerKeptBehindLaunch && ::drawer.isInitialized) drawer.resetPosition()
        }
    }

    override fun onStart() {
        super.onStart()
        discover.activityState(started = true, resumed = false)
        MainStallWatch.start()
        startedSinceResume = true
        widgets.startListening()
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this, dateChangeReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (java.time.LocalDate.now() != iconsDrawnFor) redrawForNewDay()
    }

    override fun onStop() {
        // The launched app covers the launcher now: its circle can go, and
        // the lifted icon comes back.
        launchPrelude.clear()
        if (::drag.isInitialized) drag.cancel()
        if (!viewModel.state.value.prefs.dockRememberPosition) showMainDockPage(animate = false)
        widgets.stopListening()
        runCatching { unregisterReceiver(dateChangeReceiver) }
        MainStallWatch.stop()
        discover.activityState(started = false, resumed = false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        TouchTrace.refresh()
        discover.activityState(started = true, resumed = true)
        // Cleared after onNewIntent, which runs first on the way back in.
        startedSinceResume = false
        // The launcher is what the user is looking at again, so a drawer still
        // open is one they came back to, not one parked behind an app.
        drawerKeptBehindLaunch = false
        reopenDockIfAutoClosed()
    }

    override fun onPause() {
        // Home pressed while a circle was still filling, say: that launch is
        // off. One already asked for carries on.
        launchPrelude.cancelPending()
        discover.activityState(started = true, resumed = false)
        super.onPause()
    }

    override fun onPostResume() {
        super.onPostResume()
        Log.i("Supernova", "startup_ms=${SystemClock.elapsedRealtime() - launchStartMs}")
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked != MotionEvent.ACTION_MOVE) TouchTrace.log { touchState(ev) }
        // Ahead of every route below: it has to see each stream's lift.
        if (::binding.isInitialized) pageSwipeGuard.onTouch(ev)
        // A press on the home screen: a home press after it is the user's
        // own, not the close of an unlock prompt that just went (#135).
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) privateSpaceCard.prompt.onHomeTouched()
        if (::pageDrag.isInitialized && pageDrag.isActive) {
            pageDrag.onTouch(ev)
            return true
        }
        if (::drag.isInitialized && drag.routeGesture(ev, window = null)) return true
        // An active drawer pull owns the stream until the finger lifts; it
        // runs before the visibility gate below, which the pull itself trips.
        if (drawerPullActive) return continueDrawerPull(ev)
        // Edit mode offers Resize too: a drag on that frame's edge is the frame's,
        // unless the press is on what edit mode puts over the page.
        val framedInEditMode = ::editMode.isInitialized && editMode.isOpen && !editMode.pressOnChrome(ev)
        if (framedInEditMode && widgets.trackResizeDrag(ev, keepOnPress = true)) return true
        // isVisible, not isExpanded: the sheet leaves EXPANDED the moment a
        // closing swipe starts, and the still-observing gesture detector
        // would read that swipe as the home swipe-down binding.
        if ((::drawer.isInitialized && drawer.isVisible && !drawer.isClosing) ||
            (::search.isInitialized && search.isOpen) ||
            // Edit mode: sheet drags and chip taps must not read as gestures.
            (::editMode.isInitialized && editMode.isOpen) ||
            // Desktop previews: its thumbnails take their own taps and
            // drags; a swipe there pulled the shade or opened the drawer
            // behind them, and a double tap ran the double-tap action.
            desktopPreviews.isShowing
        ) {
            return super.dispatchTouchEvent(ev)
        }
        // A drag on a widget resize frame's edge is the frame's alone, as a
        // live drag is the drag layer's: nothing below may read it. On the
        // first page the feed would take a rightward one for its pull, and
        // the drawer pull an upward one, each cancelling the frame a few
        // pixels in; a downward one would fire the swipe-down on the lift.
        if (widgets.trackResizeDrag(ev)) return true
        if (::feed.isInitialized) {
            when (feed.onTouch(ev)) {
                // The feed owns the rest of this stream (edge-swipe drag).
                FeedController.TouchResult.CONSUMED -> return true
                // Takeover moment: cancel the views underneath so the pager
                // and icons drop the gesture, then drive the pane instead.
                FeedController.TouchResult.TAKE_OVER -> {
                    val cancel = MotionEvent.obtain(ev)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    return true
                }
                FeedController.TouchResult.PASS -> Unit
            }
            // An open feed pane handles its own touches; home gestures and
            // dock swipes stay out of it like they do for drawer/search.
            if (feed.isOpen) return super.dispatchTouchEvent(ev)
        }
        if (heldBySwipe(ev)) return true
        if (twoFingersOverWidget(ev)) return true
        // A widget scrolling its own content owns the vertical stream. The
        // detector observes without consuming, so otherwise a drag inside a
        // list widget also read as the home swipe-up/down binding and fired
        // the drawer or the shade the moment the finger lifted.
        if (widgets.trackWidgetScroll(ev)) {
            drawerPullArmed = false
            return super.dispatchTouchEvent(ev)
        }
        // Swipe-up bound to the drawer: once the drag reads as a swipe-up the
        // sheet comes in under the finger instead of on the lift.
        if (trackDrawerPull(ev)) {
            TouchTrace.log { "drawer pull" }
            val cancel = MotionEvent.obtain(ev)
            cancel.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(cancel)
            gestureDetector.onTouchEvent(cancel)
            cancel.recycle()
            return true
        }
        // Gestures observe every event without consuming it, so drags, page
        // swipes and icon taps keep working underneath - until a swipe up or
        // down runs mid-stream: the views are cancelled and it takes the rest.
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) gestureDetector.moveSwipesAllowed = !inGestureStrip(ev)
        gestureDetector.onTouchEvent(ev)
        if (gestureDetector.takeSwipedMidStream()) return takeStreamForSwipe(ev)
        trackDockSwipe(ev)
        return super.dispatchTouchEvent(ev)
    }

    // What decides where a touch goes, for TouchTrace.
    private fun touchState(ev: MotionEvent): String =
        "${MotionEvent.actionToString(ev.action)} ${ev.x.toInt()},${ev.y.toInt()} " +
            "drawer=${::drawer.isInitialized && drawer.isVisible} search=${::search.isInitialized && search.isOpen} " +
            "edit=${::editMode.isInitialized && editMode.isOpen} feed=${::feed.isInitialized && feed.isOpen} " +
            "pull=$drawerPullArmed/$drawerPullActive strip=${::binding.isInitialized && inGestureStrip(ev)} " +
            "drag=${::drag.isInitialized && (drag.isArmed || drag.isActive)} held=$swipeOwnsStream"

    // The drawer pull's stream, from the move that started it to the lift.
    private fun continueDrawerPull(ev: MotionEvent): Boolean {
        drawerPullVelocity?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> drawer.pullTo(drawerPullDownY - ev.y)
            MotionEvent.ACTION_UP -> finishDrawerPull(cancelled = false)
            MotionEvent.ACTION_CANCEL -> finishDrawerPull(cancelled = true)
            else -> Unit
        }
        return true
    }

    // A home swipe ran mid-stream: the views under the finger are cancelled
    // and the rest of the stream is held from them (heldBySwipe).
    private fun takeStreamForSwipe(ev: MotionEvent): Boolean {
        swipeOwnsStream = true
        val cancel = MotionEvent.obtain(ev)
        cancel.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancel)
        gestureDetector.onTouchEvent(cancel)
        cancel.recycle()
        return true
    }

    // Set while a home swipe that ran mid-stream owns the rest of it.
    private var swipeOwnsStream = false

    // The feed page from another app (Settings > Feed > Page), when one is
    // set: it draws over this window, so it hears about the window and the
    // activity's state from here. The feed configures it from the settings.
    private val discover by lazy {
        com.supernova.launcher.ui.DiscoverClient(this) { progress ->
            if (::feed.isInitialized) feed.overlayMoved(progress)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        discover.windowAttached(true)
    }

    override fun onDetachedFromWindow() {
        discover.windowAttached(false)
        super.onDetachedFromWindow()
    }

    // Set while two fingers that landed on a widget own the rest of the
    // stream, for the home gestures alone.
    private var twoFingersOwnStream = false

    // Two-finger gestures on top of widgets, as Nova 7.0.41 fixed them: a
    // second finger on a widget takes the stream from it, and the home
    // gestures read the rest of it. The detector may not have seen the
    // press (a list widget held the stream from its down), so it is reset
    // and starts from this moment, the two fingers' midpoint included.
    private fun twoFingersOverWidget(ev: MotionEvent): Boolean {
        val held = twoFingersOwnStream && ev.actionMasked != MotionEvent.ACTION_DOWN
        val ends = ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL
        twoFingersOwnStream = held && !ends
        when {
            held -> gestureDetector.onTouchEvent(ev)
            widgets.secondFingerOnWidget(ev) -> takeStreamForTwoFingers(ev)
            else -> return false
        }
        return true
    }

    private fun takeStreamForTwoFingers(ev: MotionEvent) {
        drawerPullArmed = false
        val cancel = MotionEvent.obtain(ev)
        cancel.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancel)
        gestureDetector.onTouchEvent(cancel)
        cancel.recycle()
        val down = MotionEvent.obtain(ev.downTime, ev.eventTime, MotionEvent.ACTION_DOWN, ev.getX(0), ev.getY(0), 0)
        gestureDetector.onTouchEvent(down)
        down.recycle()
        gestureDetector.onTouchEvent(ev)
        twoFingersOwnStream = true
    }

    // The rest of a stream a home swipe ran in is the swipe's: true to drop
    // the event. A new press also finishes a nearly settled page first.
    private fun heldBySwipe(ev: MotionEvent): Boolean {
        val held = swipeOwnsStream && ev.actionMasked != MotionEvent.ACTION_DOWN
        if (!held || ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            swipeOwnsStream = false
        }
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) finishPageSettleForPress()
        return held
    }

    // A press while a page swipe is still settling: the pager would take it
    // as a new drag, so a tap on an icon as the page came to rest did
    // nothing. Close to rest, the page is put there and the press is a
    // press, as Launcher3 does.
    private fun finishPageSettleForPress() {
        val pager = binding.workspacePager
        val pages = pager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
        val nearRest = pages != null &&
            pager.scrollState == androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_SETTLING &&
            abs(pages.computeHorizontalScrollOffset() - pager.currentItem * pages.width) <= dp(SETTLE_TAP_THROUGH_DP)
        if (!nearRest) return
        pages?.stopScroll()
        pager.setCurrentItem(pager.currentItem, false)
    }

    // A quick horizontal flick across the dock switches dock pages. Observed,
    // never consumed: slower or shorter motion stays a tap/long-press.
    // Arms on a down that could become a swipe-up while the drawer is the
    // swipe-up binding; returns true at the moment the drag qualifies, when
    // the caller cancels the views underneath and the pull takes the stream.
    private fun trackDrawerPull(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                drawerPullArmed = drawerPullAllowed(ev)
                if (drawerPullArmed) {
                    drawerPullDownX = ev.x
                    drawerPullDownY = ev.y
                    drawerPullVelocity?.recycle()
                    drawerPullVelocity = android.view.VelocityTracker.obtain().also { it.addMovement(ev) }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> drawerPullArmed = false
            MotionEvent.ACTION_MOVE -> if (drawerPullArmed) {
                drawerPullVelocity?.addMovement(ev)
                val dx = ev.x - drawerPullDownX
                val dy = ev.y - drawerPullDownY
                val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
                if (-dy > slop && -dy > kotlin.math.abs(dx) * DRAWER_PULL_BIAS) {
                    // The second press of a double tap. A bound double-tap
                    // swipe up owns it outright: the pull took it at the
                    // slop, long before the swipe's own distance, so that
                    // gesture could never fire. A bound double tap keeps it
                    // until the finger has gone a swipe's distance, past a
                    // tap's drift.
                    if (gestureDetector.inDoubleTap) {
                        val bindings = GestureDefaults.decode(viewModel.state.value.prefs.gestureBindings)
                        if (bindings[Gesture.DOUBLE_TAP_SWIPE_UP]?.action != GestureAction.NONE) {
                            drawerPullArmed = false
                            return false
                        }
                        if (bindings[Gesture.DOUBLE_TAP]?.action != GestureAction.NONE &&
                            -dy < gestureDetector.minSwipeDistance
                        ) {
                            return false
                        }
                    }
                    drawerPullArmed = false
                    // A long-press may have armed an item drag since the down.
                    if (drag.isArmed || drag.activeItemPopup != null) return false
                    drawerPullActive = true
                    drawer.beginPull()
                    drawer.pullTo(-dy)
                    // The Open App Drawer haptic: one tick as the swipe
                    // takes the drawer.
                    if (viewModel.state.value.prefs.drawerSwipeHaptic) {
                        binding.root.performHapticFeedback(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                android.view.HapticFeedbackConstants.GESTURE_START
                            } else {
                                android.view.HapticFeedbackConstants.VIRTUAL_KEY
                            }
                        )
                    }
                    return true
                }
                if (dy > slop || (kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > -dy)) {
                    drawerPullArmed = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                drawerPullArmed = false
                drawerPullVelocity?.recycle()
                drawerPullVelocity = null
            }
            else -> Unit
        }
        return false
    }

    // An icon, folder or widget on the page in view, or a docked item, under
    // a press. Everything else - empty cells, the dock's background, the
    // strip between - is home screen, where a double tap counts.
    private fun pressOnItem(rawX: Float, rawY: Float): Boolean {
        val x = rawX.toInt()
        val y = rawY.toInt()
        binding.workspacePager.currentWorkspaceCanvas()?.let { canvas ->
            for (c in 0 until canvas.childCount) {
                val child = canvas.getChildAt(c)
                if (child is WidgetResizeFrame || !child.isShown || child.getTag(R.id.tag_item_id) == null) continue
                if (child.screenRect().contains(x, y)) return true
            }
        }
        if (binding.dockBar.isShown) {
            for (i in 0 until binding.dockBar.childCount) {
                val slot = binding.dockBar.getChildAt(i) as? ViewGroup ?: continue
                for (c in 0 until slot.childCount) {
                    val child = slot.getChildAt(c)
                    if (child.getTag(R.id.tag_item_id) != null && child.screenRect().contains(x, y)) return true
                }
            }
        }
        return false
    }

    // A press in the bottom strip where the system always takes a swipe
    // (gesture navigation's home bar); never with button navigation.
    private fun inGestureStrip(ev: MotionEvent): Boolean {
        val inset = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())?.bottom ?: 0
        return inset > 0 && ev.y >= binding.root.height - inset
    }

    private fun drawerPullAllowed(ev: MotionEvent): Boolean {
        if (!::drawer.isInitialized || (drawer.isVisible && !drawer.isClosing)) return false
        // A press in the system's own gesture strip (gesture navigation's
        // home swipe) is the system's: the pull it started was cancelled a
        // moment later, and the sheet closing again swallowed the Home press
        // that followed.
        if (inGestureStrip(ev)) return false
        if (::feed.isInitialized && feed.isOpen) return false
        if (::search.isInitialized && search.isOpen) return false
        if (::editMode.isInitialized && editMode.isOpen) return false
        if (desktopPreviews.isShowing) return false
        if (drag.isArmed || drag.activeItemPopup != null) return false
        return GestureDefaults.swipeUpOpensDrawer(viewModel.state.value.prefs.gestureBindings)
    }

    private fun finishDrawerPull(cancelled: Boolean) {
        drawerPullActive = false
        val tracker = drawerPullVelocity
        var velocity = 0f
        if (tracker != null) {
            tracker.computeCurrentVelocity(1000)
            velocity = tracker.yVelocity
            tracker.recycle()
        }
        drawerPullVelocity = null
        drawer.endPull(velocity, cancelled)
    }

    // Whether a swipe from (x, y) may pull the feed in like a pager page:
    // first page, no wrap-around, above the dock, nothing else armed.
    private fun feedPullAllowed(x: Float, y: Float): Boolean {
        val prefs = viewModel.state.value.prefs
        if (!prefs.feedEnabled) return false
        if (prefs.infiniteScroll && workspaceAdapter.realPageCount > 1) return false
        if (workspaceAdapter.realPage(binding.workspacePager.currentItem) != 0) return false
        if (drag.isArmed || drag.activeItemPopup != null) return false
        if (desktopPreviews.isShowing) return false
        if (::editMode.isInitialized && editMode.isOpen) return false
        val dockTop = if (binding.dockBar.visibility == View.VISIBLE) binding.dockBar.top else binding.root.height
        return y >= binding.workspacePager.top && y < dockTop && x >= 0f
    }

    private fun onDockTouchDown(ev: MotionEvent) {
        val inDock = ev.y >= binding.dockBar.top && ev.y <= binding.dockBar.bottom
        // A flick across a paged dock turns its page; the home swipe
        // left/right bindings fired on the same lift and opened their apps
        // instead. The detector saw this down first and cleared its claims,
        // so this one stands for the gesture.
        if (inDock) gestureDetector.suppressSwipe(up = false, down = false, sideways = true)
        dockTouchDownX = if (inDock) ev.x else Float.NaN
        dockTouchDownY = ev.y
        dockTouchDownAt = ev.eventTime
    }

    private fun trackDockSwipe(ev: MotionEvent) {
        val prefs = viewModel.state.value.prefs
        if (!prefs.dockEnabled || prefs.dockPages <= 1) return
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDockTouchDown(ev)
            MotionEvent.ACTION_UP -> {
                if (dockTouchDownX.isNaN()) return
                val dx = ev.x - dockTouchDownX
                val dy = ev.y - dockTouchDownY
                dockTouchDownX = Float.NaN
                val quick = ev.eventTime - dockTouchDownAt < 350
                if (quick && kotlin.math.abs(dx) > dp(56) && kotlin.math.abs(dy) < dp(48)) {
                    // A flick to the left brings in the page on the right.
                    turnDockPage(prefs, toRight = dx < 0)
                }
            }
        }
    }

    private fun turnDockPage(prefs: LauncherPrefs, toRight: Boolean) {
        val next = com.supernova.launcher.ui.DockPages.step(
            dockPage, prefs.dockPages, toRight, prefs.dockInfiniteScroll
        )
        if (next == dockPage) return
        dockPage = next
        if (prefs.dockRememberPosition) viewModel.persistDockPage(dockPage)
        renderDock(viewModel.state.value, slide = if (toRight) 1 else -1)
    }

    private fun setupWorkspace() {
        workspaceAdapter = WorkspacePagerAdapter(this)
        binding.workspacePager.adapter = workspaceAdapter
        // Build the neighbour page before the fling starts instead of during
        // it, and stop the default cross-fade on page rebinds.
        binding.workspacePager.offscreenPageLimit = 1
        (binding.workspacePager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView)?.apply {
            itemAnimator = null
            // The pager's list takes focus when the drawer's search field
            // lets it go (a drag out of search results, say), and with keys
            // typed it drew the system's focus highlight: a grey wash over
            // the whole home screen until the drawer next opened. The
            // settings root's reason (8dc7642).
            defaultFocusHighlightEnabled = false
            com.supernova.launcher.ui.PageFlingSettle.attach(this)
        }
        binding.workspacePager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (::editMode.isInitialized && editMode.isOpen) editMode.onPageChanged()
                val pages = workspaceAdapter.realPageCount
                val addPage = workspaceAdapter.isAddPage(position)
                // Edit mode's add-page slot reports as the page past the
                // last: the model's current page stays a real one, and the
                // clock rule sees the slot as away from home.
                val page = workspaceAdapter.realPage(position).coerceAtMost(pages - 1)
                viewModel.setCurrentPage(page)
                // Leaving the page takes an open resize frame down with it.
                widgets.activeResizeFrame?.let { frame ->
                    val framePage = viewModel.state.value.model.items.firstOrNull { it.id == frame.itemId }?.page
                    if (addPage || framePage != page) widgets.dismissResizeFrame()
                }
                binding.workspacePager.announceForAccessibility(
                    if (addPage) getString(R.string.add_page) else getString(R.string.page_desc, page + 1, pages)
                )
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                val realPage = workspaceAdapter.realPage(position)
                updateWallpaperOffset(realPage + positionOffset)
                binding.pageIndicator.onPageScrolled(realPage, positionOffset)
                workspaceAdapter.claimWidgetsInView()
            }

            override fun onPageScrollStateChanged(state: Int) {
                binding.pageIndicator.onScrollStateChanged(state)
                val idle = state == androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE
                // The page effects scale, fade and rotate whole pages; a
                // hardware layer per page while they move draws each page
                // once and transforms the texture, instead of re-rasterising
                // every icon and widget on every frame. Dropped again at rest
                // so widgets and badges keep repainting normally.
                setPageHardwareLayers(!idle && appliedScrollEffect != ScrollEffect.NONE)
                if (idle) {
                    workspaceAdapter.onPagerIdle(binding.workspacePager)
                    removal.removeEmptyPages()
                }
            }
        })
    }

    private var pageHardwareLayers = false

    private fun setPageHardwareLayers(on: Boolean) {
        if (on == pageHardwareLayers) return
        pageHardwareLayers = on
        val recycler = binding.workspacePager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView ?: return
        val type = if (on) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
        for (index in 0 until recycler.childCount) {
            recycler.getChildAt(index).setLayerType(type, null)
        }
    }

    // Parallax for live and static wallpapers: report our scroll position as a
    // 0..1 offset so the wallpaper pans with the pages.
    private fun updateWallpaperOffset(scrollPosition: Float) {
        val mode = WallpaperScrollMode.fromPrefs(viewModel.state.value.prefs)
        if (mode == WallpaperScrollMode.OFF) return
        val token = binding.root.windowToken ?: return
        val pages = workspaceAdapter.realPageCount
        val offset = mode.offset(scrollPosition, pages)
        // Steps depend only on the page count, so they go only when it moves.
        val step = if (pages != lastWallpaperPages) {
            lastWallpaperPages = pages
            if (pages <= 1) 0f else 1f / (pages - 1)
        } else {
            null
        }
        com.supernova.launcher.ui.WallpaperOffsets.send(this, token, offset, step)
    }

    private fun setupDrawer() {
        drawer = DrawerController(
            binding = binding,
            viewModel = viewModel,
            decorateIcon = { icon, app -> itemIcons.withBadge(icon, app.packageName, app.className, app.userSerial) },
            onAppClick = { view, app -> launchApp(app, view) },
            onAppLongPress = drawerMenus::longPressApp,
            onWebSearch = { query ->
                val engine = com.supernova.launcher.search.WebEngine.of(viewModel.state.value.prefs)
                if (openUri(engine.searchUrl(query))) hideDrawer()
            },
            onChipSearch = { chip, query ->
                if (openUri(chip.url(query), chip.fallbackUrl(query))) hideDrawer()
            },
            // Deferred: `search` is initialized right after the drawer, and
            // this only runs on a user tap.
            onGlobalSearch = {
                hideDrawer()
                search.open()
            },
            buildFolderIcon = { group, members, onLate ->
                itemIcons.drawerFolderIcon(group, members, onLate = onLate)
            },
            decorateFolderIcon = itemIcons::withFolderBadge,
            onFolderClick = { view, group, members -> folderCards.showDrawerFolder(view, group, members) },
            onFolderLongPress = drawerMenus::longPressFolder,
            onTabMenu = pickers::tabMenu,
            onSelectTabApps = pickers::groupApps,
            customTabIcon = { group ->
                itemIcons.customFolderIcon(group.iconBase64, group.iconPack, group.iconName, group.iconRaw)
            },
            onSearchBarLongPress = ::showDrawerSearchBarMenu
        )
        // Covers every close path (back, close button, drag-dismiss); opening
        // is also triggered directly from openDrawer for immediacy.
        drawer.onOpenStateChanged = { open ->
            animateWallpaperZoom(if (open) 1f else 0f)
        }
    }

    // Search's private space row (PrivateSpaceSearchProvider): offered
    // while the phone has a private space, with the action's own tile.
    private fun privateSpaceSearchLabel(): String? =
        viewModel.privateSpace()?.let { getString(R.string.action_private_space) }

    private fun privateSpaceSearchIcon(): android.graphics.drawable.Drawable? =
        androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.ic_action_private_space)
            ?.let { viewModel.appCatalog().renderActionTile(it) }

    private fun setupSearch() {
        search = SearchController(
            binding = binding,
            viewModel = viewModel,
            scope = lifecycleScope,
            onLaunchApp = { view, app -> launchApp(app, view) },
            onLaunchShortcut = { view, shortcut ->
                launchShortcut(shortcut.packageName, shortcut.shortcutId, shortcut.userSerial, view)
            },
            onOpenUri = ::openUri,
            onAppInfo = { app ->
                val user = viewModel.appCatalog().userForSerial(app.userSerial)
                    ?: android.os.Process.myUserHandle()
                runLogged("open app info") {
                    getSystemService(android.content.pm.LauncherApps::class.java).startAppDetailsActivity(
                        android.content.ComponentName(app.packageName, app.className), user, null, null
                    )
                }
            },
            folderHits = {
                val items = viewModel.state.value.model.items
                    .filter { it.itemType == ItemType.FOLDER }
                    .map { com.supernova.launcher.search.FolderSearchProvider.Hit(it.id, null, it.label) }
                val groups = viewModel.drawerFolderGroups()
                    .map { com.supernova.launcher.search.FolderSearchProvider.Hit(null, it.id, it.name) }
                items + groups
            },
            folderIcon = { hit ->
                hit.itemId?.let { id ->
                    viewModel.state.value.model.items.firstOrNull { it.id == id }
                        ?.let(itemIcons::folderPreviewDrawable)
                } ?: hit.groupId?.let { id ->
                    viewModel.drawerFolderGroups().firstOrNull { it.id == id }?.let { group ->
                        itemIcons.drawerFolderIcon(group, viewModel.drawerFolderApps(group))
                    }
                }
            },
            onOpenFolder = { folder ->
                if (folder.privateSpace) {
                    privateSpaceCard.open(null)
                } else if (folder.itemId != null) {
                    viewModel.state.value.model.items.firstOrNull { it.id == folder.itemId }
                        ?.let { folderCards.showFolder(binding.workspacePager, it) }
                } else {
                    viewModel.drawerFolderGroups().firstOrNull { it.id == folder.groupId }?.let {
                        folderCards.showDrawerFolder(binding.workspacePager, it, viewModel.drawerFolderApps(it))
                    }
                }
            },
            // A card launch from search hides the overlay, as every result does.
            cardBinder = cards.binder(afterLaunch = { search.hide() }),
            drawerColumns = { drawer.columns() },
            drawerTiles = { drawer.tileLook() },
            onShown = ::holdHomeUnderSearch,
            // A widget card it showed took the feed card's widget view.
            onHidden = {
                drawer.holdHomeForSearch(false)
                if (::feed.isInitialized) {
                    feed.setBehindSearch(false)
                    if (feed.isOpen) feed.rebindCards()
                }
            },
            // Search's apps, shortcuts and drawer folders are drawer icons
            // too, in the drawer's field or the search window alike.
            onAppLongPress = drawerMenus::longPressApp,
            onShortcutLongPress = { view, shortcut ->
                drawerMenus.longPressShortcut(
                    view,
                    DragPayload.DrawerShortcut(
                        shortcut.packageName, shortcut.shortcutId, shortcut.userSerial, shortcut.label, shortcut.icon
                    )
                )
            },
            onFolderLongPress = { view, folder ->
                viewModel.drawerFolderGroups().firstOrNull { it.id == folder.groupId }?.let { group ->
                    drawerMenus.longPressFolder(view, group, viewModel.drawerFolderApps(group))
                }
            },
            privateSpaceLabel = ::privateSpaceSearchLabel,
            privateSpaceIcon = ::privateSpaceSearchIcon
        )
        // The drawer search is the search window: the drawer's field feeds
        // the same results (top hit, bold tiles, "Search with") and completes
        // inline. An app or shortcut launched from there behaves like any
        // drawer tap; everything else closes the drawer on its way out.
        drawer.externalSearch = search.attachDrawer(
            input = binding.drawerSearch,
            ghost = binding.drawerSearchGhost,
            results = binding.drawerSearchResults,
            cards = cards.binder(afterLaunch = { hideDrawer() }),
            afterLaunch = {},
            dismiss = { hideDrawer() }
        )
        // The home bar opens the drawer, not the search window: the drawer
        // arrives with its own field focused and the keyboard up, whatever
        // the drawer's keyboard setting. The bar's search chip and the
        // gestures still reach the global overlay.
        binding.desktopSearchBar.onTap = { openDrawer(forSearch = true) }
        binding.desktopSearchBar.onLongPress = { anchor -> showSearchBarMenu(anchor) }
        // Nova's weather in the bar, read from Breezy Weather.
        binding.desktopSearchBar.onChip = weatherChip::onChip
        lifecycle.addObserver(weatherChip)
    }

    private val weatherChip by lazy {
        com.supernova.launcher.ui.WeatherChip(this, binding.desktopSearchBar) {
            SearchBarConfig.decode(viewModel.state.value.prefs.searchBarConfig).desktop.chips
                .any { it == com.supernova.launcher.ui.SearchBarChip.WEATHER.name }
        }
    }

    // The Direct dial intent waiting on the phone permission. Refused, it
    // opens the dialer with the number in instead.
    private var pendingCall: Intent? = null

    private val requestCallPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val call = pendingCall ?: return@registerForActivityResult
            pendingCall = null
            val intent = if (granted) call else Intent(call).setAction(Intent.ACTION_DIAL).setComponent(null)
            runLogged("launch direct dial") { startActivity(intent) }
                .onFailure { Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show() }
        }

    // In-card grant buttons on the feed's calendar/contacts cards; a grant
    // refreshes the still-open feed so the card upgrades in place.
    private val requestFeedCalendarPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && ::feed.isInitialized) feed.refreshSources()
        }

    private val requestFeedContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && ::feed.isInitialized) feed.refreshSources()
        }

    private fun setupFeed() {
        feed = FeedController(
            binding = binding,
            viewModel = viewModel,
            scope = lifecycleScope,
            // In the feed a long-press picks a card up; its menu opens when
            // it is let go unmoved.
            cardBinder = cards.binder(afterLaunch = null, inFeed = true),
            canPullFromWorkspace = ::feedPullAllowed,
            itemDragArmed = { drag.isArmed || drag.isActive },
            host = object : FeedController.Host {
                override fun onOpenSearch() {
                    feed.setBehindSearch(true)
                    search.open()
                }

                override fun requestCalendarPermission() {
                    val permission = android.Manifest.permission.READ_CALENDAR
                    com.supernova.launcher.ui.PermissionPrompts.askRuntime(this@LauncherActivity, permission) {
                        requestFeedCalendarPermission.launch(permission)
                    }
                }

                override fun requestContactsPermission() {
                    val permission = android.Manifest.permission.READ_CONTACTS
                    com.supernova.launcher.ui.PermissionPrompts.askRuntime(this@LauncherActivity, permission) {
                        requestFeedContactsPermission.launch(permission)
                    }
                }

                override fun requestMediaAccess() {
                    com.supernova.launcher.ui.PermissionPrompts.explainNotificationAccess(
                        this@LauncherActivity, R.string.notification_access_media
                    )
                }

                override fun openCalendarEvent(eventId: Long) {
                    runLogged("open calendar event") {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                android.content.ContentUris.withAppendedId(
                                    android.provider.CalendarContract.Events.CONTENT_URI, eventId
                                )
                            )
                        )
                    }
                }

                override fun openContact(
                    contact: com.supernova.launcher.feed.ContactsCardSource.Starred,
                    anchor: View
                ) {
                    val uri = android.provider.ContactsContract.Contacts.getLookupUri(
                        contact.contactId, contact.lookupKey
                    )
                    runCatching {
                        android.provider.ContactsContract.QuickContact.showQuickContact(
                            this@LauncherActivity, anchor, uri,
                            android.provider.ContactsContract.QuickContact.MODE_LARGE, null
                        )
                    }.onFailure {
                        runLogged("open contact") { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    }
                }

                override fun addCard() = cards.add()

                override fun addWidgetCard() = cards.addWidget()

                override fun addShortcutsCard() = cards.edit(null)

                override fun openFeedSettings() {
                    startActivity(
                        Intent(this@LauncherActivity, SettingsActivity::class.java)
                            .putExtra(SettingsActivity.EXTRA_SECTION_TITLE_RES, R.string.settings_section_feed)
                    )
                }
            }
        )
        feed.overlay = discover
        // Edge swipes flip pages rather than run system Back, while nothing
        // is over the home screen.
        com.supernova.launcher.ui.HomeEdgeSwipes(
            binding.workspacePager,
            homeInFront = {
                !((::drawer.isInitialized && drawer.isVisible) ||
                    (::search.isInitialized && search.isOpen) ||
                    (::editMode.isInitialized && editMode.isOpen) ||
                    feed.isOpen || desktopPreviews.isShowing)
            }
        )
        // A launch from the drawer leaves it open behind the app (unless Close
        // drawer after launching is on), so its frequent row catches up here
        // rather than on the next open. The feed pane watches for itself.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.launchGeneration.collect {
                    if (drawer.isVisible) drawer.refreshFrequentRow()
                }
            }
        }

        // Page 0's left edge draws no overscroll glow: a clearly horizontal
        // drag there is the feed pull (FeedController.onTouch takes the
        // stream before the pager can), and anything less horizontal is a
        // vertical gesture that merely drifted - which must NOT open the
        // feed. The old snap-open on overscroll fired on exactly that drift.
        (binding.workspacePager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView)
            ?.edgeEffectFactory = object : androidx.recyclerview.widget.RecyclerView.EdgeEffectFactory() {
            override fun createEdgeEffect(
                view: androidx.recyclerview.widget.RecyclerView,
                direction: Int
            ): android.widget.EdgeEffect {
                if (direction != DIRECTION_LEFT) return super.createEdgeEffect(view, direction)
                return object : android.widget.EdgeEffect(view.context) {
                    override fun onPull(deltaDistance: Float) = Unit

                    override fun onPull(deltaDistance: Float, displacement: Float) = Unit
                }
            }
        }
    }

    // Search targets (web engines, chips) launch without probing for a
    // handler (no <queries> entry): a refusal tries the fallback page, and
    // with nothing to open either the user is told. False then, so the search
    // that asked stays open on its query.
    private fun openUri(uri: String, fallback: String? = null): Boolean {
        fun open(target: String) =
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target))) }.isSuccess
        val opened = open(uri) || (fallback != null && open(fallback))
        if (!opened) Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show()
        return opened
    }

    private fun setupDock() {
        // The dock sizes itself to its icon columns, so its height is only
        // known once it has been laid out - and the workspace, indicator and
        // an above-dock search bar are all spaced off that height. Reading it
        // back here keeps them in step with a label that wrapped, an icon
        // scale change or a font-size change, whatever caused it. The margins
        // can't be rewritten from inside a layout pass, so dockChromeWatch
        // applies them after it, before the pass draws.
        binding.dockBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val height = bottom - top
            if (height > 0 && height != dockHeightPx) {
                dockHeightPx = height
                dockChromePending = true
                val observer = binding.root.viewTreeObserver
                observer.removeOnPreDrawListener(dockChromeWatch)
                observer.addOnPreDrawListener(dockChromeWatch)
            }
        }
    }

    // A dock height that moved in a layout pass leaves everything spaced off
    // it - the workspace, the page indicator, an above-dock search bar - laid
    // out against the old height, and a posted re-space came too late: the
    // frame drew first. On a cold start the dock fills in the same pass page
    // 0 first binds, and the post only ran after the next frame as well, so
    // the page and its indicator drew twice sized for the empty dock before
    // jumping up. Instead the chrome is re-spaced between layout and draw and
    // the frame dropped, so the pass re-runs against the new margins before
    // anything reaches the screen.
    //
    // Only a height that really moved holds a frame, and at most
    // MAX_DOCK_CHROME_HOLDS in a row: a cold start can move it in back-to-back
    // passes (the empty dock, then its icons), but a height still moving
    // after that is not settling, and holding every frame would freeze the
    // screen.
    private var dockChromePending = false
    private var dockChromeHolds = 0
    private val dockChromeWatch = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            if (dockChromePending) {
                dockChromePending = false
                dockChrome.apply(viewModel.state.value.prefs)
                if (dockChromeHolds < MAX_DOCK_CHROME_HOLDS) {
                    dockChromeHolds++
                    return false
                }
            }
            // Laid out against the chrome as it stands (or out of holds).
            binding.root.viewTreeObserver.removeOnPreDrawListener(this)
            dockChromeHolds = 0
            return true
        }
    }

    // dockRememberPosition: pull the persisted page back in (0 when the pref
    // is off); renderDock clamps it against the current page count.
    // Back to the dock's main page (page 0, the middle one): a Home press on
    // the home screen and the Default page action slide it there; leaving home
    // with Remember position off puts it there unseen.
    private fun showMainDockPage(animate: Boolean) {
        if (dockPage == 0) return
        val state = viewModel.state.value
        val direction = com.supernova.launcher.ui.DockPages.direction(dockPage, 0, state.prefs.dockPages)
        dockPage = 0
        if (state.prefs.dockRememberPosition) viewModel.persistDockPage(0)
        // Not yet bindable: the emission that flips iconsReady renders page 0.
        if (state.iconsReady) renderDock(state, slide = if (animate) direction else 0)
    }

    private fun restoreDockPage() {
        lifecycleScope.launch {
            val saved = viewModel.restoredDockPage()
            if (saved > 0 && saved != dockPage) {
                dockPage = saved
                val state = viewModel.state.value
                // Not yet bindable: the state emission that flips iconsReady
                // renders the dock on this page anyway.
                if (state.iconsReady) renderDock(state)
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // The stateIn placeholder would paint an empty workspace
                    // for one frame before the real layout decodes.
                    if (!state.loaded) return@collect
                    widgets.rebindRestoredWidgets(state.model)
                    ThemeEngine.applyMode(this@LauncherActivity, state.prefs)
                    if (state.prefs.accentColor != appliedAccent) {
                        appliedAccent = state.prefs.accentColor
                        recreate()
                        return@collect
                    }
                    pendingEditAddPage?.let { pending ->
                        if (pending.landed && state.currentPage == pending.page) {
                            pendingEditAddPage = null
                            pending.then?.invoke()
                        }
                    }
                    // A page swipe emits a state differing only in currentPage;
                    // everything below is page-agnostic, so skip it and leave
                    // the settle animation's frames alone.
                    val last = lastRenderedState
                    lastRenderedState = state
                    if (last != null && state.onlyPageMovedFrom(last)) return@collect
                    // Disable-animations forces the NONE transformer path.
                    applyScrollEffect(
                        if (Animations.pageEffects(state.prefs)) {
                            state.prefs.scrollEffect
                        } else {
                            ScrollEffect.NONE
                        }
                    )
                    applySystemChrome(state.prefs)
                    feed.onPrefs(state.prefs)
                    // Everything above needs no icons, so it runs as soon as
                    // the persisted state decodes. Everything below binds
                    // icons: doing that before the catalog is configured and
                    // the first frame's icons are warmed renders them on this
                    // thread, and with an icon pack draws stock icons that
                    // are evicted moments later. The generation going 0 -> 1
                    // brings us straight back here.
                    if (!state.iconsReady) return@collect
                    if (!reportedFullyDrawn) {
                        reportedFullyDrawn = true
                        runCatching { reportFullyDrawn() }
                    }
                    drawer.onState(state)
                    // Search lists what the apps, the hidden set, app edits
                    // and the drawer's groups made; a change to any of them
                    // (Hide, Edit or Rename from one of its own menus, an
                    // install) runs a showing query again.
                    if (last != null && (last.apps !== state.apps ||
                            last.prefs.hiddenApps != state.prefs.hiddenApps ||
                            last.prefs.drawerGroups != state.prefs.drawerGroups ||
                            last.model.appOverrides != state.model.appOverrides)
                    ) {
                        search.refresh()
                    } else if (last != null && last.pausedWorkProfiles != state.pausedWorkProfiles) {
                        // Work apps pausing or turning back on greys or
                        // restores their icons in the results.
                        search.refresh()
                    }
                    workspaceAdapter.refresh()
                    workspaceAdapter.recenterIfNeeded(binding.workspacePager)
                    drag.carryOverToNewPage()
                    removal.onPageCountChanged()
                    // Edit mode's Remove page follows the page emptying or filling.
                    if (::editMode.isInitialized) editMode.onPageChanged()
                    // Snapshot once the real layout has arrived, so a bad edit
                    // later in the session is recoverable.
                    if (state.model.items.isNotEmpty()) viewModel.writeAutoBackupOnce()
                    // Its own color, or the labels' as the swipe arrow has.
                    binding.pageIndicator.configure(
                        state.prefs.pageIndicatorStyle,
                        workspaceAdapter.realPageCount,
                        state.prefs.pageIndicatorAutoHide,
                        LabelStyler.parseColor(
                            state.prefs.pageIndicatorColor.ifEmpty { state.prefs.labelColor },
                            getColor(R.color.onWallpaper)
                        ),
                        Animations.factor(state.prefs)
                    )
                    // Edit mode's "+" page asked for a page; it exists now, so
                    // leave edit mode on it. When not looping the pager already
                    // sits at its position - the slot became the page - so no
                    // scroll event tells the indicator and the wallpaper.
                    pendingEditAddPage?.let { pending ->
                        if (!pending.landed && pending.page < workspaceAdapter.realPageCount) {
                            pending.landed = true
                            if (pending.then == null) pendingEditAddPage = null
                            editMode.exit()
                            binding.workspacePager.setCurrentItem(
                                workspaceAdapter.centerPosition(pending.page), false
                            )
                            viewModel.setCurrentPage(pending.page)
                            binding.pageIndicator.onPageScrolled(pending.page, 0f)
                            updateWallpaperOffset(pending.page.toFloat())
                        }
                    }
                    renderDock(state)
                }
            }
        }
    }

    private fun applyScrollEffect(effect: ScrollEffect) {
        if (effect == appliedScrollEffect) return
        appliedScrollEffect = effect
        // Transformers only reset the properties they mutate, so the previous
        // effect's leftovers (rotation, alpha, ...) must be cleared by hand.
        (binding.workspacePager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView)?.let { recycler ->
            for (index in 0 until recycler.childCount) {
                PageTransformers.resetTransforms(recycler.getChildAt(index))
            }
        }
        binding.workspacePager.setPageTransformer(PageTransformers.forEffect(effect))
    }

    // Status bar visibility/icon tint, nav bar transparency and rotation
    // (Part E). Guarded so repeat emissions don't touch the window.
    private fun applySystemChrome(prefs: LauncherPrefs) {
        val lightBars = com.supernova.launcher.ui.SystemBarIcons.lightStatusBar(this, prefs)
        val lightNav = com.supernova.launcher.ui.SystemBarIcons.lightNavBar(this, prefs)
        val key = listOf(
            prefs.hideStatusBar, lightBars, lightNav, prefs.transparentNav,
            prefs.transparentStatus, prefs.homeRotation,
            prefs.homeOrientation
        )
        if (appliedSystemChrome == key) return
        appliedSystemChrome = key

        applyStatusBarVisibility(prefs.hideStatusBar)

        WindowInsetsControllerCompat(window, binding.root).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightNav
        }

        window.statusBarColor =
            if (prefs.transparentStatus) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK
        // Same contrast scrim story as the nav bar below, on the other edge.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = !prefs.transparentStatus
        }
        window.navigationBarColor =
            if (prefs.transparentNav) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK
        // Without this the system paints its own translucent scrim over a
        // transparent nav bar (API 29+ contrast enforcement), leaving a dim
        // strip instead of true wallpaper.
        // Contrast scrim control is Android 10+; Android 9 keeps its default.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = !prefs.transparentNav
        }
        // From targetSdk 35 the window colours above are ignored (the bars
        // are always transparent), so the opaque choice is drawn by hand.
        binding.statusBarBand.isVisible = !prefs.transparentStatus && !prefs.hideStatusBar
        binding.navBarBand.isVisible = !prefs.transparentNav

        requestedOrientation = homeOrientationRequest(prefs)
    }

    // Landscape keeps the portrait grid: every icon stays in its cell,
    // and the cells reshape to the wider window. The manifest doesn't pin
    // orientation; this is the one pin. DEFAULT is portrait on a phone
    // and follows the system's auto-rotate on a tablet, which sits in a
    // stand or a keyboard case as often as in a hand (Android 16 ignores
    // a portrait pin on large screens anyway). The forced axes use the
    // sensor variants so the 180-degree flip still works.
    private fun homeOrientationRequest(prefs: LauncherPrefs): Int = when (HomeOrientation.fromPrefs(prefs)) {
        HomeOrientation.DEFAULT ->
            if (com.supernova.launcher.data.DeviceProfile.isWide(this)) {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        HomeOrientation.AUTO_ROTATE -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        HomeOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        HomeOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun applyStatusBarVisibility(hide: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return
            if (hide) {
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(android.view.WindowInsets.Type.statusBars())
            } else {
                controller.show(android.view.WindowInsets.Type.statusBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (hide) {
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_FULLSCREEN
            } else {
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN.inv()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) focusGainedAt = SystemClock.uptimeMillis()
        // A launched app can restore the status bar; re-hide on return.
        if (hasFocus && viewModel.state.value.prefs.hideStatusBar) {
            applyStatusBarVisibility(true)
        }
    }

    // Whether the workspace pager can still move in a direction (+1 = next
    // page). Horizontal edge-swipe gestures fire only when it can't.
    private fun workspaceCanScroll(direction: Int): Boolean {
        val pages = workspaceAdapter.realPageCount
        if (viewModel.state.value.prefs.infiniteScroll && pages > 1) return true
        val page = workspaceAdapter.realPage(binding.workspacePager.currentItem)
        return if (direction > 0) page < pages - 1 else page > 0
    }

    // force: the dock's content changed without anything in its signature
    // moving. Two callers need it - a badge count, and the day rolling over
    // under a calendar icon - because neither lives in the prefs or the model.
    // slide: a page turn to animate, 1 when the new page comes in from the
    // right and -1 from the left; 0 rebuilds in place.
    // A page turn slides when animations are on and the dock is on screen,
    // never under a drag (its drop lookup reads the slots' places).
    private fun dockSlides(slide: Int, prefs: LauncherPrefs): Boolean {
        val wanted = slide != 0 && Animations.enabled(prefs)
        return wanted && binding.dockBar.isShown && !drag.isActive
    }

    private fun renderDock(state: LauncherUiState, force: Boolean = false, slide: Int = 0) {
        val key = dockSignature(state)
        if (!force && renderedDock == key) return
        renderedDock = key
        val prefs = state.prefs
        dockSlide.cancel()
        val leaving = if (dockSlides(slide, prefs)) dockSlide.capture() else null
        binding.dockBar.removeAllViews()
        if (!prefs.dockEnabled) {
            dockChrome.apply(prefs)
            return
        }

        // Fewer pages than the one showing: back to the main page.
        if (dockPage >= prefs.dockPages) dockPage = 0
        val dockByRank = viewModel.dockItems().associateBy { it.rank }
        val rankBase = dockPage * prefs.dockCount
        // What each icon column has to work with, so the icon can be sized to
        // its slot rather than overflowing it. The dock's laid-out width is
        // authoritative once there is one; before that the window minus the
        // dock's own margins is the same number.
        dockSlotWidthPx = dockGeometry.contentWidth(prefs) / prefs.dockCount.coerceAtLeast(1)
        // An empty dock still has to be a drop target, so it never collapses
        // below a bare icon column even with every slot empty.
        binding.dockBar.minimumHeight = dockIconBoxPx(prefs) +
            dp(DOCK_ITEM_PADDING_V_DP * 2 + DOCK_SLOT_PADDING_V_DP * 2)

        repeat(prefs.dockCount) { slotIndex ->
            val rank = rankBase + slotIndex
            val slot = FrameLayout(this).apply {
                // No horizontal inset: the LinearLayout already spaces the
                // slots evenly, so padding here only narrowed the label - the
                // reason "Chrome" used to render as "Chro".
                setPadding(0, dp(DOCK_SLOT_PADDING_V_DP), 0, dp(DOCK_SLOT_PADDING_V_DP))
                // A corner badge (#250) reaches past its tile.
                clipChildren = false
                clipToPadding = false
                // An empty slot takes its own taps, so they don't fall
                // through to whatever lies behind the dock. TalkBack passes
                // over the slot itself: the item in it speaks for itself.
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                // The drag layer turns "the slot under the finger" into a rank.
                setTag(R.id.tag_dock_rank, rank)
            }

            dockByRank[rank]?.let { item ->
                val itemView = viewForItem(item)
                if (itemView.parent != null) {
                    (itemView.parent as? ViewGroup)?.removeView(itemView)
                }
                // Same as the workspace rebind: never inherit the
                // drag-source hiding on a reused view - but an item whose
                // drop is still settling stays hidden until it lands.
                itemView.alpha = if (drag.isSettling(item.id)) 0f else 1f
                slot.addView(
                    itemView,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
            }

            binding.dockBar.addView(
                slot,
                android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            )
        }
        // The slots go in at MATCH_PARENT under a wrap_content dock, so they
        // come out uniform at the tallest column's height and every icon lines
        // up whether or not its neighbour's label wrapped. The chrome goes on
        // after the slots: the height it spaces from is theirs.
        dockChrome.apply(prefs)
        leaving?.let { dockSlide.play(it, slide, Animations.duration(prefs, DOCK_SLIDE_MS)) }
    }

    // Everything the dock draws from. renderDock tears the row down with
    // removeAllViews and rebuilds every slot, and it runs on every state
    // emission that gets past observeState's guard - so dragging an icon
    // around page 3 re-inflated the dock on every model write.
    //
    // The whole prefs object goes in rather than a list of the dock-relevant
    // fields: a pref change is rare and user-driven, so rebuilding on all of
    // them costs nothing, while missing one would leave the dock stale. The
    // laid-out width is in for the same reason - the icon box is sized from
    // it, and the first render can happen before the row has been measured.
    private fun dockSignature(state: LauncherUiState): List<Any?> {
        val items = viewModel.dockItems().joinToString(";") { item ->
            "${item.id}:${item.rank}:${item.label}:${item.packageName}/${item.className}" +
                // The swipes decide the icon's touch handling and its mark.
                ":${item.swipeAction}:${item.swipeActionDown}:${item.swipeToOpen}" +
                PausedIcon.mark(state, item.userSerial) +
                FolderIconDrawable.customIconKey(item) +
                // A folder's icon is drawn from its members, which are not
                // dock items themselves, or from its own custom icon.
                if (item.itemType == ItemType.FOLDER) {
                    viewModel.folderMembers(item.id)
                        .joinToString(",", "[", "]") {
                            "${it.id}:${it.packageName}/${it.className}/${it.shortcutEdit?.hashCode()}" +
                                "/${it.appEdit?.hashCode()}" +
                                PausedIcon.mark(state, it.userSerial)
                        }
                } else {
                    ""
                }
        }
        return listOf(
            state.prefs,
            state.iconGeneration,
            dockPage,
            drag.settlingItemId,
            isLandscape,
            binding.dockBar.width,
            items
        )
    }

    // The icon box a dock item draws in. The scale rides in the box size
    // rather than in scaleX/scaleY so the row measures what it draws, and the
    // box never outgrows its slot - a seven-icon dock hands out slots narrower
    // than the 42dp base, and an oversized icon used to be clipped to them.
    override fun dockIconBoxPx(prefs: LauncherPrefs): Int {
        val scale = if (prefs.dockMatchDesktop) prefs.iconScale else prefs.dockIconScale
        val box = (dp(ICON_BASE_DP) * scale).toInt()
        val slotLimit = dockSlotWidthPx - dp(DOCK_ITEM_PADDING_H_DP * 2)
        return if (slotLimit > 0) minOf(box, slotLimit) else box
    }

    // The icon box a desktop item draws in, sized for the same reason. It has
    // no slot to stay inside: the canvas places an item by its centre, and a
    // big icon's item may be larger than its cell.
    override fun desktopIconBoxPx(prefs: LauncherPrefs): Int = (dp(ICON_BASE_DP) * prefs.iconScale).toInt()

    // How many cells a side a desktop icon takes, and how many times the
    // desktop size its icon is drawn: 2 for a large icon.
    private fun iconSpan(item: LauncherItem): Int = if (item.isLargeIcon) LARGE_ICON_SPAN else 1

    // The room a desktop icon takes at its own size: its box inside the
    // item's padding, over a line of label when labels show. A cell smaller
    // than that shrinks the icon into it (WorkspaceCanvas.IconFit).
    private var desktopNeedPrefs: LauncherPrefs? = null
    private var desktopNeed = android.util.Size(0, 0)
    override fun desktopIconNeedPx(prefs: LauncherPrefs): android.util.Size {
        if (prefs !== desktopNeedPrefs) {
            val box = desktopIconBoxPx(prefs) + dp(ICON_ITEM_PADDING_DP) * 2
            val label = if (prefs.showLabels) {
                val probe = android.widget.TextView(this)
                LabelStyler.apply(
                    probe, prefs, onWallpaper = true, context = com.supernova.launcher.ui.LabelContext.DESKTOP
                )
                probe.lineHeight
            } else {
                0
            }
            desktopNeed = android.util.Size(box, box + label)
            desktopNeedPrefs = prefs
        }
        return desktopNeed
    }

    // Edge-to-edge: the window draws behind the status and navigation bars so
    // the wallpaper is unbroken. Nothing is consumed here - the drawer sheet,
    // the search overlay, the feed pane and the edit-mode overlay all run their
    // own listeners downstream of this one.
    private fun setUpEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Without SHORT_EDGES the window stops at the cutout in landscape,
        // leaving a bare strip down the camera edge. The listener below already
        // unions displayCutout() in, so content still clears the notch.
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            if (bars != systemInsets) {
                systemInsets = bars
                applyWindowInsets()
            }
            insets
        }
    }

    // Fans the current insets out to the chrome that is always present. The
    // dock, workspace, indicator and desktop bar take theirs inside
    // DockChrome, which already owns every margin on those views.
    private fun applyWindowInsets() {
        binding.scrimTop.updateLayoutParams { height = dp(96) + systemInsets.top }
        binding.scrimBottom.updateLayoutParams { height = dp(96) + systemInsets.bottom }
        binding.statusBarBand.updateLayoutParams { height = systemInsets.top }
        binding.navBarBand.updateLayoutParams { height = systemInsets.bottom }
        if (::drawer.isInitialized) drawer.setSystemInsets(systemInsets)
        binding.dropTargetBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = systemInsets.top + dp(16)
        }
        // DockChrome's guard key carries systemInsets, so this re-applies.
        dockChrome.apply(viewModel.state.value.prefs)
    }

    private fun openDrawer(forSearch: Boolean = false) {
        animateWallpaperZoom(1f)
        drawer.open(forSearch)
    }

    // Ramp the wallpaper zoom toward target (0 = normal, 1 = zoomed out).
    // Zooming in requires the pref; restoring to 0 is always allowed so a
    // mid-open pref flip can't strand a zoomed wallpaper.
    override fun animateWallpaperZoom(target: Float) {
        if (wallpaperZoomBroken) return
        val method = com.supernova.launcher.ui.WallpaperZoom.method ?: return
        val prefs = viewModel.state.value.prefs
        if (target > 0f && (!prefs.wallpaperZoom || !Animations.enabled(prefs))) return
        val token = binding.root.windowToken ?: return
        if (target == wallpaperZoomTarget) return
        wallpaperZoomTarget = target
        wallpaperZoomAnimator?.cancel()
        // Sent from a background thread (WallpaperOffsets): each is a binder
        // call, per frame of the ramp. A service that throws stops the zoom.
        val onFail: () -> Unit = {
            wallpaperZoomBroken = true
            wallpaperZoomAnimator?.cancel()
        }
        if (!Animations.enabled(prefs)) {
            // Restore-to-0 still applies (a mid-open pref flip can't strand a
            // zoomed wallpaper), just without the ramp.
            wallpaperZoomValue = target
            com.supernova.launcher.ui.WallpaperOffsets.zoom(this, token, method, target, onFail)
            return
        }
        wallpaperZoomAnimator = android.animation.ValueAnimator.ofFloat(wallpaperZoomValue, target).apply {
            duration = Animations.duration(prefs, WALLPAPER_ZOOM_MS)
            addUpdateListener { animator ->
                wallpaperZoomValue = animator.animatedValue as Float
                com.supernova.launcher.ui.WallpaperOffsets.zoom(
                    this@LauncherActivity, token, method, wallpaperZoomValue, onFail
                )
            }
            start()
        }
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    private fun expandNotificationShade() {
        val statusBarService = getSystemService("statusbar") ?: return
        val statusBarManagerClass = runCatching {
            Class.forName("android.app.StatusBarManager")
        }.getOrElse { error ->
            Log.w("Supernova", "Unable to resolve StatusBarManager", error)
            return
        }

        val candidateMethods = listOf("expandNotificationsPanel", "expand")
        var lastError: Throwable? = null

        for (methodName in candidateMethods) {
            val method = runCatching {
                statusBarManagerClass.getMethod(methodName)
            }.getOrNull() ?: runCatching {
                statusBarManagerClass.getDeclaredMethod(methodName).apply { isAccessible = true }
            }.getOrNull()

            if (method != null) {
                val result = runCatching { method.invoke(statusBarService) }
                if (result.isSuccess) {
                    return
                }
                lastError = result.exceptionOrNull()
            }
        }

        Log.w("Supernova", "Unable to expand notification shade from launcher", lastError)
    }

    private fun hideDrawer(instant: Boolean = false) {
        drawer.hide(instant)
    }

    // Everything the home screen can have standing over its default page:
    // menus, popups, edit mode, the page previews, a widget resize frame, the
    // feed, the drawer, search and move mode - then the page itself. Two
    // callers: the home press, and the screen turning off (which keeps the
    // page, as does a press made in another app).
    //
    // Returns whether any of it was actually open, which the home press needs
    // to tell a press with a job to do from a second press with none.
    // keepDrawer spares the drawer alone (the drawerAutoClose pref), keepPage
    // the page (a press that only closed the private space's unlock prompt),
    // and animate = false makes the whole reset instant, for a screen nobody
    // is looking at.
    private fun resetToHome(animate: Boolean, keepDrawer: Boolean, keepPage: Boolean = false): Boolean {
        var closed = false
        homeMenuDialog?.let {
            it.dismiss()
            closed = true
        }
        homeMenuDialog = null
        drag.activeItemPopup?.let {
            it.dismiss()
            closed = true
        }
        drag.activeItemPopup = null
        folderCards.openCard?.let {
            it.dismiss()
            closed = true
        }
        folderCards.openCard = null
        if (::editMode.isInitialized && editMode.isOpen) {
            editMode.exit()
            closed = true
        }
        if (desktopPreviews.isShowing) {
            desktopPreviews.dismiss()
            closed = true
        }
        if (widgets.dismissResizeFrame()) closed = true
        reopenDockIfAutoClosed()
        if (::feed.isInitialized && feed.isShowing) {
            feed.close()
            closed = true
        }
        // A drawer already sliding shut isn't closed by this press: its Home
        // action (open the drawer, by default) runs and turns it round.
        if (::drawer.isInitialized && drawer.isExpanded && !drawer.isClosing) {
            closed = true
            if (!keepDrawer) hideDrawer(instant = !animate)
        }
        if (::search.isInitialized && search.isOpen) {
            search.hide()
            closed = true
        }
        if (!keepPage) binding.workspacePager.setCurrentItem(defaultPagePosition(), animate)
        return closed
    }

    // Where the model's default page sits in the pager (centred when looping).
    private fun defaultPagePosition(): Int {
        val model = viewModel.state.value.model
        return workspaceAdapter.centerPosition(model.defaultPage.coerceIn(0, model.pageCount - 1))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Settings' Add card chose a widget: the feed opens and the picker
        // comes up over it (only the launcher can bind one).
        if (intent.getBooleanExtra(EXTRA_ADD_FEED_WIDGET, false)) {
            intent.removeExtra(EXTRA_ADD_FEED_WIDGET)
            com.supernova.launcher.ui.OpenDialogs.dismissAll(this)
            if (viewModel.state.value.prefs.feedEnabled) feed.open()
            cards.addWidget()
            return
        }
        // Home press re-delivers MAIN/HOME to the singleTask instance: drawer
        // and menus close. cameFromBackground separates the two presses that
        // land here: one made on the home screen itself, and one made in another
        // app to get back to it. Only the first is a home press "on" the
        // launcher, and only it goes to the default page; from an app home
        // comes back on the page the user left (#317), unless Return to
        // default page is on.
        val cameFromBackground = startedSinceResume
        val onHome = pressedOnHome(intent)
        val keepDrawerOpen = keepDrawerForHomePress(cameFromBackground)
        // Read before the reset moves the pager off it.
        // The dock counts too: a press on home with the dock on a side page
        // brings it back to its main page rather than running the action.
        val wasOnDefaultPage = binding.workspacePager.currentItem == defaultPagePosition() && dockPage == 0
        // Whether this press had a job of its own. Anything dismissed by the
        // reset is what the press was for, so the home-button action stays out
        // of it.
        // A sheet or dialog left open is one too: the Edit sheet stayed up
        // and the drawer opened behind it (#91). `or`, so both run.
        // A private space card still waiting on its unlock is called off too,
        // and a swipe home over its unlock prompt is that prompt's close:
        // the home screen stays on its page under it (#135), even when the
        // prompt finished as the swipe began and the launcher is already
        // back in front.
        val promptClosed = privateSpaceCard.cancelPending()
        // With animations off (the launcher's or the system's) home lands
        // at once instead of scrolling past every page in between (#123).
        // From another app the launcher wasn't showing: whatever it closes
        // closes at once (#292), and the page stays put, or with Return to
        // default page on lands on the default page at once, as Launcher3's
        // does: scrolled, a slow return showed the pages in between (#292).
        val keepPage = keepPageForHomePress(onHome, promptClosed)
        val scroll = Animations.scrollPages(viewModel.state.value.prefs) && !cameFromBackground &&
            !movesPageUnseen(onHome, keepPage)
        // A folder card the same swipe closed on its first touch (#110), read
        // after the reset, whose own closes don't count twice.
        val closed = com.supernova.launcher.ui.OpenDialogs.dismissAll(this) or
            resetToHome(animate = scroll, keepDrawer = keepDrawerOpen, keepPage = keepPage) or
            cardSwipeClose.takeFor(SystemClock.uptimeMillis()) or promptClosed
        // A press on the home screen itself also slides the dock back to its
        // main page. One made in another app leaves it, unless onStop already
        // put it there (Remember position off).
        if (onHome && !promptClosed) showMainDockPage(animate = scroll)
        // The double-home action: pressing home when there was nothing to
        // close and nowhere to scroll runs the configured action instead of
        // no-opping. homeButtonAction supersedes the legacy homeSearch flag
        // ("" derives from it), and dispatch reuses the gesture action path.
        // Three things have to hold for the press to count as that second
        // press: the launcher was already what the user was looking at (a
        // press made in another app is how you GET home, not a press on it),
        // it was already showing the main page, and nothing above needed
        // closing - a drag in flight included.
        val prefs = viewModel.state.value.prefs
        val reachedHome = wasOnDefaultPage || !prefs.homeButtonOnMainPage
        if (onHome && reachedHome && !closed && !drag.isActive) {
            val action = HomeButtonAction.effective(prefs.homeButtonAction, prefs.homeSearch)
            if (action != GestureAction.NONE) actions.run(GestureBinding(action))
        }
    }

    // A home press made on the home screen: the launcher was in front, with
    // its window focused for a moment, and no bring to front: a swipe home from the
    // system's recents screen sends more home intents after the launcher has
    // resumed but before it has focus, which went to the default page and ran
    // the home-button action (#317).
    private fun pressedOnHome(intent: Intent): Boolean =
        !startedSinceResume && hasWindowFocus() &&
            SystemClock.uptimeMillis() - focusGainedAt > HOME_FOCUS_SETTLE_MS &&
            intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT == 0

    // Whether a home press leaves the page where it is: one that only closed
    // the private space's unlock prompt does, and so does one made in another
    // app unless Return to default page is on (#317).
    private fun keepPageForHomePress(onHome: Boolean, promptClosed: Boolean): Boolean =
        promptClosed || (!onHome && !viewModel.state.value.prefs.homeReturnToDefaultPage)

    // A page change the user didn't ask for on the home screen itself (Return
    // to default page), made at once rather than scrolled.
    private fun movesPageUnseen(onHome: Boolean, keepPage: Boolean): Boolean = !onHome && !keepPage

    // Whether a home press spares the drawer. drawerAutoClose off keeps a
    // drawer that stayed open behind a launched app open on return home, when
    // Remember position is on; with it off the press closes that drawer at
    // once, so the next open lands on the default tab at the top (#275). A
    // home press while the launcher itself is frontmost resets it like any
    // launcher.
    private fun keepDrawerForHomePress(cameFromBackground: Boolean): Boolean {
        val prefs = viewModel.state.value.prefs
        if (!cameFromBackground || prefs.drawerAutoClose) return false
        if (!prefs.drawerRememberPosition && ::drawer.isInitialized && drawer.isExpanded) {
            hideDrawer(instant = true)
        }
        return prefs.drawerRememberPosition
    }

    private fun launchApp(app: LaunchableApp, anchor: View? = null) {
        launchWithAnimation(anchor) { bounds, options ->
            runLogged("launch app") {
                viewModel.appCatalog().actions.launch(app, bounds, options)
            }.onSuccess {
                viewModel.noteLaunch(app)
                maybeAutoCloseDock()
                maybeAutoCloseDrawer()
            }.onFailure {
                Toast.makeText(this, getString(R.string.unable_to_launch, app.label), Toast.LENGTH_SHORT).show()
            }.isSuccess
        }
    }

    // drawerAutoClose: the drawer slides away behind the opening app or
    // shortcut; off keeps it open, so returning to the launcher (back/recents)
    // lands back in the drawer - and the screen turning off while that app is
    // in front leaves it alone too. A launch with no drawer open (home, dock,
    // gestures, the feed) leaves this nothing to do.
    private fun maybeAutoCloseDrawer() {
        if (!::drawer.isInitialized || !drawer.isExpanded) return
        if (viewModel.state.value.prefs.drawerAutoClose) {
            hideDrawer()
        } else {
            drawerKeptBehindLaunch = true
        }
    }

    // Overlay-only auto-close: the dock hides after a launch and reappears
    // when home is re-entered (onNewIntent/onResume).
    private fun maybeAutoCloseDock() {
        val prefs = viewModel.state.value.prefs
        if (!prefs.dockEnabled || !prefs.dockOverlay || !prefs.dockAutoClose) return
        dockAutoClosed = true
        binding.dockBar.visibility = View.GONE
        dockChrome.syncArrow(prefs)
    }

    private fun reopenDockIfAutoClosed() {
        if (!dockAutoClosed) return
        dockAutoClosed = false
        if (viewModel.state.value.prefs.dockEnabled && !dockToggledOff) {
            binding.dockBar.visibility = View.VISIBLE
        }
        dockChrome.syncArrow(viewModel.state.value.prefs)
    }

    // TOGGLE_DOCK action: transient visibility flip, mirroring the overlay
    // auto-close behavior (reserved margins stay, so no workspace relayout).
    private fun toggleDock() {
        if (!viewModel.state.value.prefs.dockEnabled) return
        dockToggledOff = !dockToggledOff
        binding.dockBar.visibility =
            if (dockToggledOff || dockAutoClosed) View.GONE else View.VISIBLE
        dockChrome.syncArrow(viewModel.state.value.prefs)
    }

    // The app-open transition. Single choke point: every app, shortcut and
    // intent launch routes through here, so the launchAnimation pref and the
    // disable-animations switch apply everywhere. [launch] starts the app
    // with the icon's screen bounds and the transition's options, and says
    // whether it did. Circle and Adaptive icon play their launcher-side part
    // first (LaunchPrelude) and launch as it ends.
    private fun launchWithAnimation(item: View?, launch: (Rect?, android.os.Bundle?) -> Boolean) {
        // A tap while another launch's prelude plays.
        if (launchPrelude.isBusy) return
        val prefs = viewModel.state.value.prefs
        // With animations off: no transition at all.
        val none = if (Animations.enabled(prefs)) null else
            android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
        // From the icon, as Launcher3's opens, rather than the whole item: a
        // desktop icon spans its cell, label and all.
        val anchor = item?.findViewById<View>(R.id.icon) ?: item?.findViewById<View>(R.id.appIcon) ?: item
        if (anchor == null || anchor.width == 0 || anchor.height == 0) {
            launch(null, none)
            return
        }
        val bounds = anchor.screenRect()
        if (none != null) {
            launch(bounds, none)
            return
        }
        val style = prefs.launchAnimation
        val snapshot = if (style == LaunchAnimation.CIRCLE || style == LaunchAnimation.ADAPTIVE_ICON) {
            runLogged("draw launch icon") { anchor.drawToBitmap() }.getOrNull()
        } else {
            null
        }
        if (snapshot == null) {
            launch(bounds, runLogged("build launch options") { launchOptions(style, anchor) }.getOrNull())
            return
        }
        val root = IntArray(2).also { binding.root.getLocationOnScreen(it) }
        val rect = android.graphics.RectF(bounds).apply { offset(-root[0].toFloat(), -root[1].toFloat()) }
        if (style == LaunchAnimation.CIRCLE) {
            val color = viewModel.appCatalog().dominantColorOf(snapshot) ?: android.graphics.Color.WHITE
            launchPrelude.circle(rect.centerX(), rect.centerY(), color, Animations.duration(prefs, CIRCLE_FILL_MS)) {
                if (!launch(bounds, launchOptions(style, anchor))) launchPrelude.clear()
            }
        } else {
            // The press ripple would stay behind as a gray tile where the
            // lifted icon was.
            item?.let {
                it.isPressed = false
                it.jumpDrawablesToCurrentState()
            }
            // The tapped item's height stands in for Launcher3's cell height.
            val cellHeight = (item ?: anchor).height.toFloat()
            launchPrelude.liftIcon(anchor, rect, snapshot, cellHeight, Animations.factor(prefs)) { from ->
                val scaleUp = android.app.ActivityOptions.makeScaleUpAnimation(
                    binding.root, from.left, from.top, from.width(), from.height()
                ).toBundle()
                if (!launch(bounds, scaleUp)) launchPrelude.clear()
            }
        }
    }

    // Each style's options. Circle's is the fade the
    // app opens with once its circle has filled the screen; Adaptive icon
    // falls back to the plain scale-up when its icon can't be pictured.
    private fun launchOptions(style: LaunchAnimation, anchor: View): android.os.Bundle = when (style) {
        LaunchAnimation.SYSTEM, LaunchAnimation.REVEAL -> android.app.ActivityOptions
            .makeClipRevealAnimation(anchor, 0, 0, anchor.width, anchor.height)
        LaunchAnimation.ADAPTIVE_ICON, LaunchAnimation.ZOOM -> android.app.ActivityOptions
            .makeScaleUpAnimation(anchor, 0, 0, anchor.width, anchor.height)
        LaunchAnimation.CIRCLE -> android.app.ActivityOptions
            .makeCustomAnimation(this, R.anim.launch_fade_enter, R.anim.launch_fade_exit)
        LaunchAnimation.SLIDE_UP -> android.app.ActivityOptions
            .makeCustomAnimation(this, R.anim.launch_slide_up_enter, R.anim.launch_slide_up_exit)
        LaunchAnimation.BLINK -> android.app.ActivityOptions
            .makeCustomAnimation(this, R.anim.launch_blink_enter, R.anim.launch_blink_exit)
        LaunchAnimation.SLIDE_LEFT -> android.app.ActivityOptions
            .makeCustomAnimation(this, R.anim.launch_slide_left_enter, R.anim.launch_slide_left_exit)
    }.toBundle()

    private fun launchItem(item: LauncherItem, anchor: View? = null) {
        when (item.itemType) {
            ItemType.FOLDER -> folderCards.showFolder(anchor ?: binding.root, item)
            ItemType.WIDGET -> Unit
            ItemType.APP -> item.toLaunchableApp()?.let { launchApp(it, anchor) }
            ItemType.SHORTCUT -> {
                val uri = item.intentUri
                when {
                    uri != null -> {
                        val action = com.supernova.launcher.gesture.ActionShortcut.decode(uri)
                        if (action != null) actions.run(action, anchor) else launchIntentUri(uri, anchor)
                    }
                    item.packageName != null && item.shortcutId != null ->
                        launchShortcut(item.packageName, item.shortcutId, item.userSerial, anchor)
                    else -> item.toLaunchableApp()?.let { launchApp(it, anchor) }
                }
            }
        }
    }

    // Long-press on a recent search. Pinning keeps the query as a home icon
    // that re-runs it - "make shortcuts from my recent searches".
    // The search-bar long press: swap the bar for a search widget, or jump
    // to the bar's own settings.
    private fun showSearchBarMenu(anchor: View) {
        val menu = menuKit.popup()
        drag.activeItemPopup = menu
        menu.addRow(getString(R.string.search_bar_replace), R.drawable.ic_menu_resize) {
            widgets.showSearchWidgetPicker()
        }
        if (viewModel.state.value.prefs.searchBarWidgetId != 0) {
            menu.addRow(getString(R.string.search_bar_restore), R.drawable.ic_menu_remove) {
                widgets.restoreBuiltInSearchBar()
            }
        }
        menu.addRow(getString(R.string.search_bar_settings), R.drawable.ic_tab_settings) {
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_OPEN_SEARCH_BAR, true)
            )
        }
        menu.show(anchor, menuTouchX)
        menu.makeModal()
    }

    // The drawer search bar held (#217): Paste while the clipboard has text
    // and the field takes typing, and the bar's own settings page.
    private fun showDrawerSearchBarMenu(anchor: View) {
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
        // Only the clip's type is checked here: reading it shows the system's
        // "pasted" notice, so the text is read on the row's tap.
        val canPaste = drawer.searchTypable && clipboard?.hasPrimaryClip() == true &&
            clipboard.primaryClipDescription?.let {
                it.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                    it.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_HTML)
            } == true
        drawer.hideKeyboardIfShowing()
        val menu = menuKit.popup()
        drag.activeItemPopup = menu
        if (canPaste) {
            menu.addRow(getString(android.R.string.paste), R.drawable.ic_menu_paste) {
                val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(this)
                if (!text.isNullOrBlank()) drawer.pasteIntoSearch(text)
            }
        }
        menu.addRow(getString(R.string.search_bar_settings), R.drawable.ic_tab_settings) {
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_SECTION_TITLE_RES, R.string.drawer_search_bar)
            )
        }
        menu.show(anchor, menuTouchX)
        menu.makeModal()
    }

    private fun launchShortcut(
        packageName: String,
        shortcutId: String,
        userSerial: Long,
        anchor: View? = null
    ) {
        // A paused work app's shortcut can't start (an app itself gets the
        // system's own prompt): offer to turn work apps back on instead.
        if (userSerial in viewModel.state.value.pausedWorkProfiles) {
            offerTurnOnWorkApps()
            return
        }
        val launcherApps = getSystemService(LauncherApps::class.java) ?: return
        val user = viewModel.appCatalog().userForSerial(userSerial) ?: Process.myUserHandle()
        launchWithAnimation(anchor) { bounds, options ->
            runLogged("launch shortcut") {
                launcherApps.startShortcut(packageName, shortcutId, bounds, options, user)
            }.onSuccess {
                maybeAutoCloseDock()
                maybeAutoCloseDrawer()
            }.onFailure {
                Toast.makeText(this, R.string.shortcut_failed, Toast.LENGTH_SHORT).show()
            }.isSuccess
        }
    }

    private fun offerTurnOnWorkApps() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.work_apps_paused_title)
            .setPositiveButton(R.string.work_apps_turn_on) { _, _ -> viewModel.setWorkPaused(false) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // Fires an arbitrary intent stored on an item (an activity chosen from the
    // picker, or a legacy created shortcut). Reuses the app-launch reveal.
    private fun launchIntentUri(uri: String, anchor: View?) {
        val intent = runLogged("read intent item") { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull()
        if (intent == null) {
            Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // A Direct dial shortcut places the call itself, which needs the
        // phone permission; ask once, then call (or dial, when refused).
        if (intent.action == Intent.ACTION_CALL &&
            checkSelfPermission(android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingCall = intent
            requestCallPermission.launch(android.Manifest.permission.CALL_PHONE)
            return
        }
        launchWithAnimation(anchor) { bounds, options ->
            intent.sourceBounds = bounds
            runLogged("launch intent item") { startActivity(intent, options) }
                .onSuccess { maybeAutoCloseDock() }
                .onFailure { Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show() }
                .isSuccess
        }
    }

    // What a new item's menu closes as it sends the item home, and what its
    // drag leaves for the home screen: the drawer, the search window, and the
    // feed that search was opened over - the item lands on a home page.
    override fun closeItemSources() {
        // A folder card is a window of its own: a shortcut dragged out of a
        // member's menu left it standing over the home screen. Not the card
        // a drag is carried by, which closes with the drop.
        folderCards.openCard?.takeUnless { drag.ownsGestureIn(it) }?.dismiss()
        hideDrawer()
        if (::search.isInitialized && search.isOpen) search.hide()
        if (::feed.isInitialized && feed.isOpen) feed.close()
    }

    // The installed app a package and user resolve to (a shortcut's owner).
    override fun installedApp(packageName: String, userSerial: Long): LaunchableApp? =
        viewModel.state.value.apps.firstOrNull { it.packageName == packageName && it.userSerial == userSerial }

    // A widget's host id is kept while the Undo bar shows, so Undo brings the
    // same widget back; it is deleted once the bar goes without an Undo.
    override fun removeItemWithUndo(item: LauncherItem) = removal.remove(item)

    // Posted: a drag can end inside the pickup of the next one (its settle cut
    // short), and that drag is under way by the time this runs.
    override fun onDragFinished(sourcePage: Int?) {
        binding.root.post { removal.removeEmptyPages(emptied = sourcePage) }
    }

    // Full screen over the desktop, the search window keeps it faded as the
    // drawer does; the feed hides it already when search opens there.
    private fun holdHomeUnderSearch(fullScreen: Boolean) {
        if (fullScreen && !(::feed.isInitialized && feed.isOpen)) drawer.holdHomeForSearch(true)
    }

    private val desktopLocked: Boolean
        get() = viewModel.state.value.prefs.desktopLocked

    // ---- LiveDragController.Host: what the drag engine asks of the activity
    // beyond the functions above that it shares with the rest of the screen.

    override val drawerExpanded: Boolean
        get() = ::drawer.isInitialized && drawer.isExpanded

    // The dock's top on screen, which a folder card opened over the home
    // screen stays above: none with the dock hidden or the drawer over it.
    private fun dockTopOnScreen(): Int? {
        val dock = binding.dockBar
        if (!dock.isShown || dock.height == 0 || drawerExpanded) return null
        return IntArray(2).also(dock::getLocationOnScreen)[1]
    }

    override fun onGestureDown(ev: MotionEvent) {
        menuTouchX = ev.x
    }

    // Release the hierarchy under a drag that has just begun: the anchor
    // drops its press, the pager or list under it never reads the movement
    // as a scroll, and the gesture detector, which saw the same down, lets
    // go too.
    override fun cancelTouchesBelow(cancel: MotionEvent) {
        super.dispatchTouchEvent(cancel)
        gestureDetector.onTouchEvent(cancel)
    }

    override fun exitEditMode() {
        if (::editMode.isInitialized && editMode.isOpen) editMode.exit()
    }

    override fun dockIconTopInsetPx(): Int = dp(DOCK_SLOT_PADDING_V_DP + DOCK_ITEM_PADDING_V_DP)

    override fun desktopIconBasePx(): Int = dp(ICON_BASE_DP)

    override fun isSettling(itemId: String): Boolean = drag.isSettling(itemId)

    // False when the drop can't be taken: the picture then flies back.
    override fun canMergeIntoDock(payload: DragPayload, rank: Int): Boolean =
        dockMergeItem(payload, preview = true)?.let {
            com.supernova.launcher.data.DockFolders.target(viewModel.state.value.model, it, rank)
        } != null

    // The item a dock merge drops: the dragged one, or a new one for an app
    // or shortcut from the drawer. preview: only asking, so a shortcut isn't
    // pinned yet.
    private fun dockMergeItem(payload: DragPayload, preview: Boolean): LauncherItem? = when (payload) {
        is DragPayload.ExistingItem -> viewModel.state.value.model.items.firstOrNull { it.id == payload.itemId }
        is DragPayload.DrawerApp -> LauncherItem(
            id = java.util.UUID.randomUUID().toString(),
            itemType = ItemType.APP,
            container = ContainerType.DOCK,
            label = payload.app.label,
            packageName = payload.app.packageName,
            className = payload.app.className,
            userSerial = payload.app.userSerial
        )
        is DragPayload.DrawerShortcut -> {
            val shortcut = if (preview) null else shortcuts.homeItem(payload)
            LauncherItem(
                id = java.util.UUID.randomUUID().toString(),
                itemType = ItemType.SHORTCUT,
                container = ContainerType.DOCK,
                label = payload.label,
                packageName = shortcut?.owner?.packageName ?: payload.packageName,
                className = shortcut?.owner?.className,
                shortcutId = payload.shortcutId,
                userSerial = shortcut?.owner?.userSerial ?: payload.userSerial,
                iconBase64 = shortcut?.iconBase64
            )
        }
        else -> null
    }

    override fun handleDropToDock(payload: DragPayload, rank: Int, merge: Boolean): Boolean {
        if (desktopLocked) return false
        if (merge && canMergeIntoDock(payload, rank)) {
            dockMergeItem(payload, preview = false)?.let { viewModel.mergeIntoDock(it, rank) }
            return true
        }
        when (payload) {
            is DragPayload.ExistingItem -> {
                // A folder's app on a docked folder, its own included, can't
                // trade places with it - a folder goes into no folder - and
                // the move leaves both where they are.
                val model = viewModel.state.value.model
                if (LauncherRepository.movedToDock(model, payload.itemId, rank) === model) return false
                viewModel.moveItemToDock(payload.itemId, rank)
            }
            is DragPayload.DrawerApp, is DragPayload.DrawerShortcut, is DragPayload.DrawerFolder -> {
                // A drawer folder with none of its apps showing has nothing
                // to put there.
                if (payload is DragPayload.DrawerFolder && payload.apps.isEmpty()) return false
                val page = workspaceAdapter.realPage(binding.workspacePager.currentItem)
                // A new item onto an occupied slot sends that icon to the
                // home screen, which needs a free cell somewhere.
                val model = viewModel.state.value.model
                val occupied = model.items.any { it.container == ContainerType.DOCK && it.rank == rank }
                if (occupied && (0 until model.pageCount).none { viewModel.firstFreeWorkspaceCell(it) != null }) {
                    Toast.makeText(this, R.string.no_room_anywhere, Toast.LENGTH_SHORT).show()
                    return false
                }
                when (payload) {
                    is DragPayload.DrawerApp -> viewModel.addAppToDock(payload.app, rank, page)
                    is DragPayload.DrawerShortcut -> {
                        val item = shortcuts.homeItem(payload)
                        viewModel.addShortcutToDock(item.owner, payload.shortcutId, payload.label, item.iconBase64, rank, page)
                    }
                    is DragPayload.DrawerFolder -> viewModel.addFolderToDock(payload.group, payload.apps, rank, page)
                    else -> Unit
                }
            }
            // Widgets don't dock, nor do the add sheet's shortcut rows.
            is DragPayload.NewWidget, is DragPayload.NewShortcut -> return false
        }
        return true
    }

    override fun state(): LauncherUiState = viewModel.state.value

    // A home or dock item's icon with its badge. An app's icon, or a
    // folder's member, still to be rendered is left out (an app's draws
    // nothing yet), rendered on the icon thread, and `onLate` runs on the
    // main thread once it is in. A bind used to render it right there.
    private fun itemIcon(item: LauncherItem, onLate: () -> Unit): android.graphics.drawable.Drawable? {
        val app = item.toLaunchableApp()
        val shown = lastItemIcons[item.id]
        val fresh = when (item.itemType) {
            ItemType.FOLDER -> itemIcons.folderPreviewDrawable(item, onLate, pendingAsNull = shown != null)
            ItemType.WIDGET -> getDrawable(R.drawable.ic_widget)
            ItemType.APP -> if (app == null) getDrawable(R.drawable.ic_supernova) else appItemIcon(item, app, onLate)
            ItemType.SHORTCUT -> when (val look = itemIcons.shortcutLook(item, onLate)) {
                null -> null
                else -> look.icon ?: app?.let { appItemIcon(item, it, onLate) } ?: getDrawable(R.drawable.ic_supernova)
            }
        }
        // While a late icon renders, the item keeps the picture it last
        // showed: a page built again blanked its icons until they came
        // back, which read as the home screen reloading (#292).
        return badgedItemIcon(item, fresh?.also { lastItemIcons[item.id] = it } ?: shown)
    }

    private fun badgedItemIcon(
        item: LauncherItem,
        icon: android.graphics.drawable.Drawable?
    ): android.graphics.drawable.Drawable? =
        if (item.itemType == ItemType.FOLDER) {
            icon?.let { itemIcons.withHomeFolderBadge(it, item) }
        } else {
            itemIcons.withBadge(icon, item.packageName, item.className, item.userSerial)
        }

    // Each home and dock item's last icon (before its badge), by item id.
    private val lastItemIcons = HashMap<String, android.graphics.drawable.Drawable>()

    // An item view's icon drawn again where it is (a badge moved).
    private fun redrawItemView(view: View, item: LauncherItem) {
        if (item.itemType == ItemType.WIDGET) return
        runCatching { ItemIconBinding.bind(view) }.getOrNull()?.let { redrawItemIcon(it, item) }
    }

    // The dock's views for these items redrawn in place; false when one of
    // them isn't found, for the caller to build the dock again.
    private fun redrawDockItems(items: List<LauncherItem>): Boolean {
        val wanted = items.associateBy { it.id }
        val found = HashSet<String>()
        fun walk(view: View) {
            wanted[view.getTag(R.id.tag_item_id)]?.let {
                redrawItemView(view, it)
                found += it.id
                return
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) walk(view.getChildAt(index))
        }
        walk(binding.dockBar)
        return found.size == wanted.size
    }

    // A placed app's icon from the cache (its Large copy for a Large icon),
    // or null with the render queued.
    private fun appItemIcon(
        item: LauncherItem,
        app: LaunchableApp,
        onLate: () -> Unit
    ): android.graphics.drawable.Drawable? {
        val catalog = viewModel.appCatalog()
        val large = item.isLargeIcon
        return catalog.cachedIcon(app, item.appEdit, large) ?: run {
            com.supernova.launcher.data.IconThread.renderAll(
                listOf { catalog.iconFor(app, item.appEdit, large) }, onLate
            )
            null
        }
    }

    // A late icon is in: drawn on the view, if it still shows this item.
    private fun redrawItemIcon(binding: ItemIconBinding, item: LauncherItem) {
        if (binding.root.getTag(R.id.tag_item_id) != item.id) return
        binding.icon.setImageDrawable(itemIcon(item) {})
    }

    override fun viewForItem(item: LauncherItem): View {
        if (item.itemType == ItemType.WIDGET) {
            return widgets.buildWidgetView(item).also { itemMoves.attachWidget(it, item.id) }
        }

        val iconBinding = ItemIconBinding.inflate(LayoutInflater.from(this))
        // Icons not rendered yet draw in once they are, off the main thread.
        iconBinding.icon.setImageDrawable(itemIcon(item) { redrawItemIcon(iconBinding, item) })

        val prefs = viewModel.state.value.prefs
        val override = viewModel.overrideFor(item)
        val inDock = item.container == ContainerType.DOCK
        val labelVisible = (if (inDock) prefs.dockLabels else prefs.showLabels) &&
            !(override?.hideLabel ?: false)
        if (inDock) {
            // The dock splits a fixed width dockCount ways, so scaleX/scaleY
            // here drew outside the slot and was clipped by it. Sizing the box
            // instead means the column measures what it draws, and the dock
            // grows to fit rather than shaving the icon's top and bottom off.
            val box = dockIconBoxPx(prefs)
            iconBinding.icon.updateLayoutParams { width = box; height = box }
            // Icons align across the row while labels grow downward, so a
            // wrapped label doesn't push its own icon out of line.
            iconBinding.root.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            iconBinding.root.setPadding(
                dp(DOCK_ITEM_PADDING_H_DP), dp(DOCK_ITEM_PADDING_V_DP),
                dp(DOCK_ITEM_PADDING_H_DP), dp(DOCK_ITEM_PADDING_V_DP)
            )
        } else {
            // Sized, not scaled, for the dock's reason: scaleX/scaleY drew a
            // big icon out past its box, where the item's padding cut it
            // square and the label, drawn after it, covered its bottom. The
            // item now measures what it draws, so the label sits below the
            // icon at any size. The label fills the item, which the page's
            // canvas widens to its cell (WorkspaceCanvas), so it runs the
            // cell's width, whatever the icon's size. A large icon's box is
            // twice the size, over its 2x2 cells.
            val box = desktopIconBoxPx(prefs) * iconSpan(item)
            iconBinding.icon.updateLayoutParams { width = box; height = box }
        }
        val displayLabel = override?.customLabel ?: item.label
        iconBinding.label.text = displayLabel
        LabelStyler.apply(
            iconBinding.label, prefs, onWallpaper = true,
            context = if (inDock) com.supernova.launcher.ui.LabelContext.DOCK
            else com.supernova.launcher.ui.LabelContext.DESKTOP
        )
        iconBinding.label.visibility = if (labelVisible) View.VISIBLE else View.GONE
        if (!inDock) iconBinding.root.setTag(R.id.tag_icon_fit, desktopIconFit(iconBinding, prefs))
        // Announce what the icon actually is; the label may be hidden.
        iconBinding.root.contentDescription = when (item.itemType) {
            ItemType.FOLDER -> getString(R.string.folder_desc, displayLabel)
            else -> displayLabel
        }
        iconBinding.icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        // The workspace adapter tags its children the same way; the dock has
        // no adapter, and the settle reveal looks items up by this tag.
        iconBinding.root.setTag(R.id.tag_item_id, item.id)
        // A bound icon swipe is invisible otherwise; the mark rides as the
        // icon's foreground so it costs no layout and can't move the label.
        // A swipe-to-open folder has a swipe too.
        iconBinding.icon.foreground =
            if (prefs.iconSwipeIndicator && (item.swipeAction != null || item.swipeActionDown != null || item.swipeToOpen)) {
                com.supernova.launcher.ui.SwipeIndicatorDrawable(
                    resources.displayMetrics.density,
                    LabelStyler.parseColor(prefs.labelColor, getColor(R.color.onWallpaper))
                )
            } else {
                null
            }

        val swipeToOpen = item.itemType == ItemType.FOLDER && item.swipeToOpen
        iconBinding.root.setOnClickListener {
            if (swipeToOpen) iconSwipes.launchFolderFirstItem(item, it) else launchItem(item, it)
        }
        iconBinding.root.setOnLongClickListener { v ->
            if (liftPageFromItem(v, item)) return@setOnLongClickListener true
            drag.arm(v, DragPayload.ExistingItem(item.id)) { workspaceMenus.show(v, item) }
            true
        }
        iconSwipes.attach(iconBinding.root, item)
        itemMoves.attach(iconBinding.root, item.id)
        if (swipeToOpen) {
            // A tap no longer opens it, so a screen reader gets the swipe as
            // an action of its own.
            ViewCompat.addAccessibilityAction(iconBinding.root, getString(R.string.open_folder)) { view, _ ->
                folderCards.showFolder(view, item)
                true
            }
        }

        return iconBinding.root
    }

    // How a desktop icon shrinks into a cell smaller than it needs, which the
    // page's canvas decides: icon, label and padding by the same scale, and
    // the label on one line, so icons in a dense grid stay clear of their
    // neighbours. Scale 1 puts it back as bound.
    private fun desktopIconFit(iconBinding: ItemIconBinding, prefs: LauncherPrefs): WorkspaceCanvas.IconFit {
        val textPx = iconBinding.label.textSize
        val lines = iconBinding.label.maxLines
        val pad = iconBinding.root.paddingTop
        val need = desktopIconNeedPx(prefs)
        return WorkspaceCanvas.IconFit(need.width, need.height, desktopIconBoxPx(prefs), pad) { scale, size ->
            iconBinding.icon.updateLayoutParams { width = size; height = size }
            iconBinding.label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textPx * scale)
            iconBinding.label.maxLines = if (scale < 1f) 1 else lines
            val padding = (pad * scale).roundToInt()
            iconBinding.root.setPadding(padding, padding, padding, padding)
        }
    }

    // Wallpapers: the installed wallpaper apps, or an image from any folder.
    private fun showWallpaperSources() {
        val sources = arrayOf(getString(R.string.wallpaper_apps), getString(R.string.wallpaper_from_files))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.home_action_wallpaper)
            .setItems(sources) { _, which ->
                if (which == 0) {
                    runLogged("open wallpaper picker") { startActivity(Intent(Intent.ACTION_SET_WALLPAPER)) }
                } else {
                    runLogged("open wallpaper file picker") { pickWallpaperFileLauncher.launch(arrayOf("image/*")) }
                        .onFailure { Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show() }
                }
            }
            .showTracked()
    }

    // The picked image opens in Supernova's own crop screen, which asks
    // whether it goes on the home screen, the lock screen or both. With the
    // wallpaper scrolling it is told where the home page sits across it, so
    // the home page shows what is framed.
    private fun openWallpaperCrop(uri: android.net.Uri) {
        val state = viewModel.state.value
        val mode = WallpaperScrollMode.fromPrefs(state.prefs)
        val pages = workspaceAdapter.realPageCount
        val homePage = state.model.defaultPage.coerceIn(0, (pages - 1).coerceAtLeast(0))
        val intent = Intent(this, WallpaperCropActivity::class.java)
            .setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (mode != WallpaperScrollMode.OFF) {
            intent.putExtra(WallpaperCropActivity.EXTRA_HOME_OFFSET, mode.offset(homePage.toFloat(), pages))
        }
        runLogged("open wallpaper crop") { startActivity(intent) }
            .onFailure { Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show() }
    }

    override fun onCanvasTap(page: Int, cellX: Int, cellY: Int, workspaceX: Float, workspaceY: Float) {
        // A tap on empty space with a resize frame up only takes the frame
        // down. Here rather than in a click listener swapped onto the canvas
        // while the frame is up: that left the page with no listener once the
        // frame was gone, and a tap no longer closed edit mode.
        if (widgets.dismissResizeFrame()) return
        if (editMode.isOpen) editMode.exit()
    }

    // Empty-home actions: Widgets, Wallpapers, Settings. Page
    // management (add/remove/reorder/default) is the pinch/overview
    // (DESKTOP_PREVIEWS); backup lives in Settings.
    // Empty-space long-press: edit mode (zoomed page, home marker,
    // Wallpapers/Settings chips, peeking add sheet).
    // In edit mode a press anywhere on the page in view lifts it, over an
    // icon or a widget too (its items take no touches then); one on a
    // neighbouring page peeking at the edge doesn't lift the page in view.
    override fun onCanvasLongPress(page: Int) {
        if (editMode.isOpen) {
            if (page == workspaceAdapter.realPage(binding.workspacePager.currentItem)) startPageDrag()
            return
        }
        editMode.enter()
    }

    override val editModeOpen: Boolean
        get() = ::editMode.isInitialized && editMode.isOpen

    // A long-press on a page in edit mode: the rest of the stream moves the
    // page, so the pager and the canvas let it go.
    private fun startPageDrag() {
        if (com.supernova.launcher.ui.DesktopLock.refused(this, viewModel) || !pageDrag.start()) return
        val now = android.os.SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    // In edit mode a long-press on an icon, folder or widget lifts its page
    // like one on empty space: a page full of icons (an imported layout, say)
    // has no empty spot to hold. True when edit mode took the press.
    private fun liftPageFromItem(view: View, item: LauncherItem): Boolean {
        val taken = ::editMode.isInitialized && editMode.isOpen && !desktopLocked &&
            item.container == ContainerType.WORKSPACE
        // An item on a neighbouring page peeking at the edge doesn't lift the
        // page in view.
        if (taken && item.page == workspaceAdapter.realPage(binding.workspacePager.currentItem)) {
            // The page's picture is drawn as it lifts: without the press
            // highlight still on the held item.
            view.isPressed = false
            view.jumpDrawablesToCurrentState()
            startPageDrag()
        }
        return taken
    }

    // The live view rendering this page, when the pager still has one: with
    // infinite scroll, the copy nearest the one in view.
    private fun attachedPageView(page: Int): View? {
        val recycler = binding.workspacePager.getChildAt(0)
            as? androidx.recyclerview.widget.RecyclerView ?: return null
        return (0 until recycler.childCount)
            .map(recycler::getChildAt)
            .filter { child ->
                val position = recycler.getChildAdapterPosition(child)
                position != androidx.recyclerview.widget.RecyclerView.NO_POSITION &&
                    workspaceAdapter.realPage(position) == page && !workspaceAdapter.isAddPage(position)
            }
            .minByOrNull { kotlin.math.abs(it.left) }
    }

    // The add-page slot's "+" (and a pick from the sheet made on the slot):
    // one page is asked for; the model emission carrying it closes edit mode
    // on the new page and then runs the pick (observeState).
    private fun requestEditAddPage(then: (() -> Unit)?) {
        val page = workspaceAdapter.realPageCount
        if (pendingEditAddPage != null || page >= LauncherRepository.MAX_PAGES ||
            com.supernova.launcher.ui.DesktopLock.refused(this, viewModel)
        ) {
            return
        }
        pendingEditAddPage = PendingEditAddPage(page, then)
        viewModel.addPage()
    }

    override fun onAddPageTap() = requestEditAddPage(then = null)

    // Beside the "+", the slot taps like any page's empty space.
    override fun onAddPageBackgroundTap() {
        if (editMode.isOpen) editMode.exit()
    }

    private fun setupEditMode() {
        pageDrag = com.supernova.launcher.ui.PageDragController(
            binding.root, binding.workspacePager,
            object : com.supernova.launcher.ui.PageDragController.Host {
                override fun pageCount(): Int = workspaceAdapter.realPageCount
                override fun currentPage(): Int = workspaceAdapter.realPage(binding.workspacePager.currentItem)
                override fun pageView(page: Int): View? = attachedPageView(page)
                override fun animationMs(baseMs: Long): Long =
                    Animations.duration(viewModel.state.value.prefs, baseMs)
                override fun commit(from: Int, to: Int, done: () -> Unit) {
                    val before = viewModel.state.value.model
                    viewModel.movePage(from, to)
                    lifecycleScope.launch {
                        withTimeoutOrNull(FOLDER_SAVE_WAIT_MS) { viewModel.state.first { it.model !== before } }
                        binding.workspacePager.setCurrentItem(workspaceAdapter.centerPosition(to), false)
                        editMode.onPageChanged()
                        // The moved pages rebind on the next layout pass.
                        binding.root.postDelayed(done, PAGE_REBIND_WAIT_MS)
                    }
                }
            }
        )
        editMode = com.supernova.launcher.ui.HomeEditController(
            this, binding, viewModel,
            object : com.supernova.launcher.ui.HomeEditController.Host {
                // Read off the pager: the model's page trails a swipe by an
                // emission, and the add-page slot is never in it.
                override fun currentPage(): Int =
                    workspaceAdapter.realPage(binding.workspacePager.currentItem)
                override fun defaultPage(): Int = viewModel.state.value.model.defaultPage
                override fun setDefaultPage(page: Int) = viewModel.setDefaultPage(page)
                override fun openWallpapers() = showWallpaperSources()
                override fun openSettings() {
                    startActivity(Intent(this@LauncherActivity, SettingsActivity::class.java))
                }
                override fun pickWidget(info: AppWidgetProviderInfo) = widgets.onWidgetPicked(info)
                override fun dragWidget(info: AppWidgetProviderInfo, preview: View): Boolean =
                    drag.armWidget(info, preview)
                override fun dragShortcut(target: com.supernova.launcher.ui.ShortcutTarget, icon: View): Boolean =
                    drag.armShortcut(target, icon)
                override fun pickShortcut(target: com.supernova.launcher.ui.ShortcutTarget) =
                    pickShortcutTarget(target)
                override fun cellSizePx(): Pair<Int, Int> = currentCellSizePx()
                override fun grid(): Pair<Int, Int> =
                    viewModel.state.value.prefs.let { it.gridCols to it.gridRows }
                override fun cancelPageDrag() = pageDrag.cancel()
                override fun setAddPageSlot(show: Boolean) =
                    workspaceAdapter.setAddPageSlot(show, binding.workspacePager)
                override fun onAddPage(): Boolean =
                    workspaceAdapter.isAddPage(binding.workspacePager.currentItem)
                override fun addPageFromSlot(then: (() -> Unit)?) = requestEditAddPage(then)
                override fun canRemoveCurrentPage(): Boolean = workspaceAdapter.realPageCount > 1
                // The page before it takes its place in view, the first page
                // the page after. A page with something on it goes with an
                // Undo bar (#215).
                override fun removeCurrentPage() {
                    val page = currentPage()
                    val count = workspaceAdapter.realPageCount
                    val follow = { removal.followRemovedPages(count - 1, (page - 1).coerceAtLeast(0)) }
                    if (viewModel.workspaceItems(page).isEmpty()) {
                        viewModel.removePage(page, follow)
                    } else {
                        removal.removePage(page) { follow() }
                    }
                }
            }
        )
    }

    override fun pickShortcutTarget(target: com.supernova.launcher.ui.ShortcutTarget) = shortcutPlacer.pick(target)

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgets.onActivityResult(requestCode, resultCode, data)
    }

    // ---- WidgetController.Host

    override val activeResizeFrame: WidgetResizeFrame?
        get() = widgets.activeResizeFrame

    override val feedOpen: Boolean
        get() = ::feed.isInitialized && feed.isOpen

    override fun cancelGesture(cancel: MotionEvent) {
        gestureDetector.onTouchEvent(cancel)
    }

    override fun onWidgetLongPress(view: View, item: LauncherItem) {
        if (liftPageFromItem(view, item)) return
        drag.arm(view, DragPayload.ExistingItem(item.id)) { workspaceMenus.show(view, item) }
    }

    override fun onSearchBarWidgetLongPress(anchor: View) = showSearchBarMenu(anchor)

    override fun onPageBound(canvas: ViewGroup) = widgets.onPageBound(canvas)

    override fun reportWidgetSize(item: LauncherItem, view: View) = widgets.reportWidgetSize(item, view)

    override fun currentCellSizePx(): Pair<Int, Int> {
        val prefs = viewModel.state.value.prefs
        // Mirrors WorkspacePagerAdapter: pages carry the desktopPadding prefs.
        val width = (binding.workspacePager.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels) - dp(prefs.desktopPaddingH) * 2
        val height = (binding.workspacePager.height.takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels) - dp(prefs.desktopPaddingV) * 2
        return max(1, width / max(1, prefs.gridCols)) to max(1, height / max(1, prefs.gridRows))
    }

    companion object {
        // Every live launcher activity, for closeOtherLaunchers. Main thread
        // only.
        private val liveLaunchers = mutableListOf<java.lang.ref.WeakReference<LauncherActivity>>()

        // Settings' Add card → Widget: pick and bind one onto a new feed card.
        const val EXTRA_ADD_FEED_WIDGET = "extra_add_feed_widget"

        // Same vertical bias the gesture detector uses for swipe-up/down.
        private const val DRAWER_PULL_BIAS = 1.2f
        // How long the Circle takes to fill the screen before the app opens.
        private const val CIRCLE_FILL_MS = 280L
        // How long a folder member action waits for its change to be saved.
        private const val FOLDER_SAVE_WAIT_MS = 1_000L
        private const val PAGE_REBIND_WAIT_MS = 100L
        // How long the window must have held focus for a home press to count
        // as made on the home screen.
        private const val HOME_FOCUS_SETTLE_MS = 100L

        // How close to rest a settling page lets a press through as a press.
        private const val SETTLE_TAP_THROUGH_DP = 48

        // The icon box and item padding item_icon declares, and the padding a
        // dock icon column keeps around it instead. Kept here because the
        // dock derives its own height from them rather than from a fixed bar
        // height, and a drop's settle works out where a desktop item's icon
        // will sit from them.
        private const val ICON_BASE_DP = 48
        // item_icon's padding, all round.
        private const val ICON_ITEM_PADDING_DP = 4
        private const val DOCK_ITEM_PADDING_H_DP = 2
        private const val DOCK_ITEM_PADDING_V_DP = com.supernova.launcher.ui.ChromeMargins.ITEM_PADDING_V_DP
        private const val DOCK_SLOT_PADDING_V_DP = com.supernova.launcher.ui.ChromeMargins.SLOT_PADDING_V_DP
        // A dock page turn; Launcher3 settles a flicked page in about this.
        private const val DOCK_SLIDE_MS = 300L

        // The dim behind a dialog that previews on the home screen.
        private const val PREVIEW_DIALOG_DIM = 0.15f

        // Frames dockChromeWatch may drop in a row while the dock's height
        // settles.
        private const val MAX_DOCK_CHROME_HOLDS = 2

        private const val WALLPAPER_ZOOM_MS = 250L
    }
}
