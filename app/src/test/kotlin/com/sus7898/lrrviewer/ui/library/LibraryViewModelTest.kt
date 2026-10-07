package com.sus7898.lrrviewer.ui.library

import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.data.FavoritesRepository
import com.sus7898.lrrviewer.data.api.LrrApi
import com.sus7898.lrrviewer.data.api.LrrAuthInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var api: LrrApi
    private lateinit var settings: MutableStateFlow<AppSettings>
    private val searchStarts = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when {
                    url.encodedPath == "/api/search" -> {
                        val start = url.queryParameter("start") ?: "0"
                        searchStarts += start
                        // Page 1: 3 raw rows, one malformed (no arcid) -> 2 decoded + 1 skipped. Page 2: 2 rows.
                        val data = if (start == "0") """[{"arcid":"a"},{"title":"broken"},{"arcid":"b"}]""" else """[{"arcid":"c"},{"arcid":"d"}]"""
                        MockResponse().setBody("""{"data":$data,"recordsFiltered":5,"recordsTotal":5}""")
                    }
                    url.encodedPath == "/api/categories/bookmark_link" -> MockResponse().setBody("""{"category_id":"","success":1}""")
                    url.encodedPath == "/api/categories" -> MockResponse().setBody("[]")
                    url.encodedPath == "/api/info" -> MockResponse().setBody("""{"name":"t","version":"0.9"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        settings = MutableStateFlow(AppSettings(serverUrl = "http://localhost:${server.port}"))
        api = LrrApi(OkHttpClient.Builder().addInterceptor(LrrAuthInterceptor(settings)).build(), settings)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `loadMore advances the server offset by raw rows, not by decoded items`() = runBlocking {
        val vm = LibraryViewModel(api, settings, FavoritesRepository(api, settings))
        withTimeout(10_000) { vm.state.first { !it.loading && it.items.size == 2 } }
        assertEquals(1, vm.state.value.skipped)

        vm.loadMore()
        withTimeout(10_000) { vm.state.first { it.items.size == 4 } }

        assertEquals(listOf("0", "3"), searchStarts)
        assertEquals(listOf("a", "b", "c", "d"), vm.state.value.items.map { it.arcid })
        assertTrue(vm.state.value.endReached)
    }

    @Test
    fun `clearAll resets search text and every filter`() = runBlocking {
        val vm = LibraryViewModel(api, settings, FavoritesRepository(api, settings))
        withTimeout(10_000) { vm.state.first { !it.loading } }
        vm.onSearchTextChange("artist:foo")
        vm.setQuery(vm.state.value.query.copy(newOnly = true, minRating = 3))
        assertTrue(vm.state.value.hasActiveFilters)
        vm.clearAll()
        assertTrue(!vm.state.value.hasActiveFilters)
        assertEquals("", vm.state.value.searchText)
    }
}
