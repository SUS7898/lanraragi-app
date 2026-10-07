package com.sus7898.lrrviewer.data

import com.sus7898.lrrviewer.data.api.Category
import com.sus7898.lrrviewer.data.api.LrrApi
import com.sus7898.lrrviewer.data.api.LrrException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * "좋아요" = LANraragi's bookmark feature: a static category linked via `/api/categories/bookmark_link`.
 * Membership lives on the server, so the phone and the tablet see the same hearts and the web UI shows
 * them as a category. Everything that writes needs the API key.
 */
class FavoritesRepository(
    private val api: LrrApi,
    private val settings: StateFlow<AppSettings>,
) {
    data class State(
        /** Linked category id; null when the server has no bookmark link yet (one is created on first toggle). */
        val categoryId: String? = null,
        val ids: Set<String> = emptySet(),
        /** False when the server predates the bookmark API (404). */
        val supported: Boolean = true,
        val loaded: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val mutex = Mutex()

    val canWrite: Boolean get() = settings.value.apiKey.isNotBlank() && _state.value.supported

    fun reset() { _state.value = State() }

    /** Re-reads the bookmark link and its member list. [categories] avoids a second `/api/categories` call. */
    suspend fun refresh(categories: List<Category>? = null) {
        val linkId = try {
            api.bookmarkCategoryId()
        } catch (e: LrrException.HttpError) {
            if (e.code == 404) _state.update { it.copy(supported = false, loaded = true) }
            return
        } catch (_: Exception) {
            return
        }
        val cats = categories ?: runCatching { api.categories() }.getOrNull() ?: return
        val linked = cats.firstOrNull { it.id == linkId }
        _state.value = State(categoryId = linkId, ids = linked?.archives?.toSet().orEmpty(), supported = true, loaded = true)
    }

    fun isFavorite(arcid: String): Boolean = arcid in _state.value.ids

    /** 🔑 Creates and links a bookmark category when the server has none, then returns its id. */
    private suspend fun ensureCategory(): String {
        _state.value.categoryId?.let { return it }
        val id = api.createCategory(DEFAULT_CATEGORY_NAME, pinned = true)
        api.setBookmarkCategory(id)
        _state.update { it.copy(categoryId = id, supported = true, loaded = true) }
        return id
    }

    /** 🔑 Adds or removes [arcid]; returns the new favourite state. */
    suspend fun toggle(arcid: String): Boolean = mutex.withLock {
        val categoryId = ensureCategory()
        val wasFavorite = arcid in _state.value.ids
        if (wasFavorite) api.removeFromCategory(categoryId, arcid) else api.addToCategory(categoryId, arcid)
        _state.update { s -> s.copy(ids = if (wasFavorite) s.ids - arcid else s.ids + arcid) }
        !wasFavorite
    }

    companion object {
        const val DEFAULT_CATEGORY_NAME = "즐겨찾기"
    }
}
