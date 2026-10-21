package com.sus7898.lrrviewer.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.data.FavoritesRepository
import com.sus7898.lrrviewer.data.api.Archive
import com.sus7898.lrrviewer.data.api.Category
import com.sus7898.lrrviewer.data.api.LrrApi
import com.sus7898.lrrviewer.data.api.SearchQuery
import com.sus7898.lrrviewer.data.api.ServerInfo
import com.sus7898.lrrviewer.data.api.TagStat
import com.sus7898.lrrviewer.data.api.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val api: LrrApi,
    private val settings: StateFlow<AppSettings>,
    private val favorites: FavoritesRepository,
    /** Persists a submitted search in the recent-search list (SettingsRepository.recordSearch). */
    private val recordSearch: suspend (String) -> Unit = {},
) : ViewModel() {

    data class UiState(
        val items: List<Archive> = emptyList(),
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val endReached: Boolean = false,
        val error: String? = null,
        val message: String? = null,
        val filtered: Int = 0,
        val total: Int = 0,
        val skipped: Int = 0,
        val categories: List<Category> = emptyList(),
        val serverInfo: ServerInfo? = null,
        val query: SearchQuery = SearchQuery(),
        val searchText: String = "",
        val favoriteIds: Set<String> = emptySet(),
        /** Server category linked to the bookmark feature; null until the first favourite exists. */
        val bookmarkCategoryId: String? = null,
        val favoritesSupported: Boolean = true,
        /** Tag statistics for autocompletion; loaded once, the first time the search box gets focus. */
        val tagStats: List<TagStat> = emptyList(),
        /** arcid of a card whose quick action is running. */
        val busyId: String? = null,
    ) {
        val favoritesOnly: Boolean get() = bookmarkCategoryId != null && query.category == bookmarkCategoryId

        /** True when Back should reset the library instead of leaving the app. */
        val hasActiveFilters: Boolean
            get() = searchText.isNotEmpty() || query.filter.isNotEmpty() || query.category.isNotEmpty() ||
                query.newOnly || query.untaggedOnly || query.hideCompleted || query.hasRatingFilter
    }

    private val _state = MutableStateFlow(UiState(query = SearchQuery(groupTanks = settings.value.groupByTankoubon)))
    val state = _state.asStateFlow()

    private var loadJob: Job? = null
    private var moreJob: Job? = null
    private var tagStatsRequested = false

    /** Server offset of the next page: raw rows fetched so far (decoded + skipped), not `items.size`. */
    private var nextStart = 0

    init {
        refresh()
        loadServerData()
        viewModelScope.launch {
            settings
                .map { Triple(it.serverUrl, it.apiKey, it.groupByTankoubon) }
                .distinctUntilChanged()
                .drop(1)
                .collect { (_, _, groupTanks) ->
                    favorites.reset()
                    tagStatsRequested = false
                    _state.update { it.copy(query = it.query.copy(groupTanks = groupTanks), tagStats = emptyList()) }
                    refresh()
                    loadServerData()
                }
        }
        viewModelScope.launch {
            favorites.state.collect { f ->
                _state.update { it.copy(favoriteIds = f.ids, bookmarkCategoryId = f.categoryId, favoritesSupported = f.supported) }
            }
        }
    }

    fun onSearchTextChange(text: String) = _state.update { it.copy(searchText = text) }

    fun submitSearch() {
        val text = _state.value.searchText.trim().trimEnd(',').trim()
        if (text.isNotEmpty()) viewModelScope.launch { runCatching { recordSearch(text) } }
        setQuery(_state.value.query.copy(filter = text))
    }

    fun clearSearch() {
        _state.update { it.copy(searchText = "") }
        setQuery(_state.value.query.copy(filter = ""))
    }

    /** Runs a recent search again. */
    fun useSearch(text: String) {
        _state.update { it.copy(searchText = text) }
        submitSearch()
    }

    /** Back button: drop every filter and the search text, back to the plain library. */
    fun clearAll() {
        _state.update { it.copy(searchText = "") }
        setQuery(SearchQuery(groupTanks = settings.value.groupByTankoubon))
    }

    /** Search for a tag coming from another screen (e.g. tapping a tag chip in the detail view). */
    fun searchTag(tag: String) {
        _state.update { it.copy(searchText = tag) }
        submitSearch()
    }

    fun setQuery(query: SearchQuery) {
        _state.update { it.copy(query = query) }
        refresh()
    }

    fun toggleFavoritesOnly() {
        val id = _state.value.bookmarkCategoryId ?: return
        val q = _state.value.query
        setQuery(q.copy(category = if (q.category == id) "" else id))
    }

    fun setMinRating(minRating: Int) = setQuery(_state.value.query.copy(minRating = minRating.coerceIn(0, 5)))

    /** Loads tag statistics once (they can be large on big servers, so only when the user starts searching). */
    fun ensureTagStats() {
        if (tagStatsRequested) return
        tagStatsRequested = true
        viewModelScope.launch {
            runCatching { api.tagStats(minWeight = 1) }
                .onSuccess { stats -> _state.update { it.copy(tagStats = stats.sortedByDescending { s -> s.weight }) } }
                .onFailure { tagStatsRequested = false }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        // A page request of the previous query must not append to the new results.
        moreJob?.cancel()
        _state.update { it.copy(loadingMore = false) }
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = it.items.isEmpty(), refreshing = it.items.isNotEmpty(), error = null, endReached = false) }
            try {
                val query = _state.value.query
                val page = api.search(query, start = 0)
                nextStart = page.fetched
                _state.update {
                    it.copy(
                        items = page.items,
                        filtered = page.filtered,
                        total = page.total,
                        skipped = page.skipped,
                        loading = false,
                        refreshing = false,
                        error = null,
                        endReached = page.fetched == 0 || query.hasRatingFilter || nextStart >= page.filtered,
                    )
                }
            } catch (e: CancellationException) {
                throw e // a newer refresh replaced this one: not an error
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.refreshing || s.loadingMore || s.endReached || s.items.isEmpty()) return
        moreJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = api.search(s.query, start = nextStart)
                if (_state.value.query != s.query) return@launch // query changed while loading
                nextStart += page.fetched
                _state.update {
                    it.copy(
                        items = (it.items + page.items).distinctBy { a -> a.arcid },
                        filtered = page.filtered,
                        skipped = it.skipped + page.skipped,
                        loadingMore = false,
                        endReached = page.fetched == 0 || nextStart >= page.filtered,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, error = e.userMessage()) }
            }
        }
    }

    fun random(onResult: (Archive?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { api.random(_state.value.query, 1).firstOrNull() }.getOrNull()
            onResult(result)
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ------------------------------------------------------------------ card quick actions (🔑)

    private val canEdit: Boolean get() = settings.value.apiKey.isNotBlank()

    private fun quick(arcid: String, failure: String, block: suspend () -> Unit) {
        if (!canEdit) {
            _state.update { it.copy(message = "설정에서 API 키를 입력해야 바꿀 수 있습니다.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busyId = arcid) }
            runCatching { block() }
                .onSuccess { reloadItem(arcid) }
                .onFailure { e -> _state.update { it.copy(message = "$failure: ${e.userMessage()}") } }
            _state.update { it.copy(busyId = null) }
        }
    }

    fun setRating(arcid: String, rating: Int?) = quick(arcid, "평점 저장 실패") { api.setRating(arcid, rating) }

    fun toggleNew(archive: Archive) = quick(archive.arcid, "NEW 변경 실패") {
        if (archive.isnew) api.clearNew(archive.arcid) else api.setNew(archive.arcid)
    }

    fun toggleFavorite(arcid: String) = quick(arcid, "즐겨찾기 변경 실패") { favorites.toggle(arcid) }

    /** Re-reads one archive after a quick action so its card (stars, NEW badge) updates in place. */
    private suspend fun reloadItem(arcid: String) {
        val fresh = runCatching { api.metadata(arcid) }.getOrNull() ?: return
        _state.update { s -> s.copy(items = s.items.map { if (it.arcid == arcid) fresh else it }) }
    }

    /** Re-reads bookmark membership (cheap) when the library comes back on screen. */
    fun refreshFavorites() {
        viewModelScope.launch { favorites.refresh(_state.value.categories.takeIf { it.isNotEmpty() }) }
    }

    private fun loadServerData() {
        viewModelScope.launch {
            val categories = runCatching { api.categories() }.getOrNull()
            if (categories != null) _state.update { it.copy(categories = categories) }
            favorites.refresh(categories)
            runCatching { api.info() }.onSuccess { i -> _state.update { it.copy(serverInfo = i) } }
        }
    }
}
