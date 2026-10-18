package com.sus7898.lrrviewer

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sus7898.lrrviewer.ui.AppRoot
import com.sus7898.lrrviewer.ui.lock.LockScreen
import com.sus7898.lrrviewer.ui.theme.LrrTheme
import kotlinx.coroutines.launch

/** FragmentActivity (a ComponentActivity) because BiometricPrompt needs one for the app lock. */
class MainActivity : FragmentActivity() {

    private val graph: AppGraph get() = (application as App).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by graph.settingsState.collectAsStateWithLifecycle()
            val locked by graph.appLock.locked.collectAsStateWithLifecycle()
            // FLAG_SECURE: blank recent-apps thumbnail and no screenshots, while the setting is on.
            LaunchedEffect(settings.hideInRecents) {
                if (settings.hideInRecents) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            LrrTheme(themeMode = settings.themeMode) {
                Box(Modifier.fillMaxSize()) {
                    AppRoot(graph = graph, settings = settings)
                    if (locked && settings.appLock) LockScreen(onUnlocked = graph.appLock::unlock)
                }
            }
        }
    }

    // Sync with the linked file when the app comes to the foreground (pull the other device's changes) and
    // when it leaves it (push ours). Starts are throttled so rotating or a quick app switch does not re-sync.
    override fun onStart() {
        super.onStart()
        val now = System.currentTimeMillis()
        graph.appLock.onForeground(now)
        if (now - graph.settingsState.value.lastSyncAt > 30_000) {
            graph.scope.launch { graph.sync.syncIfLinked() }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            graph.appLock.onBackground(System.currentTimeMillis())
            graph.scope.launch { graph.sync.syncIfLinked() }
        }
    }

    private val keysBlocked: Boolean get() = graph.appLock.locked.value && graph.settingsState.value.appLock

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        (!keysBlocked && ReaderKeyEvents.onKey(keyCode, isDown = true)) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        (!keysBlocked && ReaderKeyEvents.onKey(keyCode, isDown = false)) || super.onKeyUp(keyCode, event)
}
