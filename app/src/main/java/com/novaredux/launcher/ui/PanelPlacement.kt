package com.novaredux.launcher.ui

// Where a swipe's widget panel goes (#14), in screen pixels: centred across
// on its icon, and above the icon in the lower half of the room or below it
// in the upper, the side with more room; always inside the room, over the
// icon if it must. With no icon to go by, the middle.
internal object PanelPlacement {

    data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
    }

    // The panel's top-left corner.
    fun place(icon: Area?, width: Int, height: Int, room: Area, gap: Int): Pair<Int, Int> {
        val x = (icon?.centerX ?: room.centerX) - width / 2
        val y = when {
            icon == null -> room.centerY - height / 2
            icon.centerY > room.centerY -> icon.top - gap - height
            else -> icon.bottom + gap
        }
        return Pair(clamp(x, room.left, room.right - width), clamp(y, room.top, room.bottom - height))
    }

    // Kept to min..max; a panel bigger than the room lines up with its start.
    private fun clamp(value: Int, min: Int, max: Int): Int = value.coerceAtMost(max).coerceAtLeast(min)
}
