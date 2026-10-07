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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sus7898.lrrviewer.AppGraph
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.ui.common.rememberResumeTick
import com.sus7898.lrrviewer.ui.detail.ArchiveDetailScreen
import com.sus7898.lrrviewer.ui.home.HomeScreen
import com.sus7898.lrrviewer.ui.reader.ReaderScreen
import com.sus7898.lrrviewer.ui.settings.ServerSetupScreen
import com.sus7898.lrrviewer.update.UpdateManager
import kotlinx.coroutines.delay

object Routes {
    const val SETUP = "setup"
    const val HOME = "home"
    const val ARCHIVE = "archive/{id}"
    const val READER = "reader/{id}?page={page}"

    fun archive(id: String) = "archive/$id"
    fun reader(id: String, page: Int = -1) = "reader/$id?page=$page"
}

@Composable
fun AppRoot(graph: AppGraph, settings: AppSettings) {
    val navController = rememberNavController()
    val startDestination = remember { if (settings.isServerConfigured) Routes.HOME else Routes.SETUP }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SETUP) {
            ServerSetupScreen(
                graph = graph,
                onDone = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.SETUP) { inclusive = true } }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                graph = graph,
                onOpenArchive = { id -> navController.navigate(Routes.archive(id)) },
                onOpenReader = { id, page -> navController.navigate(Routes.reader(id, page)) },
            )
        }
        composable(
            Routes.ARCHIVE,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ArchiveDetailScreen(
                graph = graph,
                id = id,
                onBack = { navController.popBackStack() },
                onOpenReader = { page -> navController.navigate(Routes.reader(id, page)) },
                onOpenArchive = { other -> navController.navigate(Routes.archive(other)) },
                onSearchTag = { tag ->
                    graph.pendingLibrarySearch.value = tag
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
        composable(
            Routes.READER,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("page") { type = NavType.IntType; defaultValue = -1 },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val page = entry.arguments?.getInt("page") ?: -1
            ReaderScreen(graph = graph, arcId = id, startPage = page, onBack = { navController.popBackStack() })
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
