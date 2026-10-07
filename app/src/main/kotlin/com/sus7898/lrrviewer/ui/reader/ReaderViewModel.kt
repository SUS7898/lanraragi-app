package com.sus7898.lrrviewer.ui.reader

import android.graphics.drawable.ColorDrawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.fetch.SourceResult
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.Options
import com.sus7898.lrrviewer.AppGraph
import com.sus7898.lrrviewer.data.api.Archive
import com.sus7898.lrrviewer.data.api.FilesResponse
import com.sus7898.lrrviewer.data.api.LrrException
import com.sus7898.lrrviewer.data.api.TocEntry
import com.sus7898.lrrviewer.data.api.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Streams an archive page by page: `/api/archives/:id/files` gives the page URL list, each
 * page is fetched on demand (and a few ahead) via Coil, so nothing is downloaded as a whole.
 */
class ReaderViewModel(
    private val graph: AppGraph,
    val arcId: String,
    private val requestedPage: Int,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val extracting: Boolean = false,
        val error: String? = null,
        val title: String = "",
        val pages: List<String> = emptyList(),
        /** Page to open when the reader is first composed. */
        val initialPage: Int = 0,
        val currentPage: Int = 0,
        val toc: List<TocEntry> = emptyList(),
    ) {
        val pageCount: Int get() = pages.size
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var archive: Archive? = null
    private var serverTracksProgress: Boolean? = null
    private var clearedNew = false
    private var progressJob: Job? = null
    private val prefetched = HashSet<Int>()

    init { load() }

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, extracting = false, error = null) }
            try {
                val metaDeferred = async { runCatching { graph.api.metadata(arcId) }.getOrNull() }
                var files = graph.api.files(arcId, force)
                archive = metaDeferred.await()
                if (files.pages.isEmpty() && files.job >= 0) {
                    _state.update { it.copy(extracting = true) }
                    files = waitForExtraction(files.job)
                }
                val pages = graph.api.resolvePages(files.pages)
                if (pages.isEmpty()) {
                    throw LrrException.HttpError(200, "이 아카이브에서 이미지를 찾지 못했습니다. 서버에서 파일을 확인하거나 '서버 재추출'을 시도하세요.")
                }
                val start = resolveStartPage(pages.size)
                prefetched.clear()
                _state.update {
                    it.copy(
                        loading = false,
                        extracting = false,
                        title = archive?.title?.ifBlank { null } ?: archive?.filename.orEmpty(),
                        pages = pages,
                        initialPage = start,
                        currentPage = start,
                        toc = archive?.tocEntries.orEmpty(),
                    )
                }
                if (serverTracksProgress == null) {
                    serverTracksProgress = runCatching { graph.api.info().server_tracks_progress }.getOrDefault(false)
                }
                prefetchAround(start)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, extracting = false, error = e.userMessage()) }
            }
        }
    }

    /** Older servers return `{job, pages: []}` while a background extraction runs; poll until done. */
    private suspend fun waitForExtraction(jobId: Int): FilesResponse {
        repeat(90) { attempt ->
            delay(1_000)
            val job = runCatching { graph.api.minionJob(jobId) }.getOrNull()
            if (job != null && (job.state == "finished" || job.state == "failed")) return graph.api.files(arcId)
            if (attempt % 3 == 2) {
                val f = graph.api.files(arcId)
                if (f.pages.isNotEmpty()) return f
            }
        }
        return graph.api.files(arcId)
    }

    private suspend fun resolveStartPage(count: Int): Int {
        if (count <= 0) return 0
        if (requestedPage >= 0) return requestedPage.coerceIn(0, count - 1)
        val local = graph.history.get(arcId)?.page ?: -1
        val server = (archive?.progress ?: 0) - 1
        val page = maxOf(local, server, 0)
        return if (page >= count - 1) 0 else page
    }

    fun onPageChanged(index: Int) {
        if (index !in _state.value.pages.indices) return
        if (index == _state.value.currentPage && progressJob != null) return
        _state.update { it.copy(currentPage = index) }
        prefetchAround(index)
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            delay(700)
            persistProgress(index)
        }
    }

    private suspend fun persistProgress(index: Int) {
        val s = _state.value
        if (s.pages.isEmpty()) return
        graph.history.record(arcId, s.title, index, s.pageCount)
        val settings = graph.settingsState.value
        if (settings.clearNewOnRead && archive?.isnew == true && !clearedNew) {
            clearedNew = true
            runCatching { graph.api.clearNew(arcId) }
        }
        if (settings.syncProgress && serverTracksProgress == true) {
            runCatching { graph.api.updateProgress(arcId, index + 1) }
        }
    }

    /** Warm the disk cache for the next few pages (and the previous one) without decoding bitmaps. */
    fun prefetchAround(index: Int) {
        val pages = _state.value.pages
        if (pages.isEmpty()) return
        val count = graph.settingsState.value.prefetchPages
        val targets = (index + 1..index + count) + (index - 1)
        for (i in targets) {
            if (i !in pages.indices || !prefetched.add(i)) continue
            val request = ImageRequest.Builder(graph.appContext)
                .data(pages[i])
                .memoryCachePolicy(CachePolicy.DISABLED)
                .decoderFactory(DiskOnlyDecoder.Factory)
                .build()
            graph.imageLoader.enqueue(request)
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        val index = _state.value.currentPage
        if (_state.value.pages.isNotEmpty()) {
            graph.scope.launch { persistProgress(index) }
        }
    }
}

/** A decoder that decodes nothing: the fetcher has already written the bytes to the disk cache. */
private object DiskOnlyDecoder : Decoder {
    override suspend fun decode(): DecodeResult = DecodeResult(ColorDrawable(android.graphics.Color.TRANSPARENT), false)

    object Factory : Decoder.Factory {
        override fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder {
            result.source.close()
            return DiskOnlyDecoder
        }
    }
}
