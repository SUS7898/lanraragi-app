package com.sus7898.lrrviewer.data

import com.sus7898.lrrviewer.data.db.CategoryOrderDao
import com.sus7898.lrrviewer.data.db.DEFAULT_SERVER_ID
import kotlinx.coroutines.flow.Flow

/** Manual category order of one server (list of category ids, first = top). */
class CategoryOrderRepository(
    private val dao: CategoryOrderDao,
    private val serverId: String = DEFAULT_SERVER_ID,
) {
    val order: Flow<List<String>> = dao.observeOrder(serverId)

    suspend fun save(categoryIds: List<String>) = dao.replace(serverId, categoryIds)

    suspend fun clear() = dao.deleteAll(serverId)
}
