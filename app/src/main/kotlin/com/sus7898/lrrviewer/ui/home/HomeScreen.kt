package com.sus7898.lrrviewer.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sus7898.lrrviewer.AppGraph
import com.sus7898.lrrviewer.ui.history.HistoryScreen
import com.sus7898.lrrviewer.ui.library.LibraryScreen
import com.sus7898.lrrviewer.ui.settings.SettingsScreen

enum class HomeTab(val label: String, val icon: ImageVector) {
    LIBRARY("서재", Icons.Filled.GridView),
    HISTORY("기록", Icons.Filled.History),
    SETTINGS("설정", Icons.Filled.Settings),
}

@Composable
fun HomeScreen(
    graph: AppGraph,
    onOpenArchive: (String) -> Unit,
    onOpenReader: (String, Int) -> Unit,
    onOpenCategoryOrder: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.LIBRARY) }
    val pendingSearch by graph.pendingLibrarySearch.collectAsStateWithLifecycle()
    LaunchedEffect(pendingSearch) { if (pendingSearch != null) tab = HomeTab.LIBRARY }

    // Back from 기록/설정 returns to the library; the library itself clears its filters before the app exits.
    BackHandler(enabled = tab != HomeTab.LIBRARY) { tab = HomeTab.LIBRARY }

    Scaffold(
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                HomeTab.LIBRARY -> LibraryScreen(graph = graph, onOpenArchive = onOpenArchive)
                HomeTab.HISTORY -> HistoryScreen(graph = graph, onOpenArchive = onOpenArchive, onOpenReader = onOpenReader)
                HomeTab.SETTINGS -> SettingsScreen(graph = graph, onOpenCategoryOrder = onOpenCategoryOrder)
            }
        }
    }
}
