package com.sus7898.lrrviewer.ui.reader

import com.sus7898.lrrviewer.data.PageSize

/** One pager slot of the paged reader: a single page, or two facing pages shown side by side. */
data class Spread(val pages: List<Int>) {
    val first: Int get() = pages.first()
    val key: String get() = pages.joinToString("_")
    val isDouble: Boolean get() = pages.size == 2
}

/**
 * Groups pages into spreads. With [twoPage] off every page is its own spread. Otherwise portrait pages are
 * paired; the cover stays alone when [coverAlone]; a page wider than tall (a scanned double page) is never
 * paired; a page whose size is still unknown is assumed portrait and may be re-grouped once its size arrives.
 */
fun buildSpreads(pageCount: Int, sizes: Map<Int, PageSize>, twoPage: Boolean, coverAlone: Boolean): List<Spread> {
    if (pageCount <= 0) return emptyList()
    if (!twoPage) return (0 until pageCount).map { Spread(listOf(it)) }
    fun wide(i: Int) = sizes[i]?.let { it.width > 0 && it.height > 0 && it.width > it.height } == true
    val out = ArrayList<Spread>()
    var i = 0
    while (i < pageCount) {
        val alone = (i == 0 && coverAlone) || wide(i) || i + 1 >= pageCount || wide(i + 1)
        if (alone) {
            out += Spread(listOf(i))
            i += 1
        } else {
            out += Spread(listOf(i, i + 1))
            i += 2
        }
    }
    return out
}

/** Pager index of the spread containing [page] (clamped to the list). */
fun List<Spread>.indexOfPage(page: Int): Int {
    if (isEmpty()) return 0
    val idx = indexOfFirst { page in it.pages }
    return if (idx >= 0) idx else if (page < first().first) 0 else lastIndex
}
