package com.sus7898.lrrviewer.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.data.BRIGHTNESS_SYSTEM
import com.sus7898.lrrviewer.data.ColorFilterMode
import com.sus7898.lrrviewer.data.FitMode
import com.sus7898.lrrviewer.data.ReaderBackground
import com.sus7898.lrrviewer.data.ReadingMode
import com.sus7898.lrrviewer.data.api.TocEntry
import com.sus7898.lrrviewer.ui.common.StepperRow
import com.sus7898.lrrviewer.ui.common.SwitchRow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderSettingsSheet(
    settings: AppSettings,
    readingModeOverride: ReadingMode?,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onOverrideChange: (ReadingMode?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("뷰어 설정", style = MaterialTheme.typography.titleLarge)

            ChoiceChips("읽기 방향 (기본값)", ReadingMode.entries, settings.readingMode, { it.label }) { v -> onChange { it.copy(readingMode = v) } }
            ChoiceChips(
                "이 작품만",
                listOf<ReadingMode?>(null) + ReadingMode.entries,
                readingModeOverride,
                { it?.label ?: "기본값 따름" },
            ) { v -> onOverrideChange(v) }
            ChoiceChips("이미지 맞춤", FitMode.entries, settings.fitMode, { it.label }) { v -> onChange { it.copy(fitMode = v) } }
            ChoiceChips("배경", ReaderBackground.entries, settings.background, { it.label }) { v -> onChange { it.copy(background = v) } }
            ChoiceChips("색 필터", ColorFilterMode.entries, settings.colorFilter, { it.label }) { v -> onChange { it.copy(colorFilter = v) } }
            BrightnessRow(settings.readerBrightness) { v -> onChange { it.copy(readerBrightness = v) } }
            ChoiceChips("웹툰 페이지 간격", listOf(0, 4, 8, 16), settings.webtoonGapDp, { if (it == 0) "없음" else "${it}dp" }) { v -> onChange { it.copy(webtoonGapDp = v) } }
            StepperRow("웹툰 자동 스크롤 속도", settings.autoScrollSpeed, 1..10, subtitle = "하단 바 ▶ 버튼으로 시작, 가운데 탭으로 멈춤") { v -> onChange { it.copy(autoScrollSpeed = v) } }

            SwitchRow("가로 모드에서 양면 보기", settings.spreadInLandscape, "좌우 넘김 모드에서 세로 페이지 두 장을 나란히") { v -> onChange { it.copy(spreadInLandscape = v) } }
            SwitchRow("표지는 한 장으로", settings.spreadCoverAlone, enabled = settings.spreadInLandscape) { v -> onChange { it.copy(spreadCoverAlone = v) } }
            SwitchRow("화면 탭으로 넘기기", settings.tapNavigation) { v -> onChange { it.copy(tapNavigation = v) } }
            SwitchRow("볼륨 키로 넘기기", settings.volumeKeyNavigation) { v -> onChange { it.copy(volumeKeyNavigation = v) } }
            SwitchRow("화면 항상 켜기", settings.keepScreenOn) { v -> onChange { it.copy(keepScreenOn = v) } }
            SwitchRow("페이지 번호 표시", settings.showPageNumber) { v -> onChange { it.copy(showPageNumber = v) } }
            StepperRow("미리 불러올 페이지", settings.prefetchPages, 0..10) { v -> onChange { it.copy(prefetchPages = v) } }
        }
    }
}

/** Brightness while reading: a "시스템" chip or a 1..100 % slider (applied to the window, not the system setting). */
@Composable
private fun BrightnessRow(value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("밝기 (읽는 동안만)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            FilterChip(selected = value == BRIGHTNESS_SYSTEM, onClick = { onChange(BRIGHTNESS_SYSTEM) }, label = { Text("시스템") })
        }
        var sliderPos by remember(value) { mutableFloatStateOf(if (value in 1..100) value.toFloat() else 50f) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = sliderPos,
                onValueChange = { sliderPos = it },
                onValueChangeFinished = { onChange(sliderPos.roundToInt().coerceIn(1, 100)) },
                valueRange = 1f..100f,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (value in 1..100) "$value%" else "시스템",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.widthIn(min = 48.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocSheet(toc: List<TocEntry>, currentPage: Int, onJump: (page1Based: Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Text("목차", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            toc.forEach { entry ->
                val active = toc.lastOrNull { it.page - 1 <= currentPage } == entry
                ListItem(
                    headlineContent = { Text(entry.name) },
                    trailingContent = { Text("${entry.page}p") },
                    colors = if (active) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ListItemDefaults.colors(),
                    modifier = Modifier.clickable { onJump(entry.page) },
                )
            }
        }
    }
}
