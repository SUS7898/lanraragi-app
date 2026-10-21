package com.sus7898.lrrviewer.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sus7898.lrrviewer.AppGraph
import com.sus7898.lrrviewer.data.PageImageStore
import com.sus7898.lrrviewer.data.ReadingProgressRepository
import com.sus7898.lrrviewer.data.api.LrrApi
import com.sus7898.lrrviewer.data.api.userMessage
import com.sus7898.lrrviewer.ui.common.ErrorView
import com.sus7898.lrrviewer.ui.common.LoadingView
import com.sus7898.lrrviewer.ui.reader.PageThumbnailGrid
import com.sus7898.lrrviewer.ui.reader.PageThumbnailSource
import com.sus7898.lrrviewer.ui.reader.ThumbState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cell sizes the zoom buttons step through (smaller = more pages per screen). */
private val CELL_SIZES = listOf(64, 88, 120, 168, 240)

class PageOverviewViewModel(
    private val api: LrrApi,
    store: PageImageStore,
    private val progress: ReadingProgressRepository,
    val id: String,
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val extracting: Boolean = false,
        val error: String? = null,
        val title: String = "",
        val pages: List<String> = emptyList(),
        val lastReadPage: Int = -1,
        val thumbs: ThumbState = ThumbState.Idle,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private val thumbnails = PageThumbnailSource(api, store, id)

    init {
        viewModelScope.launch { thumbnails.state.collect { t -> _state.update { it.copy(thumbs = t) } } }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val title = runCatching { api.metadata(id) }.getOrNull()?.let { it.title.ifBlank { it.filename } }.orEmpty()
                var files = api.files(id)
                if (files.pages.isEmpty() && files.job >= 0) {
                    // Older servers extract in the background first (same handling as the reader).
                    _state.update { it.copy(extracting = true, title = title) }
                    repeat(90) {
                        if (files.pages.isNotEmpty()) return@repeat
                        delay(1_000)
                        if (it % 3 == 2) files = api.files(id)
                    }
                }
                val pages = api.resolvePages(files.pages)
                val last = runCatching { progress.get(id)?.page }.getOrNull() ?: -1
                _state.update { it.copy(loading = false, extracting = false, title = title, pages = pages, lastReadPage = last) }
                if (pages.isNotEmpty()) thumbnails.prepare(viewModelScope)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, extracting = false, error = e.userMessage()) }
            }
        }
    }

    fun urlFor(index: Int): String = thumbnails.url(index, _state.value.pages)
    fun onThumbError(url: String) = thumbnails.onError(url)
}

/** "전체 페이지": every page of an archive at once as a zoomable thumbnail grid; tap opens the reader there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageOverviewScreen(graph: AppGraph, id: String, onBack: () -> Unit, onOpenReader: (Int) -> Unit) {
    val vm: PageOverviewViewModel = viewModel(key = "pages_$id") { PageOverviewViewModel(graph.api, graph.pageStore, graph.progress, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    var sizeIndex by rememberSaveable { mutableIntStateOf(2) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("전체 페이지", style = MaterialTheme.typography.titleMedium)
                        if (state.pages.isNotEmpty()) Text("${state.title} · ${state.pages.size}p", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
                actions = {
                    IconButton(onClick = { sizeIndex = (sizeIndex - 1).coerceAtLeast(0) }, enabled = sizeIndex > 0) {
                        Icon(Icons.Filled.ZoomOut, contentDescription = "더 많이 보기")
                    }
                    IconButton(onClick = { sizeIndex = (sizeIndex + 1).coerceAtMost(CELL_SIZES.lastIndex) }, enabled = sizeIndex < CELL_SIZES.lastIndex) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = "크게 보기")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingView(message = if (state.extracting) "서버에서 아카이브 압축을 푸는 중…" else "페이지 목록을 불러오는 중…")
                state.error != null -> ErrorView(state.error!!, onRetry = vm::load)
                state.thumbs == ThumbState.Idle || state.thumbs == ThumbState.Generating ->
                    LoadingView(message = "서버에서 페이지 썸네일을 만드는 중…")
                else -> PageThumbnailGrid(
                    pageCount = state.pages.size,
                    currentPage = state.lastReadPage,
                    urlFor = vm::urlFor,
                    onThumbError = vm::onThumbError,
                    onTap = onOpenReader,
                    minCellSize = CELL_SIZES[sizeIndex].dp,
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
