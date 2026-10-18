package com.sus7898.lrrviewer

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sus7898.lrrviewer.ui.AppRoot
import com.sus7898.lrrviewer.ui.theme.LrrTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as App).graph
        setContent {
            val settings by graph.settingsState.collectAsStateWithLifecycle()
            LrrTheme(themeMode = settings.themeMode) {
                Box(Modifier.fillMaxSize()) {
                    AppRoot(graph = graph, settings = settings)
                }
            }
        }
    }

    // Sync with the linked file when the app comes to the foreground (pull the other device's changes) and
    // when it leaves it (push ours). Starts are throttled so rotating or a quick app switch does not re-sync.
    override fun onStart() {
        super.onStart()
        val graph = (application as App).graph
        if (System.currentTimeMillis() - graph.settingsState.value.lastSyncAt > 30_000) {
            graph.scope.launch { graph.sync.syncIfLinked() }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            val graph = (application as App).graph
            graph.scope.launch { graph.sync.syncIfLinked() }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        ReaderKeyEvents.onKey(keyCode, isDown = true) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        ReaderKeyEvents.onKey(keyCode, isDown = false) || super.onKeyUp(keyCode, event)
}
