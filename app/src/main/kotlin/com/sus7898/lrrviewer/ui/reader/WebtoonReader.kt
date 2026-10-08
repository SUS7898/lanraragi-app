package com.sus7898.lrrviewer.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.sus7898.lrrviewer.data.PageImageStore
import com.sus7898.lrrviewer.data.PageSize
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/**
 * Continuous vertical scrolling (webtoon) reader.
 *
 * Rows come from [buildWebtoonItems]: pages whose size is unknown show a placeholder while the size is read
 * ([onNeedSize]); normal pages are laid out with their real aspect ratio (so Coil decodes to the composable
 * size and the scroll position does not jump); pages taller than [MAX_TILE_HEIGHT_PX] are split into bands
 * decoded with BitmapRegionDecoder, so an 800×20000 strip never becomes one bitmap.
 */
@Composable
fun WebtoonReader(
    pages: List<String>,
    pageSizes: Map<Int, PageSize>,
    store: PageImageStore,
    initialPage: Int,
    jumpEvents: SharedFlow<Int>,
    scrollEvents: SharedFlow<Float>,
    onNeedSize: (Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onTap: (TapZone) -> Unit,
) {
    val items = remember(pages.size, pageSizes) { buildWebtoonItems(pages.size, pageSizes) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = items.firstIndexOfPage(initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) ?: 0,
    )

    LaunchedEffect(listState, items) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index -> items.getOrNull(index)?.let { onPageChanged(it.page) } }
    }
    LaunchedEffect(jumpEvents, items) {
        jumpEvents.collect { p -> items.firstIndexOfPage(p)?.let { listState.scrollToItem(it) } }
    }
    // Taps on the top/bottom zones and page keys scroll by a fraction of the viewport, not by whole (tall) items.
    LaunchedEffect(scrollEvents) {
        scrollEvents.collect { fraction ->
            val viewport = listState.layoutInfo.viewportSize.height
            if (viewport > 0) listState.animateScrollBy(viewport * fraction)
        }
    }
    // Read sizes for the rows on screen and the next two, so tall pages are tiled before they are reached.
    LaunchedEffect(listState, items) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .collect { visible ->
                val last = visible.lastOrNull() ?: return@collect
                (visible + listOf(last + 1, last + 2))
                    .mapNotNull { items.getOrNull(it) }
                    .filterIsInstance<WebtoonItem.Pending>()
                    .forEach { onNeedSize(it.page) }
            }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transformState = rememberTransformableState { zoomChange, pan, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 3f)
        val maxX = size.width * (scale - 1f) / 2f
        offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset -> onTap(zoneOf(offset, size, vertical = true)) },
                    onDoubleTap = { scale = 1f; offsetX = 0f },
                )
            }
            .transformable(transformState, canPan = { delta -> scale > 1f && abs(delta.x) > abs(delta.y) })
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
            },
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.key }) { item ->
                when (item) {
                    is WebtoonItem.Pending -> PendingPage()
                    is WebtoonItem.Whole -> WholePage(url = pages[item.page], aspect = item.aspect)
                    is WebtoonItem.Tile -> TileBand(store = store, url = pages[item.page], tile = item)
                }
            }
        }
    }
}

@Composable
private fun PendingPage() {
    Box(Modifier.fillMaxWidth().aspectRatio(PENDING_ASPECT), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}

/** A page that fits in one bitmap: Coil decodes it at the size of this composable (width × width/aspect). */
@Composable
private fun WholePage(url: String, aspect: Float) {
    val context = LocalContext.current
    var retry by remember(url) { mutableIntStateOf(0) }
    val request = remember(url, retry) {
        ImageRequest.Builder(context).data(url).memoryCacheKeyExtra("retry", retry.toString()).build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth().aspectRatio(aspect),
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        },
        error = { LoadError(onRetry = { retry++ }) },
    )
}

private sealed interface TileState {
    data object Loading : TileState
    data class Ready(val bitmap: Bitmap) : TileState
    data object Failed : TileState
}

/** One horizontal band of a tall page, decoded from the cached file with BitmapRegionDecoder. */
@Composable
private fun TileBand(store: PageImageStore, url: String, tile: WebtoonItem.Tile) {
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(tile.aspect)) {
        val targetWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val sample = sampleSizeFor(tile.width, targetWidthPx)
        var retry by remember(url, tile.index) { mutableIntStateOf(0) }
        var state by remember(url, tile.index, sample, retry) { mutableStateOf<TileState>(TileState.Loading) }
        LaunchedEffect(url, tile.index, sample, retry) {
            val bitmap = runCatching { store.decodeRegion(url, tile.top, tile.width, tile.height, sample) }.getOrNull()
            state = if (bitmap != null) TileState.Ready(bitmap) else TileState.Failed
        }
        when (val s = state) {
            is TileState.Ready -> Image(
                bitmap = s.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize(),
            )
            TileState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (tile.index == 0) CircularProgressIndicator(color = Color.White)
            }
            TileState.Failed -> LoadError(onRetry = { retry++ })
        }
    }
}

@Composable
private fun LoadError(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) { Text("다시 시도") }
    }
}
