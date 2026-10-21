package com.sus7898.lrrviewer

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderKeyEventsTest {

    private fun map(code: Int, shift: Boolean = false, volume: Boolean = true) = ReaderKeyEvents.map(code, shift, volume)

    @Test
    fun `page keys of a mini keyboard or remote turn pages`() {
        assertEquals(ReaderKey.NEXT, map(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(ReaderKey.NEXT, map(KeyEvent.KEYCODE_SPACE))
        assertEquals(ReaderKey.PREVIOUS, map(KeyEvent.KEYCODE_SPACE, shift = true))
        assertEquals(ReaderKey.NEXT, map(KeyEvent.KEYCODE_ENTER))
        assertEquals(ReaderKey.NEXT, map(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(ReaderKey.PREVIOUS, map(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertEquals(ReaderKey.PREVIOUS, map(KeyEvent.KEYCODE_PAGE_UP))
        assertEquals(ReaderKey.FIRST, map(KeyEvent.KEYCODE_MOVE_HOME))
        assertEquals(ReaderKey.LAST, map(KeyEvent.KEYCODE_MOVE_END))
    }

    @Test
    fun `shortcuts and exit`() {
        assertEquals(ReaderKey.TOGGLE_MENU, map(KeyEvent.KEYCODE_M))
        assertEquals(ReaderKey.PAGE_GRID, map(KeyEvent.KEYCODE_G))
        assertEquals(ReaderKey.TOGGLE_CONTINUOUS, map(KeyEvent.KEYCODE_W))
        assertEquals(ReaderKey.PLAY_PAUSE, map(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertEquals(ReaderKey.EXIT, map(KeyEvent.KEYCODE_ESCAPE))
        assertNull(map(KeyEvent.KEYCODE_Q))
    }

    @Test
    fun `volume keys only when enabled`() {
        assertNull(map(KeyEvent.KEYCODE_VOLUME_DOWN, volume = false))
        assertEquals(ReaderKey.NEXT, map(KeyEvent.KEYCODE_VOLUME_DOWN, volume = true))
    }
}
