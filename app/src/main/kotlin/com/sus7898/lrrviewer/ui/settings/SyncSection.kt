package com.sus7898.lrrviewer.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sus7898.lrrviewer.AppGraph
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.data.sync.SyncManager
import com.sus7898.lrrviewer.ui.common.formatDateTime
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → 동기화. One JSON file, picked with the system file picker, shared by both devices (e.g. in a
 * Synology Drive / Google Drive folder). Ratings and favourites are server-side already and need no sync.
 */
@Composable
fun SyncSection(graph: AppGraph, settings: AppSettings) {
    val scope = rememberCoroutineScope()
    val status by graph.sync.status.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmUnlink by remember { mutableStateOf(false) }

    fun run(block: suspend () -> String) {
        scope.launch { message = runCatching { block() }.getOrElse { it.message ?: it.toString() } }
    }

    val createSyncFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) run { graph.sync.link(uri) }
    }
    val openSyncFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run { graph.sync.link(uri) }
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) run { graph.sync.exportTo(uri) }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run { graph.sync.importFrom(uri) }
    }
    val running = status is SyncManager.Status.Running

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "설정·읽기 기록·작품별 읽기 방향·카테고리 순서·검색 기록을 파일 하나로 두 기기가 함께 씁니다. " +
                "폰과 태블릿에서 같은 파일(Synology Drive, Google Drive 등)을 연결하면 앱을 열 때와 닫을 때 자동으로 합칩니다. " +
                "평점·즐겨찾기는 서버에 저장되므로 따로 동기화하지 않아도 됩니다. 서버 주소와 API 키는 파일에 넣지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (settings.isSyncLinked) {
            ListItem(
                headlineContent = { Text("동기화 파일 연결됨") },
                supportingContent = {
                    Text(if (settings.lastSyncAt > 0) "마지막 동기화 ${formatDateTime(settings.lastSyncAt / 1000)}" else "아직 동기화하지 않음")
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { run { graph.sync.syncNow() } }, enabled = !running) { Text("지금 동기화") }
                OutlinedButton(onClick = { confirmUnlink = true }, enabled = !running) { Text("연결 해제") }
                if (running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        } else {
            Text("첫 기기에서 '새 동기화 파일 만들기', 다른 기기에서 '기존 파일 연결'을 고르세요.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { createSyncFile.launch("lrrviewer-sync.json") }, enabled = !running) { Text("새 동기화 파일 만들기") }
                OutlinedButton(onClick = { openSyncFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, enabled = !running) {
                    Text("기존 파일 연결")
                }
            }
        }
        HorizontalDivider()
        Text("백업 (한 번만 내보내기 / 가져오기)", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    exportBackup.launch("lrrviewer-backup-$stamp.json")
                },
                enabled = !running,
            ) { Text("백업 내보내기") }
            OutlinedButton(onClick = { importBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, enabled = !running) {
                Text("백업 가져오기")
            }
        }
        Text(
            "가져오기는 덮어쓰지 않고 합칩니다: 더 최근에 바뀐 설정·순서만 적용하고, 읽기 기록은 작품마다 더 최근 것을 남깁니다. " +
                "다른 서버의 기록은 건너뜁니다. 한쪽에서 지운 기록은 다른 기기에서 지워지지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (status is SyncManager.Status.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (confirmUnlink) {
        AlertDialog(
            onDismissRequest = { confirmUnlink = false },
            title = { Text("동기화 연결 해제") },
            text = { Text("이 기기에서 동기화 파일 연결만 끊습니다. 파일과 이 기기의 데이터는 그대로 남습니다.") },
            confirmButton = {
                TextButton(onClick = { confirmUnlink = false; scope.launch { graph.sync.unlink(); message = "연결을 해제했습니다." } }) { Text("해제") }
            },
            dismissButton = { TextButton(onClick = { confirmUnlink = false }) { Text("취소") } },
        )
    }
}
