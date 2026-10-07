package com.sus7898.lrrviewer.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sus7898.lrrviewer.AppGraph
import com.sus7898.lrrviewer.ui.common.EmptyView
import com.sus7898.lrrviewer.ui.common.formatDateTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    graph: AppGraph,
    onOpenArchive: (String) -> Unit,
    onOpenReader: (String, Int) -> Unit,
) {
    val entries by graph.progress.recent.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("읽기 기록", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, contentDescription = "기록 전체 삭제") }
            }
        }
        Text(
            "탭: 이어 읽기 · 길게 누르기: 상세 보기",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (entries.isEmpty()) {
            EmptyView("아직 읽은 기록이 없습니다")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.arcid }) { e ->
                    ListItem(
                        headlineContent = { Text(e.title.ifBlank { e.arcid }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                Text("${e.page + 1} / ${e.pageCount} 페이지 · ${formatDateTime(e.lastReadAt / 1000)}")
                                if (e.pageCount > 0) {
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { ((e.page + 1).toFloat() / e.pageCount).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        },
                        leadingContent = {
                            AsyncImage(
                                model = graph.api.thumbnailUrl(e.arcid),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 48.dp, height = 68.dp)
                                    .clip(MaterialTheme.shapes.small),
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { scope.launch { graph.progress.remove(e.arcid) } }) {
                                Icon(Icons.Filled.Close, contentDescription = "기록 삭제")
                            }
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = { onOpenReader(e.arcid, if (e.isCompleted) 0 else e.page) },
                            onLongClick = { onOpenArchive(e.arcid) },
                        ),
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("기록 전체 삭제") },
            text = { Text("기기에 저장된 읽기 기록을 모두 삭제합니다. 서버의 진행률은 그대로 유지됩니다.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { graph.progress.clear() }; confirmClear = false }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
        )
    }
}
