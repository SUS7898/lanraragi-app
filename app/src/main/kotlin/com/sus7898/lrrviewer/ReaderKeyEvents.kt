package com.sus7898.lrrviewer

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow

enum class ReaderKey { PREVIOUS, NEXT }

/**
 * Routes hardware keys from the Activity to the reader while it is on screen: volume keys (when enabled in
 * settings) and, always, the page keys of a physical keyboard (DeX / Bluetooth): arrows, Page Up/Down, Space.
 */
object ReaderKeyEvents {
    /** True while a reader is on screen. */
    @Volatile var active: Boolean = false
    @Volatile var volumeKeys: Boolean = false

    val events = MutableSharedFlow<ReaderKey>(extraBufferCapacity = 8)

    /** @return true when the event was consumed (reader active and a navigation key). */
    fun onKey(keyCode: Int, isDown: Boolean): Boolean {
        if (!active) return false
        val key = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> if (volumeKeys) ReaderKey.PREVIOUS else return false
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (volumeKeys) ReaderKey.NEXT else return false
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP -> ReaderKey.PREVIOUS
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_SPACE -> ReaderKey.NEXT
            else -> return false
        }
        if (isDown) events.tryEmit(key)
        return true
    }
}
