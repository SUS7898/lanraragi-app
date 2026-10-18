package com.sus7898.lrrviewer.ui.reader

import com.sus7898.lrrviewer.data.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebtoonExtrasTest {

    @Test
    fun `page gap goes only after the last tile of a page`() {
        val items = buildWebtoonItems(3, mapOf(0 to PageSize(800, 1000), 1 to PageSize(800, 5000), 2 to PageSize(800, 900)))
        // rows: 0_0 | 1_0 1_1 1_2 | 2_0
        assertEquals(listOf(true, false, false, true, true), items.indices.map { items.isPageEnd(it) })
    }

    @Test
    fun `auto scroll speed scales with the step and density and is clamped`() {
        assertEquals(90f, autoScrollPxPerSecond(3, 1f), 0.001f)
        assertEquals(270f, autoScrollPxPerSecond(3, 3f), 0.001f)
        assertEquals(300f, autoScrollPxPerSecond(99, 1f), 0.001f)
        assertEquals(30f, autoScrollPxPerSecond(0, 1f), 0.001f)
        assertTrue(autoScrollPxPerSecond(10, 1f) > autoScrollPxPerSecond(1, 1f))
    }
}
