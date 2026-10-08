package com.sus7898.lrrviewer.ui.reader

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sus7898.lrrviewer.AppGraph
import com.sus7898.lrrviewer.ReaderKey
import com.sus7898.lrrviewer.ReaderKeyEvents
import com.sus7898.lrrviewer.data.ReaderBackground
import com.sus7898.lrrviewer.data.ReadingMode
import com.sus7898.lrrviewer.ui.common.ErrorView
import com.sus7898.lrrviewer.ui.common.findActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Fraction of the viewport a tap / key scrolls in webtoon mode. */
private const val WEBTOON_SCROLL_FRACTION = 0.9f

/** A page at least this many times taller than wide is treated as a webtoon strip (mode suggestion). */
private const val WEBTOON_SUGGEST_RATIO = 2.5f

/** Reader host: system UI, hardware keys, chrome (top/bottom bars), sheets. Page rendering lives in PagedReader / WebtoonReader. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(graph: AppGraph, arcId: String, startPage: Int, onBack: () -> Unit) {
    val vm: ReaderViewModel = viewModel(key = "reader_$arcId") { ReaderViewModel(graph.readerDeps, arcId, startPage) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by graph.settingsState.collectAsStateWithLifecycle()
    val readingMode = state.readingModeOverride ?: settings.readingMode
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var orientationLocked by rememberSaveable { mutableStateOf(false) }
    var webtoonSuggested by rememberSaveable { mutableStateOf(false) }
    /** Page index to show (paged modes) or scroll to (webtoon). */
    val jumpEvents = remember { MutableSharedFlow<Int>(extraBufferCapacity = 8) }
    /** Webtoon only: scroll by this fraction of the viewport height (negative = up). */
    val scrollEvents = remember { MutableSharedFlow<Float>(extraBufferCapacity = 8) }

    // --- system UI -----------------------------------------------------------------------
    LaunchedEffect(chromeVisible) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeVisible) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(Unit) {
        // Draw into the display cutout (punch-hole) area while reading instead of leaving a black band.
        val window = activity?.window
        val previousCutoutMode = window?.attributes?.layoutInDisplayCutoutMode
        window?.attributes = window?.attributes?.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        onDispose {
            window?.let { w ->
                WindowCompat.getInsetsController(w, view).show(WindowInsetsCompat.Type.systemBars())
                w.attributes = w.attributes.apply {
                    layoutInDisplayCutoutMode = previousCutoutMode ?: WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
    }
    DisposableEffect(settings.keepScreenOn) {
        val window = activity?.window
        if (settings.keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(settings.volumeKeyNavigation) {
        ReaderKeyEvents.active = true
        ReaderKeyEvents.volumeKeys = settings.volumeKeyNavigation
        onDispose { ReaderKeyEvents.active = false; ReaderKeyEvents.volumeKeys = false }
    }
    DisposableEffect(orientationLocked) {
        activity?.requestedOrientation =
            if (orientationLocked) ActivityInfo.SCREEN_ORIENTATION_LOCKED else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { if (orientationLocked) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // --- webtoon suggestion (P1-2): a very tall first page in a paged mode -----------------
    val startSize = state.pageSizes[state.currentPage]
    LaunchedEffect(startSize, readingMode, state.readingModeOverride) {
        val s = startSize ?: return@LaunchedEffect
        if (webtoonSuggested || readingMode == ReadingMode.WEBTOON || state.readingModeOverride != null) return@LaunchedEffect
        if (s.width <= 0 || s.height < s.width * WEBTOON_SUGGEST_RATIO) return@LaunchedEffect
        webtoonSuggested = true
        val result = snackbar.showSnackbar("세로로 긴 페이지입니다. 이 작품을 웹툰 모드로 볼까요?", actionLabel = "웹툰 모드", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) vm.setReadingModeOverride(ReadingMode.WEBTOON)
    }

    // --- navigation ----------------------------------------------------------------------
    /** Next/previous for taps and keys: a page in paged modes, most of a screen in webtoon mode. */
    fun navigate(forward: Boolean) {
        val s = vm.state.value
        val mode = s.readingModeOverride ?: graph.settingsState.value.readingMode
        if (mode == ReadingMode.WEBTOON) {
            scrollEvents.tryEmit(if (forward) WEBTOON_SCROLL_FRACTION else -WEBTOON_SCROLL_FRACTION)
            return
        }
        val target = if (forward) s.currentPage + 1 else s.currentPage - 1
        if (target in 0 until s.pageCount) jumpEvents.tryEmit(target) else chromeVisible = true
    }

    LaunchedEffect(Unit) {
        ReaderKeyEvents.events.collect { key -> navigate(forward = key == ReaderKey.NEXT) }
    }

    fun onZoneTap(zone: TapZone) {
        if (!settings.tapNavigation || zone == TapZone.CENTER) {
            chromeVisible = !chromeVisible
            return
        }
        val forward = when (zone) {
            TapZone.LEFT -> readingMode == ReadingMode.RTL
            TapZone.RIGHT -> readingMode != ReadingMode.RTL
            TapZone.TOP -> false
            TapZone.BOTTOM -> true
            TapZone.CENTER -> true
        }
        navigate(forward)
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
            readingMode == ReadingMode.WEBTOON -> WebtoonReader(
                pages = state.pages,
                pageSizes = state.pageSizes,
                store = graph.pageStore,
                initialPage = state.currentPage,
                jumpEvents = jumpEvents,
                scrollEvents = scrollEvents,
                onNeedSize = vm::ensureSize,
                onPageChanged = vm::onPageChanged,
                onTap = ::onZoneTap,
            )
            else -> PagedReader(
                pages = state.pages,
                initialPage = state.currentPage,
                mode = readingMode,
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
                            Text(
                                "${state.currentPage + 1} / ${state.pageCount}" +
                                    (state.readingModeOverride?.let { " · ${it.label} (이 작품)" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                            )
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
                mode = readingMode,
                hasToc = state.toc.isNotEmpty(),
                onJump = { p -> jumpEvents.tryEmit(p.coerceIn(0, state.pageCount - 1)) },
                onSettings = { showSettings = true },
                onToc = { showToc = true },
                onCycleMode = {
                    // Quick toggle applies to this archive only; the settings sheet changes the global default.
                    val modes = ReadingMode.entries
                    vm.setReadingModeOverride(modes[(modes.indexOf(readingMode) + 1) % modes.size])
                },
            )
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 72.dp),
        )
    }

    if (showSettings) {
        ReaderSettingsSheet(
            settings = settings,
            readingModeOverride = state.readingModeOverride,
            onChange = { transform -> scope.launch { graph.settings.edit(transform) } },
            onOverrideChange = vm::setReadingModeOverride,
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
                IconButton(onClick = onCycleMode) { Icon(Icons.Filled.SwapHoriz, contentDescription = "이 작품의 읽기 방향 전환: ${mode.label}") }
                IconButton(onClick = onToc, enabled = hasToc) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "목차") }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Tune, contentDescription = "뷰어 설정") }
            }
        }
    }
}
