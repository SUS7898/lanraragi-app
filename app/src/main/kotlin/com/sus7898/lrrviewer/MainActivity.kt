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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        ReaderKeyEvents.onKey(keyCode, isDown = true) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        ReaderKeyEvents.onKey(keyCode, isDown = false) || super.onKeyUp(keyCode, event)
}
