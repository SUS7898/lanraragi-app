package com.sus7898.lrrviewer.data

import com.sus7898.lrrviewer.data.api.Category
import java.text.Collator
import java.util.Locale

enum class CategorySort(val label: String) {
    NAME("이름순"),
    SERVER("서버 순서"),
    MANUAL("수동 순서"),
}

/** "Vol 2" before "Vol 10": compares digit runs numerically and everything else with the Korean collator. */
object NaturalOrder : Comparator<String> {
    private val collator: Collator = Collator.getInstance(Locale.KOREAN)
    private val chunk = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val xa = chunk.findAll(a).map { it.value }.toList()
        val xb = chunk.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(xa.size, xb.size)) {
            val ca = xa[i]
            val cb = xb[i]
            val c = if (ca[0].isDigit() && cb[0].isDigit()) {
                ca.trimStart('0').length.compareTo(cb.trimStart('0').length).takeIf { it != 0 } ?: ca.trimStart('0').compareTo(cb.trimStart('0'))
            } else {
                collator.compare(ca, cb)
            }
            if (c != 0) return c
        }
        return xa.size.compareTo(xb.size)
    }
}

/**
 * Orders categories for display. NAME and SERVER keep pinned categories first (as the LANraragi UI does);
 * MANUAL follows [manualOrder] (category ids) and appends unknown categories by name.
 */
fun sortCategories(categories: List<Category>, mode: CategorySort, manualOrder: List<String> = emptyList()): List<Category> =
    when (mode) {
        CategorySort.SERVER -> categories.sortedByDescending { it.pinned }
        CategorySort.NAME -> categories.sortedWith(compareByDescending<Category> { it.pinned }.then(compareBy(NaturalOrder) { it.name }))
        CategorySort.MANUAL -> {
            val position = manualOrder.withIndex().associate { (i, id) -> id to i }
            val (known, unknown) = categories.partition { it.id in position }
            known.sortedBy { position.getValue(it.id) } + unknown.sortedWith(compareBy(NaturalOrder) { it.name })
        }
    }
