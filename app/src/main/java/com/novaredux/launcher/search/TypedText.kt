package com.novaredux.launcher.search

// What the search box holds when it is something to act on (#9): a phone
// number to call, a web address to open, an email address to write to.
// Pure Kotlin, so the rules are tested off the phone.
object TypedText {

    enum class Kind { PHONE, WEB, EMAIL }

    // What a row or its buttons do with the text.
    enum class Act { CALL, MESSAGE, OPEN, EMAIL, ADD_CONTACT }

    private const val MIN_DIGITS = 5
    private const val MAX_DIGITS = 15

    // The web pattern's groups: https://, www. and the address's ending.
    private const val SCHEME = 1
    private const val WWW = 2
    private const val ENDING = 4

    private val email = Regex("""[a-z0-9._%+-]+@[a-z0-9-]+(\.[a-z0-9-]+)*\.[a-z]{2,}""", RegexOption.IGNORE_CASE)
    private val web = Regex(
        """(https?://)?(www\.)?([a-z0-9-]+\.)+([a-z]{2,})(:\d+)?([/?#]\S*)?""", RegexOption.IGNORE_CASE
    )
    private val phone = Regex("""\+?\(?\d[\d ().-]*\d""")
    private val decimal = Regex("""\d+\.\d+""")

    // Endings a bare name must have to count as an address, so a search for
    // "node.js" or "notes.txt" stays a search. With https:// or www. in
    // front, any ending will do.
    private val endings = setOf(
        "com", "org", "net", "io", "dev", "app", "edu", "gov", "co", "uk", "in", "de", "fr", "es", "it",
        "nl", "ru", "jp", "cn", "br", "au", "ca", "info", "me", "tv", "xyz", "ai", "us", "eu", "ch", "se",
        "no", "pl", "be", "at", "dk", "fi", "cz", "pt", "gr", "tr", "kr", "mx", "ar", "za", "nz", "ie",
        "sg", "hk", "tw", "id", "my", "ph", "vn", "th", "il", "ua", "ro", "hu", "sk", "site", "online",
        "store", "blog", "page", "wiki", "news", "social", "email", "link", "tech", "art"
    )

    data class Match(val kind: Kind, val text: String)

    fun of(raw: String): Match? {
        val text = raw.trim()
        return when {
            text.isEmpty() || text.any { it.isWhitespace() } && !phone.matches(text) -> null
            email.matches(text) -> Match(Kind.EMAIL, text)
            isWeb(text) -> Match(Kind.WEB, text)
            isPhone(text) -> Match(Kind.PHONE, text)
            else -> null
        }
    }

    // The address a web match opens: https:// in front when none was typed.
    fun webUrl(text: String): String =
        if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
            text
        } else {
            "https://$text"
        }

    private fun isWeb(text: String): Boolean {
        val match = web.matchEntire(text) ?: return false
        val prefixed = match.groupValues[SCHEME].isNotEmpty() || match.groupValues[WWW].isNotEmpty()
        return prefixed || match.groupValues[ENDING].lowercase() in endings
    }

    private fun isPhone(text: String): Boolean =
        phone.matches(text) && !decimal.matches(text) && text.count { it.isDigit() } in MIN_DIGITS..MAX_DIGITS
}
