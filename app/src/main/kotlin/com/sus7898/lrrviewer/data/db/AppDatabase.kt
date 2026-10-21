package com.sus7898.lrrviewer.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Identifier of the (currently single) configured server. Kept in every row so multiple server profiles can be added later without a data migration. */
const val DEFAULT_SERVER_ID = "default"

@Entity(tableName = "reading_progress", primaryKeys = ["serverId", "arcid"])
data class ReadingProgressEntity(
    val serverId: String,
    val arcid: String,
    val title: String,
    /** 0-based index of the last page shown. */
    val page: Int,
    val pageCount: Int,
    val lastReadAt: Long,
    /** Per-archive override of the reading mode (name of [com.sus7898.lrrviewer.data.ReadingMode]), null = use the global default. */
    val readingModeOverride: String? = null,
)

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE serverId = :serverId ORDER BY lastReadAt DESC LIMIT :limit")
    fun observeRecent(serverId: String, limit: Int): Flow<List<ReadingProgressEntity>>

    @Query("SELECT * FROM reading_progress WHERE serverId = :serverId")
    suspend fun all(serverId: String): List<ReadingProgressEntity>

    @Query("SELECT * FROM reading_progress WHERE serverId = :serverId AND arcid = :arcid")
    suspend fun get(serverId: String, arcid: String): ReadingProgressEntity?

    @Upsert
    suspend fun upsert(entity: ReadingProgressEntity)

    @Query("UPDATE reading_progress SET readingModeOverride = :mode WHERE serverId = :serverId AND arcid = :arcid")
    suspend fun setReadingModeOverride(serverId: String, arcid: String, mode: String?)

    @Query("DELETE FROM reading_progress WHERE serverId = :serverId AND arcid = :arcid")
    suspend fun delete(serverId: String, arcid: String)

    @Query("DELETE FROM reading_progress WHERE serverId = :serverId")
    suspend fun deleteAll(serverId: String)

    @Query("UPDATE reading_progress SET title = CASE WHEN :title = '' THEN title ELSE :title END, page = :page, pageCount = :pageCount, lastReadAt = :at WHERE serverId = :serverId AND arcid = :arcid")
    suspend fun updatePosition(serverId: String, arcid: String, title: String, page: Int, pageCount: Int, at: Long): Int

    /**
     * Position update that never rewrites `readingModeOverride` (an UPDATE when the row exists), so a progress save
     * racing a reading-mode change cannot drop the override.
     */
    @Transaction
    suspend fun recordProgress(serverId: String, arcid: String, title: String, page: Int, pageCount: Int, at: Long) {
        if (updatePosition(serverId, arcid, title, page, pageCount, at) == 0) {
            upsert(ReadingProgressEntity(serverId, arcid, title.ifBlank { arcid }, page, pageCount, at))
        }
    }

    /** Sync import: keeps whichever of the stored and incoming row was read last. */
    @Transaction
    suspend fun upsertIfNewer(entity: ReadingProgressEntity) {
        val existing = get(entity.serverId, entity.arcid)
        if (existing == null || existing.lastReadAt < entity.lastReadAt) upsert(entity)
    }

    // --- deletion tombstones (sync) ---

    @Upsert
    suspend fun upsertTombstones(rows: List<HistoryTombstoneEntity>)

    @Query("SELECT * FROM history_tombstone WHERE serverId = :serverId")
    suspend fun tombstones(serverId: String): List<HistoryTombstoneEntity>

    @Query("DELETE FROM history_tombstone WHERE serverId = :serverId AND deletedAt < :before")
    suspend fun pruneTombstones(serverId: String, before: Long)

    @Query("DELETE FROM reading_progress WHERE serverId = :serverId AND arcid IN (:arcids)")
    suspend fun deleteMany(serverId: String, arcids: List<String>)

    @Transaction
    suspend fun deleteWithTombstone(serverId: String, arcids: List<String>, at: Long) {
        if (arcids.isEmpty()) return
        arcids.chunked(500).forEach { deleteMany(serverId, it) }
        upsertTombstones(arcids.map { HistoryTombstoneEntity(serverId, it, at) })
    }

    /** A deletion from the other device: drop the row unless it was read again after [deletedAt]; keep the tombstone. */
    @Transaction
    suspend fun applyTombstone(serverId: String, arcid: String, deletedAt: Long) {
        val existing = get(serverId, arcid)
        if (existing != null && existing.lastReadAt <= deletedAt) delete(serverId, arcid)
        upsertTombstones(listOf(HistoryTombstoneEntity(serverId, arcid, deletedAt)))
    }
}

/** "This history row was deleted at [deletedAt]": lets the sync file remove it on the other device too. */
@Entity(tableName = "history_tombstone", primaryKeys = ["serverId", "arcid"])
data class HistoryTombstoneEntity(
    val serverId: String,
    val arcid: String,
    val deletedAt: Long,
)

/** User-defined display order of server categories (local only; the server has no category ordering). */
@Entity(tableName = "category_order", primaryKeys = ["serverId", "categoryId"])
data class CategoryOrderEntity(
    val serverId: String,
    val categoryId: String,
    val position: Int,
)

@Dao
interface CategoryOrderDao {
    @Query("SELECT categoryId FROM category_order WHERE serverId = :serverId ORDER BY position ASC")
    fun observeOrder(serverId: String): Flow<List<String>>

    @Insert
    suspend fun insertAll(rows: List<CategoryOrderEntity>)

    @Query("DELETE FROM category_order WHERE serverId = :serverId")
    suspend fun deleteAll(serverId: String)

    @Transaction
    suspend fun replace(serverId: String, categoryIds: List<String>) {
        deleteAll(serverId)
        insertAll(categoryIds.mapIndexed { i, id -> CategoryOrderEntity(serverId, id, i) })
    }
}

/**
 * Pixel dimensions of archive pages, read once from the downloaded file. Lets the webtoon reader lay out
 * (and tile) pages before they are decoded, and is the basis for spreads / reading-mode detection.
 */
@Entity(tableName = "page_info", primaryKeys = ["serverId", "arcid", "pageIndex"])
data class PageInfoEntity(
    val serverId: String,
    val arcid: String,
    val pageIndex: Int,
    val width: Int,
    val height: Int,
)

@Dao
interface PageInfoDao {
    @Query("SELECT * FROM page_info WHERE serverId = :serverId AND arcid = :arcid")
    suspend fun forArchive(serverId: String, arcid: String): List<PageInfoEntity>

    @Query("SELECT * FROM page_info WHERE serverId = :serverId AND arcid = :arcid AND pageIndex = :pageIndex")
    suspend fun get(serverId: String, arcid: String, pageIndex: Int): PageInfoEntity?

    @Upsert
    suspend fun upsert(entity: PageInfoEntity)

    @Query("DELETE FROM page_info WHERE serverId = :serverId AND arcid = :arcid")
    suspend fun deleteArchive(serverId: String, arcid: String)
}

@Database(
    entities = [ReadingProgressEntity::class, CategoryOrderEntity::class, PageInfoEntity::class, HistoryTombstoneEntity::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun categoryOrderDao(): CategoryOrderDao
    abstract fun pageInfoDao(): PageInfoDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "lrrviewer.db").build()
    }
}
