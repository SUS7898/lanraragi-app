package com.sus7898.lrrviewer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.util.LruCache
import androidx.core.graphics.drawable.toDrawable
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Pixel size of a page image. */
data class PageSize(val width: Int, val height: Int) {
    /** width / height; a tall webtoon strip is far below 1. */
    val aspect: Float get() = width.toFloat() / height
}

/**
 * Access to page image *files*: every page goes through Coil's disk cache (the same cache the readers use),
 * so reading the dimensions or a region of a page never downloads it twice. Region decoding is what the
 * webtoon reader uses to show 800×20000 strips as ≤2048px tiles without ever creating the full bitmap.
 */
class PageImageStore(
    private val context: Context,
    private val imageLoader: ImageLoader,
) {
    private val tileCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(16L * 1024 * 1024, 64L * 1024 * 1024).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun prefetchRequest(url: String): ImageRequest =
        ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .decoderFactory(DiskOnlyDecoder.Factory)
            .build()

    /** Streams the page into the disk cache in the background without decoding a bitmap. */
    fun enqueuePrefetch(url: String) {
        imageLoader.enqueue(prefetchRequest(url))
    }

    /** Runs [block] with the cached file of [url], downloading it first when it is not cached yet. */
    @OptIn(ExperimentalCoilApi::class)
    private suspend fun <T> withFile(url: String, block: (File) -> T): T? = withContext(Dispatchers.IO) {
        val cache = imageLoader.diskCache ?: return@withContext null
        var snapshot = cache.openSnapshot(url)
        if (snapshot == null) {
            imageLoader.execute(prefetchRequest(url))
            snapshot = cache.openSnapshot(url)
        }
        snapshot?.use { block(it.data.toFile()) }
    }

    /** Image dimensions without decoding pixels (`inJustDecodeBounds`). */
    suspend fun bounds(url: String): PageSize? = withFile(url) { file ->
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        if (opts.outWidth > 0 && opts.outHeight > 0) PageSize(opts.outWidth, opts.outHeight) else null
    }

    /**
     * Decodes the region (0,[top])–([width],[top]+[height]) of the page, downsampled by [sampleSize]
     * (a power of two). Results are kept in a small LRU so scrolling back does not decode again.
     */
    suspend fun decodeRegion(url: String, top: Int, width: Int, height: Int, sampleSize: Int): Bitmap? {
        val key = "$url#$top/$height/$sampleSize"
        tileCache.get(key)?.let { if (!it.isRecycled) return it }
        val bitmap = withFile(url) { file ->
            val decoder = if (Build.VERSION.SDK_INT >= 31) {
                file.inputStream().use { BitmapRegionDecoder.newInstance(it) }
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(file.path, false)
            }
            decoder?.let { d ->
                try {
                    val rect = Rect(0, top, width.coerceAtMost(d.width), (top + height).coerceAtMost(d.height))
                    d.decodeRegion(rect, BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) })
                } finally {
                    d.recycle()
                }
            }
        }
        if (bitmap != null) tileCache.put(key, bitmap)
        return bitmap
    }

    fun clearMemory() = tileCache.evictAll()
}

/** A decoder that decodes nothing: the fetcher has already written the bytes to the disk cache. */
internal object DiskOnlyDecoder : Decoder {
    override suspend fun decode(): DecodeResult =
        DecodeResult(image = android.graphics.Color.TRANSPARENT.toDrawable().asImage(), isSampled = false)

    object Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder {
            result.source.close()
            return DiskOnlyDecoder
        }
    }
}
