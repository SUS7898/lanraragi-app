package com.sus7898.lrrviewer

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.data.ReadingHistoryRepository
import com.sus7898.lrrviewer.data.SecureStore
import com.sus7898.lrrviewer.data.SettingsRepository
import com.sus7898.lrrviewer.data.api.LrrApi
import com.sus7898.lrrviewer.data.api.LrrAuthInterceptor
import com.sus7898.lrrviewer.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Hand-rolled dependency graph (small app, no DI framework needed). */
class AppGraph(private val app: Application) {

    val appContext: Context get() = app

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val secure = SecureStore()
    val settings = SettingsRepository(app, secure)

    /** Always-current settings snapshot for synchronous consumers (interceptor, URL builders). */
    val settingsState: StateFlow<AppSettings> =
        settings.flow.stateIn(scope, SharingStarted.Eagerly, runBlocking { settings.current() })

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(LrrAuthInterceptor(settingsState))
        .build()

    val api = LrrApi(httpClient, settingsState)
    val history = ReadingHistoryRepository(app)

    val imageLoader: ImageLoader = ImageLoader.Builder(app)
        .okHttpClient(httpClient)
        // LANraragi serves pages without cache headers; keep them on disk anyway so re-reading is free.
        .respectCacheHeaders(false)
        .diskCache {
            DiskCache.Builder()
                .directory(app.cacheDir.resolve("image_cache"))
                .maxSizeBytes(settingsState.value.cacheSizeMb.toLong() * 1024L * 1024L)
                .build()
        }
        .memoryCache { MemoryCache.Builder(app).maxSizePercent(0.25).build() }
        .crossfade(false)
        .build()

    val updater = UpdateManager(app, httpClient, scope)

    /** A tag search requested from another screen; the library consumes and clears it. */
    val pendingLibrarySearch = MutableStateFlow<String?>(null)
}
