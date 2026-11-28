# Names: Supernova's and NovaRedux's

NovaRedux began as a copy of [Supernova](https://github.com/tanujnotes/Supernova)
v0.2.8 and kept its names until 28 November 2026, when they were renamed.
This page maps each Supernova name to the NovaRedux one, for reading
Supernova's code and porting its changes, and lists the names kept on
purpose. [`tools/upstream_names.py`](../tools/upstream_names.py) applies
the same map to Supernova's commits; a new rename goes in both.

The package mirrors Supernova's name for name (`com.supernova.launcher`
became `com.novaredux.launcher`), so every Supernova path has one NovaRedux
path and no line changed length. The app's ID, `io.github.revelationnow.novaredux`,
was NovaRedux's from the start, and NovaRedux Connect has always had its own
names.

## Renamed

### Code

| Supernova | NovaRedux |
| --- | --- |
| Package `com.supernova.launcher`, and every package under it | `com.novaredux.launcher` |
| Folders `app/src/{main,test,androidTest,debug}/java/com/supernova/launcher` | `app/src/…/java/com/novaredux/launcher` |
| `baselineprofile` package `com.supernova.launcher.baselineprofile` | `com.novaredux.launcher.baselineprofile` |
| Gradle `namespace` of `:app` and `:baselineprofile` | the same, renamed |
| `SupernovaApp` (`SupernovaApp.kt`) | `NovaReduxApp` (`NovaReduxApp.kt`) |
| `SupernovaAccessibilityService` | `NovaReduxAccessibilityService` |
| `SupernovaDeviceAdmin` | `NovaReduxDeviceAdmin` |
| `notification.SupernovaNotificationListener` | `notification.NovaReduxNotificationListener` |
| `ui.SupernovaPopupMenu` | `ui.NovaReduxPopupMenu` |
| `widget.SupernovaWidgetHost`, `SupernovaWidgetHostView` (`SupernovaWidgetHost.kt`) | `widget.NovaReduxWidgetHost`, `NovaReduxWidgetHostView` (`NovaReduxWidgetHost.kt`) |
| `Context.supernovaStore`, `supernovaLayoutStore`, `supernovaUsageStore` (Kotlin names) | `novaReduxStore`, `novaReduxLayoutStore`, `novaReduxUsageStore` |
| Log tag `Supernova` | `NovaRedux` |
| Crash log's own-frame test, classes starting `com.supernova.` | `com.novaredux.` |
| Wallpaper crop extra `com.supernova.launcher.extra.HOME_OFFSET` | `com.novaredux.launcher.extra.HOME_OFFSET` |
| Saved-state key `supernova:pending_widget` | `novaredux:pending_widget` |

### Resources

| Supernova | NovaRedux |
| --- | --- |
| `Theme.Supernova` and `Theme.Supernova.*` (`R.style.Theme_Supernova_*`) | `Theme.NovaRedux…` (`R.style.Theme_NovaRedux_*`) |
| `ThemeOverlay.Supernova.*` (accents, dialogs, sheets, settings pages) | `ThemeOverlay.NovaRedux.*` |
| `Widget.Supernova.*` (buttons, switches, chips, inputs, sheets) | `Widget.NovaRedux.*` |
| `TextAppearance.Supernova.*` | `TextAppearance.NovaRedux.*` |
| `ShapeAppearanceOverlay.Supernova.*` | `ShapeAppearanceOverlay.NovaRedux.*` |
| `@drawable/ic_supernova` | `@drawable/ic_novaredux` |
| `@string/restart_supernova`, `restart_supernova_subtitle` | `restart_novaredux`, `restart_novaredux_subtitle` |
| Custom views in layouts, `<com.supernova.launcher.ui.…>` | `<com.novaredux.launcher.ui.…>` |

### Build files

The R8 keep rules, the release baseline profiles, the lint and detekt
baselines and the Screens script (`tools/ci/screens.sh`) name the new
package and classes.

## Kept

These still say Supernova because phones, backups or other apps hold them.

| Name | What it is | Why it stays |
| --- | --- | --- |
| `supernova_store`, `supernova_layout`, `supernova_usage` | The DataStore files: settings, home screen layout, usage history | Renamed, every install would start with no settings or layout. Nobody sees them. |
| `supernova_crash.log` | The crash log file | The same; the backup rules leave it out by this name. |
| `supernova://action/…` | Launcher actions (Open drawer, Lock…) stored in layouts, gestures and backups | Layouts, NovaRedux backups and Supernova backups hold it. |
| `supernova:app` | An icon picked from an app's own icon, stored in icon edits | Icon edits and backups hold it. |
| `com.supernova.launcher.APPLY_ICON_THEME`, `com.supernova.launcher.extra.ICON_THEME_PACKAGE` | What an icon pack's Apply button sends | Packs that support Supernova send these, and reach NovaRedux with them. |
| `badge_test` and the other notification channel IDs | Android's channels | They never said Supernova. |
| "Supernova" in comments, commit messages and docs | Upstream's issues and versions ("Supernova issue 322", "Supernova v0.2.9") | They name upstream. Comments from before the rename may still call the app Supernova. |

## What the rename changed on a phone

Android knows an app's parts by their class names, so on a phone that had a
NovaRedux build from before the rename, after updating:

- Pick NovaRedux as the home app again (Android asks at the next Home).
- Turn notification access, the accessibility service or device admin back on, if they were.
- Put the glance widget back on the home screen, if it was there, and a
  home screen icon for NovaRedux's own Settings.

Settings, layout and backups are as they were.

## Porting a Supernova change

```sh
git fetch https://github.com/tanujnotes/Supernova.git main
git log --oneline <last ported>..FETCH_HEAD
python3 tools/upstream_names.py <commit> > port.patch
git am -3 port.patch
```

The patch carries the commit's files before and after with NovaRedux's
names, so `git am -3` merges like a cherry-pick: what conflicts is what
NovaRedux changed itself. The commit keeps its author and date, and
"(#322)" in its subject becomes "(Supernova issue 322)" so it doesn't link
to NovaRedux's own issue 322. To read one of Supernova's files in
NovaRedux's names: `git show <commit>:<path> | python3 tools/upstream_names.py --text`.

## Ported so far

Supernova is ported through v0.2.9 (`de54a7f`, 24 November 2026): start the
next `git log` there. Left out of v0.2.9, as NovaRedux had its own answer or
does it another way:

- `c3caa8d` (issue 345), `121bf37` (336), `8ee10e6` (348) and `ffe2125`
  (343): NovaRedux had fixed these itself.
- `fc471e8`, and the search bar rows of `c56a908` (323): in Supernova the
  dock's, badges', feed's, animations' and search bars' rows grey out while
  their switch is off; NovaRedux keeps hiding them. The greyed rows
  themselves came over from `c56a908`, for the rows that use them (Even row
  spacing).
- `de54a7f`: Supernova's version number.
