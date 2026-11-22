package io.github.revelationnow.novaredux.connect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallersTest {

    @Test
    fun answersNovaReduxAndItsDebugBuildOnly() {
        assertTrue(Callers.mayAsk("io.github.revelationnow.novaredux"))
        assertTrue(Callers.mayAsk("io.github.revelationnow.novaredux.debug"))
        assertFalse(Callers.mayAsk("io.github.revelationnow.novaredux.evil"))
        assertFalse(Callers.mayAsk("com.example.browser"))
        assertFalse(Callers.mayAsk(null))
    }
}
