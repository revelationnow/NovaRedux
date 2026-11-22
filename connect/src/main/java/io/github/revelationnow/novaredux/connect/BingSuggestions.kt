package io.github.revelationnow.novaredux.connect

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Bing's own suggestion format (AnswerFormat.BING): each result group's
// "Suggests", each suggestion's "Txt". Anything else is no suggestions.
object BingSuggestions {

    fun parse(body: String): List<String> {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
        val results = (root?.get("AS") as? JsonObject)?.get("Results") as? JsonArray ?: return emptyList()
        return results
            .flatMap { group -> ((group as? JsonObject)?.get("Suggests") as? JsonArray).orEmpty() }
            .mapNotNull { text((it as? JsonObject)?.get("Txt")) }
            .filter(String::isNotEmpty)
    }

    private fun text(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content?.trim()
}
