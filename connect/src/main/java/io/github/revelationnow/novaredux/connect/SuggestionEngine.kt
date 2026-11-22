package io.github.revelationnow.novaredux.connect

import java.net.URLEncoder

// Where each web search engine NovaRedux offers answers suggestions, every
// one in the OpenSearch suggestions format (OpenSearchSuggestions). The
// names are NovaRedux's own (its WebProvider). A custom search address has
// no entry here, and gets no suggestions.
enum class SuggestionEngine(private val endpoint: String) {
    GOOGLE("https://www.google.com/complete/search?client=firefox&ie=utf-8&oe=utf-8&q="),
    DUCKDUCKGO("https://ac.duckduckgo.com/ac/?type=list&q="),
    BRAVE("https://search.brave.com/api/suggest?q="),
    BING("https://www.bing.com/osjson.aspx?query="),
    ECOSIA("https://ac.ecosia.org/autocomplete?type=list&q="),
    STARTPAGE("https://www.startpage.com/suggestions?format=opensearch&q="),
    QWANT("https://api.qwant.com/api/suggest/?client=opensearch&q="),
    YAHOO("https://search.yahoo.com/sugg/os?output=fxjson&command="),
    YANDEX("https://suggest.yandex.com/suggest-ff.cgi?part="),
    KAGI("https://kagi.com/api/autosuggest?q=");

    // The query goes last, form-encoded.
    fun url(query: String): String = endpoint + URLEncoder.encode(query, "UTF-8")

    companion object {
        fun fromName(name: String?): SuggestionEngine? =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) }
    }
}
