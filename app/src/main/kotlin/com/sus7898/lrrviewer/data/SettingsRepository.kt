package com.sus7898.lrrviewer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sus7898.lrrviewer.data.api.LrrUrls
import com.sus7898.lrrviewer.data.sync.SyncedSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context, private val secure: SecureStore) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val API_KEY_ENC = stringPreferencesKey("api_key_enc")
        val ALLOW_CLEARTEXT = booleanPreferencesKey("allow_cleartext")
        val READING_MODE = stringPreferencesKey("reading_mode")
        val FIT_MODE = stringPreferencesKey("fit_mode")
        val BACKGROUND = stringPreferencesKey("reader_background")
        val TAP_NAV = booleanPreferencesKey("tap_navigation")
        val VOLUME_NAV = booleanPreferencesKey("volume_key_navigation")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SHOW_PAGE_NUMBER = booleanPreferencesKey("show_page_number")
        val PREFETCH = intPreferencesKey("prefetch_pages")
        val SYNC_PROGRESS = booleanPreferencesKey("sync_progress")
        val CLEAR_NEW = booleanPreferencesKey("clear_new_on_read")
        val CACHE_MB = intPreferencesKey("cache_size_mb")
        val THEME = stringPreferencesKey("theme_mode")
        val GRID_MIN_DP = intPreferencesKey("grid_min_column_dp")
        val GROUP_TANKS = booleanPreferencesKey("group_by_tankoubon")
        val CATEGORY_SORT = stringPreferencesKey("category_sort")
        val SPREAD_LANDSCAPE = booleanPreferencesKey("spread_in_landscape")
        val SPREAD_COVER_ALONE = booleanPreferencesKey("spread_cover_alone")
        val READER_BRIGHTNESS = intPreferencesKey("reader_brightness")
        val COLOR_FILTER = stringPreferencesKey("color_filter")
        val ANIMATE_PAGE_TURN = booleanPreferencesKey("animate_page_turn")
        val SHOW_CONTINUE = booleanPreferencesKey("show_continue_reading")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val WEBTOON_GAP = intPreferencesKey("webtoon_gap_dp")
        val AUTO_SCROLL_SPEED = intPreferencesKey("auto_scroll_speed")
        val LIBRARY_LIST = booleanPreferencesKey("library_list_view")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val APP_LOCK_GRACE = intPreferencesKey("app_lock_grace_sec")
        val HIDE_IN_RECENTS = booleanPreferencesKey("hide_in_recents")
        val AUTO_UPDATE = booleanPreferencesKey("auto_check_updates")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val SYNC_FILE_URI = stringPreferencesKey("sync_file_uri")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val SETTINGS_CHANGED_AT = longPreferencesKey("settings_changed_at")
        val CATEGORY_ORDER_CHANGED_AT = longPreferencesKey("category_order_changed_at")
    }

    val flow: Flow<AppSettings> = context.settingsStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    suspend fun current(): AppSettings = flow.first()

    /**
     * Atomically read-modify-write the settings. When the synced subset ([SyncedSettings]) changes,
     * [AppSettings.settingsChangedAt] is bumped so the sync merge knows which side is newer.
     */
    suspend fun edit(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val before = prefs.toSettings()
            var after = transform(before)
            if (after.settingsChangedAt == before.settingsChangedAt && SyncedSettings.from(after) != SyncedSettings.from(before)) {
                after = after.copy(settingsChangedAt = System.currentTimeMillis())
            }
            val encryptedKey = when {
                after.apiKey == before.apiKey -> prefs[Keys.API_KEY_ENC] ?: ""
                after.apiKey.isBlank() -> ""
                else -> secure.encrypt(after.apiKey)
            }
            prefs.write(after.copy(serverUrl = LrrUrls.normalizeBaseUrl(after.serverUrl)), encryptedKey)
        }
    }

    /** Adds [query] to the front of the recent searches (deduplicated, capped). Not a synced-settings change. */
    suspend fun recordSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        edit { s -> s.copy(recentSearches = (listOf(q) + s.recentSearches.filter { it != q }).take(MAX_RECENT_SEARCHES)) }
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        serverUrl = this[Keys.SERVER_URL] ?: "",
        apiKey = this[Keys.API_KEY_ENC]?.takeIf { it.isNotBlank() }?.let { secure.decrypt(it) } ?: "",
        allowCleartext = this[Keys.ALLOW_CLEARTEXT] ?: true,
        readingMode = enumOrDefault(this[Keys.READING_MODE], ReadingMode.RTL),
        fitMode = enumOrDefault(this[Keys.FIT_MODE], FitMode.FIT),
        background = enumOrDefault(this[Keys.BACKGROUND], ReaderBackground.BLACK),
        tapNavigation = this[Keys.TAP_NAV] ?: true,
        volumeKeyNavigation = this[Keys.VOLUME_NAV] ?: true,
        keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: true,
        showPageNumber = this[Keys.SHOW_PAGE_NUMBER] ?: true,
        prefetchPages = this[Keys.PREFETCH] ?: 3,
        syncProgress = this[Keys.SYNC_PROGRESS] ?: true,
        clearNewOnRead = this[Keys.CLEAR_NEW] ?: true,
        cacheSizeMb = this[Keys.CACHE_MB] ?: 512,
        themeMode = enumOrDefault(this[Keys.THEME], ThemeMode.SYSTEM),
        gridMinColumnDp = this[Keys.GRID_MIN_DP] ?: 120,
        groupByTankoubon = this[Keys.GROUP_TANKS] ?: true,
        categorySort = enumOrDefault(this[Keys.CATEGORY_SORT], CategorySort.NAME),
        spreadInLandscape = this[Keys.SPREAD_LANDSCAPE] ?: true,
        spreadCoverAlone = this[Keys.SPREAD_COVER_ALONE] ?: true,
        readerBrightness = this[Keys.READER_BRIGHTNESS] ?: BRIGHTNESS_SYSTEM,
        colorFilter = enumOrDefault(this[Keys.COLOR_FILTER], ColorFilterMode.NONE),
        animatePageTurn = this[Keys.ANIMATE_PAGE_TURN] ?: true,
        showContinueReading = this[Keys.SHOW_CONTINUE] ?: true,
        webtoonGapDp = this[Keys.WEBTOON_GAP] ?: 0,
        autoScrollSpeed = this[Keys.AUTO_SCROLL_SPEED] ?: 3,
        libraryListView = this[Keys.LIBRARY_LIST] ?: false,
        appLock = this[Keys.APP_LOCK] ?: false,
        appLockGraceSec = this[Keys.APP_LOCK_GRACE] ?: 0,
        hideInRecents = this[Keys.HIDE_IN_RECENTS] ?: false,
        recentSearches = this[Keys.RECENT_SEARCHES].orEmpty().split('\n').filter { it.isNotBlank() }.take(MAX_RECENT_SEARCHES),
        autoCheckUpdates = this[Keys.AUTO_UPDATE] ?: true,
        lastUpdateCheck = this[Keys.LAST_UPDATE_CHECK] ?: 0L,
        syncFileUri = this[Keys.SYNC_FILE_URI] ?: "",
        lastSyncAt = this[Keys.LAST_SYNC_AT] ?: 0L,
        settingsChangedAt = this[Keys.SETTINGS_CHANGED_AT] ?: 0L,
        categoryOrderChangedAt = this[Keys.CATEGORY_ORDER_CHANGED_AT] ?: 0L,
    )

    private fun MutablePreferences.write(s: AppSettings, encryptedKey: String) {
        this[Keys.SERVER_URL] = s.serverUrl
        this[Keys.API_KEY_ENC] = encryptedKey
        this[Keys.ALLOW_CLEARTEXT] = s.allowCleartext
        this[Keys.READING_MODE] = s.readingMode.name
        this[Keys.FIT_MODE] = s.fitMode.name
        this[Keys.BACKGROUND] = s.background.name
        this[Keys.TAP_NAV] = s.tapNavigation
        this[Keys.VOLUME_NAV] = s.volumeKeyNavigation
        this[Keys.KEEP_SCREEN_ON] = s.keepScreenOn
        this[Keys.SHOW_PAGE_NUMBER] = s.showPageNumber
        this[Keys.PREFETCH] = s.prefetchPages.coerceIn(0, 10)
        this[Keys.SYNC_PROGRESS] = s.syncProgress
        this[Keys.CLEAR_NEW] = s.clearNewOnRead
        this[Keys.CACHE_MB] = s.cacheSizeMb.coerceIn(64, 8192)
        this[Keys.THEME] = s.themeMode.name
        this[Keys.GRID_MIN_DP] = s.gridMinColumnDp.coerceIn(80, 240)
        this[Keys.GROUP_TANKS] = s.groupByTankoubon
        this[Keys.CATEGORY_SORT] = s.categorySort.name
        this[Keys.SPREAD_LANDSCAPE] = s.spreadInLandscape
        this[Keys.SPREAD_COVER_ALONE] = s.spreadCoverAlone
        this[Keys.READER_BRIGHTNESS] = if (s.readerBrightness in 1..100) s.readerBrightness else BRIGHTNESS_SYSTEM
        this[Keys.COLOR_FILTER] = s.colorFilter.name
        this[Keys.ANIMATE_PAGE_TURN] = s.animatePageTurn
        this[Keys.SHOW_CONTINUE] = s.showContinueReading
        this[Keys.WEBTOON_GAP] = s.webtoonGapDp.coerceIn(0, 32)
        this[Keys.AUTO_SCROLL_SPEED] = s.autoScrollSpeed.coerceIn(1, 10)
        this[Keys.LIBRARY_LIST] = s.libraryListView
        this[Keys.APP_LOCK] = s.appLock
        this[Keys.APP_LOCK_GRACE] = s.appLockGraceSec.coerceIn(0, 3600)
        this[Keys.HIDE_IN_RECENTS] = s.hideInRecents
        this[Keys.RECENT_SEARCHES] = s.recentSearches.map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }
            .distinct().take(MAX_RECENT_SEARCHES).joinToString("\n")
        this[Keys.AUTO_UPDATE] = s.autoCheckUpdates
        this[Keys.LAST_UPDATE_CHECK] = s.lastUpdateCheck
        this[Keys.SYNC_FILE_URI] = s.syncFileUri
        this[Keys.LAST_SYNC_AT] = s.lastSyncAt
        this[Keys.SETTINGS_CHANGED_AT] = s.settingsChangedAt
        this[Keys.CATEGORY_ORDER_CHANGED_AT] = s.categoryOrderChangedAt
    }
}

private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
