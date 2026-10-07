package com.sus7898.lrrviewer.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sus7898.lrrviewer.AppGraph
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.data.api.LrrApi
import com.sus7898.lrrviewer.data.api.LrrUrls
import com.sus7898.lrrviewer.data.api.userMessage
import kotlinx.coroutines.launch

/** Server URL + API key editor with a connection test. Shared by first-run setup and Settings. */
@Composable
fun ServerForm(
    graph: AppGraph,
    settings: AppSettings,
    saveLabel: String = "저장",
    onSaved: (() -> Unit)? = null,
) {
    var url by rememberSaveable(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var apiKey by rememberSaveable(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val normalized = LrrUrls.normalizeBaseUrl(url)
    val dirty = normalized != settings.serverUrl || apiKey != settings.apiKey

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; testResult = null },
            label = { Text("서버 주소") },
            placeholder = { Text("http://192.168.0.10:3000") },
            supportingText = { Text("NAS의 LANraragi 주소. 리버스 프록시 하위 경로(예: https://nas.example.com/lrr)도 지원") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; testResult = null },
            label = { Text("API 키 (선택)") },
            supportingText = { Text("서버 설정 → Security 의 API Key. No-Fun 모드 서버는 필수. 기기 보안 키스토어로 암호화 저장됨") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "키 표시 전환")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        if (normalized.startsWith("http://")) {
            Text(
                "⚠ 평문 HTTP 주소입니다. 같은 집 네트워크(LAN) 또는 VPN에서만 사용하세요. 인터넷에 공개된 서버라면 HTTPS를 권장합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    testing = true
                    testResult = null
                    scope.launch {
                        try {
                            val info = LrrApi.probe(url, apiKey)
                            testOk = true
                            testResult = buildString {
                                append("연결 성공: ${info.name} v${info.version} · 아카이브 ${info.total_archives}개")
                                if (info.nofun_mode && apiKey.isBlank()) append("\nNo-Fun 모드 서버입니다 → API 키가 필요합니다.")
                                if (!info.server_tracks_progress) append("\n서버 측 진행률 추적이 꺼져 있어 진행률은 이 기기에만 저장됩니다.")
                            }
                        } catch (e: Exception) {
                            testOk = false
                            testResult = e.userMessage()
                        }
                        testing = false
                    }
                },
                enabled = !testing && url.isNotBlank(),
            ) {
                if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("연결 테스트")
            }
            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        graph.settings.edit { it.copy(serverUrl = url, apiKey = apiKey) }
                        saving = false
                        onSaved?.invoke()
                    }
                },
                enabled = !saving && url.isNotBlank() && (dirty || onSaved != null),
            ) { Text(saveLabel) }
        }
        testResult?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (testOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}
