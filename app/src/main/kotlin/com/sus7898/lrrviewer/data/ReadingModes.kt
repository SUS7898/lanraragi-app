package com.sus7898.lrrviewer.data

/** Pages advance right-to-left (Japanese manga order). */
val ReadingMode.isRightToLeft: Boolean get() = this == ReadingMode.RTL || this == ReadingMode.CONTINUOUS_RTL

/** Free dragging through a strip instead of flipping page by page. */
val ReadingMode.isContinuous: Boolean
    get() = this == ReadingMode.WEBTOON || this == ReadingMode.CONTINUOUS_LTR || this == ReadingMode.CONTINUOUS_RTL

val ReadingMode.isHorizontalContinuous: Boolean
    get() = this == ReadingMode.CONTINUOUS_LTR || this == ReadingMode.CONTINUOUS_RTL

/**
 * The reader's "연속 보기" button: from a page-flip mode go to webtoon (vertical continuous); from a continuous
 * mode go back to what was used before ([remembered], null = the global default). Returns the new per-archive
 * override. If going back would still be continuous (the default itself is continuous), fall back to RTL paging.
 */
fun continuousToggle(current: ReadingMode?, default: ReadingMode, remembered: ReadingMode?): ReadingMode? {
    val effective = current ?: default
    if (!effective.isContinuous) return ReadingMode.WEBTOON
    val back = remembered?.takeUnless { it.isContinuous }
    val backEffective = back ?: default
    return if (backEffective.isContinuous) ReadingMode.RTL else back
}
