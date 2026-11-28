# NovaRedux Connect

The optional companion app that goes online for NovaRedux, so the launcher itself never holds the internet permission. It has one job today: web search suggestions while a search is typed.

## How NovaRedux asks

NovaRedux queries this app's content provider:

```
content://io.github.revelationnow.novaredux.connect.suggestions/suggestions?q=<typed>&engine=<ENGINE>
```

- `engine` is the name of a web search engine in NovaRedux's settings (`GOOGLE`, `DUCKDUCKGO`, `BRAVE`, `BING`, `ECOSIA`, `STARTPAGE`, `QWANT`, `YAHOO`, `YANDEX` or `KAGI`). A custom search address gets no suggestions.
- The answer is one column, `suggestion`, best first.
- Only NovaRedux (`io.github.revelationnow.novaredux` and its `.debug` build) gets an answer; any other app gets a `SecurityException`.

NovaRedux's side is [`NovaConnect.kt`](../app/src/main/java/com/novaredux/launcher/connect/NovaConnect.kt); this side is [`SuggestionsContract.kt`](src/main/java/io/github/revelationnow/novaredux/connect/SuggestionsContract.kt). The two change together.

## What it sends

For each question, one HTTPS request to the engine's public suggestion address ([`SuggestionEngine.kt`](src/main/java/io/github/revelationnow/novaredux/connect/SuggestionEngine.kt)) with the typed words, this app's name as the user agent and the phone's language. No cookies, accounts or identifiers. Answers stay in memory for a few minutes, so the same words aren't asked twice; nothing is written to storage.

NovaRedux asks only when Web suggestions is on in its search settings, from the second letter typed, after a short pause in the typing.

## Checks

- Unit tests: `./gradlew :connect:testDebugUnitTest`
- The [Suggestion endpoints](../.github/workflows/suggest-endpoints.yml) workflow asks every engine for real each week and prints what each said, so an engine that moves its suggestion address shows up there.
