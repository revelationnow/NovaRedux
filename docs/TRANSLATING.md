# Translating NovaRedux

Nova Launcher was translated into 74 languages by its users. NovaRedux's text
lives in two files:

- `app/src/main/res/values/strings.xml`: the launcher and its settings
  (about 1,100 strings)
- `connect/src/main/res/values/strings.xml`: NovaRedux Connect (a few)

A translation is a copy of a file under `values-<language>/`, for example
`app/src/main/res/values-de/strings.xml` for German or `values-pt-rBR/` for
Brazilian Portuguese, with each string's text translated and its `name` kept.
The build lists every translation in the phone's per-app language settings
(Android 13 and later), so a language can be picked for NovaRedux alone.

## Doing it

1. Copy the English file into the new folder.
2. Translate the text between the tags. Leave out any string marked
   `translatable="false"`.
3. Keep placeholders as they are: `%1$s`, `%2$d` and `%%` stand for values
   the app fills in, in any order the sentence needs.
4. Escape an apostrophe as `\'` and a double quote as `\"`, as the English
   file does.
5. Open a pull request. A partly translated file is welcome: anything left
   out shows in English.

CI checks every translation: placeholders that don't match the English
string, or a string that isn't in the English file, fail the build.

## Style

The English is plain and short: sentence case, "you" where a person is
addressed, no exclamation marks, and the names of Nova's own features as Nova
used them (Dock, App drawer, Gestures). Translations work best the same way:
the words the phone's own settings use in that language, rather than a literal
rendering.
