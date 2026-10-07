package com.sus7898.lrrviewer

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the stack trace of the last uncaught exception in `files/crash/last-crash.txt` so it can be read
 * from Settings after a restart. Nothing is sent anywhere; the user shares it by hand if they want to.
 */
object CrashLog {
    private const val DIR = "crash"
    private const val FILE = "last-crash.txt"
    private const val MAX_BYTES = 64 * 1024

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(app, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun file(context: Context): File = File(File(context.filesDir, DIR), FILE)

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val text = buildString {
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())).append('\n')
            append("LRR Viewer ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" · Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Thread: ").append(thread.name).append("\n\n")
            append(sw.toString().take(MAX_BYTES))
        }
        val f = file(context)
        f.parentFile?.mkdirs()
        f.writeText(text)
    }

    fun read(context: Context): String? = file(context).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) { file(context).delete() }
}
