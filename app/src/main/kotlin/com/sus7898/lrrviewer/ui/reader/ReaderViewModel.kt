package com.sus7898.lrrviewer.ui.reader

import android.content.Context
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.Options
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.data.ReadingMode
import com.sus7898.lrrviewer.data.ReadingProgressRepository
import com.sus7898.lrrviewer.data.api.Archive
import com.sus7898.lrrviewer.data.api.FilesResponse
import com.sus7898.lrrviewer.data.api.LrrApi
import com.sus7898.lrrviewer.data.api.LrrException
import com.sus7898.lrrviewer.data.api.TocEntry
import com.sus7898.lrrviewer.data.api.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Streams an archive page by page: `/api/archives/:id/files` gives the page URL list, each
 * page is fetched on demand (and a few ahead) via Coil, so nothing is downloaded as a whole.
 */
class ReaderViewModel(
    private val deps: Deps,
    val arcId: String,
    private val requestedPage: Int,
) : ViewModel() {

    /** Everything the reader needs from the app graph; keeps the ViewModel testable without the whole graph. */
    class Deps(
        val api: LrrApi,
        val progress: ReadingProgressRepository,
        val settings: StateFlow<AppSettings>,
        val imageLoader: ImageLoader,
        val appContext: Context,
        /** Outlives the ViewModel; used to flush progress from onCleared(). */
        val appScope: CoroutineScope,
    )

    data class UiState(
        val loading: Boolean = true,
        val extracting: Boolean = false,
        val error: String? = null,
        val title: String = "",
        val pages: List<String> = emptyList(),
        val currentPage: Int = 0,
        val toc: List<TocEntry> = emptyList(),
        /** Per-archive reading mode; null means "use the global default". */
        val readingModeOverride: ReadingMode? = null,
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
                val metaDeferred = async { runCatching { deps.api.metadata(arcId) }.getOrNull() }
                val savedDeferred = async { deps.progress.get(arcId) }
                var files = deps.api.files(arcId, force)
                archive = metaDeferred.await()
                val saved = savedDeferred.await()
                if (files.pages.isEmpty() && files.job >= 0) {
                    _state.update { it.copy(extracting = true) }
                    files = waitForExtraction(files.job)
                }
                val pages = deps.api.resolvePages(files.pages)
                if (pages.isEmpty()) {
                    throw LrrException.HttpError(200, "이 아카이브에서 이미지를 찾지 못했습니다. 서버에서 파일을 확인하거나 '서버 재추출'을 시도하세요.")
                }
                val start = resolveStartPage(pages.size, saved?.page ?: -1)
                prefetched.clear()
                _state.update {
                    it.copy(
                        loading = false,
                        extracting = false,
                        title = archive?.title?.ifBlank { null } ?: archive?.filename.orEmpty(),
                        pages = pages,
                        currentPage = start,
                        toc = archive?.tocEntries.orEmpty(),
                        readingModeOverride = saved?.readingModeOverride,
                    )
                }
                if (serverTracksProgress == null) {
                    serverTracksProgress = runCatching { deps.api.info().server_tracks_progress }.getOrDefault(false)
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
            val job = runCatching { deps.api.minionJob(jobId) }.getOrNull()
            if (job != null && (job.state == "finished" || job.state == "failed")) return deps.api.files(arcId)
            if (attempt % 3 == 2) {
                val f = deps.api.files(arcId)
                if (f.pages.isNotEmpty()) return f
            }
        }
        return deps.api.files(arcId)
    }

    private fun resolveStartPage(count: Int, localPage: Int): Int {
        if (count <= 0) return 0
        if (requestedPage >= 0) return requestedPage.coerceIn(0, count - 1)
        val server = (archive?.progress ?: 0) - 1
        val page = maxOf(localPage, server, 0)
        return if (page >= count - 1) 0 else page
    }

    fun effectiveReadingMode(settings: AppSettings): ReadingMode = _state.value.readingModeOverride ?: settings.readingMode

    /** Sets (or clears with null) the reading mode for this archive only. */
    fun setReadingModeOverride(mode: ReadingMode?) {
        _state.update { it.copy(readingModeOverride = mode) }
        viewModelScope.launch {
            persistProgress(_state.value.currentPage) // make sure the row exists
            deps.progress.setReadingModeOverride(arcId, mode)
        }
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
        deps.progress.record(arcId, s.title, index, s.pageCount)
        val settings = deps.settings.value
        if (settings.clearNewOnRead && archive?.isnew == true && !clearedNew) {
            clearedNew = true
            runCatching { deps.api.clearNew(arcId) }
        }
        if (settings.syncProgress && serverTracksProgress == true) {
            runCatching { deps.api.updateProgress(arcId, index + 1) }
        }
    }

    /** Warm the disk cache for the next few pages (and the previous one) without decoding bitmaps. */
    fun prefetchAround(index: Int) {
        val pages = _state.value.pages
        if (pages.isEmpty()) return
        val count = deps.settings.value.prefetchPages
        val targets = (index + 1..index + count) + (index - 1)
        for (i in targets) {
            if (i !in pages.indices || !prefetched.add(i)) continue
            val request = ImageRequest.Builder(deps.appContext)
                .data(pages[i])
                .memoryCachePolicy(CachePolicy.DISABLED)
                .decoderFactory(DiskOnlyDecoder.Factory)
                .build()
            deps.imageLoader.enqueue(request)
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        val index = _state.value.currentPage
        if (_state.value.pages.isNotEmpty()) {
            deps.appScope.launch { persistProgress(index) }
        }
    }
}

/** A decoder that decodes nothing: the fetcher has already written the bytes to the disk cache. */
private object DiskOnlyDecoder : Decoder {
    override suspend fun decode(): DecodeResult =
        DecodeResult(image = android.graphics.Color.TRANSPARENT.toDrawable().asImage(), isSampled = false)

    object Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder {
            result.source.close()
            return DiskOnlyDecoder
        }
    }
}
