package com.sus7898.lrrviewer.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.abs

/**
 * Continuous vertical scrolling (webtoon) reader.
 *
 * Known limitation (see docs/BACKLOG.md P0-4): very tall strip images are decoded at full size;
 * tiling with BitmapRegionDecoder is the planned fix.
 */
@Composable
fun WebtoonReader(
    pages: List<String>,
    initialPage: Int,
    jumpEvents: SharedFlow<Int>,
    onPageChanged: (Int) -> Unit,
    onTap: (TapZone) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { onPageChanged(it) }
    }
    LaunchedEffect(jumpEvents) {
        jumpEvents.collect { p -> if (p in pages.indices) listState.scrollToItem(p) }
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
            itemsIndexed(pages, key = { index, _ -> index }) { _, url -> WebtoonPage(url) }
        }
    }
}

@Composable
private fun WebtoonPage(url: String) {
    val context = LocalContext.current
    var retry by remember(url) { mutableIntStateOf(0) }
    val request = remember(url, retry) {
        ImageRequest.Builder(context).data(url).memoryCacheKeyExtra("retry", retry.toString()).build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth(),
        loading = {
            Box(Modifier.fillMaxWidth().aspectRatio(0.7f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        },
        error = {
            Column(
                Modifier.fillMaxWidth().aspectRatio(0.7f).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { retry++ }) { Text("다시 시도") }
            }
        },
    )
}
