package com.sus7898.lrrviewer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

@Serializable
data class HistoryEntry(
    val arcid: String,
    val title: String,
    /** 0-based page index of the last page shown. */
    val page: Int,
    val pageCount: Int,
    val lastReadAt: Long,
) {
    val isCompleted: Boolean get() = pageCount > 0 && page >= pageCount - 1
}

private val Context.historyStore: DataStore<Preferences> by preferencesDataStore(name = "reading_history")

/** Local reading history / progress, independent of the server's optional progress tracking. */
class ReadingHistoryRepository(private val context: Context) {

    private val key = stringPreferencesKey("entries")
    private val json = Json { ignoreUnknownKeys = true }
    private val maxEntries = 300

    val flow: Flow<List<HistoryEntry>> = context.historyStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { decode(it[key]) }

    suspend fun get(arcid: String): HistoryEntry? = flow.first().firstOrNull { it.arcid == arcid }

    suspend fun record(arcid: String, title: String, page: Int, pageCount: Int) {
        val entry = HistoryEntry(arcid, title, page, pageCount, System.currentTimeMillis())
        edit { list -> (listOf(entry) + list.filter { it.arcid != arcid }).take(maxEntries) }
    }

    suspend fun remove(arcid: String) = edit { list -> list.filter { it.arcid != arcid } }

    suspend fun clear() = edit { emptyList() }

    private suspend fun edit(transform: (List<HistoryEntry>) -> List<HistoryEntry>) {
        context.historyStore.edit { prefs ->
            prefs[key] = json.encodeToString(transform(decode(prefs[key])))
        }
    }

    private fun decode(raw: String?): List<HistoryEntry> =
        raw?.let { runCatching { json.decodeFromString<List<HistoryEntry>>(it) }.getOrNull() } ?: emptyList()
}
