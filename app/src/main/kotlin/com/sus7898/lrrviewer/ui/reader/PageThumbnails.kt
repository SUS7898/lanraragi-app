package com.sus7898.lrrviewer.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import com.sus7898.lrrviewer.data.PageImageStore
import com.sus7898.lrrviewer.data.api.LrrApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Page-grid thumbnail source. */
sealed interface ThumbState {
    data object Idle : ThumbState
    data object Generating : ThumbState
    /** Server page thumbnails exist: use `thumbnail?page=N`. */
    data object Ready : ThumbState
    /** No server page thumbnails: use the page images, downsampled. */
    data object Fallback : ThumbState
}

/**
 * Page thumbnails of one archive (P1-5), shared by the reader's page sheet and the full-page overview screen.
 * Asks the server to generate every page thumbnail and waits for the minion job; servers without page thumbnails
 * (404) or a failed/slow job fall back to the page images themselves, downsampled by Coil.
 */
class PageThumbnailSource(
    private val api: LrrApi,
    private val store: PageImageStore,
    private val arcId: String,
) {
    private val _state = MutableStateFlow<ThumbState>(ThumbState.Idle)
    val state: StateFlow<ThumbState> = _state.asStateFlow()

    fun prepare(scope: CoroutineScope) {
        if (_state.value != ThumbState.Idle) return
        _state.value = ThumbState.Generating
        scope.launch {
            val ok = runCatching {
                val job = api.queuePageThumbnails(arcId) ?: return@runCatching true
                repeat(60) {
                    delay(1_000)
                    when (runCatching { api.minionJob(job).state }.getOrDefault("")) {
                        "finished" -> return@runCatching true
                        "failed" -> return@runCatching false
                    }
                }
                false
            }.getOrDefault(false)
            _state.value = if (ok) ThumbState.Ready else ThumbState.Fallback
        }
    }

    fun url(index: Int, pages: List<String>): String = when (_state.value) {
        ThumbState.Ready -> api.pageThumbnailUrl(arcId, index + 1)
        else -> pages.getOrElse(index) { "" }
    }

    /** A thumbnail URL that failed to decode (e.g. a 202 JSON body got cached): forget it so a retry refetches. */
    fun onError(url: String) = store.evict(url)
}

/** Grid of page thumbnails with page numbers; the [currentPage] cell gets a border. */
@Composable
fun PageThumbnailGrid(
    pageCount: Int,
    currentPage: Int,
    urlFor: (Int) -> String,
    onThumbError: (String) -> Unit,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minCellSize: Dp = 96.dp,
    state: LazyGridState = rememberLazyGridState(initialFirstVisibleItemIndex = (currentPage - 3).coerceAtLeast(0)),
    contentPadding: PaddingValues = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
) {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCellSize),
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(pageCount, key = { it }) { i ->
            val url = urlFor(i)
            val request = remember(url) {
                ImageRequest.Builder(context)
                    .data(url)
                    .size(400)
                    .listener(object : ImageRequest.Listener {
                        override fun onError(request: ImageRequest, result: ErrorResult) = onThumbError(url)
                    })
                    .build()
            }
            Column(
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .then(if (i == currentPage) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
                    .clickable { onTap(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = "${i + 1}페이지",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).background(Color.DarkGray),
                )
                Text("${i + 1}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(2.dp))
            }
        }
    }
}
