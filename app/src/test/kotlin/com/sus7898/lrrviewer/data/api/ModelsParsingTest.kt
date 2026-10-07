package com.sus7898.lrrviewer.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the exact failure class that breaks the Mihon extension:
 * `NumberFormatException: For input string: ""` caused by empty/stringly-typed numbers.
 */
class ModelsParsingTest {

    private val json = LrrJson.instance

    @Test
    fun `empty and stringly numbers do not throw`() {
        val raw = """
            {"arcid":"abc","title":"T","tags":"artist:foo, date_added:, pages:12","isnew":"none",
             "pagecount":"", "progress":"3", "lastreadtime":null, "size":"1234", "extension":"zip"}
        """.trimIndent()
        val a = json.decodeFromString<Archive>(raw)
        assertEquals(0, a.pagecount)
        assertEquals(3, a.progress)
        assertEquals(0L, a.lastreadtime)
        assertEquals(1234L, a.size)
        assertFalse(a.isnew)
        assertNull("empty date_added must not crash", a.dateAdded)
    }

    @Test
    fun `boolean variants`() {
        assertTrue(json.decodeFromString<Archive>("""{"arcid":"a","isnew":"true"}""").isnew)
        assertTrue(json.decodeFromString<Archive>("""{"arcid":"a","isnew":true}""").isnew)
        assertTrue(json.decodeFromString<Archive>("""{"arcid":"a","isnew":1}""").isnew)
        assertFalse(json.decodeFromString<Archive>("""{"arcid":"a","isnew":"false"}""").isnew)
        assertFalse(json.decodeFromString<Archive>("""{"arcid":"a"}""").isnew)
    }

    @Test
    fun `tags are parsed into namespaces and date_added is read`() {
        val a = json.decodeFromString<Archive>("""{"arcid":"a","tags":"artist:foo,  group:bar baz, full color, date_added:1700000000"}""")
        assertEquals(listOf(Tag("artist", "foo"), Tag("group", "bar baz"), Tag("", "full color"), Tag("date_added", "1700000000")), a.tagList)
        assertEquals(1700000000L, a.dateAdded)
    }

    @Test
    fun `toc is parsed leniently`() {
        val a = json.decodeFromString<Archive>("""{"arcid":"a","toc":[{"name":"Ch1","page":1},{"name":"Ch2","page":"13"},{"bogus":true}]}""")
        assertEquals(listOf(TocEntry("Ch1", 1), TocEntry("Ch2", 13)), a.tocEntries)
        val none = json.decodeFromString<Archive>("""{"arcid":"a","toc":"not an array"}""")
        assertTrue(none.tocEntries.isEmpty())
    }

    @Test
    fun `server info with legacy integer booleans`() {
        val info = json.decodeFromString<ServerInfo>("""{"name":"LRR","version":"0.8.9","has_password":1,"nofun_mode":0,"server_tracks_progress":"1","archives_per_page":"50"}""")
        assertTrue(info.has_password)
        assertFalse(info.nofun_mode)
        assertTrue(info.server_tracks_progress)
        assertEquals(50, info.archives_per_page)
    }

    @Test
    fun `files response with and without job`() {
        val new = json.decodeFromString<FilesResponse>("""{"pages":["./api/archives/a/page?path=00.jpg"]}""")
        assertEquals(-1, new.job)
        assertEquals(1, new.pages.size)
        val old = json.decodeFromString<FilesResponse>("""{"job":561,"pages":[]}""")
        assertEquals(561, old.job)
    }

    @Test
    fun `tankoubon ids are detected`() {
        assertTrue(json.decodeFromString<Archive>("""{"arcid":"TANK_1234567890"}""").isTankoubon)
        assertFalse(json.decodeFromString<Archive>("""{"arcid":"28697b96f0ac5858be2614ed10ca47742c9522fd"}""").isTankoubon)
    }
}
