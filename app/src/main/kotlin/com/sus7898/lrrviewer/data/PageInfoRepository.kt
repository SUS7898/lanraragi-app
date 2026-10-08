package com.sus7898.lrrviewer.data

import com.sus7898.lrrviewer.data.db.DEFAULT_SERVER_ID
import com.sus7898.lrrviewer.data.db.PageInfoDao
import com.sus7898.lrrviewer.data.db.PageInfoEntity

/** Page dimensions of one server's archives: Room cache in front of [PageImageStore.bounds]. */
class PageInfoRepository(
    private val dao: PageInfoDao,
    private val store: PageImageStore,
    private val serverId: String = DEFAULT_SERVER_ID,
) {
    suspend fun cached(arcid: String): Map<Int, PageSize> =
        dao.forArchive(serverId, arcid).associate { it.pageIndex to PageSize(it.width, it.height) }

    /** Dimensions of page [index] ([url]); reads the file (downloading it if needed) on a cache miss. */
    suspend fun size(arcid: String, index: Int, url: String): PageSize? {
        dao.get(serverId, arcid, index)?.let { return PageSize(it.width, it.height) }
        val size = store.bounds(url) ?: return null
        dao.upsert(PageInfoEntity(serverId, arcid, index, size.width, size.height))
        return size
    }

    /** Call when the server re-extracts an archive: its page list (and sizes) may change. */
    suspend fun clear(arcid: String) = dao.deleteArchive(serverId, arcid)
}
