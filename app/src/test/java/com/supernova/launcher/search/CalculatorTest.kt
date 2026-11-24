package com.supernova.launcher.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalculatorTest {

    private fun display(input: String): String? = Calculator.evaluate(input)?.display

    @Test
    fun arithmeticWithPrecedence() {
        assertEquals("= 42", display("5*8+2"))
        assertEquals("= 26", display("2+8*3"))
        assertEquals("= 30", display("(2+8)*3"))
        assertEquals("= 7", display("15 % 8"))
        assertEquals("= 2.5", display("5/2"))
    }

    @Test
    fun powerIsRightAssociative() {
        // 2^(3^2) = 512, not (2^3)^2 = 64.
        assertEquals("= 512", display("2^3^2"))
    }

    @Test
    fun unaryMinus() {
        assertEquals("= -6", display("-2*3"))
        assertEquals("= 1", display("-2+3"))
        assertEquals("= 10", display("5--5"))
        assertEquals("= -4", display("(-2)*2"))
    }

    @Test
    fun unicodeOperatorsAndThousandsSeparators() {
        assertEquals("= 42", display("84 ÷ 2"))
        assertEquals("= 42", display("21 × 2"))
        assertEquals("= 2000", display("1,000*2"))
    }

    @Test
    fun aCommaIsADecimalPointUnlessItGroupsThousands() {
        assertEquals("= 2.5", display("1,5+1"))
        assertEquals("= 6.28", display("3,14*2"))
        assertEquals("1.5 km = 1500 m", display("1,5 km to m"))
        // India's grouping: pairs after the first thousand.
        assertEquals("= 25000", display("1,00,000/4"))
        assertEquals("= 24691356", display("12,345,678*2"))
        // Both marks: the later one is the decimal point.
        assertEquals("= 1235", display("1,234.5+0.5"))
        assertEquals("= 1235", display("1.234,5+0,5"))
    }

    @Test
    fun aCommaThatReadsNeitherWayIsNoResult() {
        assertNull(display("1,2,3+1"))
        assertNull(display("5,+1"))
        assertNull(display("1,5,+1"))
    }

    @Test
    fun bareNumbersAndJunkAreNotResults() {
        assertNull(display("5"))
        assertNull(display("chrome"))
        assertNull(display("5*"))
        assertNull(display("(5+2"))
        assertNull(display("5..2+1"))
        assertNull(display(""))
    }

    @Test
    fun divisionByZeroIsRejected() {
        assertNull(display("5/0"))
    }

    @Test
    fun lengthConversion() {
        assertEquals("12 km = 7.456454 mi", display("12 km to mi"))
        assertEquals("1 m = 100 cm", display("m to cm"))
    }

    @Test
    fun temperatureConversion() {
        assertEquals("100 °F = 37.777778 °C", display("100 f to c"))
        assertEquals("0 °C = 32 °F", display("0 c in f"))
    }

    @Test
    fun dataAndMassConversion() {
        assertEquals("2 GB = 2048 MB", display("2 gb to mb"))
        assertEquals("1 kg = 2.204623 lb", display("1 kg to lb"))
    }

    @Test
    fun volumeSpeedAreaEnergyAndPressure() {
        assertEquals("1 gal = 3.785412 l", display("1 gal in l"))
        assertEquals("2 fl oz = 4 tbsp", display("2 fl oz in tbsp"))
        assertEquals("100 km/h = 62.137119 mph", display("100 km/h in mph"))
        assertEquals("30 kn = 55.56 km/h", display("30 knots to km/h"))
        assertEquals("1 ac = 4046.856422 m²", display("1 acre in m2"))
        assertEquals("1 kWh = 3600 kJ", display("1 kwh in kj"))
        assertEquals("1 atm = 14.695949 psi", display("1 atm to psi"))
    }

    @Test
    fun spacedUnitNamesAreFoldedTogether() {
        assertEquals("1 mi² = 2.589988 km²", display("1 square mile in sq km"))
        assertEquals("60 mph = 96.56064 km/h", display("60 miles per hour in km/h"))
        assertEquals("10 nmi = 18.52 km", display("10 nautical miles into km"))
        assertEquals("5 km = 3.106856 mi", display("5 kilometres in mi"))
    }

    @Test
    fun crossCategoryConversionIsRejected() {
        assertNull(display("5 kg to km"))
        assertNull(display("1 l to km"))
        assertNull(display("5 xyz to m"))
    }
}
