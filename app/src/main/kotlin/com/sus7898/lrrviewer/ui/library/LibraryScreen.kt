package com.sus7898.lrrviewer.ui.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
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
import com.sus7898.lrrviewer.data.api.Category
import com.sus7898.lrrviewer.data.api.SearchQuery
import com.sus7898.lrrviewer.data.sortCategories
import com.sus7898.lrrviewer.ui.common.EmptyView
import com.sus7898.lrrviewer.ui.common.ErrorView
import com.sus7898.lrrviewer.ui.common.LoadingView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(graph: AppGraph, onOpenArchive: (String) -> Unit) {
    val vm: LibraryViewModel = viewModel { LibraryViewModel(graph.api, graph.settingsState, graph.favorites) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by graph.settingsState.collectAsStateWithLifecycle()
    val manualOrder by graph.categoryOrder.order.collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingSearch by graph.pendingLibrarySearch.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val gridState = rememberLazyGridState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val sortedCategories = remember(state.categories, settings.categorySort, manualOrder) {
        sortCategories(state.categories, settings.categorySort, manualOrder)
    }

    // Back = clear search/filters first; only an unfiltered library lets Back leave the app.
    BackHandler(enabled = state.hasActiveFilters) {
        focusManager.clearFocus()
        vm.clearAll()
    }

    // Coming back from the detail screen: pick up hearts toggled there.
    LaunchedEffect(Unit) { vm.refreshFavorites() }

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

            FilterRow(
                state = state,
                categories = sortedCategories,
                onQuery = vm::setQuery,
                onToggleFavorites = vm::toggleFavoritesOnly,
                onMinRating = vm::setMinRating,
            )

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.weight(1f),
            ) {
                when {
                    state.loading && state.items.isEmpty() -> LoadingView(message = "서재를 불러오는 중…")
                    state.items.isEmpty() && state.error != null -> ErrorView(state.error!!, onRetry = { vm.refresh() })
                    state.items.isEmpty() -> EmptyView(if (state.hasActiveFilters) "조건에 맞는 항목이 없습니다 (뒤로 가기: 필터 초기화)" else "결과가 없습니다")
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
                                favorite = archive.arcid in state.favoriteIds,
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
private fun FilterRow(
    state: LibraryViewModel.UiState,
    categories: List<Category>,
    onQuery: (SearchQuery) -> Unit,
    onToggleFavorites: () -> Unit,
    onMinRating: (Int) -> Unit,
) {
    val q = state.query
    var categoryMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var ratingMenu by remember { mutableStateOf(false) }
    var customSortDialog by remember { mutableStateOf(false) }
    val selectedCategory = categories.firstOrNull { it.id == q.category }
    val sortLabel = SearchQuery.sortLabel(q.sortBy)

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
                label = {
                    Text(
                        if (state.favoritesOnly) "즐겨찾기" else selectedCategory?.name ?: "전체 카테고리",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            )
            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                DropdownMenuItem(text = { Text("전체") }, onClick = { onQuery(q.copy(category = "")); categoryMenu = false })
                categories.forEach { c ->
                    DropdownMenuItem(
                        text = { Text((if (c.pinned) "📌 " else "") + c.name + (if (c.id == q.category) " ✓" else "")) },
                        onClick = { onQuery(q.copy(category = c.id)); categoryMenu = false },
                    )
                }
            }
        }

        Box {
            AssistChip(
                onClick = { sortMenu = true },
                label = { Text(sortLabel + if (q.order == "desc") " ↓" else " ↑") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, Modifier.size(18.dp)) },
            )
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                SearchQuery.SORT_OPTIONS.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(if (key == q.sortBy) "✓ $label" else label) },
                        onClick = { onQuery(q.copy(sortBy = key)); sortMenu = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text("다른 네임스페이스로 정렬…") },
                    onClick = { sortMenu = false; customSortDialog = true },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (q.order == "desc") "오름차순으로" else "내림차순으로") },
                    onClick = { onQuery(q.copy(order = if (q.order == "desc") "asc" else "desc")); sortMenu = false },
                )
            }
        }

        FilterChip(
            selected = state.favoritesOnly,
            enabled = state.bookmarkCategoryId != null,
            onClick = onToggleFavorites,
            leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null, Modifier.size(18.dp)) },
            label = { Text("즐겨찾기") },
        )

        Box {
            FilterChip(
                selected = q.hasRatingFilter,
                onClick = { ratingMenu = true },
                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, Modifier.size(18.dp)) },
                label = { Text(if (q.hasRatingFilter) "${q.minRating}점 이상" else "평점") },
            )
            DropdownMenu(expanded = ratingMenu, onDismissRequest = { ratingMenu = false }) {
                DropdownMenuItem(text = { Text("평점 필터 끄기") }, onClick = { onMinRating(0); ratingMenu = false })
                (5 downTo 1).forEach { n ->
                    DropdownMenuItem(
                        text = { Text("★".repeat(n) + "  ${n}점 이상" + if (q.minRating == n) " ✓" else "") },
                        onClick = { onMinRating(n); ratingMenu = false },
                    )
                }
            }
        }

        FilterChip(selected = q.newOnly, onClick = { onQuery(q.copy(newOnly = !q.newOnly)) }, label = { Text("신규만") })
        FilterChip(selected = q.untaggedOnly, onClick = { onQuery(q.copy(untaggedOnly = !q.untaggedOnly)) }, label = { Text("미태그") })
        FilterChip(selected = q.hideCompleted, onClick = { onQuery(q.copy(hideCompleted = !q.hideCompleted)) }, label = { Text("완독 숨김") })

        if (state.filtered > 0 || state.skipped > 0) {
            Text(
                "${state.filtered}개" + if (state.skipped > 0) " · ${state.skipped}개는 서버 응답 오류로 제외" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (customSortDialog) {
        var text by remember { mutableStateOf(if (SearchQuery.SORT_OPTIONS.none { it.first == q.sortBy }) q.sortBy else "") }
        AlertDialog(
            onDismissRequest = { customSortDialog = false },
            title = { Text("네임스페이스로 정렬") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("태그 네임스페이스 이름을 입력하면 그 값으로 정렬합니다 (예: character, language, parody).", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("네임스페이스") })
                }
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = { onQuery(q.copy(sortBy = text.trim().lowercase())); customSortDialog = false },
                ) { Text("정렬") }
            },
            dismissButton = { TextButton(onClick = { customSortDialog = false }) { Text("취소") } },
        )
    }
}
