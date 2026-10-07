package com.sus7898.lrrviewer.data

import com.sus7898.lrrviewer.data.api.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorySortingTest {

    private val cats = listOf(
        Category(id = "c10", name = "Vol 10"),
        Category(id = "c2", name = "Vol 2"),
        Category(id = "pin", name = "즐겨찾기", pinned = true),
        Category(id = "b", name = "가나다"),
        Category(id = "a", name = "Apple"),
    )

    @Test
    fun `name order is natural and keeps pinned first`() {
        // Korean collation puts Latin before Hangul; "Vol 2" sorts before "Vol 10" because digit runs compare numerically.
        assertEquals(listOf("pin", "a", "c2", "c10", "b"), sortCategories(cats, CategorySort.NAME).map { it.id })
    }

    @Test
    fun `server order only moves pinned to the front`() {
        assertEquals(listOf("pin", "c10", "c2", "b", "a"), sortCategories(cats, CategorySort.SERVER).map { it.id })
    }

    @Test
    fun `manual order follows the saved ids and appends unknown categories by name`() {
        val order = listOf("b", "c10", "missing")
        assertEquals(listOf("b", "c10", "a", "c2", "pin"), sortCategories(cats, CategorySort.MANUAL, order).map { it.id })
    }

    @Test
    fun `natural order compares digit runs numerically`() {
        assertEquals(listOf("v1", "v2", "v10", "v100"), listOf("v100", "v10", "v2", "v1").sortedWith(NaturalOrder))
    }
}
