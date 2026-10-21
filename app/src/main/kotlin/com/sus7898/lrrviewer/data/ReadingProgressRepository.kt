package com.sus7898.lrrviewer.data

import com.sus7898.lrrviewer.data.db.DEFAULT_SERVER_ID
import com.sus7898.lrrviewer.data.db.ReadingProgressDao
import com.sus7898.lrrviewer.data.db.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Local reading progress / history of one server, independent of the server's optional progress tracking. */
data class ReadingProgress(
    val arcid: String,
    val title: String,
    /** 0-based page index of the last page shown. */
    val page: Int,
    val pageCount: Int,
    val lastReadAt: Long,
    val readingModeOverride: ReadingMode? = null,
) {
    val isCompleted: Boolean get() = pageCount > 0 && page >= pageCount - 1
}

/** History deletions are remembered this long so a linked sync file can carry them to the other device. */
const val TOMBSTONE_TTL_MS = 90L * 24 * 60 * 60 * 1000

class ReadingProgressRepository(
    private val dao: ReadingProgressDao,
    private val serverId: String = DEFAULT_SERVER_ID,
) {
    val recent: Flow<List<ReadingProgress>> = dao.observeRecent(serverId, limit = 300).map { rows -> rows.map { it.toModel() } }

    suspend fun get(arcid: String): ReadingProgress? = dao.get(serverId, arcid)?.toModel()

    /** Updates position and time in one transaction; never touches the per-archive reading mode. */
    suspend fun record(arcid: String, title: String, page: Int, pageCount: Int) =
        dao.recordProgress(serverId, arcid, title, page, pageCount, System.currentTimeMillis())

    suspend fun setReadingModeOverride(arcid: String, mode: ReadingMode?) =
        dao.setReadingModeOverride(serverId, arcid, mode?.name)

    /** Deletes one row and leaves a tombstone so sync removes it on the other device too. */
    suspend fun remove(arcid: String) = dao.deleteWithTombstone(serverId, listOf(arcid), System.currentTimeMillis())

    suspend fun clear() = dao.deleteWithTombstone(serverId, dao.all(serverId).map { it.arcid }, System.currentTimeMillis())

    // ------------------------------------------------------------------ sync

    /** Every row of this server (sync export). */
    suspend fun all(): List<ReadingProgress> = dao.all(serverId).map { it.toModel() }

    /** arcid → deletion time, after dropping tombstones older than [TOMBSTONE_TTL_MS]. */
    suspend fun tombstones(now: Long = System.currentTimeMillis()): Map<String, Long> {
        dao.pruneTombstones(serverId, now - TOMBSTONE_TTL_MS)
        return dao.tombstones(serverId).associate { it.arcid to it.deletedAt }
    }

    /** Sync import: writes [p] only when it is newer than the local row (checked inside one transaction). */
    suspend fun putIfNewer(p: ReadingProgress) = dao.upsertIfNewer(
        ReadingProgressEntity(
            serverId = serverId,
            arcid = p.arcid,
            title = p.title,
            page = p.page,
            pageCount = p.pageCount,
            lastReadAt = p.lastReadAt,
            readingModeOverride = p.readingModeOverride?.name,
        ),
    )

    /** Sync import: a deletion made on the other device — drops the row unless it was read again afterwards. */
    suspend fun applyTombstone(arcid: String, deletedAt: Long) = dao.applyTombstone(serverId, arcid, deletedAt)

    private fun ReadingProgressEntity.toModel() = ReadingProgress(
        arcid = arcid,
        title = title,
        page = page,
        pageCount = pageCount,
        lastReadAt = lastReadAt,
        readingModeOverride = readingModeOverride?.let { name -> ReadingMode.entries.firstOrNull { it.name == name } },
    )
}

