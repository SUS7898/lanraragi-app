package com.sus7898.lrrviewer.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class LrrUrlsTest {

    @Test
    fun `normalize adds scheme and strips trailing slash`() {
        assertEquals("http://192.168.0.10:3000", LrrUrls.normalizeBaseUrl(" 192.168.0.10:3000/ "))
        assertEquals("https://nas.example.com/lrr", LrrUrls.normalizeBaseUrl("HTTPS://nas.example.com/lrr/"))
        assertEquals("http://nas.local", LrrUrls.normalizeBaseUrl("http://nas.local/?x=1#frag"))
        assertEquals("", LrrUrls.normalizeBaseUrl("   "))
    }

    @Test
    fun `page url resolution without sub path`() {
        val url = LrrUrls.resolvePageUrl("http://nas:3000", "./api/archives/abc/page?path=001%20a.jpg")
        assertEquals("http://nas:3000/api/archives/abc/page?path=001%20a.jpg", url)
    }

    @Test
    fun `page url resolution behind reverse proxy sub path`() {
        val url = LrrUrls.resolvePageUrl("https://nas.example.com/lrr", "/lrr/api/archives/abc/page?path=sub/01.png")
        assertEquals("https://nas.example.com/lrr/api/archives/abc/page?path=sub/01.png", url)
    }

    @Test
    fun `absolute urls are left untouched`() {
        assertEquals("http://x/y.jpg", LrrUrls.resolvePageUrl("http://nas", "http://x/y.jpg"))
    }
}
