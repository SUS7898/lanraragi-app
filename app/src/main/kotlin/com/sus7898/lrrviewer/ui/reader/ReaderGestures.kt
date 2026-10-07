package com.sus7898.lrrviewer.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

enum class TapZone { LEFT, RIGHT, TOP, BOTTOM, CENTER }

/**
 * Maps a tap position to a navigation zone: outer 30% = previous/next, middle 40% = toggle UI.
 *
 * The zones are measured on the *displayed image* ([contentBounds], in viewport coordinates, clamped to the
 * viewport) rather than on the whole screen, so a page letter-boxed in the middle of a landscape tablet still
 * has its own left/center/right thirds, and taps in the empty margins beside it count as the nearest edge.
 * Without bounds (webtoon list, image not yet laid out) the viewport itself is used.
 */
fun zoneOf(offset: Offset, size: IntSize, vertical: Boolean, contentBounds: Rect? = null): TapZone {
    if (size.width <= 0 || size.height <= 0) return TapZone.CENTER
    val viewport = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
    val bounds = contentBounds
        ?.takeIf { it.width > 1f && it.height > 1f }
        ?.intersect(viewport)
        ?.takeIf { it.width > 1f && it.height > 1f }
        ?: viewport
    return if (vertical) {
        val fy = (offset.y - bounds.top) / bounds.height
        when {
            fy < 0.3f -> TapZone.TOP
            fy > 0.7f -> TapZone.BOTTOM
            else -> TapZone.CENTER
        }
    } else {
        val fx = (offset.x - bounds.left) / bounds.width
        when {
            fx < 0.3f -> TapZone.LEFT
            fx > 0.7f -> TapZone.RIGHT
            else -> TapZone.CENTER
        }
    }
}
