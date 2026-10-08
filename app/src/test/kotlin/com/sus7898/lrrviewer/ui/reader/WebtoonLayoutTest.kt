package com.sus7898.lrrviewer.ui.reader

import com.sus7898.lrrviewer.data.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebtoonLayoutTest {

    @Test
    fun `unknown pages are pending, normal pages whole, tall strips tiled`() {
        val sizes = mapOf(0 to PageSize(800, 1200), 2 to PageSize(800, 5000))
        val items = buildWebtoonItems(pageCount = 3, sizes = sizes, maxTileHeight = 2048)

        assertEquals(listOf("0_0", "1_0", "2_0", "2_1", "2_2"), items.map { it.key })
        assertTrue(items[0] is WebtoonItem.Whole)
        assertTrue(items[1] is WebtoonItem.Pending)
        val tiles = items.filterIsInstance<WebtoonItem.Tile>()
        assertEquals(listOf(0, 2048, 4096), tiles.map { it.top })
        assertEquals(listOf(2048, 2048, 904), tiles.map { it.height })
        assertEquals(3, tiles.first().count)
        assertEquals(800f / 2048f, tiles.first().aspect, 1e-6f)
        assertEquals(800f / 904f, tiles.last().aspect, 1e-6f)
    }

    @Test
    fun `tile zero keeps the pending key so the scroll anchor survives size discovery`() {
        assertEquals(WebtoonItem.Pending(4).key, WebtoonItem.Tile(4, 0, 2, 0, 2048, 800).key)
        assertEquals(WebtoonItem.Pending(4).key, WebtoonItem.Whole(4, PageSize(800, 1000)).key)
    }

    @Test
    fun `page to row mapping`() {
        val items = buildWebtoonItems(3, mapOf(1 to PageSize(800, 4100)))
        assertEquals(0, items.firstIndexOfPage(0))
        assertEquals(1, items.firstIndexOfPage(1))
        assertEquals(4, items.firstIndexOfPage(2))
        assertEquals(items.lastIndex, items.firstIndexOfPage(99))
        assertEquals(null, emptyList<WebtoonItem>().firstIndexOfPage(0))
    }

    @Test
    fun `sample size is the largest power of two that still covers the target width`() {
        assertEquals(1, sampleSizeFor(800, 1080))
        assertEquals(1, sampleSizeFor(2000, 1080))
        assertEquals(2, sampleSizeFor(2160, 1080))
        assertEquals(4, sampleSizeFor(5000, 1080))
        assertEquals(1, sampleSizeFor(0, 1080))
    }
}
