package com.sus7898.lrrviewer.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

enum class TapZone { LEFT, RIGHT, TOP, BOTTOM, CENTER }

/** Maps a tap position to a navigation zone: outer ~30% = previous/next, middle = toggle UI. */
fun zoneOf(offset: Offset, size: IntSize, vertical: Boolean): TapZone {
    if (size.width <= 0 || size.height <= 0) return TapZone.CENTER
    return if (vertical) {
        val fy = offset.y / size.height
        when {
            fy < 0.28f -> TapZone.TOP
            fy > 0.72f -> TapZone.BOTTOM
            else -> TapZone.CENTER
        }
    } else {
        val fx = offset.x / size.width
        when {
            fx < 0.3f -> TapZone.LEFT
            fx > 0.7f -> TapZone.RIGHT
            else -> TapZone.CENTER
        }
    }
}
