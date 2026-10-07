package com.sus7898.lrrviewer.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sus7898.lrrviewer.data.ReadingProgressRepository
import com.sus7898.lrrviewer.data.api.LrrApi
import com.sus7898.lrrviewer.data.api.Archive
import com.sus7898.lrrviewer.data.api.TankoubonFull
import com.sus7898.lrrviewer.data.api.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArchiveDetailViewModel(
    private val api: LrrApi,
    private val progress: ReadingProgressRepository,
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
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

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

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
