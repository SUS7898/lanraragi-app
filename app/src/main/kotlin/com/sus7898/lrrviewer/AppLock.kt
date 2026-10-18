package com.sus7898.lrrviewer

import com.sus7898.lrrviewer.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App lock state. Locked on a cold start when the lock is on, and again when the app comes back after
 * staying in the background longer than the grace period. Unlocking is done by the lock screen after a
 * successful BiometricPrompt (fingerprint / face / screen-lock PIN).
 */
class AppLock(private val settings: StateFlow<AppSettings>) {
    private val _locked = MutableStateFlow(settings.value.appLock)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt = 0L

    fun onBackground(now: Long) {
        if (!_locked.value) backgroundedAt = now
    }

    fun onForeground(now: Long) {
        val s = settings.value
        if (!s.appLock) {
            _locked.value = false
            return
        }
        if (shouldLock(s.appLock, backgroundedAt, now, s.appLockGraceSec)) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
        backgroundedAt = 0L
    }

    companion object {
        /** True when the app was backgrounded at [backgroundedAt] (0 = never) and [graceSec] has passed. */
        fun shouldLock(enabled: Boolean, backgroundedAt: Long, now: Long, graceSec: Int): Boolean =
            enabled && backgroundedAt > 0 && now - backgroundedAt >= graceSec * 1000L
    }
}
