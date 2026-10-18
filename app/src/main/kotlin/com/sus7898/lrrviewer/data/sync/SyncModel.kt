package com.sus7898.lrrviewer.data.sync

import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.data.CategorySort
import com.sus7898.lrrviewer.data.ColorFilterMode
import com.sus7898.lrrviewer.data.FitMode
import com.sus7898.lrrviewer.data.MAX_RECENT_SEARCHES
import com.sus7898.lrrviewer.data.ReaderBackground
import com.sus7898.lrrviewer.data.ReadingMode
import com.sus7898.lrrviewer.data.ThemeMode
import kotlinx.serialization.Serializable

/**
 * The settings that travel between devices. Deliberately excluded: server URL and API key (secret; a phone
 * may use the LAN address and a tablet the HTTPS one), cache size, grid width and brightness (per device),
 * update/sync bookkeeping.
 */
@Serializable
data class SyncedSettings(
    val readingMode: String = ReadingMode.RTL.name,
    val fitMode: String = FitMode.FIT.name,
    val background: String = ReaderBackground.BLACK.name,
    val tapNavigation: Boolean = true,
    val volumeKeyNavigation: Boolean = true,
    val keepScreenOn: Boolean = true,
    val showPageNumber: Boolean = true,
    val prefetchPages: Int = 3,
    val syncProgress: Boolean = true,
    val clearNewOnRead: Boolean = true,
    val themeMode: String = ThemeMode.SYSTEM.name,
    val groupByTankoubon: Boolean = true,
    val categorySort: String = CategorySort.NAME.name,
    val spreadInLandscape: Boolean = true,
    val spreadCoverAlone: Boolean = true,
    val colorFilter: String = ColorFilterMode.NONE.name,
    val animatePageTurn: Boolean = true,
    val showContinueReading: Boolean = true,
    val allowCleartext: Boolean = true,
    val autoCheckUpdates: Boolean = true,
) {
    fun applyTo(s: AppSettings): AppSettings = s.copy(
        readingMode = enumOr(readingMode, s.readingMode),
        fitMode = enumOr(fitMode, s.fitMode),
        background = enumOr(background, s.background),
        tapNavigation = tapNavigation,
        volumeKeyNavigation = volumeKeyNavigation,
        keepScreenOn = keepScreenOn,
        showPageNumber = showPageNumber,
        prefetchPages = prefetchPages.coerceIn(0, 10),
        syncProgress = syncProgress,
        clearNewOnRead = clearNewOnRead,
        themeMode = enumOr(themeMode, s.themeMode),
        groupByTankoubon = groupByTankoubon,
        categorySort = enumOr(categorySort, s.categorySort),
        spreadInLandscape = spreadInLandscape,
        spreadCoverAlone = spreadCoverAlone,
        colorFilter = enumOr(colorFilter, s.colorFilter),
        animatePageTurn = animatePageTurn,
        showContinueReading = showContinueReading,
        allowCleartext = allowCleartext,
        autoCheckUpdates = autoCheckUpdates,
    )

    companion object {
        fun from(s: AppSettings) = SyncedSettings(
            readingMode = s.readingMode.name,
            fitMode = s.fitMode.name,
            background = s.background.name,
            tapNavigation = s.tapNavigation,
            volumeKeyNavigation = s.volumeKeyNavigation,
            keepScreenOn = s.keepScreenOn,
            showPageNumber = s.showPageNumber,
            prefetchPages = s.prefetchPages,
            syncProgress = s.syncProgress,
            clearNewOnRead = s.clearNewOnRead,
            themeMode = s.themeMode.name,
            groupByTankoubon = s.groupByTankoubon,
            categorySort = s.categorySort.name,
            spreadInLandscape = s.spreadInLandscape,
            spreadCoverAlone = s.spreadCoverAlone,
            colorFilter = s.colorFilter.name,
            animatePageTurn = s.animatePageTurn,
            showContinueReading = s.showContinueReading,
            allowCleartext = s.allowCleartext,
            autoCheckUpdates = s.autoCheckUpdates,
        )

        private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: fallback
    }
}

