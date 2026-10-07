package com.sus7898.lrrviewer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sus7898.lrrviewer.AppGraph
import com.sus7898.lrrviewer.BuildConfig
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.data.FitMode
import com.sus7898.lrrviewer.data.ReaderBackground
import com.sus7898.lrrviewer.data.ReadingMode
import com.sus7898.lrrviewer.data.ThemeMode
import com.sus7898.lrrviewer.ui.common.ChoiceRow
import com.sus7898.lrrviewer.ui.common.SectionTitle
import com.sus7898.lrrviewer.ui.common.StepperRow
import com.sus7898.lrrviewer.ui.common.SwitchRow
import com.sus7898.lrrviewer.ui.common.formatBytes
import com.sus7898.lrrviewer.ui.common.openUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CACHE_SIZE_OPTIONS = listOf(256, 512, 1024, 2048, 4096)

@Composable
fun SettingsScreen(graph: AppGraph) {
    val settings by graph.settingsState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { graph.settings.edit(transform) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Text("설정", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        }

        item {
            SectionTitle("서버")
            ServerForm(graph = graph, settings = settings)
            SwitchRow(
                title = "평문 HTTP 허용",
                subtitle = "끄면 http:// 로 시작하는 모든 연결을 차단합니다 (HTTPS 서버 전용)",
                checked = settings.allowCleartext,
            ) { v -> update { it.copy(allowCleartext = v) } }
        }

        item {
            SectionTitle("뷰어 기본값")
            ChoiceRow("읽기 방향", ReadingMode.entries, settings.readingMode, { it.label }) { v -> update { it.copy(readingMode = v) } }
            ChoiceRow("이미지 맞춤", FitMode.entries, settings.fitMode, { it.label }) { v -> update { it.copy(fitMode = v) } }
            ChoiceRow("배경색", ReaderBackground.entries, settings.background, { it.label }) { v -> update { it.copy(background = v) } }
            SwitchRow("화면 탭으로 페이지 넘기기", settings.tapNavigation, "가장자리 탭: 이전/다음 · 가운데 탭: 메뉴") { v -> update { it.copy(tapNavigation = v) } }
            SwitchRow("볼륨 키로 페이지 넘기기", settings.volumeKeyNavigation) { v -> update { it.copy(volumeKeyNavigation = v) } }
            SwitchRow("읽는 동안 화면 항상 켜기", settings.keepScreenOn) { v -> update { it.copy(keepScreenOn = v) } }
            SwitchRow("페이지 번호 표시", settings.showPageNumber, "메뉴가 숨겨져 있을 때 하단에 표시") { v -> update { it.copy(showPageNumber = v) } }
            StepperRow("미리 불러올 페이지 수", settings.prefetchPages, 0..10, subtitle = "다음 페이지를 백그라운드에서 스트리밍해 디스크 캐시에 저장") { v -> update { it.copy(prefetchPages = v) } }
            SwitchRow("서버에 읽기 진행률 저장", settings.syncProgress, "서버의 '서버 측 진행률 추적'이 켜져 있을 때만 동작") { v -> update { it.copy(syncProgress = v) } }
            SwitchRow("읽기 시작 시 NEW 표시 해제", settings.clearNewOnRead) { v -> update { it.copy(clearNewOnRead = v) } }
        }

        item {
            SectionTitle("서재")
            SwitchRow("탄코본(묶음)으로 그룹화", settings.groupByTankoubon, "묶음에 속한 아카이브를 한 항목으로 표시") { v -> update { it.copy(groupByTankoubon = v) } }
            StepperRow("썸네일 최소 너비 (dp)", settings.gridMinColumnDp, 80..240, step = 20, subtitle = "작을수록 한 줄에 더 많이 표시") { v -> update { it.copy(gridMinColumnDp = v) } }
        }

        item {
            SectionTitle("화면")
            ChoiceRow("테마", ThemeMode.entries, settings.themeMode, { it.label }) { v -> update { it.copy(themeMode = v) } }
        }

        item {
            SectionTitle("캐시")
            CacheSection(graph = graph, settings = settings, onCacheSize = { v -> update { it.copy(cacheSizeMb = v) } })
        }

        item {
            SectionTitle("업데이트")
            UpdateSection(graph = graph, settings = settings)
        }

        item {
            SectionTitle("정보")
            ListItem(headlineContent = { Text("버전") }, supportingContent = { Text(BuildConfig.VERSION_NAME) })
            ListItem(
                headlineContent = { Text("소스 코드 / 릴리스") },
                supportingContent = { Text(graph.updater.repoUrl) },
                modifier = Modifier.clickable { context.openUrl(graph.updater.repoUrl) },
            )
            ListItem(
                headlineContent = { Text("LANraragi") },
                supportingContent = { Text("https://github.com/Difegue/LANraragi") },
                modifier = Modifier.clickable { context.openUrl("https://github.com/Difegue/LANraragi") },
            )
        }
    }
}

@OptIn(coil3.annotation.ExperimentalCoilApi::class)
@Composable
private fun CacheSection(graph: AppGraph, settings: AppSettings, onCacheSize: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var used by remember { mutableStateOf<Long?>(null) }
    var clearing by remember { mutableStateOf(false) }

    suspend fun measure() {
        used = withContext(Dispatchers.IO) { runCatching { graph.imageLoader.diskCache?.size ?: 0L }.getOrDefault(0L) }
    }
    LaunchedEffect(Unit) { measure() }

    ChoiceRow(
        title = "디스크 캐시 최대 크기",
        options = CACHE_SIZE_OPTIONS,
        selected = CACHE_SIZE_OPTIONS.minByOrNull { kotlin.math.abs(it - settings.cacheSizeMb) } ?: 512,
        label = { "${it} MB" },
        subtitle = "변경 사항은 앱을 완전히 종료 후 다시 실행하면 적용됩니다",
        onSelect = onCacheSize,
    )
    ListItem(
        headlineContent = { Text("현재 사용량") },
        supportingContent = { Text(used?.let { formatBytes(it) } ?: "측정 중…") },
        trailingContent = {
            OutlinedButton(
                onClick = {
                    clearing = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { graph.imageLoader.diskCache?.clear() }
                        }
                        graph.imageLoader.memoryCache?.clear()
                        measure()
                        clearing = false
                    }
                },
                enabled = !clearing,
            ) { Text(if (clearing) "비우는 중…" else "비우기") }
        },
    )
}
