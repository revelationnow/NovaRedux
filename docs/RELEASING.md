# Releasing NovaRedux

NovaRedux and NovaRedux Connect are released from tags. The Release workflow
(`.github/workflows/release.yml`) builds the tagged app, signs it with the
project's key, and publishes the APK on the repository's Releases page with
its release notes. F-Droid builds the same tag from source and, when its build
matches the published APK byte for byte, publishes ours: a reproducible build,
as upstream Supernova has on F-Droid.

Both apps carry the one key. A phone only installs an update signed with the
key the installed app was signed with, so the key is made once and kept.

## Once: the signing key

1. Make a keystore, and keep it and its passwords somewhere safe outside the
   repository (a password manager). Losing it means no more updates for
   anyone who installed a release.

   ```sh
   keytool -genkeypair -v -keystore novaredux-release.jks -alias novaredux \
       -keyalg RSA -keysize 4096 -validity 36500
   ```

   Passwords without a backslash keep `keystore.properties` simple.

2. Add four repository secrets, under Settings > Secrets and variables >
   Actions > New repository secret:

   | Secret | Value |
   |---|---|
   | `SIGNING_KEYSTORE_BASE64` | `base64 -w0 novaredux-release.jks` (macOS: `base64 -i novaredux-release.jks`) |
   | `SIGNING_STORE_PASSWORD` | the keystore's password |
   | `SIGNING_KEY_ALIAS` | `novaredux`, or the alias you chose |
   | `SIGNING_KEY_PASSWORD` | the key's password |

3. Note the key's SHA-256 certificate fingerprint, for F-Droid:

   ```sh
   keytool -list -v -keystore novaredux-release.jks -alias novaredux | grep SHA256
   ```

   F-Droid wants it as lowercase hex without colons.

To build signed APKs on your own computer, put the keystore and a
`keystore.properties` in `local/` (git ignores it):

```properties
storeFile=local/novaredux-release.jks
storePassword=...
keyAlias=novaredux
keyPassword=...
```

## Each release of NovaRedux

1. In `app/build.gradle.kts`, raise `versionCode` by one and set
   `versionName` (`v0.3.0`).
2. Write the release notes in
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: what
   changed, for the people installing it, in under 500 characters. The app
   shows them under Settings > About > What's new, and F-Droid shows them too.
3. Commit, then tag and push:

   ```sh
   git tag v0.3.0
   git push origin v0.3.0
   ```

The workflow checks that the tag matches `versionName` and that the notes
exist, then builds, signs, checks the signature and publishes
`NovaRedux-v0.3.0.apk` with a `SHA256SUMS` file.

## Each release of NovaRedux Connect

The same, in `connect/build.gradle.kts` and
`connect/fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, with a
tag of the form `connect-v0.1.0`. Its APK is `NovaRedux-Connect-v0.1.0.apk`.
NovaRedux's prompt to install Connect opens the Releases page.

## F-Droid

`docs/fdroid/` holds the two apps' metadata, as fdroiddata expects it. For
the first listing:

1. Make the first releases above, so there are tags and APKs to compare with.
2. In each file, set `AllowedAPKSigningKeys` to the fingerprint from step 3
   of the key setup, and `Builds` and `CurrentVersion`/`CurrentVersionCode` to
   the released versions.
3. Fork https://gitlab.com/fdroid/fdroiddata, add the two files under
   `metadata/`, and open a merge request. F-Droid's reviewers run the build;
   their notes on the request say if anything differs.

After that, F-Droid's bot picks up each new tag (`UpdateCheckMode: Tags`) and
adds the build itself.

Reproducible builds depend on settings already in the build files: no
dependency-info block in the APK, and DataStore's native library shipped as it
comes. A change to the build that makes F-Droid's APK differ from ours shows as
a failed verification there, and that release doesn't appear on F-Droid until
it is fixed.
