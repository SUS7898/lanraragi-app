package com.sus7898.lrrviewer

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.sus7898.lrrviewer.data.AppSettings
import com.sus7898.lrrviewer.data.CategoryOrderRepository
import com.sus7898.lrrviewer.data.FavoritesRepository
import com.sus7898.lrrviewer.data.PageImageStore
import com.sus7898.lrrviewer.data.PageInfoRepository
import com.sus7898.lrrviewer.data.ReadingProgressRepository
import com.sus7898.lrrviewer.data.SecureStore
import com.sus7898.lrrviewer.data.SettingsRepository
import com.sus7898.lrrviewer.data.api.LrrApi
import com.sus7898.lrrviewer.data.api.LrrAuthInterceptor
import com.sus7898.lrrviewer.data.db.AppDatabase
import com.sus7898.lrrviewer.ui.reader.ReaderViewModel
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
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled dependency graph (small app, no DI framework). Screens obtain their ViewModels
 * through `viewModel { ... }` and pass only the dependencies each ViewModel needs.
 */
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

    val database: AppDatabase = AppDatabase.create(app)
    val progress = ReadingProgressRepository(database.readingProgressDao())
    val categoryOrder = CategoryOrderRepository(database.categoryOrderDao())

    /** Server-side bookmarks ("좋아요"); shared by the library and the detail screen so hearts stay in sync. */
    val favorites = FavoritesRepository(api, settingsState)

    val imageLoader: ImageLoader = ImageLoader.Builder(app)
        // Same OkHttp client as the API, so the auth interceptor and cleartext policy apply to images too.
        // Coil 3 ignores HTTP cache headers by default, which is what we want: LANraragi sends none.
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { httpClient })) }
        .diskCache {
            DiskCache.Builder()
                .directory(app.cacheDir.resolve("image_cache").toOkioPath())
                .maxSizeBytes(settingsState.value.cacheSizeMb.toLong() * 1024L * 1024L)
                .build()
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(app, 0.25).build() }
        .crossfade(false)
        .build()

    /** Page files in the Coil disk cache: prefetch, dimensions, region (tile) decoding. */
    val pageStore = PageImageStore(app, imageLoader)
    val pageInfo = PageInfoRepository(database.pageInfoDao(), pageStore)

    val updater = UpdateManager(app, httpClient, scope)

    val readerDeps = ReaderViewModel.Deps(
        api = api,
        progress = progress,
        pageInfo = pageInfo,
        store = pageStore,
        settings = settingsState,
        appScope = scope,
    )

    /** A tag search requested from another screen; the library consumes and clears it. */
    val pendingLibrarySearch = MutableStateFlow<String?>(null)
}
