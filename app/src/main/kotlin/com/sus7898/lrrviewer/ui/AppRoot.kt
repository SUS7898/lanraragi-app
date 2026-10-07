package com.sus7898.lrrviewer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sus7898.lrrviewer.AppGraph
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.ui.common.rememberResumeTick
import com.sus7898.lrrviewer.ui.detail.ArchiveDetailScreen
import com.sus7898.lrrviewer.ui.home.HomeScreen
import com.sus7898.lrrviewer.ui.reader.ReaderScreen
import com.sus7898.lrrviewer.ui.settings.ServerSetupScreen
import com.sus7898.lrrviewer.update.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

// Type-safe routes (Navigation Compose 2.8+). Add new screens here, never as string routes.
@Serializable object SetupRoute
@Serializable object HomeRoute
@Serializable data class ArchiveRoute(val id: String)
@Serializable data class ReaderRoute(val id: String, val page: Int = -1)

@Composable
fun AppRoot(graph: AppGraph, settings: AppSettings) {
    val navController = rememberNavController()
    val startDestination: Any = remember { if (settings.isServerConfigured) HomeRoute else SetupRoute }

    NavHost(navController = navController, startDestination = startDestination) {
        composable<SetupRoute> {
            ServerSetupScreen(
                graph = graph,
                onDone = { navController.navigate(HomeRoute) { popUpTo<SetupRoute> { inclusive = true } } },
            )
        }
        composable<HomeRoute> {
            HomeScreen(
                graph = graph,
                onOpenArchive = { id -> navController.navigate(ArchiveRoute(id)) },
                onOpenReader = { id, page -> navController.navigate(ReaderRoute(id, page)) },
            )
        }
        composable<ArchiveRoute> { entry ->
            val route = entry.toRoute<ArchiveRoute>()
            ArchiveDetailScreen(
                graph = graph,
                id = route.id,
                onBack = { navController.popBackStack() },
                onOpenReader = { page -> navController.navigate(ReaderRoute(route.id, page)) },
                onOpenArchive = { other -> navController.navigate(ArchiveRoute(other)) },
                onSearchTag = { tag ->
                    graph.pendingLibrarySearch.value = tag
                    navController.popBackStack<HomeRoute>(inclusive = false)
                },
            )
        }
        composable<ReaderRoute> { entry ->
            val route = entry.toRoute<ReaderRoute>()
            ReaderScreen(graph = graph, arcId = route.id, startPage = route.page, onBack = { navController.popBackStack() })
        }
    }

    AutoUpdateCheck(graph)
    UpdateDialogs(graph)
}

/** Once a day (if enabled) look for a newer GitHub release shortly after start-up. */
@Composable
private fun AutoUpdateCheck(graph: AppGraph) {
    LaunchedEffect(Unit) {
        val s = graph.settingsState.value
        if (!s.autoCheckUpdates) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - s.lastUpdateCheck < 24L * 60 * 60 * 1000) return@LaunchedEffect
        delay(2_000)
        graph.updater.check(auto = true)
        graph.settings.edit { it.copy(lastUpdateCheck = now) }
    }
}

/** Dialogs for the automatic update prompt. Manual checks from Settings render inline instead. */
@Composable
private fun UpdateDialogs(graph: AppGraph) {
    val updater = graph.updater
    val state by updater.state.collectAsStateWithLifecycle()
    if (!updater.autoPrompt) return
    val context = LocalContext.current
    val resumeTick = rememberResumeTick()
    val canInstall = remember(resumeTick, state) { updater.canRequestInstalls() }

    when (val s = state) {
        is UpdateManager.State.Available -> AlertDialog(
            onDismissRequest = { updater.dismiss() },
            title = { Text("새 버전 ${s.release.version}") },
            text = {
                Column {
                    Text("현재 ${updater.currentVersionName} → ${s.release.version}")
                    if (s.release.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(s.release.notes.take(600), style = MaterialTheme.typography.bodySmall, maxLines = 12, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            confirmButton = { Button(onClick = { updater.download(s.release) }) { Text("다운로드") } },
            dismissButton = { TextButton(onClick = { updater.dismiss() }) { Text("나중에") } },
        )
        is UpdateManager.State.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("업데이트 다운로드 중") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(s.progress * 100).toInt()}%")
                }
            },
            confirmButton = {},
        )
        is UpdateManager.State.Verifying -> AlertDialog(
            onDismissRequest = {},
            title = { Text("검증 중") },
            text = { Text("SHA-256 체크섬과 서명 인증서를 확인하고 있습니다…") },
            confirmButton = {},
        )
        is UpdateManager.State.ReadyToInstall -> AlertDialog(
            onDismissRequest = { updater.dismiss() },
            title = { Text("설치 준비 완료") },
            text = {
                Text(
                    if (canInstall) "버전 ${s.release.version} 검증이 끝났습니다. 설치를 누르면 시스템 설치 화면이 표시됩니다."
                    else "이 앱이 업데이트를 설치할 수 있도록 '알 수 없는 앱 설치' 권한을 한 번만 허용해주세요.",
                )
            },
            confirmButton = {
                if (canInstall) Button(onClick = { updater.install(s.release, s.file) }) { Text("설치") }
                else Button(onClick = { context.startActivity(updater.unknownSourcesSettingsIntent()) }) { Text("권한 설정 열기") }
            },
            dismissButton = { TextButton(onClick = { updater.dismiss() }) { Text("닫기") } },
        )
        is UpdateManager.State.Error -> AlertDialog(
            onDismissRequest = { updater.dismiss() },
            title = { Text("업데이트 오류") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { updater.dismiss() }) { Text("확인") } },
        )
        else -> Unit
    }
}
