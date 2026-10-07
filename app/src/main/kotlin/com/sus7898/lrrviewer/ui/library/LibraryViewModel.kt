package com.sus7898.lrrviewer.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.data.api.LrrApi
import com.sus7898.lrrviewer.data.api.Archive
import com.sus7898.lrrviewer.data.api.Category
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
    )

    private val _state = MutableStateFlow(UiState(query = SearchQuery(groupTanks = settings.value.groupByTankoubon)))
    val state = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
        loadServerData()
        viewModelScope.launch {
            settings
                .map { Triple(it.serverUrl, it.apiKey, it.groupByTankoubon) }
                .distinctUntilChanged()
                .drop(1)
                .collect { (_, _, groupTanks) ->
                    _state.update { it.copy(query = it.query.copy(groupTanks = groupTanks)) }
                    refresh()
                    loadServerData()
                }
        }
    }

    fun onSearchTextChange(text: String) = _state.update { it.copy(searchText = text) }

    fun submitSearch() = setQuery(_state.value.query.copy(filter = _state.value.searchText.trim()))

    fun clearSearch() {
        _state.update { it.copy(searchText = "") }
        setQuery(_state.value.query.copy(filter = ""))
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

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = it.items.isEmpty(), refreshing = it.items.isNotEmpty(), error = null, endReached = false) }
            try {
                val page = api.search(_state.value.query, start = 0)
                _state.update {
                    it.copy(
                        items = page.items,
                        filtered = page.filtered,
                        total = page.total,
                        skipped = page.skipped,
                        loading = false,
                        refreshing = false,
                        endReached = page.items.isEmpty() || page.items.size >= page.filtered,
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
                val page = api.search(s.query, start = s.items.size)
                val merged = (s.items + page.items).distinctBy { it.arcid }
                _state.update {
                    it.copy(
                        items = merged,
                        filtered = page.filtered,
                        skipped = it.skipped + page.skipped,
                        loadingMore = false,
                        endReached = page.items.isEmpty() || merged.size >= page.filtered,
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

    private fun loadServerData() {
        viewModelScope.launch {
            runCatching { api.categories() }.onSuccess { c -> _state.update { it.copy(categories = c) } }
            runCatching { api.info() }.onSuccess { i -> _state.update { it.copy(serverInfo = i) } }
        }
    }
}
