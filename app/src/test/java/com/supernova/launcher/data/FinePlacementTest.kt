package com.supernova.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

// Fine widget placement: sixteenths of a cell, pulled onto whole and half
// cells close by.
class FinePlacementTest {

    @Test
    fun stepsBesideAWholeOrHalfCellArePulledOntoIt() {
        val steps = listOf(0, 1, 2, 6, 7, 8, 9, 10, 15, 17)
        assertEquals(listOf(0, 0, 2, 6, 8, 8, 8, 10, 16, 16), steps.map(FinePlacement::snapSteps))
    }

    @Test
    fun cellsGoToTheNearestStepFirst() {
        // 0.3 cells is 4.8 steps: 5, away from any line.
        assertEquals(0.3125f, FinePlacement.snap(0.3f), 0f)
        // 0.47 cells is 7.52 steps: 8, the half cell.
        assertEquals(0.5f, FinePlacement.snap(0.47f), 0f)
        assertEquals(2f, FinePlacement.snap(2.04f), 0f)
    }
}
