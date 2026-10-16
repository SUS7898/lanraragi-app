package com.sus7898.lrrviewer.ui.reader

import com.sus7898.lrrviewer.data.PageSize
import org.junit.Assert.assertEquals
import org.junit.Test

class SpreadLayoutTest {

    private fun keys(spreads: List<Spread>) = spreads.map { it.key }

    @Test
    fun `single page mode keeps every page alone`() {
        assertEquals(listOf("0", "1", "2"), keys(buildSpreads(3, emptyMap(), twoPage = false, coverAlone = true)))
    }

    @Test
    fun `cover alone then pairs, odd last page alone`() {
        assertEquals(listOf("0", "1_2", "3_4", "5"), keys(buildSpreads(6, emptyMap(), twoPage = true, coverAlone = true)))
        assertEquals(listOf("0_1", "2_3", "4_5"), keys(buildSpreads(6, emptyMap(), twoPage = true, coverAlone = false)))
    }

    @Test
    fun `a landscape page is never paired and re-aligns the following pairs`() {
        val sizes = mapOf(2 to PageSize(2000, 1400), 3 to PageSize(800, 1200))
        assertEquals(listOf("0", "1", "2", "3_4", "5"), keys(buildSpreads(6, sizes, twoPage = true, coverAlone = true)))
    }

    @Test
    fun `index lookup finds the spread of a page and clamps outside the range`() {
        val spreads = buildSpreads(6, emptyMap(), twoPage = true, coverAlone = true) // 0 | 1_2 | 3_4 | 5
        assertEquals(0, spreads.indexOfPage(0))
        assertEquals(1, spreads.indexOfPage(2))
        assertEquals(2, spreads.indexOfPage(3))
        assertEquals(3, spreads.indexOfPage(5))
        assertEquals(3, spreads.indexOfPage(99))
        assertEquals(0, emptyList<Spread>().indexOfPage(4))
    }
}
