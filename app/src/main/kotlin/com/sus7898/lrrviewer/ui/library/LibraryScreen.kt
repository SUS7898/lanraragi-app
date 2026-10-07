package com.sus7898.lrrviewer.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sus7898.lrrviewer.AppGraph
import com.sus7898.lrrviewer.data.api.SearchQuery
import com.sus7898.lrrviewer.ui.common.EmptyView
import com.sus7898.lrrviewer.ui.common.ErrorView
import com.sus7898.lrrviewer.ui.common.LoadingView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(graph: AppGraph, onOpenArchive: (String) -> Unit) {
    val vm: LibraryViewModel = viewModel { LibraryViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by graph.settingsState.collectAsStateWithLifecycle()
    val pendingSearch by graph.pendingLibrarySearch.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val gridState = rememberLazyGridState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pendingSearch) {
        pendingSearch?.let { tag ->
            vm.searchTag(tag)
            graph.pendingLibrarySearch.value = null
        }
    }

    // Infinite scrolling: request the next server page when close to the end.
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (last, total) -> if (total > 0 && last >= total - 8) vm.loadMore() }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            if (state.items.isNotEmpty()) {
                snackbar.showSnackbar(it)
                vm.consumeError()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = state.searchText,
                onValueChange = vm::onSearchTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = { Text("검색 (예: artist:foo, \"정확한 문구\", -제외)") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchText.isNotEmpty()) {
                        IconButton(onClick = { vm.clearSearch() }) { Icon(Icons.Filled.Close, contentDescription = "지우기") }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.submitSearch(); focusManager.clearFocus() }),
            )

            FilterRow(state = state, onQuery = vm::setQuery)

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.weight(1f),
            ) {
                when {
                    state.loading && state.items.isEmpty() -> LoadingView("서재를 불러오는 중…")
                    state.items.isEmpty() && state.error != null -> ErrorView(state.error!!, onRetry = { vm.refresh() })
                    state.items.isEmpty() -> EmptyView("결과가 없습니다")
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = settings.gridMinColumnDp.dp),
                        state = gridState,
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.items, key = { it.arcid }) { archive ->
                            ArchiveCard(
                                archive = archive,
                                thumbnailUrl = graph.api.thumbnailUrl(archive.arcid),
                                onClick = { onOpenArchive(archive.arcid) },
                            )
                        }
                        if (state.loadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                vm.random { archive ->
                    if (archive != null) onOpenArchive(archive.arcid)
                    else scope.launch { snackbar.showSnackbar("무작위 선택에 실패했습니다") }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) { Icon(Icons.Filled.Shuffle, contentDescription = "무작위 열기") }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun FilterRow(state: LibraryViewModel.UiState, onQuery: (SearchQuery) -> Unit) {
    val q = state.query
    var categoryMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val selectedCategory = state.categories.firstOrNull { it.id == q.category }
    val sortLabel = SearchQuery.SORT_OPTIONS.firstOrNull { it.first == q.sortBy }?.second ?: q.sortBy

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AssistChip(
                onClick = { categoryMenu = true },
                label = { Text(selectedCategory?.name ?: "전체 카테고리", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            )
            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                DropdownMenuItem(text = { Text("전체") }, onClick = { onQuery(q.copy(category = "")); categoryMenu = false })
                state.categories.forEach { c ->
                    DropdownMenuItem(
                        text = { Text((if (c.pinned) "📌 " else "") + c.name) },
                        onClick = { onQuery(q.copy(category = c.id)); categoryMenu = false },
                    )
                }
            }
        }

        Box {
            AssistChip(
                onClick = { sortMenu = true },
                label = { Text(sortLabel + if (q.order == "desc") " ↓" else " ↑") },
                leadingIcon = { Icon(Icons.Filled.Sort, contentDescription = null, Modifier.size(18.dp)) },
            )
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                SearchQuery.SORT_OPTIONS.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(if (key == q.sortBy) "✓ $label" else label) },
                        onClick = { onQuery(q.copy(sortBy = key)); sortMenu = false },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (q.order == "desc") "오름차순으로" else "내림차순으로") },
                    onClick = { onQuery(q.copy(order = if (q.order == "desc") "asc" else "desc")); sortMenu = false },
                )
            }
        }

        FilterChip(selected = q.newOnly, onClick = { onQuery(q.copy(newOnly = !q.newOnly)) }, label = { Text("신규만") })
        FilterChip(selected = q.untaggedOnly, onClick = { onQuery(q.copy(untaggedOnly = !q.untaggedOnly)) }, label = { Text("미태그") })
        FilterChip(selected = q.hideCompleted, onClick = { onQuery(q.copy(hideCompleted = !q.hideCompleted)) }, label = { Text("완독 숨김") })

        if (state.filtered > 0) {
            Text("${state.filtered}개", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
