package com.sus7898.lrrviewer.data

enum class ReadingMode(val label: String) {
    LTR("좌→우 넘김"),
    RTL("우→좌 넘김 (만화)"),
    VERTICAL("세로 넘김"),
    WEBTOON("웹툰 (연속 스크롤)"),
}

enum class FitMode(val label: String) {
    FIT("화면 맞춤"),
    FILL_WIDTH("가로 채움"),
    FILL_HEIGHT("세로 채움"),
}

enum class ReaderBackground(val label: String) {
    BLACK("검정"),
    WHITE("흰색"),
    GRAY("회색"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("시스템"),
    LIGHT("라이트"),
    DARK("다크"),
}

/**
 * All user preferences. The API key is kept decrypted only in memory; on disk it is
 * encrypted with an Android Keystore key (see [SecureStore]).
 */
data class AppSettings(
    val serverUrl: String = "",
    val apiKey: String = "",
    val allowCleartext: Boolean = true,
    val readingMode: ReadingMode = ReadingMode.RTL,
    val fitMode: FitMode = FitMode.FIT,
    val background: ReaderBackground = ReaderBackground.BLACK,
    val tapNavigation: Boolean = true,
    val volumeKeyNavigation: Boolean = true,
    val keepScreenOn: Boolean = true,
    val showPageNumber: Boolean = true,
    val prefetchPages: Int = 3,
    val syncProgress: Boolean = true,
    val clearNewOnRead: Boolean = true,
    val cacheSizeMb: Int = 512,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val gridMinColumnDp: Int = 120,
    val groupByTankoubon: Boolean = true,
    val categorySort: CategorySort = CategorySort.NAME,
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheck: Long = 0L,
) {
    val isServerConfigured: Boolean get() = serverUrl.isNotBlank()
}
