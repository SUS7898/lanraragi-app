package com.sus7898.lrrviewer.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.sus7898.lrrviewer.data.FitMode
import com.sus7898.lrrviewer.data.PageSize
import com.sus7898.lrrviewer.data.ReadingMode
import kotlinx.coroutines.flow.SharedFlow
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/**
 * Page-flip reader for LTR / RTL / vertical modes. One zoomable image per page; in landscape (with the
 * setting on) two portrait pages share a slot as a spread, RTL putting the earlier page on the right.
 */
@Composable
fun PagedReader(
    pages: List<String>,
    pageSizes: Map<Int, PageSize>,
    initialPage: Int,
    mode: ReadingMode,
    fitMode: FitMode,
    twoPageLandscape: Boolean,
    coverAlone: Boolean,
    jumpEvents: SharedFlow<Int>,
    onNeedSize: (Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onTap: (TapZone) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPage = twoPageLandscape && mode != ReadingMode.VERTICAL && maxWidth > maxHeight
        val spreads = remember(pages.size, pageSizes, twoPage, coverAlone) { buildSpreads(pages.size, pageSizes, twoPage, coverAlone) }
        var currentPage by remember { mutableIntStateOf(initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) }
        val pagerState = rememberPagerState(initialPage = spreads.indexOfPage(currentPage)) { spreads.size }

        // Spreads are rebuilt when sizes arrive or the orientation changes: stay on the same *page*.
        LaunchedEffect(spreads) {
            val target = spreads.indexOfPage(currentPage)
            if (pagerState.currentPage != target) pagerState.scrollToPage(target)
            snapshotFlow { pagerState.currentPage }.collect { index ->
                val spread = spreads.getOrNull(index) ?: return@collect
                if (currentPage !in spread.pages) currentPage = spread.first
                onPageChanged(currentPage)
                if (twoPage) {
                    // Know the shape of the pages ahead so a scanned double page is shown alone before it is reached.
                    for (p in spread.first - 2..spread.pages.last() + 4) if (p in pages.indices) onNeedSize(p)
                }
            }
        }
        LaunchedEffect(jumpEvents, spreads) {
            jumpEvents.collect { p ->
                if (p in pages.indices) {
                    currentPage = p
                    pagerState.scrollToPage(spreads.indexOfPage(p))
                }
            }
        }

        val contentScale = when (fitMode) {
            FitMode.FIT -> ContentScale.Fit
            FitMode.FILL_WIDTH -> ContentScale.FillWidth
            FitMode.FILL_HEIGHT -> ContentScale.FillHeight
        }
        val vertical = mode == ReadingMode.VERTICAL
        val slotContent: @Composable (Int) -> Unit = { index ->
            val spread = spreads[index]
            if (!spread.isDouble) {
                ReaderPage(
                    url = pages[spread.first],
                    contentScale = contentScale,
                    alignment = Alignment.Center,
                    onTap = { offset, size, bounds -> onTap(zoneOf(offset, size, vertical, bounds)) },
                )
            } else {
                SpreadSlot(
                    urls = (if (mode == ReadingMode.RTL) spread.pages.reversed() else spread.pages).map { pages[it] },
                    contentScale = contentScale,
                    onTap = onTap,
                )
            }
        }

        if (vertical) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { spreads[it].key },
            ) { slotContent(it) }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                reverseLayout = mode == ReadingMode.RTL,
                key = { spreads[it].key },
            ) { slotContent(it) }
        }
    }
}

/** Two pages side by side meeting in the middle; tap zones are measured on the whole slot, not per page. */
@Composable
private fun SpreadSlot(urls: List<String>, contentScale: ContentScale, onTap: (TapZone) -> Unit) {
    var slotSize by remember { mutableStateOf(IntSize.Zero) }
    Row(Modifier.fillMaxSize().onSizeChanged { slotSize = it }) {
        urls.forEachIndexed { slot, url ->
            Box(Modifier.weight(1f).fillMaxHeight()) {
                ReaderPage(
                    url = url,
                    contentScale = contentScale,
                    alignment = if (slot == 0) Alignment.CenterEnd else Alignment.CenterStart,
                    onTap = { offset, size, bounds ->
                        val dx = slot * size.width.toFloat()
                        onTap(zoneOf(offset + Offset(dx, 0f), slotSize, vertical = false, contentBounds = bounds.translate(dx, 0f)))
                    },
                )
            }
        }
    }
}

/** [onTap] receives the tap position, the viewport size and the displayed image bounds (viewport coordinates). */
@Composable
private fun ReaderPage(
    url: String,
    contentScale: ContentScale,
    alignment: Alignment,
    onTap: (Offset, IntSize, Rect) -> Unit,
) {
    val context = LocalContext.current
    var retry by remember(url) { mutableIntStateOf(0) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val request = remember(url, retry) {
        ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKeyExtra("retry", retry.toString())
            .listener(object : ImageRequest.Listener {
                override fun onError(request: ImageRequest, result: ErrorResult) {
                    error = result.throwable.message ?: "이미지를 불러오지 못했습니다"
                }
                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    error = null
                }
            })
            .build()
    }
    val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 6f))
    val imageState = rememberZoomableImageState(zoomableState)

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it },
    ) {
        ZoomableAsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            state = imageState,
            contentScale = contentScale,
            alignment = alignment,
            onClick = { offset -> onTap(offset, size, zoomableState.transformedContentBounds) },
        )
        if (!imageState.isImageDisplayed && error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
        error?.let { message ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(message, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { error = null; retry++ }) { Text("다시 시도") }
            }
        }
    }
}
