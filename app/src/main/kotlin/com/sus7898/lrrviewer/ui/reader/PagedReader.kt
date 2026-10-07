package com.sus7898.lrrviewer.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import com.sus7898.lrrviewer.data.ReadingMode
import kotlinx.coroutines.flow.SharedFlow
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/** Page-flip reader for LTR / RTL / vertical modes. One zoomable image per page. */
@Composable
fun PagedReader(
    pages: List<String>,
    initialPage: Int,
    mode: ReadingMode,
    fitMode: FitMode,
    jumpEvents: SharedFlow<Int>,
    onPageChanged: (Int) -> Unit,
    onTap: (TapZone) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) { pages.size }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChanged(it) }
    }
    LaunchedEffect(jumpEvents) {
        jumpEvents.collect { p -> if (p in pages.indices) pagerState.scrollToPage(p) }
    }

    val contentScale = when (fitMode) {
        FitMode.FIT -> ContentScale.Fit
        FitMode.FILL_WIDTH -> ContentScale.FillWidth
        FitMode.FILL_HEIGHT -> ContentScale.FillHeight
    }
    val pageContent: @Composable (Int) -> Unit = { index ->
        ReaderPage(
            url = pages[index],
            contentScale = contentScale,
            onTap = { offset, size, imageBounds ->
                onTap(zoneOf(offset, size, vertical = mode == ReadingMode.VERTICAL, contentBounds = imageBounds))
            },
        )
    }

    if (mode == ReadingMode.VERTICAL) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { it },
        ) { pageContent(it) }
    } else {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            reverseLayout = mode == ReadingMode.RTL,
            key = { it },
        ) { pageContent(it) }
    }
}

/** [onTap] receives the tap position, the viewport size and the displayed image bounds (viewport coordinates). */
@Composable
private fun ReaderPage(url: String, contentScale: ContentScale, onTap: (Offset, IntSize, Rect) -> Unit) {
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
