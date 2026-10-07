package com.sus7898.lrrviewer.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sus7898.lrrviewer.AppGraph
import com.sus7898.lrrviewer.data.CategorySort
import com.sus7898.lrrviewer.data.api.Category
import com.sus7898.lrrviewer.data.api.userMessage
import com.sus7898.lrrviewer.data.sortCategories
import com.sus7898.lrrviewer.ui.common.EmptyView
import com.sus7898.lrrviewer.ui.common.ErrorView
import com.sus7898.lrrviewer.ui.common.LoadingView
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Drag-to-reorder list of the server's categories. Every move is persisted locally (Room `category_order`)
 * and switches the category sort mode to MANUAL; "이름순으로" resets the list to name order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryOrderScreen(graph: AppGraph, onBack: () -> Unit) {
    val settings by graph.settingsState.collectAsStateWithLifecycle()
    val manualOrder by graph.categoryOrder.order.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf<List<Category>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val list = remember { mutableStateOf<List<Category>>(emptyList()) }
    var initialised by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { graph.api.categories() }
            .onSuccess { loaded = it }
            .onFailure { error = it.userMessage() }
    }
    LaunchedEffect(loaded, manualOrder) {
        val cats = loaded ?: return@LaunchedEffect
        if (!initialised) {
            list.value = sortCategories(cats, settings.categorySort, manualOrder)
            initialised = true
        }
    }

    fun persist(newList: List<Category>) {
        list.value = newList
        scope.launch {
            graph.categoryOrder.save(newList.map { it.id })
            if (graph.settingsState.value.categorySort != CategorySort.MANUAL) {
                graph.settings.edit { it.copy(categorySort = CategorySort.MANUAL) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("카테고리 순서") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
                actions = {
                    TextButton(onClick = { persist(sortCategories(list.value, CategorySort.NAME)) }, enabled = list.value.isNotEmpty()) {
                        Text("이름순으로")
                    }
                },
            )
        },
    ) { padding ->
        when {
            error != null -> ErrorView(error!!, modifier = Modifier.padding(padding), onRetry = {
                error = null
                scope.launch { runCatching { graph.api.categories() }.onSuccess { loaded = it }.onFailure { error = it.userMessage() } }
            })
            !initialised -> LoadingView(modifier = Modifier.padding(padding))
            list.value.isEmpty() -> EmptyView("서버에 카테고리가 없습니다", modifier = Modifier.padding(padding))
            else -> {
                val lazyListState = rememberLazyListState()
                val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    val current = list.value.toMutableList()
                    current.add(to.index, current.removeAt(from.index))
                    persist(current)
                }
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    items(list.value, key = { it.id }) { category ->
                        ReorderableItem(reorderState, key = category.id) { isDragging ->
                            ListItem(
                                headlineContent = { Text(category.name) },
                                supportingContent = {
                                    val notes = buildList {
                                        if (category.pinned) add("📌 서버 고정")
                                        if (category.isDynamic) add("동적 카테고리") else add("${category.archives.size}개")
                                    }
                                    Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                },
                                trailingContent = {
                                    IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                        Icon(Icons.Filled.DragHandle, contentDescription = "끌어서 순서 변경")
                                    }
                                },
                                colors = if (isDragging) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ListItemDefaults.colors(),
                                tonalElevation = if (isDragging) 4.dp else 0.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}
