# Security

NovaRedux is a home screen app, so it sees which apps you have and how you
use your phone. This file says how to report a problem, how to check that
an APK was built from this repository, and what the apps can and can't do.
[PRIVACY.md](PRIVACY.md) lists every permission and what it is used for.

## Reporting a problem

Please report anything that could put users at risk privately, through
**Security → Report a vulnerability** on the repository's GitHub page, not
in a public issue. Say what you found, how to reproduce it, and which
version (Settings → About). The report stays between you and the
maintainers until they publish an advisory. Anything else goes in an ordinary
[issue](https://github.com/revelationnow/NovaRedux/issues).

## Checking an APK

Source code alone doesn't show that the APK you install was built from it.
Three ways to check:

1. **Build provenance.** Every release APK, and every debug APK that CI
   builds, has a signed attestation of where it was built: this
   repository's workflow, at which commit. With the
   [GitHub CLI](https://cli.github.com), signed in:

   ```
   gh attestation verify NovaRedux-v0.3.0.apk --repo revelationnow/NovaRedux
   ```

   It fails for an APK that wasn't built here, or was changed since.

2. **Checksums.** Each release lists the APK's SHA-256 in `SHA256SUMS`:
   run `sha256sum -c SHA256SUMS` beside the downloaded APK.

3. **Signing certificate.** Each release's notes give the SHA-256
   fingerprint of the certificate it is signed with. Every release of
   NovaRedux and NovaRedux Connect uses the same one, and Android won't
   install an update signed with another.
   `apksigner verify --print-certs NovaRedux-v0.3.0.apk` shows the one an
   APK carries. Once NovaRedux is on F-Droid (`docs/fdroid`), F-Droid will
   build each release from source itself and publish the project-signed
   APK only when its build matches it byte for byte.

## What the build checks

- The GitHub Actions the workflows use are pinned to a release's commit,
  not to a tag that can be moved.
- The Gradle wrapper jar is checked against Gradle's published checksums
  on every run. The wrapper checks the Gradle it downloads against
  `distributionSha256Sum`, and CI checks that sum is the one Gradle
  publishes.
- `gradle/verification-metadata.xml` holds the SHA-256 of every plugin and
  library the builds fetch, and Gradle stops on anything that differs or
  isn't listed. When a dependency changes, the Supply chain workflow writes
  the file afresh, so the change shows up in review.
- CI checks that the launcher's APK holds no internet permission.

## What the apps can do

- **No internet in the launcher.** NovaRedux declares no internet
  permission, and removes one any library might add. NovaRedux Connect, a
  separate optional app, is the only part that goes online. It asks your
  chosen search engine for suggestions while you type, over HTTPS, and
  follows no redirects. The two check each other's signing key: Connect
  answers only NovaRedux, and NovaRedux sends your typing only to a Connect
  signed with its own key.
- **Accessibility service** (optional): performs global actions only (lock,
  Recents, Quick settings, power menu, screenshot, split screen). Its
  configuration can't read window content or perform gestures, and it
  ignores accessibility events.
- **Notification access** (optional): reads notifications for badges and
  their pictures, the icon menu's previews, the newest message in place of
  an icon's label, and the notification summary. The summary holds
  notifications from the apps you pick until its hours, using Android's
  own snooze. Nothing is stored or sent.
- **Root** (optional lock method): runs one fixed command, a power key
  press, through your root manager. A root grant gives an app full control
  of the phone, so NovaRedux warns you before you pick it. The
  accessibility service and device admin lock without root.
- **Stored intents.** Shortcuts made by other apps, picked activities and
  imported backups can hold any intent. NovaRedux starts them without URI
  permission grants, and they can't open NovaRedux's own internal screens.
  A phone call in an imported backup opens the dialer with the number
  instead.
- **Backups.** Android's backup takes NovaRedux's layout and settings, not
  its recent searches, launch counts, recently opened apps or crash log.
- **Feed page** (optional): the app picked as the feed provider (the
  Google app through a bridge, say) is given the launcher's window and
  scroll position so it can draw there, as Nova's feed worked. What it
  shows and sends is up to that app.

## Advice for the cautious

- Install NovaRedux Connect only if you want web suggestions. Without it,
  nothing NovaRedux does needs the internet.
- Grant notification access, contacts, calendar, usage access and the
  accessibility service only for the features you use. Each is asked for
  when you turn its feature on.
- Prefer the accessibility service or device admin to root for locking.
- Import only backups you made yourself.
