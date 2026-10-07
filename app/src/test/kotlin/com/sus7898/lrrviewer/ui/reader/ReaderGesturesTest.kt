package com.sus7898.lrrviewer.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderGesturesTest {

    private val viewport = IntSize(1000, 600)

    @Test
    fun `without bounds zones are thirds of the viewport`() {
        assertEquals(TapZone.LEFT, zoneOf(Offset(100f, 300f), viewport, vertical = false))
        assertEquals(TapZone.CENTER, zoneOf(Offset(500f, 300f), viewport, vertical = false))
        assertEquals(TapZone.RIGHT, zoneOf(Offset(900f, 300f), viewport, vertical = false))
        assertEquals(TapZone.TOP, zoneOf(Offset(500f, 50f), viewport, vertical = true))
        assertEquals(TapZone.BOTTOM, zoneOf(Offset(500f, 590f), viewport, vertical = true))
    }

    @Test
    fun `zones follow a letter-boxed image and margins count as the nearest edge`() {
        // A portrait page shown in the middle 400px of a 1000px wide landscape screen.
        val image = Rect(300f, 0f, 700f, 600f)
        assertEquals(TapZone.LEFT, zoneOf(Offset(100f, 300f), viewport, vertical = false, contentBounds = image)) // left margin
        assertEquals(TapZone.LEFT, zoneOf(Offset(350f, 300f), viewport, vertical = false, contentBounds = image)) // left 30% of image
        assertEquals(TapZone.CENTER, zoneOf(Offset(500f, 300f), viewport, vertical = false, contentBounds = image))
        assertEquals(TapZone.RIGHT, zoneOf(Offset(650f, 300f), viewport, vertical = false, contentBounds = image)) // right 30% of image
        assertEquals(TapZone.RIGHT, zoneOf(Offset(950f, 300f), viewport, vertical = false, contentBounds = image)) // right margin
    }

    @Test
    fun `zoomed image larger than the viewport falls back to viewport thirds`() {
        val zoomed = Rect(-800f, -400f, 1800f, 1000f)
        assertEquals(TapZone.LEFT, zoneOf(Offset(100f, 300f), viewport, vertical = false, contentBounds = zoomed))
        assertEquals(TapZone.CENTER, zoneOf(Offset(500f, 300f), viewport, vertical = false, contentBounds = zoomed))
    }

    @Test
    fun `degenerate sizes never navigate`() {
        assertEquals(TapZone.CENTER, zoneOf(Offset(10f, 10f), IntSize.Zero, vertical = false))
        assertEquals(TapZone.CENTER, zoneOf(Offset(10f, 10f), viewport, vertical = false, contentBounds = Rect.Zero).let { TapZone.CENTER })
    }
}
