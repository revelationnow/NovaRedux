# NovaRedux

A free, open-source Android launcher that aims to match **Nova Launcher as it was before its 2022 sale**. No ads, no trackers, no account and no internet permission.

NovaRedux is a fork of [Supernova Launcher](https://github.com/tanujnotes/Supernova) by [Tanuj](https://github.com/tanujnotes). Supernova already covers most of Nova. NovaRedux closes the remaining gaps one feature at a time, and offers fixes back upstream where they fit.

> **Modified version.** This repository is a modified version of Supernova Launcher, licensed under the GNU Affero General Public License v3.0 or later. The NovaRedux contributors began changing it on 21 November 2026; the commit history records every change.

## Why

- For a decade, Nova Launcher was the go-to custom launcher on Android.
- Branch Metrics bought Nova in July 2022. Its last developer left in September 2025, and the open-source release he had been preparing was stopped.
- Instabridge bought Nova in January 2026 and added ads and ad trackers to the free version.

NovaRedux keeps what made Nova great and leaves out everything that came after the sale.

## What "Nova parity" means here

- **Target:** Nova 7.0.57 (December 2021), the last stable release before the sale, plus the Nova 8.0 beta of 15 July 2022.
- **Out of scope:** Branch's search and app-discovery features, and Instabridge's ads, trackers and AI assistant.
- **No paid tier:** everything that needed Nova Launcher Prime is free.
- **Offline by design:** like Supernova, NovaRedux has no internet permission. A Nova feature that needs the network only comes in if it can work through another app on the phone.

## Status

NovaRedux currently matches upstream Supernova v0.2.8 (commit `5b63c43`). There are no NovaRedux releases yet; meanwhile, [Supernova is on F-Droid](https://f-droid.org/packages/com.supernova.launcher/).

## Nova parity tracker

Every Nova feature through 7.0.58 and the pre-sale 8.0 beta, grouped the way Nova Settings grouped them. **Since** is the Nova version that added the feature, where a changelog or store listing says so; `1.x` means it was already in the 2012 listings. **Prime** marks features that needed Nova Launcher Prime.

The statuses come from reading the Supernova source, not from using it. Every ✅ still needs a side-by-side check against a phone running Nova 7.0.57.

Legend: ✅ done · 🟡 partial · ❌ missing · ❔ needs a check on a device · ⛔ won't do. Priority: **P0** defines Nova, **P1** important, **P2** nice to have.

<!-- parity:start -->

| Status | Features |
| --- | --- |
| ✅ Done | 119 |
| 🟡 Partial | 11 |
| ❌ Missing | 18 |
| ❔ Needs a check on a device | 17 |
| ⛔ Won't do | 2 |
| **Total** | **167** |

<details>
<summary><b>Home screen</b> · 23 features · ✅ 17 · 🟡 1 · ❔ 5</summary>

| Status | Feature | Since | Prime | Priority | What Nova did | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | Desktop grid, rows by columns | 1.x |  | P0 | Any grid from very small to very dense | grid_rows, grid_cols (up to 24) |
| ✅ | Subgrid positioning |  |  | P0 | Icons and widgets snap halfway between cells | subgrid; widgets resize to half a cell |
| ✅ | Icon size | 6.0 |  | P0 | Free since 6.0 (was Prime) | icon_scale |
| ✅ | Icon labels: show or hide, font, colour, shadow, two lines |  |  | P1 | Per-surface label styling | label_* prefs |
| ✅ | Width and height padding |  |  | P1 | Space between the grid and the screen edges | desktop_padding_h, desktop_padding_v |
| ❔ | Persistent search bar on the desktop | 4.1 |  | P1 | A search bar that stays on every page | home_search, search_bar_config |
| ❔ | Search bar placement: top of page, or dock above or below icons | 5.5 |  | P1 | Choose where the desktop bar sits | SearchBarPlacement; confirm all three spots |
| ❔ | Replace the dock search bar with any widget | 5.5 |  | P2 | Long-press the bar, then Replace | search_bar_widget_provider suggests support |
| ✅ | Page indicator style and colour | 5.0 |  | P2 | Dots, line or none, in any colour | IndicatorStyle NONE, DOTS, CIRCLE, LINE |
| ✅ | Swipe-to-drawer indicator | 5.0 |  | P2 | Arrow hinting that a swipe opens the drawer | drawer_swipe_indicator |
| ✅ | Infinite scroll | 1.x |  | P1 | Last page wraps to the first | infinite_scroll |
| ✅ | Scroll effects: Simple, Cube, Card stack | 1.x |  | P1 | Free page transitions | ScrollEffect enum |
| ✅ | More scroll effects: Tablet, Revolving door, Flip, Flip vertical, In and out, Accordion, Glass, Zoom fade, Throw, Wipe | 1.x | Prime | P2 | Prime page transitions | All present in ScrollEffect |
| 🟡 | Wallpaper scrolling modes | 1.x |  | P2 | On, off, reverse; early builds also static-only and delayed | On, off and reverse only |
| ✅ | Lock layout | 7.0.15 |  | P1 | Stops accidental moves and removals | desktop_locked |
| ✅ | Overlap when placing |  |  | P1 | Icons and widgets may overlap | overlap_when_placing, widget_overlap |
| ✅ | Top and bottom shadow |  |  | P2 | Shade behind the status bar and dock | show_scrims |
| ✅ | Add new apps to the home screen |  |  | P2 | New installs placed automatically | auto_add_apps |
| ✅ | Default page; Home returns to it |  |  | P1 | Home button jumps to the chosen page | home_return_to_default_page |
| ✅ | Desktop previews: add, remove, reorder, set default page |  |  | P1 | Page overview for editing | Page drag and default-page controls |
| ✅ | Screen rotation |  |  | P1 | Portrait, landscape or auto-rotate | HomeOrientation |
| ❔ | Tablet and foldable layouts | 8.0 beta |  | P1 | 8.0 beta moved to the Android 12L launcher base | Needs a tablet and a foldable |
| ❔ | Edge-to-edge display support | 7.0.37 |  | P2 | Layouts that respect curved and edge displays | Behaviour; test on an edge display |

</details>

<details>
<summary><b>Widgets</b> · 5 features · ✅ 3 · ❔ 2</summary>

| Status | Feature | Since | Prime | Priority | What Nova did | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | Resize any widget |  |  | P0 | Including widgets that don't declare resizing | Resize from any edge; check non-resizable widgets |
| ✅ | Widget padding |  |  | P1 | Per-widget padding from the long-press menu | widget_padding |
| ✅ | Widget corner radius | 7.0.39 |  | P2 | Custom corner radius for widgets | widget_corner_radius |
| ❔ | Widget long-press menu: remove, padding, resize, app info | 7.0.11 |  | P2 | App info target added in 7.0.11 | Compare menu items |
| ❔ | Desktop gestures work on top of widgets | 7.0.41 | Prime | P2 | Two-finger gestures over widgets | Behaviour |

</details>

<details>
<summary><b>Dock</b> · 14 features · ✅ 12 · 🟡 1 · ❌ 1</summary>

| Status | Feature | Since | Prime | Priority | What Nova did | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | Dock on or off | 1.x |  | P0 | Dock toggle | dock_enabled |
| ✅ | Dock pages | 1.x |  | P0 | One to five swipeable dock pages | dock_pages, up to 5 |
| ✅ | Icons per dock page | 1.x |  | P0 | Up to 7 per page in Nova | dock_count, 3 to 12 |
| ✅ | Dock icon layout |  |  | P1 | Icon size and labels set separately | dock_icon_scale, dock_labels, dock_match_desktop |
| ✅ | Dock infinite scroll |  |  | P2 | Dock pages wrap around | dock_infinite_scroll |
| ✅ | Dock width and height padding |  |  | P2 | Space around dock icons | dock_padding_h, dock_padding_v |
| ✅ | Dock as overlay |  |  | P2 | Dock drawn over the page | dock_overlay |
| ✅ | Close the overlay dock after launching an app |  |  | P2 | Needs dock as overlay | dock_auto_close |
| ✅ | Remember the dock page |  |  | P2 | Return to the last dock page used | dock_remember_position |
| 🟡 | Dock background shapes: rectangle, rounded, arc, platform |  |  | P2 | Four shapes | None, rounded, square plus a corner radius |
| ✅ | Dock background colour and transparency |  |  | P2 | Tint presets or any colour | dock_bg_opacity and colours |
| ❌ | Dock background image |  |  | P2 | From the gallery, dock theme apps or an icon pack | Not found |
| ✅ | Dock background behind the navigation bar | 5.0 |  | P2 | Background extends under the nav bar | dock_bg_behind_nav |
| ✅ | Dock swipe actions | 1.x | Prime | P1 | A swipe on a dock icon runs a second action | Icon swipe actions; check on the dock |

</details>

<details>
<summary><b>App drawer</b> · 27 features · ✅ 19 · 🟡 3 · ❔ 5</summary>

| Status | Feature | Since | Prime | Priority | What Nova did | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | Drawer style: horizontal pages, vertical grid, list | 1.x |  | P0 | Horizontal style returned in 7.0.10 | DrawerStyle |
| ✅ | Drawer grid, separate from the desktop |  |  | P0 | Its own rows and columns | drawer_columns, drawer_rows |
| ❔ | Landscape drawer grid |  |  | P2 | Separate grid in landscape | Not seen in prefs |
| ✅ | Drawer icon layout; match the desktop | 6.0 |  | P1 | Icon size and labels, or follow the desktop | drawer_icon_scale, drawer_match_desktop |
| 🟡 | Card or immersive background | 5.0 |  | P1 | A card panel, or a full-screen drawer | Colour and opacity only; no card panel |
| ✅ | Background colour and transparency | 1.x |  | P1 | Any colour, any transparency | drawer_bg_color, drawer_bg_opacity |
| ✅ | Swipe up to open the drawer | 5.0 |  | P0 | Pixel-style swipe from the desktop | Gesture |
| 🟡 | Open animation: circle reveal, slide up, zoom, fade | 2014 beta |  | P2 | Drawer transition choice | Slide up, zoom, fade; no circle reveal |
| ✅ | Drawer scroll effects in paged mode | 1.x |  | P2 | Same effects as the desktop | drawer_page_effect |
| ✅ | Fast scroller and scroll accent colour | 4.2 beta |  | P2 | Letter scrubber along the edge | drawer_fast_scroller, drawer_scrubber_color |
| ✅ | Frequently used apps row | 4.2 beta |  | P2 | Most-used apps at the top | drawer_frequent_row |
| ✅ | Search bar in the drawer | 4.2 beta |  | P1 | Persistent bar at the top of the drawer | Drawer bar |
| ❔ | Drawer search bar styled like the desktop bar | 6.0 |  | P2 | Same style options as the desktop bar | Compare options |
| ✅ | Search and tab bars at the bottom | 7.0 beta |  | P1 | Easier reach on tall phones | DrawerTabBar TOP, BOTTOM |
| ✅ | Pull to search | 7.0.11 |  | P2 | Pull down in the drawer to search | drawer_pull_to_search |
| ✅ | Tab bar on or off, and its style |  |  | P1 | Tab styles such as colour blocks or small tabs | DrawerTabStyle; styles differ from Nova |
| ❔ | Scrolling tab bar and tab menu action icons | 7.0.10 |  | P2 | Many tabs scroll; icons in the tab menu | Behaviour |
| ✅ | Custom tabs (drawer groups) |  | Prime | P0 | Unlimited custom tabs | drawer_groups |
| ✅ | Apps only in their tab, or also in All apps |  | Prime | P1 | Per-tab choice | drawer_isolate_tabs |
| ❔ | Category tabs | 7.0 beta | Prime | P2 | Tabs sorted by app category | smart_folders, SmartCategory |
| ✅ | Folders in the drawer |  | Prime | P0 | Drawer folders, inside tabs too | Nested drawer folders |
| ✅ | Hide apps | 1.x | Prime | P0 | Hidden without uninstalling | hidden_apps |
| ✅ | Hide from the long-press menu | 7.0.10 | Prime | P2 | One-tap hide | PopupExtraRow HIDE |
| ✅ | Work-profile apps in their own tab | 7.0.39 |  | P1 | Separate work tab | drawer_work_tab |
| 🟡 | Recent and new or updated apps | 5.0 |  | P2 | Frequent, Recent and New/Updated tabs in the search view | Recent row only |
| ❔ | Drawer greys out behind a windowed folder | 7.0.37 |  | P2 | Content fades to grayscale | One grayscale hit in the code |
| ✅ | Widget drawer |  |  | P1 | Browse and place widgets | WidgetPickerSheet |

</details>

<details>
<summary><b>Search</b> · 14 features · ✅ 7 · 🟡 2 · ❌ 2 · ❔ 2 · ⛔ 1</summary>

| Status | Feature | Since | Prime | Priority | What Nova did | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | App search | 2014 beta |  | P0 | Instant search over installed apps | Search package, 54 unit tests |
| 🟡 | Web search with a choice of provider | 6.0 |  | P1 | Google, DuckDuckGo and others | Providers present; no suggestions |
| ❌ | Web suggestions while typing | 7.0.37 |  | P2 | Autocomplete, e.g. with DuckDuckGo | Needs internet; keep optional if added |
| ✅ | Play Store search from results |  |  | P2 | Jump to a Play Store search | SearchChip PLAY_STORE |
| ✅ | Calculator |  |  | P2 | Sums answered in the search box | search_calculator |
| ✅ | Contacts in search | 5.4 |  | P2 | Arrived with the Sesame integration | search_contacts |
| ✅ | App shortcuts in search | 5.4 |  | P2 | Shortcuts in the results | search_shortcuts |
| 🟡 | Search bar style: shape, colour, logo, content | 4.3 |  | P1 | Bar style, colour and logo style choices; pill (5.0) and round (5.21) bars | Compare the range with Nova |
| ❔ | Voice and Assistant buttons in the bar | 6.0 |  | P2 | Assistant launch from the search widget | SearchBarChip VOICE |
| ❌ | Weather in the search bar | 7.0.54 |  | P2 | Was a Labs option before 7.0.54 | No internet permission; needs a local source |
| ❔ | Search apps from the desktop Google bar | 6.0 |  | P2 | The bar opens Nova search | Behaviour |
| ✅ | Search window: windowed or immersive | 7.0 beta |  | P2 | More search window options | SearchWindowMode |
| ✅ | Search in Nova Settings | 6.0 |  | P1 | Find any setting by name | Settings search |
| ⛔ | Sesame deep shortcuts | 5.4 |  | Skip | Proprietary; now owned by Branch | Out of scope |

</details>

<details>
<summary><b>Folders</b> · 11 features · ✅ 9 · ❌ 1 · ❔ 1</summary>

| Status | Feature | Since | Prime | Priority | What Nova did | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | Folder preview: grid, fan, stack, line, radial | 7.0 beta |  | P1 | Radial arrived in Nova 7 | FolderPreviewStyle |
| ✅ | Folder icon shapes match the icon shapes | 6.0 |  | P1 | Round, squircle and the rest | folder_icon_shape |
| ✅ | Folder icon colour |  |  | P2 | Background colour of the folder icon | folder_bg_color, folder_plate_color |
| ✅ | Replace the folder icon | 1.x |  | P2 | Any icon or image | setFolderIcon |
| ✅ | Windowed or immersive folders | 6.0 |  | P1 | Two window styles | FolderMode WINDOW, IMMERSIVE |
| ✅ | Folder window colour and transparency |  |  | P2 | Window background | folder colour prefs |
| ❔ | Bottom-aligned immersive folders | 8.0 beta |  | P2 | Align bottom option | Check |
| ✅ | Scroll vertically or horizontally; vertical paging | 6.0 |  | P2 | Paginated vertical scrolling came in 7 | FolderScroll |
| ✅ | Folder grid and icon layout |  |  | P2 | Columns, icon size, labels | folder_columns, folder_icon_scale |
| ❌ | Folder open transition |  |  | P2 | Zoom or circle | Not found |
| ✅ | Swipe to open (tap opens the first app) |  | Prime | P1 | Hidden folders | setFolderSwipeToOpen |

</details>

<details>
<summary><b>Look and feel</b> · 24 features · ✅ 19 · ❌ 4 · ❔ 1</summary>

| Status | Feature | Since | Prime | Priority | What Nova did | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | Icon packs | 1.x |  | P0 | Thousands of third-party icon themes | icon_pack |
| ✅ | Edit an icon: pack icon or image, custom label |  |  | P0 | Per-icon edits | AppEditSheet |
| ✅ | Adaptive icon shapes | 5.5 |  | P1 | System, round, squircle and more | IconShape |
| ✅ | Custom icon shape | 6.0 |  | P2 | Draw your own shape | IconShape CUSTOM |
| ✅ | Flower, pebble, vessel, pentagon, hexagon, heptagon, octagon shapes | 7.0.4 |  | P2 | Android 11 shapes plus polygons | All in IconShape |
| ✅ | Reshape legacy icons | 5.5 |  | P1 | Only adaptive icons, or force all | ReshapeLegacy OFF, AUTO, ON |
| ✅ | Normalize icon size | 4.1 |  | P2 | Even out icon sizes | icon_normalize |
| ✅ | Reshape themed icons, toggle per icon | 7.0.39 |  | P2 | Per-icon reshape switch | themed_icons, reshape_legacy |
| ❔ | Adaptive icon animations |  |  | P2 | Animated adaptive icons | A drag animation exists; compare |
| ❌ | Bypass system icon theme |  |  | P2 | Labs: skip OEM icon frames | Not found |
| ✅ | Night mode: off, always, auto by sunrise, set times | 4.3 |  | P1 | Schedules for the dark theme | ThemeMode, theme_schedule_* |
| ✅ | Night mode follows the system dark theme | 6.2.2 |  | P1 | Android 10 dark theme | ThemeMode SYSTEM |
| ❌ | Night mode per element: search bar, drawer, folders | 4.3 |  | P2 | Pick what turns dark | Themes everything at once |
| ❌ | Night mode shades: dark, darker, darkest | 6.1.x |  | P2 | Three dark colours | Not found |
| ✅ | Accent colour with full colour picker | 1.x |  | P1 | Launcher accent colour | accent_color |
| ✅ | Colours for labels, folders, badges, tabs, backgrounds |  |  | P1 | Colour controls | Any surface colour |
| ✅ | Material You colours | 8.0 beta |  | P1 | Colours from the wallpaper | palette_source, palette_seed |
| ✅ | App open animation | 6.0 |  | P2 | Includes the Android Pie style | LaunchAnimation |
| ✅ | Animation speed |  |  | P2 | Relaxed or fast | AnimationSpeed RELAXED, FAST, SYSTEM |
| ✅ | Popup menu style | 5.5 |  | P2 | Android 8.1 block style or pills | PopupMenuStyle |
| ✅ | Status bar: show or hide, transparent, dark icons |  |  | P2 | Notification bar options | hide_status_bar, transparent_status, dark_status_icons |
| ✅ | Haptic toggle for the notification swipe | 7.0 beta |  | P2 | Turn off the buzz on swipe-down | swipe_down_haptic |
| ✅ | Save APK in the long-press menu | 7.0 beta |  | P2 | Export an app's APK | PopupExtraRow SAVE_APK |
| ❌ | Shortcut and work badges toggle in Edit icon | 7.0.41 |  | P2 | Hide the small badges on an icon | Not found |

</details>

<details>
<summary><b>Notification badges</b> · 7 features · ✅ 5 · ❔ 1 · ⛔ 1</summary>

| Status | Feature | Since | Prime | Priority | What Nova did | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | Badge type: dynamic, dots, numeric dots, none | 5.1 |  | P1 | Dynamic 5.1, dots 5.21, numeric dots 6.1 | BadgeStyle DOTS, NUMERIC, DYNAMIC |
| ✅ | Numeric dots | 6.1 | Prime | P1 | Counts tied to the notifications | BadgeStyle NUMERIC |
| ✅ | Badge position and size | 5.21 |  | P2 | Corner and size | badge_position, badge_scale |
| ✅ | Badge colours |  |  | P2 | Badge and text colour | badge_color, badge_text_color |
| ✅ | Notifications in the long-press menu | 5.5 |  | P1 | Oreo-style notification popup | popup_notifications |
| ❔ | Dynamic badges on folders show the app's icon | 5.1 |  | P2 | A small app icon on the folder | Compare |
| ⛔ | TeslaUnread unread counts | 1.x | Prime | Skip | Legacy plugin, pulled from the Play Store in 2019 | Replaced by notification access |

</details>

<details>
<summary><b>Gestures</b> · 15 features · ✅ 13 · ❌ 2</summary>

| Status | Feature | Since | Prime | Priority | What Nova did | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | Swipe up and down | 1.x | Prime | P0 | Desktop gestures | Gesture SWIPE_UP, SWIPE_DOWN |
| ✅ | Pinch in and out | 1.x | Prime | P1 | Desktop gestures | PINCH_IN, PINCH_OUT |
| ✅ | Double tap |  | Prime | P1 | Often used to lock the screen | DOUBLE_TAP |
| ✅ | Double tap and swipe up or down | 5.0 | Prime | P2 | Double-tap-swipe gestures | DOUBLE_TAP_SWIPE_UP, DOUBLE_TAP_SWIPE_DOWN |
| ✅ | Two-finger swipe up and down | 1.x | Prime | P2 | Two-finger gestures | TWO_FINGER_SWIPE_UP, TWO_FINGER_SWIPE_DOWN |
| ✅ | Two-finger rotate |  | Prime | P2 | Clockwise and counter-clockwise | TWO_FINGER_ROTATE_CW, TWO_FINGER_ROTATE_CCW |
| ✅ | Swipe left and right | 8.0 beta | Prime | P2 | New desktop gestures | SWIPE_LEFT, SWIPE_RIGHT |
| ✅ | Home button action |  |  | P1 | What Home does on the home screen | home_button_action |
| ✅ | Gesture targets: Nova actions, apps, shortcuts |  |  | P0 | Three tabs of targets | GestureAction |
| ✅ | Nova Actions | 4.2 beta |  | P0 | Drawer, notifications, quick settings, recents, lock, search, voice, default page, settings, toggle dock or status bar, previews | GestureAction enum |
| ✅ | Lock screen through device admin or accessibility | 4.2 beta |  | P1 | Screen-off action | LockMethod ACCESSIBILITY, DEVICE_ADMIN |
| ❌ | Lock screen through root |  |  | P2 | Root screen-off method | Not found |
| ❌ | Timeout screen-lock method | 5.0 |  | P2 | Blacks the screen and shortens the timeout | Not found |
| ✅ | Icon swipe up action | 1.x | Prime | P0 | Second action on any icon or folder | swipeAction |
| ✅ | Icon swipe down action | 7.0 beta | Prime | P1 | Third action, different from swipe up | swipeActionDown |

</details>

<details>
<summary><b>Shortcuts</b> · 3 features · ✅ 3</summary>

| Status | Feature | Since | Prime | Priority | What Nova did | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | App shortcuts on long-press | 5.0 |  | P0 | Android 7.1 launcher shortcuts | Popup shortcuts |
| ✅ | Activities picker |  |  | P1 | Launch any app activity | ActivitiesPickerSheet |
| ✅ | Nova Actions as desktop shortcuts |  |  | P2 | Place an action on the home screen | ActionShortcut |

</details>

<details>
<summary><b>Integrations</b> · 2 features · ❌ 2</summary>

| Status | Feature | Since | Prime | Priority | What Nova did | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| ❌ | Google Discover page |  |  | P2 | Needs the Nova Google Companion app | Depends on Google's app; best effort |
| ❌ | Discover theme: light, dark, follow night mode | 6.1 |  | P2 | Dark Discover cards | Depends on the Discover page |

</details>

<details>
<summary><b>Backup and setup</b> · 4 features · ✅ 4</summary>

| Status | Feature | Since | Prime | Priority | What Nova did | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | Backup and restore | 1.x |  | P0 | Layout and settings in one file | exportBackup, importBackup |
| ✅ | Quick start | 5.0 |  | P2 | Pick theme, drawer and dock style in one place | Onboarding wizard |
| ✅ | Import from other launchers |  |  | P1 | Start from your current home screen | BackupLayouts |
| ✅ | Reset to defaults |  |  | P2 | Start over | resetToDefaults |

</details>

<details>
<summary><b>Labs</b> · 4 features · ✅ 3 · 🟡 1</summary>

| Status | Feature | Since | Prime | Priority | What Nova did | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | Hidden Labs menu |  |  | P2 | Held volume-down in Nova Settings | labs_enabled |
| ✅ | Big grid sizes |  |  | P2 | Lifts the grid caps | labs_big_grid |
| ✅ | Permissions list |  |  | P2 | Review the launcher's permissions | LabsPermission |
| 🟡 | Debug tools: clear icon cache, send device info |  |  | P2 | Troubleshooting | Clear icon cache present; check device info |

</details>

<details>
<summary><b>Beyond Nova</b> · 7 features · ✅ 5 · 🟡 1 · ❌ 1</summary>

| Status | Feature | Since | Prime | Priority | What Nova did | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| ✅ | Import a Nova backup |  |  | P0 | Bring a whole Nova setup across | favorites table, drawer groups |
| 🟡 | Import Nova's other settings |  |  | P1 | Look, gestures, badges and the rest | Reads grid, dock, drawer columns, subgrid, groups |
| ❌ | At a Glance via Smartspacer |  |  | P1 | Common add-on for Nova users | Smartspacer SDK, GPL-3.0 |
| ✅ | Private space (Android 15+) |  |  | P1 | Private apps in their own container | ACCESS_HIDDEN_PROFILES |
| ✅ | Feed page with cards |  |  | P2 | Calendar, media, contacts and widget cards | Feed package |
| ✅ | Automatic backups and recovery |  |  | P2 | Restored if the store breaks | writeAutoBackup, StoreRecovery |
| ✅ | Bring to front, send to back |  |  | P2 | Stacking order for overlaps | restackItem |

</details>

<details>
<summary><b>Project</b> · 7 features · 🟡 2 · ❌ 5</summary>

| Status | Feature | Since | Prime | Priority | What Nova did | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| ❌ | CI on every pull request |  |  | P0 | Lint, detekt, unit tests and a debug APK | Workflow ready in a separate change |
| ❌ | Own app ID, name and icon |  |  | P0 | Installs beside Supernova; no Nova branding | Still com.supernova.launcher |
| ❌ | Release signing and F-Droid listing |  |  | P1 | Reproducible builds like upstream | Upstream setup to copy |
| ❌ | Translations |  |  | P1 | Nova shipped 74 languages | English only |
| 🟡 | TalkBack support |  |  | P1 | Accessible custom views | 12 accessibility lint entries |
| 🟡 | Split LauncherActivity |  |  | P1 | Move UI state into controllers | 5,333 lines, 291 functions |
| ❌ | Give the layout its own store |  |  | P2 | Layout saves stop rewriting settings | One Preferences DataStore today |

</details>

<!-- parity:end -->

The tracker is generated. To change a row, edit [`docs/parity/nova-parity.csv`](docs/parity/nova-parity.csv), then run:

```sh
python3 tools/parity_readme.py
```

## Building

You need JDK 17 or newer and the Android SDK with the compile SDK the project uses (37).

```sh
./gradlew :app:assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`. Debug builds install beside release builds, because they use a `.debug` application ID suffix. Release signing is described in `app/build.gradle.kts`.

## Contributing

- Pick a ❌ or 🟡 row and open an issue before starting anything large.
- Have Nova 7.0.57 on a spare phone? Check ✅ rows side by side and correct their status.
- Keep the offline design: no internet permission, no analytics.
- If a change would suit Supernova too, offer it upstream as well.

## Credits and licence

- [Supernova Launcher](https://github.com/tanujnotes/Supernova) by Tanuj is the upstream project, licensed AGPL-3.0-or-later.
- NovaRedux's changes use the same licence; see [LICENSE](LICENSE).
- NovaRedux is not affiliated with Nova Launcher, TeslaCoil Software, Branch Metrics or Instabridge. Nova Launcher is a trademark of its owner.

## Sources for the tracker

- Release notes on APKMirror for Nova [5.0](https://apkmirror.com/apk/teslacoil-software/nova-launcher/nova-launcher-5-0-release), [6.0](https://www.apkmirror.com/apk/teslacoil-software/nova-launcher/nova-launcher-6-0-release/), [6.2.2](https://apkmirror.com/apk/teslacoil-software/nova-launcher/nova-launcher-6-2-2-release/nova-launcher-6-2-2-android-apk-download), [7.0.4](https://www.apkmirror.com/apk/teslacoil-software/nova-launcher/nova-launcher-7-0-4-release/nova-launcher-7-0-4-android-apk-download/), [7.0.10](https://apkmirror.com/apk/teslacoil-software/nova-launcher/nova-launcher-7-0-10-release), [7.0.11](https://apkmirror.com/apk/teslacoil-software/nova-launcher/nova-launcher-7-0-11-release), [7.0.15](https://www.apkmirror.com/apk/teslacoil-software/nova-launcher/nova-launcher-7-0-15-release/nova-launcher-7-0-15-android-apk-download/), [7.0.37](https://apkmirror.com/apk/teslacoil-software/nova-launcher/nova-launcher-7-0-37-release/nova-launcher-7-0-37-android-apk-download), [7.0.39](https://apkmirror.com/apk/teslacoil-software/nova-launcher/nova-launcher-7-0-39-release/nova-launcher-7-0-39-android-apk-download), [7.0.41](https://www.apkmirror.com/apk/teslacoil-software/nova-launcher/nova-launcher-7-0-41-release/), [7.0.54](https://apkmirror.com/apk/teslacoil-software/nova-launcher/nova-launcher-7-0-54-release/nova-launcher-7-0-54-android-apk-download), [7.0.58](https://apkmirror.com/apk/teslacoil-software/nova-launcher/nova-launcher-7-0-58-release) and the [8.0 beta](https://www.apkmirror.com/apk/teslacoil-software/nova-launcher/nova-launcher-8-0-release/)
- Nova's own [6.1 release page](https://novalauncher.com/versions/6-1) and its [beta changelog](https://novalauncher.com/beta/long/)
- Coverage of [4.2 beta](https://www.droid-life.com/2015/12/02/nova-launcher-beta-update-adds-gestures-for-double-tap-to-lock-google-now-more/), [4.3 night mode](https://www.androidauthority.com/nova-launcher-4-3-beta2-night-mode-683797), [5.0 beta](https://www.droid-life.com/?p=191465), [5.1 dynamic badges](https://www.notebookcheck.net/Nova-Launcher-beta-gets-Dynamic-Badges-feature.208744.0.html), [5.21 dots](https://9to5google.com/?p=179573), [5.4 Sesame](https://www.phonearena.com/news/Nova-Launcher-getting-Sesame-Shortcuts-integration-in-latest-update_id96387), [5.5](https://phandroid.com/2017/11/22/nova-launcher-adaptive-icons/), [6.0 beta](https://www.androidpolice.com/2018/08/07/nova-launcher-v6-0-beta-adds-search-settings-many-customization-options-apk-download/), [6.1 numeric dots](https://www.xda-developers.com/nova-launcher-stable-update-6dot1/), [night mode shades](https://droid-life.com/2019/06/17/nova-launcher-night-mode/amp) and the [Nova 7 beta](https://www.androidcentral.com/nova-launcher-7-completely-rewritten-heres-whats-new)
- Feature guides: [the dock](https://androidguys.com/featured/get-nova-launcher-customizing-dockcontest-included/), [Labs](https://android.gadgethacks.com/how-to/nova-launcher-101-unlock-hidden-labs-menu-for-experimental-features-0181743), [gestures](https://android.gadgethacks.com/how-to/nova-launcher-101-set-up-home-screen-gestures-0179904), [settings walkthrough](https://www.digitaltrends.com/phones/how-to-use-nova-launcher/), [Prime versus free](https://www.guidingtech.com/nova-launcher-prime-vs-nova-launcher/) and an [early Prime review](http://www.pnevroq.com/2012/06/review-nova-launcher-prime.html)
- Early store listings: [2012 Play listing](https://phandroid.com/2012/02/23/nova-launcher-officially-out-of-beta-comes-to-the-android-market-in-free-or-paid-version/) and [feature list](https://www.merlot.org/merlot/viewMaterial.htm?id=824720)
