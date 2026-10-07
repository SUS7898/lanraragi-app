package com.sus7898.lrrviewer.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sus7898.lrrviewer.AppGraph
import com.sus7898.lrrviewer.BuildConfig
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.ui.common.SwitchRow
import com.sus7898.lrrviewer.ui.common.formatDateTime
import com.sus7898.lrrviewer.ui.common.openUrl
import com.sus7898.lrrviewer.ui.common.rememberResumeTick
import com.sus7898.lrrviewer.update.UpdateManager
import kotlinx.coroutines.launch

@Composable
fun UpdateSection(graph: AppGraph, settings: AppSettings) {
    val updater = graph.updater
    val state by updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resumeTick = rememberResumeTick()
    val canInstall = remember(resumeTick, state) { updater.canRequestInstalls() }

    fun checkNow() = scope.launch {
        updater.check(auto = false)
        graph.settings.edit { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
    }

    ListItem(
        headlineContent = { Text("현재 버전") },
        supportingContent = {
            Text(BuildConfig.VERSION_NAME + if (BuildConfig.DEBUG) "  (디버그 빌드 - 릴리스 업데이트 설치 불가)" else "")
        },
    )
    SwitchRow(
        title = "시작할 때 자동으로 업데이트 확인",
        subtitle = "하루 1회 GitHub Releases 를 조회합니다",
        checked = settings.autoCheckUpdates,
    ) { v -> scope.launch { graph.settings.edit { it.copy(autoCheckUpdates = v) } } }
    ListItem(
        headlineContent = { Text("마지막 확인") },
        supportingContent = { Text(if (settings.lastUpdateCheck > 0) formatDateTime(settings.lastUpdateCheck / 1000) else "없음") },
    )

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (val s = state) {
            UpdateManager.State.Idle -> Button(onClick = { checkNow() }) { Text("업데이트 확인") }

            UpdateManager.State.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("확인 중…")
            }

            is UpdateManager.State.UpToDate -> {
                Text("최신 버전입니다 (${updater.currentVersionName})", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { checkNow() }) { Text("다시 확인") }
            }

            is UpdateManager.State.Available -> Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("새 버전 ${s.release.version}", style = MaterialTheme.typography.titleMedium)
                    if (s.release.notes.isNotBlank()) {
                        Text(s.release.notes.take(800), style = MaterialTheme.typography.bodySmall, maxLines = 16, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { updater.download(s.release) }) { Text("다운로드") }
                        TextButton(onClick = { context.openUrl(s.release.htmlUrl) }) { Text("릴리스 페이지") }
                    }
                }
            }

            is UpdateManager.State.Downloading -> Column {
                Text("다운로드 중 ${(s.progress * 100).toInt()}%  (${s.release.version})")
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
            }

            is UpdateManager.State.Verifying -> Text("SHA-256 체크섬·서명 인증서 검증 중…")

            is UpdateManager.State.ReadyToInstall -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("검증 완료: ${s.release.version} 설치 준비됨", style = MaterialTheme.typography.bodyMedium)
                if (!canInstall) {
                    Text(
                        "이 앱이 업데이트를 설치하려면 '알 수 없는 앱 설치' 권한이 필요합니다. 최초 1회만 허용하면 됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = { context.startActivity(updater.unknownSourcesSettingsIntent()) }) { Text("권한 설정 열기") }
                } else {
                    Button(onClick = { updater.install(s.release, s.file) }) { Text("설치") }
                }
                TextButton(onClick = { updater.dismiss() }) { Text("취소") }
            }

            is UpdateManager.State.Installing -> Text("설치 중… 시스템 설치 화면의 안내를 따르세요.")

            is UpdateManager.State.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { checkNow() }) { Text("다시 확인") }
                    s.release?.let { r -> TextButton(onClick = { updater.download(r) }) { Text("다시 다운로드") } }
                }
            }
        }

        Text(
            "업데이트는 GitHub Releases(${updater.repoOwner}/${updater.repoName})에서 받아 SHA-256 체크섬과 " +
                "APK 서명 인증서가 현재 앱과 일치하는지 검증한 뒤 설치합니다. 같은 키로 서명되어 있으므로 재설치 없이 덮어씌워지고 데이터가 유지됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
