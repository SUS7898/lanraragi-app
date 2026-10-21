package com.sus7898.lrrviewer

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.atomic.AtomicInteger

/** Reader actions reachable from a hardware keyboard / remote (e.g. Rii i4 mini keyboard), DeX or volume keys. */
enum class ReaderKey { PREVIOUS, NEXT, FIRST, LAST, TOGGLE_MENU, PAGE_GRID, TOGGLE_CONTINUOUS, PLAY_PAUSE, EXIT }

/**
 * Routes hardware keys from the Activity to the reader while it is on screen. Volume keys only when enabled in
 * the settings; everything else always (a physical keyboard never sends them by accident).
 */
object ReaderKeyEvents {
    /** True while a reader is on screen. */
    @Volatile var active: Boolean = false
    @Volatile var volumeKeys: Boolean = false

    val events = MutableSharedFlow<ReaderKey>(extraBufferCapacity = 8)

    /**
     * Readers currently composed. "Next archive" swaps readers with a cross-fade, so the new reader starts before the
     * old one is disposed; counting keeps the old reader's cleanup from undoing the new reader's keys/window setup.
     */
    private val readers = AtomicInteger()
    private val ids = AtomicInteger()

    /** Only the newest reader handles keys, so during the cross-fade a key (e.g. Esc) is not handled twice. */
    @Volatile var currentSession: Int = 0
        private set

    /** Registers a reader and returns its session id. */
    fun enterReader(): Int {
        readers.incrementAndGet()
        active = true
        return ids.incrementAndGet().also { currentSession = it }
    }

    /** @return true when this was the last reader, i.e. the caller should restore the window. */
    fun exitReader(): Boolean {
        val left = readers.decrementAndGet()
        if (left > 0) return false
        readers.set(0)
        active = false
        volumeKeys = false
        return true
    }

    /** @return true when the event was consumed (reader active and a mapped key). */
    fun onKey(keyCode: Int, isDown: Boolean, shift: Boolean = false, repeat: Int = 0): Boolean {
        if (!active) return false
        val key = map(keyCode, shift, volumeKeys) ?: return false
        // Holding an arrow keeps turning pages; one-shot actions fire once per press.
        if (isDown && (repeat == 0 || key == ReaderKey.NEXT || key == ReaderKey.PREVIOUS)) events.tryEmit(key)
        return true
    }

    /**
     * Key → action. Arrows / Page Up-Down / Space / Enter turn pages (Shift+Space and Shift+Enter go back),
     * Home/End jump, media keys work as a remote, Esc leaves the reader, and letters are shortcuts:
     * M = menu, G = page grid, W = 연속 보기, A or media play/pause = auto-scroll.
     */
    fun map(keyCode: Int, shift: Boolean, volumeKeys: Boolean): ReaderKey? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> if (volumeKeys) ReaderKey.PREVIOUS else null
        KeyEvent.KEYCODE_VOLUME_DOWN -> if (volumeKeys) ReaderKey.NEXT else null
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_DEL -> ReaderKey.PREVIOUS
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> ReaderKey.NEXT
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER ->
            if (shift) ReaderKey.PREVIOUS else ReaderKey.NEXT
        KeyEvent.KEYCODE_MOVE_HOME -> ReaderKey.FIRST
        KeyEvent.KEYCODE_MOVE_END -> ReaderKey.LAST
        KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_MENU -> ReaderKey.TOGGLE_MENU
        KeyEvent.KEYCODE_G -> ReaderKey.PAGE_GRID
        KeyEvent.KEYCODE_W -> ReaderKey.TOGGLE_CONTINUOUS
        KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> ReaderKey.PLAY_PAUSE
        KeyEvent.KEYCODE_ESCAPE -> ReaderKey.EXIT
        else -> null
    }
}
