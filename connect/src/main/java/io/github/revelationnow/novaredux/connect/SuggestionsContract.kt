package io.github.revelationnow.novaredux.connect

// What NovaRedux asks and what it gets back:
// content://AUTHORITY/suggestions?q=<typed>&engine=<name>, where the name is
// one of NovaRedux's web search engines (its WebProvider), answered as one
// column of suggestions, best first. NovaRedux's side of it is
// app/src/main/java/com/supernova/launcher/connect/NovaConnect.kt; the two
// change together.
object SuggestionsContract {
    const val AUTHORITY = "io.github.revelationnow.novaredux.connect.suggestions"
    const val PATH = "suggestions"
    const val PARAM_QUERY = "q"
    const val PARAM_ENGINE = "engine"
    const val COLUMN = "suggestion"
}
