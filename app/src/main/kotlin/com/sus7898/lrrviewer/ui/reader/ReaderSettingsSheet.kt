package com.sus7898.lrrviewer.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.data.FitMode
import com.sus7898.lrrviewer.data.ReaderBackground
import com.sus7898.lrrviewer.data.ReadingMode
import com.sus7898.lrrviewer.data.api.TocEntry
import com.sus7898.lrrviewer.ui.common.StepperRow
import com.sus7898.lrrviewer.ui.common.SwitchRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderSettingsSheet(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
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

            ChoiceChips("읽기 방향", ReadingMode.entries, settings.readingMode, { it.label }) { v -> onChange { it.copy(readingMode = v) } }
            ChoiceChips("이미지 맞춤", FitMode.entries, settings.fitMode, { it.label }) { v -> onChange { it.copy(fitMode = v) } }
            ChoiceChips("배경", ReaderBackground.entries, settings.background, { it.label }) { v -> onChange { it.copy(background = v) } }

            SwitchRow("화면 탭으로 넘기기", settings.tapNavigation) { v -> onChange { it.copy(tapNavigation = v) } }
            SwitchRow("볼륨 키로 넘기기", settings.volumeKeyNavigation) { v -> onChange { it.copy(volumeKeyNavigation = v) } }
            SwitchRow("화면 항상 켜기", settings.keepScreenOn) { v -> onChange { it.copy(keepScreenOn = v) } }
            SwitchRow("페이지 번호 표시", settings.showPageNumber) { v -> onChange { it.copy(showPageNumber = v) } }
            StepperRow("미리 불러올 페이지", settings.prefetchPages, 0..10) { v -> onChange { it.copy(prefetchPages = v) } }
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
