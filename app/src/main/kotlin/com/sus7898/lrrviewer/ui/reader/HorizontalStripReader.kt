package com.sus7898.lrrviewer.ui.reader

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.sus7898.lrrviewer.data.PageSize
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Horizontal continuous ("가로 연속") reader: pages sit side by side at full screen height and the user drags
 * through them freely instead of flipping (RTL puts the first page on the right). Page widths come from the
 * page-size cache ([onNeedSize]) so the strip does not jump when images arrive. No zoom here — paged modes zoom.
 */
@Composable
fun HorizontalStripReader(
    pages: List<String>,
    pageSizes: Map<Int, PageSize>,
    initialPage: Int,
    rightToLeft: Boolean,
    gapDp: Int,
    jumpEvents: SharedFlow<Int>,
    scrollEvents: SharedFlow<Float>,
    onNeedSize: (Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onTap: (TapZone) -> Unit,
    onLongPress: (Int) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
    var size by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    /** Page bounds in root coordinates, for mapping a long press to a page regardless of layout direction. */
    val bounds = remember { mutableStateMapOf<Int, Rect>() }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    LaunchedEffect(listState) {
        snapshotFlow { if (!listState.canScrollForward && pages.isNotEmpty()) pages.lastIndex else listState.firstVisibleItemIndex }
            .collect { onPageChanged(it) }
    }
    LaunchedEffect(jumpEvents) {
        jumpEvents.collect { p -> if (p in pages.indices) listState.scrollToItem(p) }
    }
    // Taps and page keys move by most of a screen (scroll deltas follow item order, also with reverseLayout).
    LaunchedEffect(scrollEvents) {
        scrollEvents.collect { fraction ->
            val viewport = listState.layoutInfo.viewportSize.width
            if (viewport > 0) listState.animateScrollBy(viewport * fraction)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .collect { visible ->
                val last = visible.maxOrNull() ?: return@collect
                (visible + listOf(last + 1, last + 2)).filter { it in pages.indices && it !in pageSizes }.forEach(onNeedSize)
            }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset -> currentOnTap(zoneOf(offset, size, vertical = false)) },
                    onLongPress = { offset ->
                        val p = origin + offset
                        bounds.entries.firstOrNull { (_, r) -> r.contains(p) }?.let { currentOnLongPress(it.key) }
                    },
                )
            },
    ) {
        LazyRow(
            state = listState,
            reverseLayout = rightToLeft,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(gapDp.dp),
        ) {
            itemsIndexed(pages, key = { index, _ -> index }) { index, url ->
                val aspect = pageSizes[index]?.takeIf { it.width > 0 && it.height > 0 }?.aspect ?: PENDING_ASPECT
                Box(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(aspect, matchHeightConstraintsFirst = true)
                        .onGloballyPositioned { bounds[index] = it.boundsInRoot() },
                ) {
                    StripPage(url)
                }
            }
        }
    }
}

@Composable
private fun StripPage(url: String) {
    val context = LocalContext.current
    var retry by remember(url) { mutableIntStateOf(0) }
    val request = remember(url, retry) {
        ImageRequest.Builder(context).data(url).memoryCacheKeyExtra("retry", retry.toString()).build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.FillHeight,
        modifier = Modifier.fillMaxSize(),
        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) } },
        error = {
            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { retry++ }) { Text("다시 시도") }
            }
        },
    )
}
