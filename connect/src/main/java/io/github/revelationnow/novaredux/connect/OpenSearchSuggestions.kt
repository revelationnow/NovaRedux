package io.github.revelationnow.novaredux.connect

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

// The OpenSearch suggestions format every engine here answers in: a JSON
// array of the query, then the array of suggestions, then extras some
// engines add (descriptions, links, their own metadata). Anything else
// counts as no suggestions.
object OpenSearchSuggestions {

    fun parse(body: String): List<String> {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonArray
        val suggestions = root?.getOrNull(1) as? JsonArray ?: return emptyList()
        return suggestions
            .mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content?.trim() }
            .filter(String::isNotEmpty)
    }
}
