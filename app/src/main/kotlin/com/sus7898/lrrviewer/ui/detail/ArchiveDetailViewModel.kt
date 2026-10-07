package com.sus7898.lrrviewer.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.data.FavoritesRepository
import com.sus7898.lrrviewer.data.ReadingProgressRepository
import com.sus7898.lrrviewer.data.api.Archive
import com.sus7898.lrrviewer.data.api.LrrApi
import com.sus7898.lrrviewer.data.api.TankoubonFull
import com.sus7898.lrrviewer.data.api.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArchiveDetailViewModel(
    private val api: LrrApi,
    private val progress: ReadingProgressRepository,
    private val favorites: FavoritesRepository,
    private val settings: StateFlow<AppSettings>,
    val id: String,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val archive: Archive? = null,
        val tank: TankoubonFull? = null,
        val tankArchives: List<Archive> = emptyList(),
        /** 0-based page from the local reading history, if any. */
        val localPage: Int? = null,
        val busy: Boolean = false,
        val message: String? = null,
        val favorite: Boolean = false,
        /** Server supports bookmarks (false on servers without `/api/categories/bookmark_link`). */
        val favoritesSupported: Boolean = true,
        /** Rating and favourite writes need the API key. */
        val canEdit: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState(canEdit = settings.value.apiKey.isNotBlank()))
    val state = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            favorites.state.collect { f -> _state.update { it.copy(favorite = id in f.ids, favoritesSupported = f.supported) } }
        }
        viewModelScope.launch {
            settings.collect { s -> _state.update { it.copy(canEdit = s.apiKey.isNotBlank()) } }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                if (id.startsWith("TANK_")) {
                    val (tank, archives) = api.tankoubon(id)
                    _state.update { it.copy(loading = false, tank = tank, tankArchives = archives) }
                } else {
                    val archive = api.metadata(id)
                    val local = progress.get(id)
                    _state.update { it.copy(loading = false, archive = archive, localPage = local?.page) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    /** 0-based page to resume from, or null when there is nothing to resume (unread or finished). */
    fun resumePage(): Int? {
        val s = _state.value
        val a = s.archive ?: return null
        val candidate = maxOf(s.localPage ?: -1, a.progress - 1)
        if (candidate <= 0) return null
        if (a.pagecount > 0 && candidate >= a.pagecount - 1) return null
        return candidate
    }

    fun toggleNew() {
        val a = _state.value.archive ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { if (a.isnew) api.clearNew(id) else api.setNew(id) }
                .onFailure { e -> _state.update { it.copy(message = e.userMessage()) } }
            _state.update { it.copy(busy = false) }
            load()
        }
    }

    fun forceReextract() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { api.files(id, force = true) }
                .onSuccess { _state.update { it.copy(message = "서버에 재추출을 요청했습니다.") } }
                .onFailure { e -> _state.update { it.copy(message = e.userMessage()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    /** 🔑 Adds/removes this archive in the server's bookmark category. */
    fun toggleFavorite() {
        if (!favorites.canWrite) {
            _state.update { it.copy(message = NEEDS_KEY) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { favorites.toggle(id) }
                .onFailure { e -> _state.update { it.copy(message = "즐겨찾기 변경 실패: ${e.userMessage()}") } }
            _state.update { it.copy(busy = false) }
        }
    }

    /** 🔑 Stores [rating] (1..5, or null to clear) as a `rating:N` tag; title and summary are re-sent unchanged. */
    fun setRating(rating: Int?) {
        val a = _state.value.archive ?: return
        if (!_state.value.canEdit) {
            _state.update { it.copy(message = NEEDS_KEY) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching {
                val fresh = api.metadata(id) // never overwrite tags edited elsewhere with a stale copy
                api.updateMetadata(id, fresh.title, Archive.tagsWithRating(fresh.tags, rating), fresh.summary.orEmpty())
            }.onFailure { e -> _state.update { it.copy(message = "평점 저장 실패: ${e.userMessage()}") } }
            _state.update { it.copy(busy = false) }
            if (a.rating != rating) load()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private companion object {
        const val NEEDS_KEY = "평점과 즐겨찾기는 서버에 저장되므로 설정에서 API 키를 입력해야 합니다."
    }
}
