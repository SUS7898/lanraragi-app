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
        val AUTO_UPDATE = booleanPreferencesKey("auto_check_updates")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
    }

    val flow: Flow<AppSettings> = context.settingsStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    suspend fun current(): AppSettings = flow.first()

    /** Atomically read-modify-write the settings. */
    suspend fun edit(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val before = prefs.toSettings()
            val after = transform(before)
            val encryptedKey = when {
                after.apiKey == before.apiKey -> prefs[Keys.API_KEY_ENC] ?: ""
                after.apiKey.isBlank() -> ""
                else -> secure.encrypt(after.apiKey)
            }
            prefs.write(after.copy(serverUrl = LrrUrls.normalizeBaseUrl(after.serverUrl)), encryptedKey)
        }
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
        autoCheckUpdates = this[Keys.AUTO_UPDATE] ?: true,
        lastUpdateCheck = this[Keys.LAST_UPDATE_CHECK] ?: 0L,
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
        this[Keys.AUTO_UPDATE] = s.autoCheckUpdates
        this[Keys.LAST_UPDATE_CHECK] = s.lastUpdateCheck
    }
}

private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
