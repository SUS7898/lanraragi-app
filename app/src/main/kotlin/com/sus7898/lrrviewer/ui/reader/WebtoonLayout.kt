package com.sus7898.lrrviewer.ui.reader

import com.sus7898.lrrviewer.data.PageSize

/** Tiles taller than this (source pixels) are split so no single bitmap exceeds the GPU texture limit. */
const val MAX_TILE_HEIGHT_PX = 2048

/** Aspect (w/h) used for pages whose size is not known yet. */
const val PENDING_ASPECT = 0.7f

/**
 * One LazyColumn row of the webtoon reader. [key] stays identical when a Pending page resolves into a Whole
 * page or its first Tile, so the scroll anchor does not jump when sizes arrive.
 */
sealed interface WebtoonItem {
    val page: Int
    val aspect: Float
    val key: String

    /** Size unknown: a placeholder is shown while the dimensions are read. */
    data class Pending(override val page: Int) : WebtoonItem {
        override val aspect: Float get() = PENDING_ASPECT
        override val key: String get() = "${page}_0"
    }

    /** Fits in one tile: rendered by Coil at the composable size. */
    data class Whole(override val page: Int, val size: PageSize) : WebtoonItem {
        override val aspect: Float get() = size.aspect
        override val key: String get() = "${page}_0"
    }

    /** Horizontal band [top, top+height) of a tall page, decoded with BitmapRegionDecoder. */
    data class Tile(
        override val page: Int,
        val index: Int,
        val count: Int,
        val top: Int,
        val height: Int,
        val width: Int,
    ) : WebtoonItem {
        override val aspect: Float get() = width.toFloat() / height
        override val key: String get() = "${page}_$index"
    }
}

/** Builds the row list for [pageCount] pages from the sizes known so far. */
fun buildWebtoonItems(pageCount: Int, sizes: Map<Int, PageSize>, maxTileHeight: Int = MAX_TILE_HEIGHT_PX): List<WebtoonItem> =
    buildList {
        for (page in 0 until pageCount) {
            val size = sizes[page]
            when {
                size == null || size.width <= 0 || size.height <= 0 -> add(WebtoonItem.Pending(page))
                size.height <= maxTileHeight -> add(WebtoonItem.Whole(page, size))
                else -> {
                    val count = (size.height + maxTileHeight - 1) / maxTileHeight
                    for (i in 0 until count) {
                        val top = i * maxTileHeight
                        add(WebtoonItem.Tile(page, i, count, top, minOf(maxTileHeight, size.height - top), size.width))
                    }
                }
            }
        }
    }

/** Index of the first row that belongs to [page], or null when the list is empty. */
fun List<WebtoonItem>.firstIndexOfPage(page: Int): Int? =
    indexOfFirst { it.page == page }.takeIf { it >= 0 } ?: lastIndex.takeIf { it >= 0 }

/** Largest power of two by which a [sourceWidth]px image can be downsampled and still cover [targetWidth]px. */
fun sampleSizeFor(sourceWidth: Int, targetWidth: Int): Int {
    if (sourceWidth <= 0 || targetWidth <= 0) return 1
    var sample = 1
    while (sourceWidth / (sample * 2) >= targetWidth) sample *= 2
    return sample
}
