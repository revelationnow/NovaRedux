package io.github.revelationnow.novaredux.connect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallersTest {

    @Test
    fun answersNovaReduxAndItsDebugBuildOnly() {
        assertTrue(Callers.named("io.github.revelationnow.novaredux"))
        assertTrue(Callers.named("io.github.revelationnow.novaredux.debug"))
        assertFalse(Callers.named("io.github.revelationnow.novaredux.evil"))
        assertFalse(Callers.named("com.example.browser"))
        assertFalse(Callers.named(null))
    }
}
