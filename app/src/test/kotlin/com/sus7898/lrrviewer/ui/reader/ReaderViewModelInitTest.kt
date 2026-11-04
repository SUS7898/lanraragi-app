package com.sus7898.lrrviewer.ui.reader

import android.content.ContextWrapper
import coil3.ImageLoader
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.data.PageImageStore
import com.sus7898.lrrviewer.data.PageInfoRepository
import com.sus7898.lrrviewer.data.ReadingMode
import com.sus7898.lrrviewer.data.ReadingProgressRepository
import com.sus7898.lrrviewer.data.api.LrrApi
import com.sus7898.lrrviewer.data.api.LrrAuthInterceptor
import com.sus7898.lrrviewer.data.db.PageInfoDao
import com.sus7898.lrrviewer.data.db.ReadingProgressDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Regression test for the v0.1.7 crash on every reader open (docs/TROUBLESHOOTING.md I-1): viewModelScope runs on
 * Dispatchers.Main.immediate, so a launch in `init` executes on the spot. It read a property declared further down
 * the class, which Kotlin had not initialized yet. This test really constructs the ViewModel the way production does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelInitTest {

    private lateinit var server: MockWebServer
    private lateinit var settings: MutableStateFlow<AppSettings>
    private lateinit var deps: ReaderViewModel.Deps
    private val uncaught = CopyOnWriteArrayList<Throwable>()
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    /** DAOs and ImageLoader are interfaces; anything the reader does not need here answers "nothing". */
    private inline fun <reified T : Any> fake(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            when {
                method.returnType == Flow::class.java -> emptyFlow<Any>()
                method.name == "forArchive" || method.name == "all" || method.name == "tombstones" -> emptyList<Any>()
                method.returnType == Void.TYPE || method.name.startsWith("get") -> null
                else -> Unit
            }
        } as T

    @Before
    fun setUp() {
        previousHandler = Thread.currentThread().uncaughtExceptionHandler
        Thread.currentThread().uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e -> uncaught += e }
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/api/archives/$ID/metadata" -> MockResponse().setBody("""{"arcid":"$ID","title":"t","pagecount":3}""")
                "/api/archives/$ID/files" ->
                    MockResponse().setBody("""{"pages":["./api/archives/$ID/page?path=0","./api/archives/$ID/page?path=1","./api/archives/$ID/page?path=2"]}""")
                "/api/archives/$ID/files/thumbnails" ->
                    MockResponse().setBody("""{"operation":"generate_page_thumbnails","success":1,"message":"No job queued, all thumbnails already exist."}""")
                "/api/info" -> MockResponse().setBody("""{"name":"t","version":"0.9","server_tracks_progress":"0"}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        // prefetchPages = 0: no Coil request is built, so the Android-only parts of ImageRequest stay out of this JVM test.
        settings = MutableStateFlow(AppSettings(serverUrl = "http://localhost:${server.port}", prefetchPages = 0, readingMode = ReadingMode.RTL))
        val api = LrrApi(OkHttpClient.Builder().addInterceptor(LrrAuthInterceptor(settings)).build(), settings)
        val store = PageImageStore(ContextWrapper(null), fake<ImageLoader>())
        deps = ReaderViewModel.Deps(
            api = api,
            progress = ReadingProgressRepository(fake<ReadingProgressDao>()),
            pageInfo = PageInfoRepository(fake<PageInfoDao>(), store),
            store = store,
            settings = settings,
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
        Thread.currentThread().uncaughtExceptionHandler = previousHandler
    }

    @Test
    fun `constructing the reader view model does not crash and it follows the thumbnail state`() = runBlocking {
        val vm = ReaderViewModel(deps, ID, requestedPage = -1)
        assertTrue("uncaught during construction: $uncaught", uncaught.isEmpty())

        val loaded = withTimeout(10_000) { vm.state.first { !it.loading } }
        assertNull(loaded.error)
        assertEquals(3, loaded.pageCount)
        assertEquals(ThumbState.Idle, loaded.thumbs)

        // The collector started in init must really be running: this is what died in v0.1.7.
        vm.preparePageThumbnails()
        withTimeout(10_000) { vm.state.first { it.thumbs == ThumbState.Ready } }
        assertTrue(vm.pageThumbnailUrl(1).contains("thumbnail?page=2"))
        assertTrue("uncaught: $uncaught", uncaught.isEmpty())
    }

    @Test
    fun `the continuous toggle goes to webtoon and back to the previous choice`() = runBlocking {
        val vm = ReaderViewModel(deps, ID, requestedPage = -1)
        withTimeout(10_000) { vm.state.first { !it.loading } }

        vm.toggleContinuous()
        assertEquals(ReadingMode.WEBTOON, vm.state.value.readingModeOverride)
        vm.toggleContinuous()
        assertNull(vm.state.value.readingModeOverride)

        vm.setReadingModeOverride(ReadingMode.LTR)
        vm.toggleContinuous()
        assertEquals(ReadingMode.WEBTOON, vm.state.value.readingModeOverride)
        vm.toggleContinuous()
        assertEquals(ReadingMode.LTR, vm.state.value.readingModeOverride)
        assertTrue("uncaught: $uncaught", uncaught.isEmpty())
    }

    private companion object {
        const val ID = "abc123"
    }
}
