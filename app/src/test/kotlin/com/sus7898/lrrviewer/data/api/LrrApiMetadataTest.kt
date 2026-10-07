package com.sus7898.lrrviewer.data.api

import com.sus7898.lrrviewer.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

class LrrApiMetadataTest {

    private lateinit var server: MockWebServer
    private lateinit var api: LrrApi
    private val baseUrl: String get() = "http://localhost:${server.port}"

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val settings = MutableStateFlow(AppSettings(serverUrl = baseUrl, apiKey = "k"))
        api = LrrApi(OkHttpClient.Builder().addInterceptor(LrrAuthInterceptor(settings)).build(), settings)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `updateMetadata sends title, tags and summary as a form body`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"operation":"update_metadata","success":1}"""))
        api.updateMetadata("abc", "Title & co", "artist:foo, rating:4", "한 줄 요약")
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/api/archives/abc/metadata", req.path)
        assertTrue(req.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
        val form = req.body.readUtf8().split('&').associate { kv ->
            val (k, v) = kv.split('=', limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }
        assertEquals("Title & co", form["title"])
        assertEquals("artist:foo, rating:4", form["tags"])
        assertEquals("한 줄 요약", form["summary"])
    }

    @Test
    fun `rating filter fetches everything with start=-1 and keeps only archives rated high enough`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"data":[{"arcid":"a","tags":"rating:5"},{"arcid":"b","tags":"rating:3"},{"arcid":"c","tags":"artist:x"},{"arcid":"d","tags":"rating:4"}],
                    "recordsFiltered":4,"recordsTotal":10}""",
            ),
        )
        val page = api.search(SearchQuery(minRating = 4), start = 200)
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("start=-1"))
        assertEquals(listOf("a", "d"), page.items.map { it.arcid })
        assertEquals(2, page.filtered)
        assertEquals(10, page.total)
    }

    @Test
    fun `bookmark link maps empty id to null and category creation returns the new id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"operation":"get_bookmark_link","success":1,"category_id":""}"""))
        assertNull(api.bookmarkCategoryId())
        server.enqueue(MockResponse().setBody("""{"operation":"create_category","success":1,"category_id":"SET_123"}"""))
        assertEquals("SET_123", api.createCategory("즐겨찾기", pinned = true))
        server.takeRequest()
        val create = server.takeRequest()
        assertEquals("PUT", create.method)
        assertTrue(create.path!!.startsWith("/api/categories?"))
        assertTrue(create.path!!.contains("pinned=1"))
    }
}
