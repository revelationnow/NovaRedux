# Privacy policy

Effective 22 November 2026. Applies to the NovaRedux launcher app
(`io.github.revelationnow.novaredux`), a modified version of the Supernova
launcher, and to its optional companion app NovaRedux Connect
(`io.github.revelationnow.novaredux.connect`), wherever they are installed
from.

## In short

NovaRedux collects no data. It has no internet permission, no analytics, no
ads, no accounts and no tracking. Everything it reads stays on your phone,
and nothing is sent to the developer or anyone else. The one exception is
optional: with web suggestions turned on and the NovaRedux Connect app
installed, what you type in search goes to your chosen search engine to
suggest searches.

## What NovaRedux reads locally

Each of these is used only on the device, only for the feature named, and
most are off until you turn the feature on.

| Access | Used for | When it is asked for |
| --- | --- | --- |
| Installed apps (`QUERY_ALL_PACKAGES`) | Showing your apps in the drawer, on the home screen and in search, and the Activities picker | Granted at install; a launcher needs it to work |
| Contacts (`READ_CONTACTS`) | Contacts in search results and the feed's contacts card | Only when you turn on contact search or that card |
| Calendar (`READ_CALENDAR`) | The feed's upcoming events card | Only when you add that card |
| Notification access | Notification badges, notifications in an app's long-press menu, and the feed's media card | Only when you turn on one of these |
| Accessibility service | Locking the screen, opening Recent apps and opening Quick settings from a gesture. It reads no screen content and nothing you type | Only after you agree in NovaRedux's dialog and switch it on in Android's settings |
| Device admin (force lock only) | Locking the screen from a gesture, as an alternative to the accessibility service. It can't wipe data or change passwords | Only if you choose this lock method |
| Phone (`CALL_PHONE`) | Direct dial shortcuts placed on the home screen | The first time you tap one |
| Modify system settings (`WRITE_SETTINGS`) | The Screen timeout lock method: the screen timeout is shortened while its black screen shows, and put back once the screen is off | Only if you choose this lock method |
| Root (`su`), if your phone has it | The Root lock method, which presses the power key | Only if you choose this lock method; your root manager asks you |
| Weather from Breezy Weather (`org.breezyweather.READ_PROVIDER`) | The temperature and conditions on the search bar's weather button, read from the Breezy Weather app on your phone for the first place in its list | Only when you add the weather button and allow it |
| Post notifications | The "Test notification badge" row in Settings | Only when you tap that row |
| Private space apps (`ACCESS_HIDDEN_PROFILES`) | Showing Android 15+ private space apps while NovaRedux is your home app | Granted at install |
| Set wallpaper, close apps, request uninstall | Setting a wallpaper you picked, the optional Close app row, and uninstalling an app you chose | Granted at install; used only when you take that action |

## What NovaRedux stores locally

Your layout, settings, icons, folders, drawer groups, automatic backups and
a crash log are kept in NovaRedux's private app storage on your phone.
Uninstalling NovaRedux, or clearing its storage, deletes them.

## When something leaves your phone

Only when you do it yourself:

- **Web search.** Tapping a web search result opens your browser with your
  query on the search engine you picked. From then on that search engine's
  and that browser's privacy policies apply.
- **Web suggestions** (off unless you turn them on, and only with NovaRedux
  Connect installed). While you type a search, NovaRedux passes what you
  have typed (two letters or more, after a pause) to NovaRedux Connect, which
  asks the suggestion service of the search engine you picked and hands the
  suggestions back. See below.
- **Sharing.** Export backup and Share crash log hand a file to the app you
  choose. The crash log holds the error, the app version and the device
  model, and is sent only if you share it.
- **Report an issue** opens a new GitHub issue in your browser with the app
  version, Android version and device model filled in. You can edit or
  remove these before posting.
- **Android backup.** If Android's own backup is on, Android may include
  NovaRedux's settings in your device backup (for example to your Google
  account) or copy them to a new phone. NovaRedux doesn't send these
  itself; your device backup settings control it.

## NovaRedux Connect

NovaRedux Connect is a separate, optional app, and the only part of
NovaRedux with internet access. It exists so that the launcher itself never
needs that permission. It does one thing: when NovaRedux asks, it sends
what you have typed in NovaRedux's search to the suggestion service of the
search engine you picked in NovaRedux (Google, DuckDuckGo, Brave, Bing,
Ecosia, Startpage, Qwant, Yahoo, Yandex or Kagi), over HTTPS, and returns
the suggested searches. That search engine's privacy policy applies to
what it receives.

It sends no cookies, accounts, identifiers or history. Requests carry its
own name as the user agent and your phone's language, so the suggestions
come in it, and the search engine sees your IP address as with any web
request. It answers only NovaRedux (other apps are refused), keeps recent
answers in memory for a few minutes so the same words aren't asked twice,
and stores nothing. Turn off Web suggestions in NovaRedux's search settings,
or uninstall NovaRedux Connect, to stop it.

## Children

NovaRedux is not aimed at children and collects no data from anyone.

## Changes

Changes to this policy are published in this file in the source repository,
with a new effective date. Its history is public.

## Contact

Questions: open an issue at https://github.com/revelationnow/NovaRedux/issues.
