package com.novaredux.launcher.data

import kotlin.math.abs
import kotlin.math.roundToInt

// Fine widget placement: a widget moves and resizes in sixteenths of a cell,
// and an edge that comes within a sixteenth of a whole or half cell is
// pulled onto it, as a magnet would, so lining widgets up with the grid
// and each other takes no care.
object FinePlacement {

    // The steps a cell is cut into; a widget placed finely counts its cells
    // in these (LauncherItem.fineCells).
    const val UNITS = 16

    // The steps between the lines that pull: every half cell.
    private const val LINE = UNITS / 2

    // How near a line, in steps, pulls a position onto it.
    private const val PULL = 1

    // A position in steps, pulled onto the whole or half cell beside it.
    fun snapSteps(steps: Int): Int {
        val line = (steps.toFloat() / LINE).roundToInt() * LINE
        return if (abs(steps - line) <= PULL) line else steps
    }

    // A position in cells: to the nearest step, then pulled the same way.
    fun snap(cells: Float): Float = snapSteps((cells * UNITS).roundToInt()) / UNITS.toFloat()
}
