package com.novaredux.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TurnsTest {

    @Test
    fun aPhoneWellOnItsSideCountsAsTurned() {
        assertEquals(1, Turns.quarter(90, current = 0))
        assertEquals(3, Turns.quarter(270, current = 0))
        assertEquals(1, Turns.quarter(65, current = 0))
        assertEquals(3, Turns.quarter(295, current = 0))
    }

    @Test
    fun nearTheMiddleOfTwoTheTurnStaysAsItWas() {
        assertEquals(0, Turns.quarter(50, current = 0))
        assertEquals(1, Turns.quarter(40, current = 1))
        assertEquals(3, Turns.quarter(320, current = 3))
        assertEquals(0, Turns.quarter(310, current = 0))
    }

    @Test
    fun flatOrUpsideDownTheViewsStayUpright() {
        assertEquals(1, Turns.quarter(-1, current = 1))
        assertEquals(0, Turns.quarter(180, current = 1))
        assertEquals(0, Turns.quarter(359, current = 3))
    }

    @Test
    fun theViewsTurnBackTheOtherWayFromThePhone() {
        // On its side with its left edge up, the picture leans clockwise.
        assertEquals(-90, Turns.forViews(quarter = 1, displayRotation = 0))
        assertEquals(90, Turns.forViews(quarter = 3, displayRotation = 0))
        assertEquals(0, Turns.forViews(quarter = 0, displayRotation = 0))
    }

    @Test
    fun aWindowThatTurnedWithThePhoneLeavesNothingToTurn() {
        // Surface.ROTATION_90 is a phone turned anticlockwise, the reading 270.
        assertEquals(0, Turns.forViews(quarter = 3, displayRotation = 1))
        assertEquals(0, Turns.forViews(quarter = 1, displayRotation = 3))
    }
}