/** One reading-history row (local DB `reading_progress`), including the per-archive reading mode. */
@Serializable
data class SyncedProgress(
    val arcid: String,
    val title: String = "",
    val page: Int = 0,
    val pageCount: Int = 0,
    val lastReadAt: Long = 0L,
    val readingModeOverride: String? = null,
)

/**
 * Contents of the sync / backup file. Ratings and favourites are not here: they already live on the
 * LANraragi server (`rating:N` tags, bookmark category) and are shared by every device that uses it.
 */
@Serializable
data class SyncSnapshot(
    val app: String = APP_ID,
    val format: Int = FORMAT,
    val exportedAt: Long = 0L,
    val device: String = "",
    /** Identifies the server the history belongs to (normalised host[:port][/path]); history of another server is not merged. */
    val server: String = "",
    val settings: SyncedSettings = SyncedSettings(),
    val settingsChangedAt: Long = 0L,
    val categoryOrder: List<String> = emptyList(),
    val categoryOrderChangedAt: Long = 0L,
    val recentSearches: List<String> = emptyList(),
    val progress: List<SyncedProgress> = emptyList(),
) {
    companion object {
        const val APP_ID = "lrrviewer"
        const val FORMAT = 1
    }
}

/** What a merge changed locally (for the UI message). */
data class MergeResult(
    val settingsApplied: Boolean = false,
    val categoryOrderApplied: Boolean = false,
    val progressUpdated: Int = 0,
    val searchesAdded: Int = 0,
    val skippedOtherServer: Boolean = false,
) {
    val changedAnything: Boolean get() = settingsApplied || categoryOrderApplied || progressUpdated > 0 || searchesAdded > 0

    fun describe(): String = buildList {
        if (settingsApplied) add("설정")
        if (categoryOrderApplied) add("카테고리 순서")
        if (progressUpdated > 0) add("읽기 기록 ${progressUpdated}건")
        if (searchesAdded > 0) add("검색 기록 ${searchesAdded}건")
    }.joinToString(", ").ifEmpty { "변경 없음" } + if (skippedOtherServer) " (다른 서버의 기록은 건너뜀)" else ""
}

/** Pure merge rules, kept free of Android types so they are unit-tested. */
object SyncMerge {
    /** Newer side wins for settings and category order; each history row keeps the newer lastReadAt. */
    fun plan(
        local: SyncSnapshot,
        remote: SyncSnapshot,
    ): Pair<MergeResult, SyncSnapshot> {
        val sameServer = remote.server.isBlank() || local.server.isBlank() || remote.server == local.server
        val settingsWin = remote.settingsChangedAt > local.settingsChangedAt
        val orderWin = remote.categoryOrderChangedAt > local.categoryOrderChangedAt && sameServer

        val localRows = local.progress.associateBy { it.arcid }
        val newerRemoteRows = if (!sameServer) emptyList() else remote.progress.filter { r ->
            val l = localRows[r.arcid]
            l == null || r.lastReadAt > l.lastReadAt
        }
        val mergedRows = (localRows + newerRemoteRows.associateBy { it.arcid }).values.sortedByDescending { it.lastReadAt }

        val searches = (local.recentSearches + remote.recentSearches).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_RECENT_SEARCHES)
        val added = searches.count { it !in local.recentSearches }

        val merged = local.copy(
            settings = if (settingsWin) remote.settings else local.settings,
            settingsChangedAt = maxOf(local.settingsChangedAt, remote.settingsChangedAt),
            categoryOrder = if (orderWin) remote.categoryOrder else local.categoryOrder,
            categoryOrderChangedAt = if (orderWin) remote.categoryOrderChangedAt else local.categoryOrderChangedAt,
            recentSearches = searches,
            progress = mergedRows,
        )
        val result = MergeResult(
            settingsApplied = settingsWin && remote.settings != local.settings,
            categoryOrderApplied = orderWin && remote.categoryOrder != local.categoryOrder,
            progressUpdated = newerRemoteRows.size,
            searchesAdded = added,
            skippedOtherServer = !sameServer && remote.progress.isNotEmpty(),
        )
        return result to merged
    }

    /** Server identity used to avoid mixing histories of different LANraragi instances: host + port + path, scheme-agnostic. */
    fun serverId(serverUrl: String): String =
        serverUrl.trim().substringAfter("://").trimEnd('/').lowercase()
}
