package com.novaredux.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

// Where a swipe's widget panel goes by its icon (#14).
class PanelPlacementTest {

    private val room = PanelPlacement.Area(0, 100, 1000, 2000)

    private fun icon(left: Int, top: Int) = PanelPlacement.Area(left, top, left + 150, top + 150)

    @Test
    fun belowAnIconInTheUpperHalf() {
        assertEquals(Pair(275, 470), PanelPlacement.place(icon(400, 300), 400, 300, room, 20))
    }

    @Test
    fun aboveAnIconInTheLowerHalf() {
        assertEquals(Pair(275, 1380), PanelPlacement.place(icon(400, 1700), 400, 300, room, 20))
    }

    @Test
    fun aTallPanelIsPulledBackIntoTheRoom() {
        assertEquals(Pair(275, 100), PanelPlacement.place(icon(400, 1200), 400, 1500, room, 20))
    }

    @Test
    fun keptInsideTheRoomAcross() {
        assertEquals(Pair(0, 470), PanelPlacement.place(icon(0, 300), 400, 300, room, 20))
        assertEquals(Pair(600, 470), PanelPlacement.place(icon(850, 300), 400, 300, room, 20))
    }

    @Test
    fun inTheMiddleWithNoIcon() {
        assertEquals(Pair(300, 900), PanelPlacement.place(null, 400, 300, room, 20))
    }

    @Test
    fun aPanelWithNoRoomEitherSideStaysInTheRoom() {
        val small = PanelPlacement.Area(0, 100, 1000, 700)
        assertEquals(Pair(275, 200), PanelPlacement.place(icon(400, 300), 400, 500, small, 20))
    }
}
