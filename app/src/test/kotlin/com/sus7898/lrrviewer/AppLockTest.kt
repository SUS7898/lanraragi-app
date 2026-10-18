package com.sus7898.lrrviewer

import com.sus7898.lrrviewer.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {

    @Test
    fun `grace period decides whether returning locks`() {
        assertTrue(AppLock.shouldLock(enabled = true, backgroundedAt = 1_000, now = 1_000, graceSec = 0))
        assertFalse(AppLock.shouldLock(enabled = true, backgroundedAt = 1_000, now = 30_000, graceSec = 60))
        assertTrue(AppLock.shouldLock(enabled = true, backgroundedAt = 1_000, now = 61_000, graceSec = 60))
        assertFalse(AppLock.shouldLock(enabled = false, backgroundedAt = 1_000, now = 999_000, graceSec = 0))
        assertFalse(AppLock.shouldLock(enabled = true, backgroundedAt = 0, now = 999_000, graceSec = 0))
    }

    @Test
    fun `cold start is locked when enabled, unlock and background cycle relocks`() {
        val settings = MutableStateFlow(AppSettings(appLock = true, appLockGraceSec = 0))
        val lock = AppLock(settings)
        assertTrue(lock.locked.value)
        lock.unlock()
        assertFalse(lock.locked.value)
        lock.onBackground(now = 10_000)
        lock.onForeground(now = 10_500)
        assertTrue(lock.locked.value)
    }

    @Test
    fun `turning the lock off unlocks on the next foreground`() {
        val settings = MutableStateFlow(AppSettings(appLock = true))
        val lock = AppLock(settings)
        settings.value = settings.value.copy(appLock = false)
        lock.onForeground(now = 5_000)
        assertFalse(lock.locked.value)
    }
}
