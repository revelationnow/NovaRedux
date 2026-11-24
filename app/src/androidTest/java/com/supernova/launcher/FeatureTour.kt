package com.supernova.launcher

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.UiAutomation
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.provider.CalendarContract
import android.text.format.DateUtils
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.EditText
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.supernova.launcher.data.AppPairs
import com.supernova.launcher.data.ContainerType
import com.supernova.launcher.data.ItemType
import com.supernova.launcher.data.LaunchableApp
import com.supernova.launcher.data.LauncherItem
import com.supernova.launcher.data.Pref
import com.supernova.launcher.data.ShutterWidgets
import com.supernova.launcher.data.StackedWidget
import com.supernova.launcher.data.supernovaStore
import com.supernova.launcher.notification.SummaryRules
import com.supernova.launcher.notification.SupernovaNotificationListener
import com.supernova.launcher.onboarding.DefaultLayoutBuilder
import com.supernova.launcher.ui.DesktopSearchBarConfig
import com.supernova.launcher.ui.FontFiles
import com.supernova.launcher.ui.NotificationLabels
import com.supernova.launcher.ui.SearchBarConfig
import com.supernova.launcher.widget.SupernovaWidgetHost
import java.io.File
import java.time.LocalTime
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// The offline features from other launchers (#4 to #17), each set up and
// used on an emulator as a person would use it: what it should show is
// looked for on the screen, and each step is photographed. The Screens
// workflow runs it with `tour FeatureTour`; results.txt, printed beside the
// pictures, says per feature what was seen, and the run fails if a step did.
//
// `-e features units,stack` picks steps by name (all by default). Each step
// starts from the settings and the home screen as the tour's own first-run
// setup left them. The tour changes the emulator for good (an accessibility
// service on, notification access, a calendar event, a PIN while the lock
// step runs): run it on a throwaway emulator, never on a phone.
@RunWith(AndroidJUnit4::class)
class FeatureTour {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    // Other accessibility services keep running beside the test's own
    // connection: the launcher's runs the power menu, screenshots and split
    // screen.
    private val automation: UiAutomation by lazy {
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).apply {
            serviceInfo = serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
        }
    }
    private val device: UiDevice by lazy {
        Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation
        UiDevice.getInstance(instrumentation)
    }
    private val context = instrumentation.targetContext
    private val appId = context.packageName
    private val graph = context.appGraph
    private val args = InstrumentationRegistry.getArguments()
    private val notifications: NotificationManager by lazy { context.getSystemService(NotificationManager::class.java) }
    private val serial: Long by lazy {
        context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
    }
    private val apps: List<LaunchableApp> by lazy {
        context.getSystemService(LauncherApps::class.java).getActivityList(null, Process.myUserHandle()).map {
            LaunchableApp(it.componentName.packageName, it.componentName.className, it.label.toString(), serial)
        }
    }
    private val glance = ComponentName(context.packageName, "com.supernova.launcher.widget.GlanceWidget")

    private lateinit var dir: File
    private lateinit var storeBefore: Preferences
    private lateinit var homeItems: List<LauncherItem>
    private var cols = 0
    private var shots = 0

    @Test
    fun tour() {
        dir = File(context.getExternalFilesDir(null), "screens").apply {
            deleteRecursively()
            mkdirs()
        }
        device.executeShellCommand("svc power stayon true")
        device.wakeUp()
        prepare()
        val steps = linkedMapOf<String, () -> String>(
            "units" to ::units,
            "typed" to ::typedActions,
            "calendar" to ::calendarSearch,
            "font" to ::labelFont,
            "stack" to ::widgetStack,
            "panel" to ::widgetPanel,
            "blur" to ::blur,
            "gestures" to ::gestureActions,
            "pairs" to ::appPairs,
            "notification-text" to ::notificationText,
            "summary" to ::notificationSummary,
            "screen-time" to ::screenTime,
            // Last: it sets a PIN while it runs.
            "lock" to ::hiddenAppsLock
        )
        val only = args.getString("features")?.split(',')?.map { it.trim() }?.toSet()
        val results = steps.filterKeys { only == null || it in only }.map { (name, step) ->
            reset()
            val outcome = runCatching { step() }.fold(
                onSuccess = { "PASS $name: $it" },
                onFailure = { "FAIL $name: ${it.message ?: it}" }
            )
            if (outcome.startsWith("FAIL")) shot("$name-failed")
            Log.i(TAG, outcome)
            outcome
        }
        File(dir, "results.txt").writeText(results.joinToString("\n", postfix = "\n"))
        assertTrue(results.joinToString("\n"), results.none { it.startsWith("FAIL") })
    }

    // ---- Steps: each returns what it saw, or throws what it didn't --------

    // #8: the units added to the calculator.
    private fun units(): String {
        showHome()
        search("60 miles per hour in km/h")
        check(device.wait(Until.hasObject(By.textContains("96.56")), WAIT_MS)) { "no conversion for 60 mph" }
        shot("units-speed")
        retype("2 cups in ml")
        check(device.wait(Until.hasObject(By.textContains("473.17")), WAIT_MS)) { "no conversion for 2 cups" }
        shot("units-volume")
        return "60 mph came out as 96.56 km/h and 2 cups as 473.18 ml"
    }

    // #9: a typed number and a typed address get their own rows.
    private fun typedActions(): String {
        setPrefs { it[booleanPreferencesKey(Pref.searchTypedActions.key.name)] = true }
        showHome()
        search("+1 650 555 0123")
        check(device.wait(Until.hasObject(By.textStartsWith(CALL)), WAIT_MS)) { "no Call row for a typed number" }
        shot("typed-number")
        retype("example.org")
        check(device.wait(Until.hasObject(By.textStartsWith(OPEN)), WAIT_MS)) { "no Open row for a typed address" }
        shot("typed-address")
        return "a typed number offered Call, a typed address Open"
    }

    // #7: an event in the phone's calendar comes up in search.
    private fun calendarSearch(): String {
        addCalendarEvent("Dentist appointment")
        setPrefs { it[booleanPreferencesKey(Pref.searchCalendar.key.name)] = true }
        showHome()
        search("dentist")
        check(device.wait(Until.hasObject(By.textContains("Dentist appointment")), WAIT_MS)) {
            "the calendar event didn't come up"
        }
        shot("calendar")
        return "an event added to the phone's calendar came up for \"dentist\""
    }

    // #6: a font file for the labels, taken in as Label font > Font file takes
    // one, from the phone's own fonts.
    private fun labelFont(): String {
        val font = FONTS.map { File(SYSTEM_FONTS, it) }.firstOrNull { it.canRead() } ?: error("no font file to try")
        val name = FontFiles.importLabelFont(context, Uri.fromFile(font)) ?: error("${font.name} wasn't taken in")
        setPrefs {
            it[stringPreferencesKey(Pref.labelFont.key.name)] = "FILE"
            it[stringPreferencesKey(Pref.labelFontFile.key.name)] = FontFiles.stamp(name, System.currentTimeMillis())
        }
        layout(listOf(appItem(app(*SETTINGS), 0, ROW), appItem(app(*CLOCKS), 1, ROW)))
        showHome()
        shot("font")
        return "labels in ${font.name}, taken in as $name (see the picture)"
    }

    // #10 and #13: NovaRedux's glance widget stacked on another widget, and a
    // sideways swipe on the stack turning it.
    private fun widgetStack(): String {
        allowWidgets()
        val other = otherWidget() ?: error("no widget but NovaRedux's own to stack")
        val first = bind(other.provider)
        val second = bind(glance)
        val stack = LauncherItem(
            id = "tour-stack", itemType = ItemType.WIDGET, container = ContainerType.WORKSPACE,
            page = 0, cellX = 0, cellY = 0, spanX = cols, spanY = 2,
            appWidgetId = first, widgetProvider = other.provider.flattenToString(),
            label = other.loadLabel(context.packageManager),
            stack = listOf(StackedWidget(second, glance.flattenToString(), serial, "At a glance")),
            stackShown = 1
        )
        val marker = app(*SETTINGS)
        layout(listOf(stack, appItem(marker, 0, ROW)))
        showHome()
        val date = glanceDate()
        val shown = device.wait(Until.findObject(By.text(date)), WAIT_MS)
            ?: error("the glance widget didn't show \"$date\" on top of the stack")
        shot("stack-glance")
        val y = shown.visibleCenter.y
        device.swipe(device.displayWidth * 4 / 5, y, device.displayWidth / 5, y, FLICK_STEPS)
        check(device.wait(Until.gone(By.text(date)), WAIT_MS)) { "a sideways swipe on the stack didn't turn it" }
        check(device.hasObject(By.desc(marker.label))) { "the swipe changed the page instead of turning the stack" }
        shot("stack-turned")
        return "the glance widget showed \"$date\" on a stack with ${stack.label}; a swipe turned it"
    }

    // #14: a swipe up on an icon opens a widget in a panel by it.
    private fun widgetPanel(): String {
        allowWidgets()
        val id = bind(glance)
        val app = app(*SETTINGS)
        val swiped = appItem(app, 1, ROW).copy(
            swipeAction = ShutterWidgets.target(id, glance.flattenToString()),
            swipeLabel = context.getString(R.string.swipe_widget_label, "At a glance")
        )
        layout(listOf(swiped))
        showHome()
        val icon = device.wait(Until.findObject(By.desc(app.label)), WAIT_MS)
            ?: error("${app.label} isn't on the home screen")
        val at = icon.visibleCenter
        device.swipe(at.x, at.y, at.x, at.y - device.displayHeight / ICON_SWIPE_SHARE, ICON_SWIPE_STEPS)
        val date = glanceDate()
        check(device.wait(Until.hasObject(By.text(date)), WAIT_MS)) { "a swipe up on ${app.label} opened no widget" }
        shot("widget-panel")
        device.pressBack()
        check(device.wait(Until.gone(By.text(date)), WAIT_MS)) { "Back left the panel open" }
        return "a swipe up on ${app.label} opened the glance widget by it, and Back closed it"
    }

    // #11: the drawer over the blurred wallpaper. The drawer's background is
    // let through a little, as the blur shows only through what isn't solid.
    private fun blur(): String {
        setPrefs {
            it[booleanPreferencesKey(Pref.blurWallpaper.key.name)] = true
            it[intPreferencesKey(Pref.blurStrength.key.name)] = FULL_BLUR
            it[floatPreferencesKey(Pref.drawerBgOpacity.key.name)] = SEE_THROUGH
        }
        val blurs = context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled
        showHome()
        check(openDrawer()) { "the drawer didn't open" }
        shot("blur-drawer")
        return if (blurs) {
            "the drawer opened with blur on (see the picture)"
        } else {
            "this emulator turns cross-window blur off, so nothing can blur here"
        }
    }

    // #5: a double tap bound to the power menu, a screenshot and the torch.
    private fun gestureActions(): String {
        enableAccessibility()
        layout(emptyList())
        bindDoubleTap("POWER_MENU")
        showHome()
        doubleTapMiddle()
        check(device.wait(Until.hasObject(By.text(POWER_MENU)), WAIT_MS)) { "a double tap opened no power menu" }
        shot("power-menu")
        device.pressBack()
        bindDoubleTap("SCREENSHOT")
        showHome()
        val before = screenshotCount()
        doubleTapMiddle()
        val taken = waitFor { screenshotCount() > before }
        shot("screenshot-taken")
        check(taken) { "a double tap took no screenshot" }
        bindDoubleTap("TORCH")
        showHome()
        return "a double tap opened the power menu and took a screenshot; " + torch()
    }

    // The torch, as the camera service reports it: on with a double tap, off
    // with the next.
    private fun torch(): String {
        val watch = TorchWatch(context)
        try {
            if (!watch.hasFlash) {
                doubleTapMiddle()
                shot("torch")
                return "this emulator has no flash, so the torch couldn't be tried"
            }
            doubleTapMiddle()
            check(waitFor { watch.lit }) { "a double tap didn't turn the torch on" }
            shot("torch")
            doubleTapMiddle()
            check(waitFor { !watch.lit }) { "a second double tap didn't turn the torch off" }
            return "the torch went on with a double tap and off with the next"
        } finally {
            watch.close()
        }
    }

    // #12: an app pair's icon opens both apps side by side: from Android 12L
    // with no accessibility service, which this checks by turning it off;
    // below, with the launcher's.
    private fun appPairs(): String {
        val needsService = Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2
        if (needsService) enableAccessibility() else disableAccessibility()
        // The system's split screen notes, for a report if it doesn't split.
        device.executeShellCommand("dumpsys activity service SystemUIService WMShell protolog enable-text $SPLIT_LOG")
        val first = app(*SETTINGS)
        val second = app(*CLOCKS, *CONTACTS)
        val label = "${first.label} + ${second.label}"
        layout(listOf(AppPairs.paired(appItem(first, 0, ROW), second, label)))
        showHome()
        val icon = device.wait(Until.findObject(By.desc(label)), WAIT_MS) ?: error("the pair isn't on the home screen")
        shot("pair-icon")
        icon.click()
        val both = waitFor(PAIR_WAIT_MS) { setOf(first.packageName, second.packageName).all { it in windowPackages() } }
        shot("pair-opened")
        val seen = windowPackages()
        val tops = windowTops()
        device.pressHome()
        check(both) { "after the tap the screen showed $seen. ${launchNotes()}" }
        check(appId !in seen) { "the pair's own screen stayed up: $seen" }
        val upper = if ((tops[first.packageName] ?: 0) <= (tops[second.packageName] ?: 0)) first else second
        return "$label opened both apps side by side, ${upper.label} in the upper half" +
            if (needsService) "" else ", with accessibility off"
    }

    // #15: a chosen app's label shows its newest notification.
    private fun notificationText(): String {
        allowNotifications()
        val own = ownApp()
        setPrefs {
            it[stringSetPreferencesKey(Pref.notificationTextApps.key.name)] =
                setOf(NotificationLabels.key(own.userSerial, own.packageName, own.className))
        }
        layout(listOf(appItem(own, 0, ROW)))
        showHome()
        post(TEXT_ID, "Anna", "See you at 7")
        val line = "Anna: See you at 7"
        check(device.wait(Until.hasObject(By.text(line)), WAIT_MS)) { "the label didn't change to \"$line\"" }
        shot("notification-text")
        notifications.cancel(TEXT_ID)
        check(device.wait(Until.hasObject(By.text(own.label)), WAIT_MS)) { "the name didn't come back" }
        shot("notification-text-gone")
        return "${own.label}'s label read \"$line\" and went back to its name"
    }

    // #16: a notification from a held app waits for the next delivery hour.
    private fun notificationSummary(): String {
        allowNotifications()
        val own = ownApp()
        val hour = (LocalTime.now().hour + HOURS_AHEAD) % SummaryRules.HOURS_PER_DAY
        setPrefs {
            it[booleanPreferencesKey(Pref.notificationSummary.key.name)] = true
            it[stringSetPreferencesKey(Pref.summaryApps.key.name)] = setOf(SummaryRules.appKey(own.userSerial, appId))
            it[stringPreferencesKey(Pref.summaryHours.key.name)] = hour.toString()
        }
        // The listener reads its rules from the settings as they change.
        SystemClock.sleep(SETTLE_MS)
        showHome()
        // One from an app the summary doesn't hold, the shell, beside it: the
        // shade shows that one and not the held one. (A snoozed notification
        // still counts as active to the app that posted it, so the shade is
        // where to look.)
        device.executeShellCommand("cmd notification post -t $CONTROL_TITLE $CONTROL_TAG $CONTROL_TEXT")
        post(SUMMARY_ID, HELD_TITLE, "Your order shipped")
        SystemClock.sleep(SETTLE_MS)
        device.openNotification()
        val shadeShows = device.wait(Until.hasObject(By.text(CONTROL_TITLE)), WAIT_MS)
        val heldShows = device.hasObject(By.text(HELD_TITLE))
        shot("summary-shade")
        device.pressBack()
        // Cancelled, a snoozed notification doesn't come back at the hour.
        notifications.cancel(SUMMARY_ID)
        check(shadeShows) { "the shade didn't show the shell's notification" }
        check(!heldShows) { "the shade showed ${own.label}'s notification" }
        return "the shade showed the shell's notification but not ${own.label}'s, held for the $hour:00 delivery"
    }

    // #17: the screen time card asks for usage access, then shows the day.
    private fun screenTime(): String {
        // As Settings leaves it with Usage access turned off. (By default it
        // follows the permission, which the test's install grants.)
        device.executeShellCommand("appops set $appId GET_USAGE_STATS ignore")
        setPrefs {
            it[booleanPreferencesKey(Pref.feedEnabled.key.name)] = true
            it[booleanPreferencesKey(Pref.feedShowScreenTime.key.name)] = true
        }
        showHome()
        openFeed()
        val grant = context.getString(R.string.feed_grant_access)
        check(device.wait(Until.hasObject(By.text(grant)), WAIT_MS)) { "the card didn't ask for usage access" }
        shot("screen-time-ask")
        device.executeShellCommand("appops set $appId GET_USAGE_STATS allow")
        val today = context.getString(R.string.feed_screen_time_total, "")
        check(device.wait(Until.hasObject(By.textEndsWith(today)), WAIT_MS)) {
            "the card didn't show the day once usage access was on"
        }
        shot("screen-time")
        return "the card asked for usage access, then showed the day's time as soon as it was on"
    }

    // #4: Hidden apps asks for the phone's lock first.
    private fun hiddenAppsLock(): String {
        device.executeShellCommand("locksettings set-pin $PIN")
        try {
            val hidden = app(*CONTACTS, *CLOCKS)
            setPrefs {
                it[booleanPreferencesKey(Pref.hiddenAppsLock.key.name)] = true
                it[stringSetPreferencesKey(Pref.hiddenApps.key.name)] =
                    setOf("${hidden.userSerial}/${hidden.packageName}/${hidden.className}")
            }
            startSettings(R.string.settings_section_drawer, R.string.hidden_apps)
            val row = device.wait(Until.findObject(By.text(context.getString(R.string.hidden_apps))), WAIT_MS)
                ?: error("no Hidden apps row")
            row.click()
            check(device.wait(Until.hasObject(By.text(context.getString(R.string.hidden_apps_unlock))), WAIT_MS)) {
                "Hidden apps opened without asking for the phone's lock"
            }
            shot("lock-prompt")
            device.executeShellCommand("input text $PIN")
            device.executeShellCommand("input keyevent KEYCODE_ENTER")
            check(device.wait(Until.hasObject(By.text(hidden.label)), WAIT_MS)) {
                "the PIN didn't open the hidden apps list"
            }
            shot("lock-open")
            device.pressBack()
            return "Hidden apps asked for the phone's PIN, and opened once it was entered"
        } finally {
            device.executeShellCommand("locksettings clear --old $PIN")
        }
    }

    // ---- Setting up ---------------------------------------------------------

    // As the Screens tour sets up: the first-run layout, the fresh search bar,
    // onboarding done, and a Home press that does nothing of its own.
    private fun prepare() = runBlocking {
        val prefs = graph.preferences.snapshot()
        if (graph.repository.modelFlow.first().items.isEmpty() && graph.preferences.claimDefaultLayoutSeed()) {
            graph.repository.seedDefaultLayout(
                DefaultLayoutBuilder.buildItems(context, graph.catalog, prefs.gridCols, prefs.gridRows, prefs.dockCount)
            )
            graph.preferences.set(
                Pref.searchBarConfig, SearchBarConfig(desktop = DesktopSearchBarConfig.FRESH).encode()
            )
        }
        graph.preferences.markOnboardingDone()
        context.supernovaStore.edit { it[stringPreferencesKey(Pref.homeButtonAction.key.name)] = "NONE" }
        storeBefore = context.supernovaStore.data.first()
        homeItems = graph.repository.modelFlow.first().items
        cols = prefs.gridCols
    }

    // The settings and the home screen as prepare() left them.
    private fun reset() {
        repeat(2) { device.pressBack() }
        runBlocking {
            context.supernovaStore.edit { store ->
                store.clear()
                store += storeBefore
            }
            graph.repository.replantDefaultLayout(homeItems)
        }
    }

    // Only these on the first page; the dock as planted.
    private fun layout(items: List<LauncherItem>) = runBlocking {
        val dock = homeItems.filter { it.container == ContainerType.DOCK }
        val dockIds = dock.mapTo(HashSet()) { it.id }
        val inDockFolders = homeItems.filter { it.container == ContainerType.FOLDER && it.folderId in dockIds }
        graph.repository.replantDefaultLayout(dock + inDockFolders + items)
    }

    private fun setPrefs(change: (MutablePreferences) -> Unit) {
        runBlocking { context.supernovaStore.edit { change(it) } }
    }

    private fun appItem(app: LaunchableApp, cellX: Int, cellY: Int) = LauncherItem(
        id = "tour-${app.packageName}-$cellX-$cellY", itemType = ItemType.APP, container = ContainerType.WORKSPACE,
        page = 0, cellX = cellX, cellY = cellY, label = app.label, packageName = app.packageName,
        className = app.className, userSerial = app.userSerial
    )

    // The first of these packages installed, else any other app.
    private fun app(vararg packages: String): LaunchableApp =
        packages.firstNotNullOfOrNull { name -> apps.firstOrNull { it.packageName == name } }
            ?: apps.first { it.packageName != appId }

    private fun ownApp(): LaunchableApp =
        apps.filter { it.packageName == appId }.minByOrNull { if (it.className.endsWith("LauncherActivity")) 0 else 1 }
            ?: error("this build has no icon of its own")

    private fun bindDoubleTap(action: String) =
        setPrefs { it[stringSetPreferencesKey(Pref.gestureBindings.key.name)] = setOf("DOUBLE_TAP:$action") }

    private fun enableAccessibility() {
        val service = "$appId/${SupernovaAccessibilityService::class.java.name}"
        device.executeShellCommand("settings put secure enabled_accessibility_services $service")
        device.executeShellCommand("settings put secure accessibility_enabled 1")
        SystemClock.sleep(SERVICE_WAIT_MS)
    }

    private fun disableAccessibility() {
        device.executeShellCommand("settings delete secure enabled_accessibility_services")
        SystemClock.sleep(SERVICE_WAIT_MS)
    }

    private fun allowNotifications() {
        val listener = "$appId/${SupernovaNotificationListener::class.java.name}"
        device.executeShellCommand("cmd notification allow_listener $listener")
        SystemClock.sleep(SERVICE_WAIT_MS)
    }

    private fun post(id: Int, title: String, text: String) {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Feature tour", NotificationManager.IMPORTANCE_DEFAULT)
        )
        notifications.notify(
            id,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .build()
        )
    }

    // A local calendar with one event tomorrow, written with the shell's
    // calendar access, as this app has none to write with.
    private fun addCalendarEvent(title: String) {
        automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR)
        try {
            val account = "NovaRedux tour"
            val calendars = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build()
            val calendar = context.contentResolver.insert(
                calendars,
                ContentValues().apply {
                    put(CalendarContract.Calendars.ACCOUNT_NAME, account)
                    put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                    put(CalendarContract.Calendars.NAME, account)
                    put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, account)
                    put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
                    put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                    put(CalendarContract.Calendars.OWNER_ACCOUNT, account)
                    put(CalendarContract.Calendars.VISIBLE, 1)
                    put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                }
            ) ?: error("the calendar provider took no calendar")
            val start = System.currentTimeMillis() + DateUtils.DAY_IN_MILLIS
            context.contentResolver.insert(
                CalendarContract.Events.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, ContentUris.parseId(calendar))
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.EVENT_LOCATION, "Main Street clinic")
                    put(CalendarContract.Events.DTSTART, start)
                    put(CalendarContract.Events.DTEND, start + DateUtils.HOUR_IN_MILLIS)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                }
            ) ?: error("the calendar provider took no event")
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }

    // ---- Widgets ------------------------------------------------------------

    private fun allowWidgets() {
        device.executeShellCommand("appwidget grantbind --package $appId --user 0")
    }

    // A widget bound to the launcher's own host, as its add flow binds one.
    private fun bind(provider: ComponentName): Int {
        val id = SupernovaWidgetHost(context.applicationContext, SupernovaWidgetHost.HOST_ID).allocateAppWidgetId()
        check(AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(id, provider)) {
            "binding ${provider.flattenToShortString()} was refused"
        }
        return id
    }

    // Another app's widget that needs no setup, a clock's if there is one.
    private fun otherWidget(): AppWidgetProviderInfo? =
        AppWidgetManager.getInstance(context).installedProviders
            .filter { it.provider.packageName != appId && it.configure == null }
            .minByOrNull { if ("clock" in it.provider.packageName) 0 else 1 }

    // What the glance widget's date line says today.
    private fun glanceDate(): String = DateUtils.formatDateTime(
        context, System.currentTimeMillis(),
        DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
    )

    // ---- Moving about -------------------------------------------------------

    // By class: this build may not be the default home app.
    private fun showHome() {
        context.startActivity(
            Intent(context, LauncherActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        check(device.wait(Until.hasObject(By.res(appId, "workspacePager")), WAIT_MS)) { "the home screen didn't draw" }
        repeat(2) { if (device.hasObject(By.res(appId, "drawerSheet"))) device.pressBack() }
        settle()
    }

    private fun search(query: String) {
        val bar = device.wait(Until.findObject(By.res(appId, "desktopSearchBar")), WAIT_MS)
            ?: error("no search bar on the home screen")
        bar.click()
        retype(query)
    }

    private fun retype(query: String) {
        val field = device.wait(Until.findObject(By.focused(true).clazz(EditText::class.java)), WAIT_MS)
            ?: error("no search field to type in")
        field.text = query
        SystemClock.sleep(QUERY_MS)
    }

    // A pull up from above the dock, past half the screen.
    private fun openDrawer(): Boolean {
        val x = device.displayWidth / 2
        val height = device.displayHeight
        device.swipe(x, height * PULL_FROM / PULL_SHARES, x, height * PULL_TO / PULL_SHARES, SLOW_SWIPE_STEPS)
        return device.wait(Until.hasObject(By.res(appId, "drawerSheet")), WAIT_MS)
    }

    // A slow pull rightward across the first page.
    private fun openFeed() {
        val y = device.displayHeight / 2
        val edge = device.displayWidth / EDGE_SHARE
        device.swipe(edge, y, device.displayWidth - edge, y, SLOW_SWIPE_STEPS)
        SystemClock.sleep(FEED_WAIT_MS)
    }

    private fun startSettings(section: Int, highlight: Int) {
        context.startActivity(
            Intent(context, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_SECTION_TITLE_RES, section)
                .putExtra(SettingsActivity.EXTRA_HIGHLIGHT_TITLE_RES, highlight)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        check(device.wait(Until.hasObject(By.res(appId, "settingsCoordinator")), WAIT_MS)) { "Settings didn't open" }
    }

    // Two taps in the middle of the empty first page, close enough together
    // to be a double tap.
    private fun doubleTapMiddle() {
        val x = device.displayWidth / 2f
        val y = device.displayHeight / 2f
        repeat(2) {
            val down = SystemClock.uptimeMillis()
            touch(down, down, MotionEvent.ACTION_DOWN, x, y)
            SystemClock.sleep(TAP_MS)
            touch(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y)
            SystemClock.sleep(TAP_GAP_MS)
        }
    }

    private fun touch(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        automation.injectInputEvent(event, true)
        event.recycle()
    }

    private fun screenshotCount(): Int =
        device.executeShellCommand("ls /sdcard/Pictures/Screenshots").lines().count { it.endsWith(".png") }

    // What the system log says of launches it stopped or split, for a step
    // that failed.
    private fun launchNotes(): String {
        val lines = device.executeShellCommand("logcat -d -t $LOG_LINES").lines()
            .filter { line -> LAUNCH_NOTES.any { it in line } }
            .takeLast(LOG_LINES_KEPT)
        return if (lines.isEmpty()) "The log says nothing of it." else lines.joinToString(" | ")
    }

    // How far down the screen each app's window starts.
    private fun windowTops(): Map<String, Int> = automation.windows.mapNotNull { window ->
        window.root?.packageName?.toString()?.let { it to Rect().also(window::getBoundsInScreen).top }
    }.toMap()

    // The packages of the windows on screen.
    private fun windowPackages(): Set<String> =
        automation.windows.mapNotNullTo(HashSet()) { it.root?.packageName?.toString() }

    // Whether a flash is lit, as the camera service tells every app.
    private class TorchWatch(context: Context) : CameraManager.TorchCallback() {
        private val camera = context.getSystemService(CameraManager::class.java)
        private val modes = ConcurrentHashMap<String, Boolean>()
        val hasFlash = camera.cameraIdList.any {
            camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        val lit: Boolean get() = modes.values.any { it }

        init {
            camera.registerTorchCallback(context.mainExecutor, this)
        }

        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            modes[cameraId] = enabled
        }

        fun close() = camera.unregisterTorchCallback(this)
    }

    private fun waitFor(timeoutMs: Long = WAIT_MS, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return true
            SystemClock.sleep(POLL_MS)
        }
        return condition()
    }

    private fun settle() {
        device.waitForIdle()
        SystemClock.sleep(SETTLE_MS)
    }

    private fun shot(name: String) {
        settle()
        shots += 1
        device.takeScreenshot(File(dir, String.format(Locale.ROOT, "%02d-%s.png", shots, name)))
    }

    private companion object {
        const val TAG = "FeatureTour"
        const val WAIT_MS = 8_000L
        const val SETTLE_MS = 1_500L
        const val QUERY_MS = 2_000L
        const val FEED_WAIT_MS = 3_000L
        const val SERVICE_WAIT_MS = 3_000L
        const val PAIR_WAIT_MS = 10_000L
        const val POLL_MS = 250L
        const val TAP_MS = 40L
        const val TAP_GAP_MS = 90L
        const val SLOW_SWIPE_STEPS = 60
        const val FLICK_STEPS = 12
        const val ICON_SWIPE_STEPS = 10
        const val ICON_SWIPE_SHARE = 6
        const val PULL_SHARES = 20
        const val PULL_FROM = 15
        const val PULL_TO = 1
        const val EDGE_SHARE = 10
        const val ROW = 3
        const val FULL_BLUR = 100
        const val SEE_THROUGH = 0.6f
        const val HOURS_AHEAD = 3
        const val TEXT_ID = 41
        const val SUMMARY_ID = 42
        const val HELD_TITLE = "Shop"
        // Single words: the shell command isn't parsed by a shell.
        const val CONTROL_TITLE = "Elsewhere"
        const val CONTROL_TAG = "feature_tour_control"
        const val CONTROL_TEXT = "Unheld"
        const val CHANNEL = "feature_tour"
        const val LOG_LINES = 1500
        const val LOG_LINES_KEPT = 10
        const val SPLIT_LOG = "WM_SHELL_SPLIT_SCREEN"
        val LAUNCH_NOTES = listOf(
            "Background activity launch blocked", "Abort background activity", "BAL_BLOCK", "LAUNCH_ADJACENT",
            "ShellSplitScreen", "AppPairActivity"
        )
        const val PIN = "1234"
        const val CALENDAR_COLOR = 0xFF3F72AF.toInt()
        const val SYSTEM_FONTS = "/system/fonts"
        val FONTS = listOf("DancingScript-Regular.ttf", "ComingSoon.ttf", "CutiveMono.ttf", "NotoSerif-Regular.ttf")
        const val CALL = "Call "
        const val OPEN = "Open "
        val POWER_MENU: Pattern = Pattern.compile("(?i)power off|restart")
        val SETTINGS = arrayOf("com.android.settings")
        val CLOCKS = arrayOf("com.google.android.deskclock", "com.android.deskclock")
        val CONTACTS = arrayOf("com.google.android.contacts", "com.android.contacts")
    }
}
