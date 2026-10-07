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

class ReadingProgressRepository(
    private val dao: ReadingProgressDao,
    private val serverId: String = DEFAULT_SERVER_ID,
) {
    val recent: Flow<List<ReadingProgress>> = dao.observeRecent(serverId, limit = 300).map { rows -> rows.map { it.toModel() } }

    suspend fun get(arcid: String): ReadingProgress? = dao.get(serverId, arcid)?.toModel()

    suspend fun record(arcid: String, title: String, page: Int, pageCount: Int) {
        val existing = dao.get(serverId, arcid)
        dao.upsert(
            ReadingProgressEntity(
                serverId = serverId,
                arcid = arcid,
                title = title.ifBlank { existing?.title ?: arcid },
                page = page,
                pageCount = pageCount,
                lastReadAt = System.currentTimeMillis(),
                readingModeOverride = existing?.readingModeOverride,
            ),
        )
    }

    suspend fun setReadingModeOverride(arcid: String, mode: ReadingMode?) =
        dao.setReadingModeOverride(serverId, arcid, mode?.name)

    suspend fun remove(arcid: String) = dao.delete(serverId, arcid)

    suspend fun clear() = dao.deleteAll(serverId)

    private fun ReadingProgressEntity.toModel() = ReadingProgress(
        arcid = arcid,
        title = title,
        page = page,
        pageCount = pageCount,
        lastReadAt = lastReadAt,
        readingModeOverride = readingModeOverride?.let { name -> ReadingMode.entries.firstOrNull { it.name == name } },
    )
}
