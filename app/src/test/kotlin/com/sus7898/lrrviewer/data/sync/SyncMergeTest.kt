package com.sus7898.lrrviewer.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    private val local = SyncSnapshot(
        server = "nas.example.com",
        settings = SyncedSettings(readingMode = "RTL"),
        settingsChangedAt = 100,
        categoryOrder = listOf("a", "b"),
        categoryOrderChangedAt = 50,
        recentSearches = listOf("artist:foo", "bar"),
        progress = listOf(
            SyncedProgress("x", "X", page = 3, pageCount = 10, lastReadAt = 1_000),
            SyncedProgress("y", "Y", page = 9, pageCount = 10, lastReadAt = 5_000),
        ),
    )

    @Test
    fun `newer remote settings, order and history rows win and older ones are ignored`() {
        val remote = SyncSnapshot(
            server = "nas.example.com",
            settings = SyncedSettings(readingMode = "WEBTOON"),
            settingsChangedAt = 200,
            categoryOrder = listOf("b", "a"),
            categoryOrderChangedAt = 60,
            recentSearches = listOf("baz", "bar"),
            searchesChangedAt = 300,
            progress = listOf(
                SyncedProgress("x", "X", page = 7, pageCount = 10, lastReadAt = 2_000, readingModeOverride = "LTR"), // newer
                SyncedProgress("y", "Y", page = 1, pageCount = 10, lastReadAt = 4_000), // older -> ignored
                SyncedProgress("z", "Z", page = 0, pageCount = 5, lastReadAt = 3_000), // new row
            ),
        )
        val (result, merged) = SyncMerge.plan(local, remote)

        assertTrue(result.settingsApplied)
        assertEquals("WEBTOON", merged.settings.readingMode)
        assertEquals(200, merged.settingsChangedAt)
        assertTrue(result.categoryOrderApplied)
        assertEquals(listOf("b", "a"), merged.categoryOrder)
        assertEquals(2, result.progressUpdated)
        val rows = merged.progress.associateBy { it.arcid }
        assertEquals(7, rows.getValue("x").page)
        assertEquals("LTR", rows.getValue("x").readingModeOverride)
        assertEquals(9, rows.getValue("y").page)
        assertEquals(listOf("y", "z", "x"), merged.progress.map { it.arcid }) // newest first
        assertEquals(listOf("baz", "bar"), merged.recentSearches) // newer list wins (so clearing it syncs too)
        assertTrue(result.searchesApplied)
    }

    @Test
    fun `older remote changes nothing`() {
        val remote = local.copy(settings = SyncedSettings(readingMode = "LTR"), settingsChangedAt = 10, categoryOrderChangedAt = 1, progress = emptyList())
        val (result, merged) = SyncMerge.plan(local, remote)
        assertFalse(result.changedAnything)
        assertEquals("RTL", merged.settings.readingMode)
        assertEquals(local.progress.size, merged.progress.size)
    }

    @Test
    fun `history and category order of another server are not merged, settings still are`() {
        val remote = SyncSnapshot(
            server = "other.example.com",
            settings = SyncedSettings(readingMode = "LTR"),
            settingsChangedAt = 999,
            categoryOrder = listOf("q"),
            categoryOrderChangedAt = 999,
            progress = listOf(SyncedProgress("x", lastReadAt = 99_999)),
        )
        val (result, merged) = SyncMerge.plan(local, remote)
        assertTrue(result.settingsApplied)
        assertFalse(result.categoryOrderApplied)
        assertEquals(0, result.progressUpdated)
        assertTrue(result.skippedOtherServer)
        assertEquals(listOf("a", "b"), merged.categoryOrder)
    }

    @Test
    fun `server id ignores scheme, trailing slash and case`() {
        assertEquals("nas.example.com/lrr", SyncMerge.serverId("HTTPS://NAS.example.com/lrr/"))
        assertEquals("192.168.0.10:3000", SyncMerge.serverId("http://192.168.0.10:3000"))
    }

    @Test
    fun `a deletion on the other device removes the row unless it was read again later`() {
        val remote = SyncSnapshot(
            server = "nas.example.com",
            deletedProgress = mapOf("x" to 1_500L, "y" to 4_000L), // x deleted after it was read (1_000), y before (5_000)
        )
        val (result, merged) = SyncMerge.plan(local, remote)
        assertEquals(listOf("y"), merged.progress.map { it.arcid })
        assertEquals(1, result.progressDeleted)
        assertEquals(1_500L, merged.deletedProgress["x"])
    }

    @Test
    fun `a local deletion keeps the file's old copy of the row from coming back`() {
        val localWithTombstone = local.copy(progress = local.progress.filter { it.arcid != "x" }, deletedProgress = mapOf("x" to 2_000L))
        val remote = SyncSnapshot(server = "nas.example.com", progress = listOf(SyncedProgress("x", lastReadAt = 1_000)))
        val (result, merged) = SyncMerge.plan(localWithTombstone, remote)
        assertEquals(0, result.progressUpdated)
        assertFalse(merged.progress.any { it.arcid == "x" })
        // ...but reading it again on the other device after the deletion brings it back.
        val readAgain = remote.copy(progress = listOf(SyncedProgress("x", lastReadAt = 3_000)))
        assertTrue(SyncMerge.plan(localWithTombstone, readAgain).second.progress.any { it.arcid == "x" })
    }

    @Test
    fun `clearing recent searches on one device wins over an older non-empty list`() {
        val cleared = local.copy(recentSearches = emptyList(), searchesChangedAt = 900)
        val older = SyncSnapshot(server = "nas.example.com", recentSearches = listOf("a", "b"), searchesChangedAt = 100)
        assertTrue(SyncMerge.plan(cleared, older).second.recentSearches.isEmpty())
    }
}
