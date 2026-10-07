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
import com.sus7898.lrrviewer.data.api.userMessage
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
) : ViewModel() {

    data class UiState(
        val items: List<Archive> = emptyList(),
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val endReached: Boolean = false,
        val error: String? = null,
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
                    _state.update { it.copy(query = it.query.copy(groupTanks = groupTanks)) }
                    refresh()
                    loadServerData()
                }
        }
        viewModelScope.launch {
            favorites.state.collect { f ->
                _state.update { it.copy(favoriteIds = f.ids, bookmarkCategoryId = f.categoryId) }
            }
        }
    }

    fun onSearchTextChange(text: String) = _state.update { it.copy(searchText = text) }

    fun submitSearch() = setQuery(_state.value.query.copy(filter = _state.value.searchText.trim()))

    fun clearSearch() {
        _state.update { it.copy(searchText = "") }
        setQuery(_state.value.query.copy(filter = ""))
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

    fun refresh() {
        loadJob?.cancel()
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
                        endReached = page.fetched == 0 || query.hasRatingFilter || nextStart >= page.filtered,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.refreshing || s.loadingMore || s.endReached || s.items.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = api.search(s.query, start = nextStart)
                nextStart += page.fetched
                val merged = (s.items + page.items).distinctBy { it.arcid }
                _state.update {
                    it.copy(
                        items = merged,
                        filtered = page.filtered,
                        skipped = it.skipped + page.skipped,
                        loadingMore = false,
                        endReached = page.fetched == 0 || nextStart >= page.filtered,
                    )
                }
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
