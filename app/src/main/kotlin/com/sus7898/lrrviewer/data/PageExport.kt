package com.sus7898.lrrviewer.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Share / save a single page image (the copy already in the Coil disk cache, so nothing is downloaded again).
 * Sharing goes through a FileProvider limited to `cache/share/`; saving uses MediaStore (Android 10+), so no
 * storage permission is requested.
 */
class PageExport(private val context: Context, private val store: PageImageStore) {

    private data class Typed(val mime: String, val ext: String)

    private fun typeOf(file: File): Typed {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        return when (opts.outMimeType) {
            "image/png" -> Typed("image/png", "png")
            "image/webp" -> Typed("image/webp", "webp")
            "image/gif" -> Typed("image/gif", "gif")
            "image/avif", "image/heif", "image/heic" -> Typed(opts.outMimeType!!, opts.outMimeType!!.substringAfter('/'))
            else -> Typed("image/jpeg", "jpg")
        }
    }

    private fun safeName(title: String, page1: Int): String =
        (title.ifBlank { "page" }.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").take(60) + "_p" + page1)

    /** Returns a chooser intent for sharing page [page1] of [title]. */
    suspend fun shareIntent(url: String, title: String, page1: Int): Intent = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = store.copyTo(url) { src ->
            val t = typeOf(src)
            File(dir, "${safeName(title, page1)}.${t.ext}").also { src.copyTo(it, overwrite = true) } to t.mime
        } ?: throw IOException("페이지 이미지를 불러오지 못했습니다.")
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out.first)
        val send = Intent(Intent.ACTION_SEND)
            .setType(out.second)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        Intent.createChooser(send, "페이지 공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val canSave: Boolean get() = Build.VERSION.SDK_INT >= 29

    /** Saves page [page1] to Pictures/LRR Viewer (Android 10+). */
    suspend fun save(url: String, title: String, page1: Int): String = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) throw IOException("기기에 저장은 Android 10 이상에서 지원합니다. 공유를 이용하세요.")
        val resolver = context.contentResolver
        store.copyTo(url) { src ->
            val t = typeOf(src)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "${safeName(title, page1)}.${t.ext}")
                put(MediaStore.Images.Media.MIME_TYPE, t.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LRR Viewer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw IOException("저장 위치를 만들 수 없습니다.")
            try {
                resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } } ?: throw IOException("파일에 쓸 수 없습니다.")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            "사진 › Pictures/LRR Viewer 에 저장했습니다."
        } ?: throw IOException("페이지 이미지를 불러오지 못했습니다.")
    }
}
