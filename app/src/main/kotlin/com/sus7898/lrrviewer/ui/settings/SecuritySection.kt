package com.sus7898.lrrviewer.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.sus7898.lrrviewer.AppGraph
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.ui.common.ChoiceRow
import com.sus7898.lrrviewer.ui.common.SwitchRow
import com.sus7898.lrrviewer.ui.common.findActivity
import com.sus7898.lrrviewer.ui.lock.authenticate
import com.sus7898.lrrviewer.ui.lock.canUseAppLock
import kotlinx.coroutines.launch

private val GRACE_OPTIONS = listOf(0, 60, 300, 1800)

/** Settings → 보안: app lock (biometric / screen lock) and hiding the app content from recents and screenshots. */
@Composable
fun SecuritySection(graph: AppGraph, settings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }

    SwitchRow(
        title = "앱 잠금",
        subtitle = "앱을 열 때 지문·얼굴 또는 화면 잠금으로 확인 (이 기기에만 적용)",
        checked = settings.appLock,
    ) { enable ->
        if (!enable) {
            scope.launch { graph.settings.edit { it.copy(appLock = false) } }
            graph.appLock.unlock()
            note = null
            return@SwitchRow
        }
        val activity = context.findActivity() as? FragmentActivity
        when {
            activity == null -> note = "잠금을 설정할 수 없습니다."
            !canUseAppLock(context) -> note = "기기에 지문·얼굴 또는 화면 잠금(PIN/패턴/비밀번호)을 먼저 설정하세요."
            // Confirm once before turning it on, so the user knows the unlock works on this device.
            else -> authenticate(
                activity,
                "앱 잠금 켜기",
                onSuccess = {
                    scope.launch { graph.settings.edit { it.copy(appLock = true) } }
                    graph.appLock.unlock()
                    note = null
                },
                onError = { note = it },
            )
        }
    }
    ChoiceRow(
        title = "다시 잠그기까지",
        options = GRACE_OPTIONS,
        selected = GRACE_OPTIONS.minByOrNull { kotlin.math.abs(it - settings.appLockGraceSec) } ?: 0,
        label = { if (it == 0) "즉시" else if (it < 3600 && it % 60 == 0) "${it / 60}분" else "${it}초" },
        subtitle = "앱을 벗어난 뒤 이 시간이 지나면 다시 잠급니다",
        onSelect = { v -> scope.launch { graph.settings.edit { it.copy(appLockGraceSec = v) } } },
    )
    SwitchRow(
        title = "최근 앱 화면에서 숨기기",
        subtitle = "최근 앱 미리보기를 가리고 스크린샷을 막습니다",
        checked = settings.hideInRecents,
    ) { v -> scope.launch { graph.settings.edit { it.copy(hideInRecents = v) } } }
    note?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
    }
}
