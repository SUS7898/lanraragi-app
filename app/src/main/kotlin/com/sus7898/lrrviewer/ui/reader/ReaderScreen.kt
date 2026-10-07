package com.sus7898.lrrviewer.ui.reader

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.sus7898.lrrviewer.AppGraph
import com.sus7898.lrrviewer.ReaderKey
import com.sus7898.lrrviewer.ReaderKeyEvents
import com.sus7898.lrrviewer.data.FitMode
import com.sus7898.lrrviewer.data.ReaderBackground
import com.sus7898.lrrviewer.data.ReadingMode
import com.sus7898.lrrviewer.ui.common.ErrorView
import com.sus7898.lrrviewer.ui.common.findActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import kotlin.math.abs
import kotlin.math.roundToInt

enum class TapZone { LEFT, RIGHT, TOP, BOTTOM, CENTER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(graph: AppGraph, arcId: String, startPage: Int, onBack: () -> Unit) {
    val vm: ReaderViewModel = viewModel(key = "reader_$arcId") { ReaderViewModel(graph, arcId, startPage) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by graph.settingsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var orientationLocked by rememberSaveable { mutableStateOf(false) }
    val jumpEvents = remember { MutableSharedFlow<Int>(extraBufferCapacity = 8) }

    // --- system UI -----------------------------------------------------------------------
    LaunchedEffect(chromeVisible) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeVisible) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
    DisposableEffect(settings.keepScreenOn) {
        val window = activity?.window
        if (settings.keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(settings.volumeKeyNavigation) {
        ReaderKeyEvents.active = settings.volumeKeyNavigation
        onDispose { ReaderKeyEvents.active = false }
    }
    DisposableEffect(orientationLocked) {
        activity?.requestedOrientation =
            if (orientationLocked) ActivityInfo.SCREEN_ORIENTATION_LOCKED else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { if (orientationLocked) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // --- hardware keys -------------------------------------------------------------------
    LaunchedEffect(Unit) {
        ReaderKeyEvents.events.collect { key ->
            val s = vm.state.value
            val target = if (key == ReaderKey.NEXT) s.currentPage + 1 else s.currentPage - 1
            if (target in 0 until s.pageCount) jumpEvents.tryEmit(target)
        }
    }

    fun onZoneTap(zone: TapZone) {
        if (!settings.tapNavigation || zone == TapZone.CENTER) {
            chromeVisible = !chromeVisible
            return
        }
        val forward = when (zone) {
            TapZone.LEFT -> settings.readingMode == ReadingMode.RTL
            TapZone.RIGHT -> settings.readingMode != ReadingMode.RTL
            TapZone.TOP -> false
            TapZone.BOTTOM -> true
            TapZone.CENTER -> true
        }
        val target = if (forward) state.currentPage + 1 else state.currentPage - 1
        if (target in 0 until state.pageCount) jumpEvents.tryEmit(target) else chromeVisible = true
    }

    val background = when (settings.background) {
        ReaderBackground.BLACK -> Color.Black
        ReaderBackground.WHITE -> Color.White
        ReaderBackground.GRAY -> Color(0xFF2B2B2B)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(background),
    ) {
        when {
            state.loading -> ReaderLoading(if (state.extracting) "서버에서 아카이브 압축을 푸는 중…" else "불러오는 중…")
            state.error != null -> Column(Modifier.fillMaxSize()) {
                ErrorView(state.error!!, modifier = Modifier.weight(1f), onRetry = { vm.load() })
                TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 32.dp)) { Text("돌아가기") }
            }
            settings.readingMode == ReadingMode.WEBTOON -> WebtoonReader(
                pages = state.pages,
                initialPage = state.currentPage,
                jumpEvents = jumpEvents,
                onPageChanged = vm::onPageChanged,
                onTap = ::onZoneTap,
            )
            else -> PagedReader(
                pages = state.pages,
                initialPage = state.currentPage,
                mode = settings.readingMode,
                fitMode = settings.fitMode,
                jumpEvents = jumpEvents,
                onPageChanged = vm::onPageChanged,
                onTap = ::onZoneTap,
            )
        }

        if (!chromeVisible && settings.showPageNumber && state.pageCount > 0) {
            Text(
                "${state.currentPage + 1} / ${state.pageCount}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        if (state.pageCount > 0) {
                            Text("${state.currentPage + 1} / ${state.pageCount}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    IconButton(onClick = { orientationLocked = !orientationLocked }) {
                        Icon(
                            if (orientationLocked) Icons.Filled.ScreenLockRotation else Icons.Filled.ScreenRotation,
                            contentDescription = if (orientationLocked) "회전 잠금 해제" else "회전 잠금",
                        )
                    }
                    IconButton(onClick = { vm.load() }) { Icon(Icons.Filled.Refresh, contentDescription = "새로고침") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.65f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        }

        AnimatedVisibility(
            visible = chromeVisible && state.pageCount > 0,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            ReaderBottomBar(
                currentPage = state.currentPage,
                pageCount = state.pageCount,
                mode = settings.readingMode,
                hasToc = state.toc.isNotEmpty(),
                onJump = { p -> jumpEvents.tryEmit(p.coerceIn(0, state.pageCount - 1)) },
                onSettings = { showSettings = true },
                onToc = { showToc = true },
                onCycleMode = {
                    val modes = ReadingMode.entries
                    val next = modes[(modes.indexOf(settings.readingMode) + 1) % modes.size]
                    scope.launch { graph.settings.edit { it.copy(readingMode = next) } }
                },
            )
        }
    }

    if (showSettings) {
        ReaderSettingsSheet(
            settings = settings,
            onChange = { transform -> scope.launch { graph.settings.edit(transform) } },
            onDismiss = { showSettings = false },
        )
    }
    if (showToc) {
        TocSheet(
            toc = state.toc,
            currentPage = state.currentPage,
            onJump = { page1 ->
                jumpEvents.tryEmit((page1 - 1).coerceIn(0, (state.pageCount - 1).coerceAtLeast(0)))
                showToc = false
            },
            onDismiss = { showToc = false },
        )
    }
}

@Composable
private fun ReaderLoading(message: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = Color.White)
        Spacer(Modifier.height(12.dp))
        Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReaderBottomBar(
    currentPage: Int,
    pageCount: Int,
    mode: ReadingMode,
    hasToc: Boolean,
    onJump: (Int) -> Unit,
    onSettings: () -> Unit,
    onToc: () -> Unit,
    onCycleMode: () -> Unit,
) {
    Surface(color = Color.Black.copy(alpha = 0.65f), contentColor = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${currentPage + 1}", modifier = Modifier.widthIn(min = 36.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                Box(Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalLayoutDirection provides if (mode == ReadingMode.RTL) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                        var sliderPos by remember(currentPage) { mutableFloatStateOf(currentPage.toFloat()) }
                        Slider(
                            value = sliderPos,
                            onValueChange = { sliderPos = it },
                            onValueChangeFinished = { onJump(sliderPos.roundToInt()) },
                            valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                            enabled = pageCount > 1,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Text("$pageCount", modifier = Modifier.widthIn(min = 36.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = onCycleMode) { Icon(Icons.Filled.SwapHoriz, contentDescription = "읽기 방향 전환: ${mode.label}") }
                IconButton(onClick = onToc, enabled = hasToc) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "목차") }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Tune, contentDescription = "뷰어 설정") }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Paged modes (LTR / RTL / vertical)
// ------------------------------------------------------------------------------------------

@Composable
private fun PagedReader(
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
            onTap = { offset, size -> onTap(zoneOf(offset, size, vertical = mode == ReadingMode.VERTICAL)) },
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

@Composable
private fun ReaderPage(url: String, contentScale: ContentScale, onTap: (Offset, IntSize) -> Unit) {
    val context = LocalContext.current
    var retry by remember(url) { mutableIntStateOf(0) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val request = remember(url, retry) {
        ImageRequest.Builder(context)
            .data(url)
            .setParameter("retry", retry)
            .listener(
                onError = { _, result -> error = result.throwable.message ?: "이미지를 불러오지 못했습니다" },
                onSuccess = { _, _ -> error = null },
            )
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
            onClick = { offset -> onTap(offset, size) },
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

// ------------------------------------------------------------------------------------------
// Webtoon (continuous vertical scroll)
// ------------------------------------------------------------------------------------------

@Composable
private fun WebtoonReader(
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
        ImageRequest.Builder(context).data(url).setParameter("retry", retry).build()
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

private fun zoneOf(offset: Offset, size: IntSize, vertical: Boolean): TapZone {
    if (size.width <= 0 || size.height <= 0) return TapZone.CENTER
    return if (vertical) {
        val fy = offset.y / size.height
        when {
            fy < 0.28f -> TapZone.TOP
            fy > 0.72f -> TapZone.BOTTOM
            else -> TapZone.CENTER
        }
    } else {
        val fx = offset.x / size.width
        when {
            fx < 0.3f -> TapZone.LEFT
            fx > 0.7f -> TapZone.RIGHT
            else -> TapZone.CENTER
        }
    }
}
