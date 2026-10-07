package com.sus7898.lrrviewer

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow

enum class ReaderKey { PREVIOUS, NEXT }

/** Routes hardware volume keys from the Activity to the reader while it is on screen. */
object ReaderKeyEvents {
    @Volatile var active: Boolean = false

    val events = MutableSharedFlow<ReaderKey>(extraBufferCapacity = 8)

    /** @return true when the event was consumed (reader active and a volume key). */
    fun onKey(keyCode: Int, isDown: Boolean): Boolean {
        if (!active) return false
        val key = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> ReaderKey.PREVIOUS
            KeyEvent.KEYCODE_VOLUME_DOWN -> ReaderKey.NEXT
            else -> return false
        }
        if (isDown) events.tryEmit(key)
        return true
    }
}
