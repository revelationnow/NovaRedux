package com.supernova.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Actions on what you type (#9): what counts as a number, a web address or
// an email address, and what stays a plain search.
class TypedTextTest {

    private fun kind(text: String) = TypedText.of(text)?.kind

    @Test
    fun phoneNumbersInTheirUsualShapes() {
        listOf("+44 20 7946 0958", "(555) 123-4567", "555.123.4567", "9876543210", "030 1234567").forEach {
            assertEquals(it, TypedText.Kind.PHONE, kind(it))
        }
    }

    @Test
    fun shortNumbersSumsAndDecimalsAreNotPhoneNumbers() {
        listOf("1234", "3.14159", "12*34567", "55+5555", "1234567890123456").forEach { assertNull(it, kind(it)) }
    }

    @Test
    fun webAddresses() {
        listOf("example.com", "www.wikipedia.org/wiki/Nova", "https://f-droid.org", "news.ycombinator.com/item?id=1")
            .forEach { assertEquals(it, TypedText.Kind.WEB, kind(it)) }
    }

    @Test
    fun fileNamesAndLibraryNamesStaySearches() {
        listOf("node.js", "notes.txt", "readme.md", "nova launcher").forEach { assertNull(it, kind(it)) }
    }

    @Test
    fun anyEndingWithAPrefix() {
        assertEquals(TypedText.Kind.WEB, kind("https://kvaesitso.mm20.de"))
    }

    @Test
    fun emailAddresses() {
        assertEquals(TypedText.Kind.EMAIL, kind("anoop.r+nova@example.co.uk"))
        assertNull(kind("not an@email"))
    }

    @Test
    fun aWebAddressOpensOverHttpsUnlessItSaysOtherwise() {
        assertEquals("https://example.com", TypedText.webUrl("example.com"))
        assertEquals("http://192.168.1.1", TypedText.webUrl("http://192.168.1.1"))
    }

    @Test
    fun theTextIsTrimmed() {
        assertEquals("example.com", TypedText.of("  example.com ")?.text)
    }
}
