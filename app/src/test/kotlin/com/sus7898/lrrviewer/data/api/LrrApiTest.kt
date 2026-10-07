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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Base64

class LrrApiTest {

    private lateinit var server: MockWebServer
    private lateinit var settings: MutableStateFlow<AppSettings>
    private lateinit var api: LrrApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        settings = MutableStateFlow(AppSettings(serverUrl = server.url("/").toString(), apiKey = "secret-key"))
        val client = OkHttpClient.Builder().addInterceptor(LrrAuthInterceptor(settings)).build()
        api = LrrApi(client, settings)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `search skips malformed items instead of failing whole page`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"data":[{"arcid":"a1","title":"ok","pagecount":""},{"title":"missing id"},{"arcid":"a2","progress":"x"}],
                    "draw":0,"recordsFiltered":"3","recordsTotal":3}""",
            ),
        )
        val page = api.search(SearchQuery(filter = "artist:foo"), start = 0)
        assertEquals(listOf("a1", "a2"), page.items.map { it.arcid })
        assertEquals(1, page.skipped)
        assertEquals(3, page.filtered)

        val recorded = server.takeRequest()
        assertEquals("/api/search", recorded.requestUrl!!.encodedPath)
        assertEquals("artist:foo", recorded.requestUrl!!.queryParameter("filter"))
        assertEquals("0", recorded.requestUrl!!.queryParameter("start"))
        val expectedAuth = "Bearer " + Base64.getEncoder().encodeToString("secret-key".toByteArray())
        assertEquals(expectedAuth, recorded.getHeader("Authorization"))
    }

    @Test
    fun `no auth header when api key is blank`() = runBlocking {
        settings.value = settings.value.copy(apiKey = "")
        server.enqueue(MockResponse().setBody("""{"name":"LRR","version":"0.9.30"}"""))
        api.info()
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `401 maps to Unauthorized`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"This API is protected"}"""))
        try {
            api.info()
            fail("expected Unauthorized")
        } catch (e: LrrException.Unauthorized) {
            assertTrue(e.userMessage().contains("API 키"))
        }
    }

    @Test
    fun `files are resolved to absolute urls on this server`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"pages":["./api/archives/abc/page?path=00.jpg","./api/archives/abc/page?path=01.jpg"]}"""))
        val files = api.files("abc")
        val pages = api.resolvePages(files.pages)
        assertEquals(2, pages.size)
        assertTrue(pages[0].startsWith(server.url("/").toString().trimEnd('/') + "/api/archives/abc/page?path=00.jpg"))
    }

    @Test
    fun `progress update is a PUT with 1-based page`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"operation":"update_progress","success":1}"""))
        api.updateProgress("abc", 12)
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/api/archives/abc/progress/12", req.path)
    }

    @Test
    fun `cleartext can be blocked by setting`() = runBlocking {
        settings.value = settings.value.copy(allowCleartext = false)
        try {
            api.info()
            fail("expected cleartext block")
        } catch (e: java.io.IOException) {
            assertTrue(e.message!!.contains("HTTP"))
        }
    }
}
