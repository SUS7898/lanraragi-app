package com.sus7898.lrrviewer.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.data.PageImageStore
import com.sus7898.lrrviewer.data.PageInfoRepository
import com.sus7898.lrrviewer.data.PageSize
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
    /** Ids of the list this archive was opened from; the entry after [arcId] becomes the "next archive". */
    private val queue: List<String> = emptyList(),
) : ViewModel() {

    /** Everything the reader needs from the app graph; keeps the ViewModel testable without the whole graph. */
    class Deps(
        val api: LrrApi,
        val progress: ReadingProgressRepository,
        val pageInfo: PageInfoRepository,
        val store: PageImageStore,
        val settings: StateFlow<AppSettings>,
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
        /** Pixel sizes of the pages read so far (webtoon layout/tiling, mode suggestion). */
        val pageSizes: Map<Int, PageSize> = emptyMap(),
        /** Metadata of the archive that follows in the queue, loaded when the last pages come into view. */
        val nextArchive: Archive? = null,
        /** Rating of this archive (`rating:N` tag); shown on the last page. */
        val rating: Int? = null,
        val thumbs: ThumbState = ThumbState.Idle,
        /** One-shot message for the snackbar (rating saved, thumbnail set, …). */
        val message: String? = null,
        val busy: Boolean = false,
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
    private val sizeRequests = HashSet<Int>()
    private var nextLoaded = false

    init { load() }

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, extracting = false, error = null) }
            try {
                val metaDeferred = async { runCatching { deps.api.metadata(arcId) }.getOrNull() }
                val savedDeferred = async { deps.progress.get(arcId) }
                val sizesDeferred = async { runCatching { deps.pageInfo.cached(arcId) }.getOrDefault(emptyMap()) }
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
                sizeRequests.clear()
                _state.update {
                    it.copy(
                        loading = false,
                        extracting = false,
                        title = archive?.title?.ifBlank { null } ?: archive?.filename.orEmpty(),
                        pages = pages,
                        currentPage = start,
                        toc = archive?.tocEntries.orEmpty(),
                        readingModeOverride = saved?.readingModeOverride,
                        rating = archive?.rating,
                        pageSizes = sizesDeferred.await().filterKeys { k -> k in pages.indices },
                    )
                }
                if (serverTracksProgress == null) {
                    serverTracksProgress = runCatching { deps.api.info().server_tracks_progress }.getOrDefault(false)
                }
                prefetchAround(start)
                ensureSize(start)
                maybeLoadNext(start)
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
        maybeLoadNext(index)
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
            deps.store.enqueuePrefetch(pages[i])
        }
    }

    /** Reads the pixel size of page [index] once (downloading the page into the disk cache if needed). */
    fun ensureSize(index: Int) {
        val pages = _state.value.pages
        if (index !in pages.indices || index in _state.value.pageSizes || !sizeRequests.add(index)) return
        viewModelScope.launch {
            val size = runCatching { deps.pageInfo.size(arcId, index, pages[index]) }.getOrNull()
            if (size != null) _state.update { it.copy(pageSizes = it.pageSizes + (index to size)) }
            else sizeRequests.remove(index) // allow a retry later (network error)
        }
    }

    /** Within the last three pages: fetch the title/thumbnail of the next archive once so the end card is ready. */
    private fun maybeLoadNext(index: Int) {
        val s = _state.value
        if (nextLoaded || s.pages.isEmpty() || index < s.pageCount - 3) return
        val nextId = nextArchiveIn(queue, arcId) ?: return
        nextLoaded = true
        viewModelScope.launch {
            val next = runCatching { deps.api.metadata(nextId) }.getOrNull() ?: Archive(arcid = nextId)
            _state.update { it.copy(nextArchive = next) }
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        val index = _state.value.currentPage
        if (_state.value.pages.isNotEmpty()) {
            deps.appScope.launch { persistProgress(index) }
        }
    }

    // ------------------------------------------------------------------ rating / page actions (🔑 where noted)

    val canEdit: Boolean get() = deps.settings.value.apiKey.isNotBlank()

    /** 🔑 Rates this archive from the reader (stored as the server tag `rating:N`). */
    fun setRating(rating: Int?) {
        if (!canEdit) { _state.update { it.copy(message = "설정에서 API 키를 입력해야 평점을 저장할 수 있습니다.") }; return }
        val before = _state.value.rating
        _state.update { it.copy(rating = rating, busy = true) }
        viewModelScope.launch {
            runCatching { deps.api.setRating(arcId, rating) }
                .onSuccess { _state.update { it.copy(busy = false, message = rating?.let { r -> "평점 ${r}점을 저장했습니다." } ?: "평점을 지웠습니다.") } }
                .onFailure { e -> _state.update { it.copy(busy = false, rating = before, message = "평점 저장 실패: ${e.userMessage()}") } }
        }
    }

    /** 🔑 Makes page [index] (0-based) the archive cover and drops the stale cached thumbnail. */
    fun setAsThumbnail(index: Int) {
        if (!canEdit) { _state.update { it.copy(message = "설정에서 API 키를 입력해야 썸네일을 바꿀 수 있습니다.") }; return }
        viewModelScope.launch {
            runCatching { deps.api.setThumbnail(arcId, index + 1) }
                .onSuccess {
                    deps.store.evict(deps.api.thumbnailUrl(arcId))
                    _state.update { it.copy(message = "${index + 1}페이지를 썸네일로 지정했습니다.") }
                }
                .onFailure { e -> _state.update { it.copy(message = "썸네일 변경 실패: ${e.userMessage()}") } }
        }
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    /**
     * Page grid (P1-5): asks the server to generate every page thumbnail and waits for the job; servers without
     * page thumbnails (404) or a failed job fall back to the page images themselves (downsampled by Coil).
     */
    fun preparePageThumbnails() {
        val current = _state.value.thumbs
        if (current is ThumbState.Ready || current is ThumbState.Generating || current is ThumbState.Fallback) return
        _state.update { it.copy(thumbs = ThumbState.Generating) }
        viewModelScope.launch {
            val result = runCatching {
                val job = deps.api.queuePageThumbnails(arcId)
                if (job != null) {
                    var finished = false
                    repeat(60) {
                        if (finished) return@repeat
                        delay(1_000)
                        val st = runCatching { deps.api.minionJob(job).state }.getOrDefault("")
                        if (st == "failed") throw IllegalStateException("thumbnail job failed")
                        if (st == "finished") finished = true
                    }
                    if (!finished) throw IllegalStateException("thumbnail job timeout")
                }
            }
            _state.update { it.copy(thumbs = if (result.isSuccess) ThumbState.Ready else ThumbState.Fallback) }
        }
    }

    fun pageThumbnailUrl(index: Int): String = when (_state.value.thumbs) {
        ThumbState.Ready -> deps.api.pageThumbnailUrl(arcId, index + 1)
        else -> _state.value.pages.getOrElse(index) { "" }
    }

    /** A thumbnail URL that failed to decode (e.g. a 202 JSON body got cached): forget it so a retry refetches. */
    fun onThumbnailError(url: String) = deps.store.evict(url)

    companion object {
        /** The entry after [current] in [queue], skipping tankoubons; null when absent or [current] is not queued. */
        fun nextArchiveIn(queue: List<String>, current: String): String? {
            val i = queue.indexOf(current)
            if (i < 0) return null
            return queue.drop(i + 1).firstOrNull { !it.startsWith("TANK_") }
        }
    }
}

/** Page-grid thumbnail source. */
sealed interface ThumbState {
    data object Idle : ThumbState
    data object Generating : ThumbState
    /** Server page thumbnails exist: use `thumbnail?page=N`. */
    data object Ready : ThumbState
    /** No server page thumbnails: use the page images, downsampled. */
    data object Fallback : ThumbState
}
