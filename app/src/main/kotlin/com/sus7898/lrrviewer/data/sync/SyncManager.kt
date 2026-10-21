package com.sus7898.lrrviewer.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import com.sus7898.lrrviewer.data.CategoryOrderRepository
import com.sus7898.lrrviewer.data.ReadingMode
import com.sus7898.lrrviewer.data.ReadingProgress
import com.sus7898.lrrviewer.data.ReadingProgressRepository
import com.sus7898.lrrviewer.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Device-to-device sync through one JSON file the user picks with the system file picker (Storage Access
 * Framework), e.g. in a Synology Drive / Google Drive / Nextcloud folder that both devices see.
 *
 * `sync()` = read the file → merge into the local data (newer side wins) → write the merged snapshot back.
 * It runs when the app starts, when it goes to the background, and on demand. The same snapshot format is
 * used for one-off backup export / import. No server, no account, nothing leaves the device except through
 * the document provider the user chose.
 */
class SyncManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val progress: ReadingProgressRepository,
    private val categoryOrder: CategoryOrderRepository,
) {
    sealed interface Status {
        data object Idle : Status
        data object Running : Status
        data class Done(val at: Long, val message: String) : Status
        data class Failed(val message: String) : Status
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; encodeDefaults = true; prettyPrint = true }
    private val mutex = Mutex()
    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    // ------------------------------------------------------------------ snapshot

    suspend fun snapshot(): SyncSnapshot {
        val s = settings.current()
        return SyncSnapshot(
            exportedAt = System.currentTimeMillis(),
            device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            server = SyncMerge.serverId(s.serverUrl),
            settings = SyncedSettings.from(s),
            settingsChangedAt = s.settingsChangedAt,
            categoryOrder = categoryOrder.current(),
            categoryOrderChangedAt = s.categoryOrderChangedAt,
            recentSearches = s.recentSearches,
            searchesChangedAt = s.searchesChangedAt,
            progress = progress.all().map {
                SyncedProgress(it.arcid, it.title, it.page, it.pageCount, it.lastReadAt, it.readingModeOverride?.name)
            },
            deletedProgress = progress.tombstones(),
        )
    }

    fun encode(snapshot: SyncSnapshot): String = json.encodeToString(SyncSnapshot.serializer(), snapshot)

    fun decode(text: String): SyncSnapshot {
        val snap = json.decodeFromString(SyncSnapshot.serializer(), text)
        if (snap.app != SyncSnapshot.APP_ID) throw IOException("LRR Viewer 동기화/백업 파일이 아닙니다.")
        if (snap.format > SyncSnapshot.FORMAT) throw IOException("더 새 버전의 앱에서 만든 파일입니다. 앱을 업데이트하세요.")
        return snap
    }

    /** Merges [remote] into the local data and returns what changed. */
    suspend fun merge(remote: SyncSnapshot): MergeResult {
        val local = snapshot()
        val (result, merged) = SyncMerge.plan(local, remote)
        if (result.settingsApplied || result.searchesApplied || merged.settingsChangedAt != local.settingsChangedAt ||
            merged.searchesChangedAt != local.searchesChangedAt
        ) {
            settings.edit { s ->
                val base = if (result.settingsApplied) merged.settings.applyTo(s) else s
                base.copy(
                    recentSearches = merged.recentSearches,
                    settingsChangedAt = merged.settingsChangedAt,
                    searchesChangedAt = merged.searchesChangedAt,
                )
            }
        }
        if (result.categoryOrderApplied) {
            categoryOrder.save(merged.categoryOrder)
            settings.edit { it.copy(categoryOrderChangedAt = merged.categoryOrderChangedAt) }
        }
        // Deletions first (each one is a no-op if the row was read again after it), then newer rows from the file.
        merged.deletedProgress.forEach { (arcid, at) ->
            if (local.deletedProgress[arcid] != at) progress.applyTombstone(arcid, at)
        }
        if (result.progressUpdated > 0) {
            val localIds = local.progress.associateBy { it.arcid }
            merged.progress.filter { m -> localIds[m.arcid]?.lastReadAt != m.lastReadAt }.forEach { r ->
                progress.putIfNewer(
                    ReadingProgress(
                        arcid = r.arcid,
                        title = r.title,
                        page = r.page,
                        pageCount = r.pageCount,
                        lastReadAt = r.lastReadAt,
                        readingModeOverride = r.readingModeOverride?.let { n -> ReadingMode.entries.firstOrNull { it.name == n } },
                    ),
                )
            }
        }
        return result
    }

    // ------------------------------------------------------------------ files (SAF)

    private suspend fun readText(uri: Uri): String? = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        // "wt" truncates; a few providers only accept "w".
        val out = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("파일에 쓸 수 없습니다.")
        out.use { it.write(bytes) }
    }

    /** One-off backup to a file the user just created. */
    suspend fun exportTo(uri: Uri): String = runReporting {
        writeText(uri, encode(snapshot()))
        "백업 파일을 저장했습니다."
    }

    /** One-off restore/merge from a file the user picked. */
    suspend fun importFrom(uri: Uri): String = runReporting {
        val text = readText(uri) ?: throw IOException("파일을 열 수 없습니다.")
        "가져오기 완료: " + merge(decode(text)).describe()
    }

    /**
     * Links [uri] as the sync file (persisting read/write access) and syncs immediately. A freshly created,
     * empty file simply receives this device's data.
     */
    suspend fun link(uri: Uri): String {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        settings.edit { it.copy(syncFileUri = uri.toString()) }
        return syncNow()
    }

    suspend fun unlink() {
        val current = settings.current().syncFileUri
        if (current.isNotBlank()) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    current.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        settings.edit { it.copy(syncFileUri = "", lastSyncAt = 0L) }
        _status.value = Status.Idle
    }

    /** Read → merge → write back on the linked file. Returns a user-facing message. */
    suspend fun syncNow(): String = runReporting {
        val uriString = settings.current().syncFileUri
        if (uriString.isBlank()) throw IOException("동기화 파일이 연결되어 있지 않습니다.")
        val uri = uriString.toUri()
        val text = readText(uri)
        val result = if (text.isNullOrBlank()) MergeResult() else merge(decode(text))
        writeText(uri, encode(snapshot()))
        val now = System.currentTimeMillis()
        settings.edit { it.copy(lastSyncAt = now) }
        "동기화 완료: " + result.describe()
    }

    /** Lifecycle-triggered sync: silent no-op when nothing is linked, never throws. */
    suspend fun syncIfLinked() {
        if (!settings.current().isSyncLinked || _status.value is Status.Running) return
        runCatching { syncNow() }
    }

    private suspend fun runReporting(block: suspend () -> String): String = mutex.withLock {
        _status.value = Status.Running
        try {
            val message = block()
            _status.value = Status.Done(System.currentTimeMillis(), message)
            message
        } catch (e: Exception) {
            val message = when (e) {
                is SecurityException -> "파일 접근 권한이 없어졌습니다. 동기화 파일을 다시 연결하세요."
                is kotlinx.serialization.SerializationException -> "파일 내용을 읽지 못했습니다 (손상되었거나 다른 형식)."
                else -> e.message ?: e.toString()
            }
            _status.value = Status.Failed(message)
            throw IOException(message, e)
        }
    }
}
