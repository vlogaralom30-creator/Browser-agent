package com.example.data

import com.example.model.BrowserTab
import com.example.model.SearchEngine
import com.example.model.ShortcutItem
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class BrowserRepository(
    private val bookmarkDao: BookmarkDao,
    private val historyDao: HistoryDao,
    private val downloadDao: DownloadDao,
    private val preferences: BrowserPreferences
) {
    // Bookmarks
    val allBookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.getAllBookmarks()

    fun searchBookmarks(query: String): Flow<List<BookmarkEntity>> =
        bookmarkDao.searchBookmarks(query)

    suspend fun isBookmarked(url: String): Boolean {
        if (url.isBlank()) return false
        return bookmarkDao.getBookmarkByUrl(url) != null
    }

    suspend fun addBookmark(title: String, url: String) {
        if (url.isBlank()) return
        val existing = bookmarkDao.getBookmarkByUrl(url)
        if (existing == null) {
            bookmarkDao.insertBookmark(
                BookmarkEntity(
                    title = title.ifBlank { url },
                    url = url
                )
            )
        }
    }

    suspend fun removeBookmark(url: String) {
        bookmarkDao.deleteBookmarkByUrl(url)
    }

    suspend fun deleteBookmark(bookmark: BookmarkEntity) {
        bookmarkDao.deleteBookmark(bookmark)
    }

    suspend fun updateBookmark(bookmark: BookmarkEntity) {
        bookmarkDao.updateBookmark(bookmark)
    }

    suspend fun clearAllBookmarks() {
        bookmarkDao.clearAllBookmarks()
    }

    // History
    val allHistory: Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    fun searchHistory(query: String): Flow<List<HistoryEntity>> =
        historyDao.searchHistory(query)

    suspend fun addHistory(title: String, url: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("browser:")) return
        historyDao.insertHistory(
            HistoryEntity(
                title = title.ifBlank { url },
                url = url,
                visitTime = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteHistory(history: HistoryEntity) {
        historyDao.deleteHistory(history)
    }

    suspend fun clearAllHistory() {
        historyDao.clearAllHistory()
    }

    // Downloads
    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    suspend fun addDownload(download: DownloadEntity): Long {
        return downloadDao.insertDownload(download)
    }

    suspend fun updateDownload(download: DownloadEntity) {
        downloadDao.updateDownload(download)
    }

    suspend fun deleteDownload(download: DownloadEntity) {
        downloadDao.deleteDownload(download)
    }

    suspend fun clearAllDownloads() {
        downloadDao.clearAllDownloads()
    }

    // Preferences
    var searchEngine: SearchEngine
        get() = preferences.searchEngine
        set(value) {
            preferences.searchEngine = value
        }

    var themeMode: AppThemeMode
        get() = preferences.themeMode
        set(value) {
            preferences.themeMode = value
        }

    var homePageUrl: String
        get() = preferences.homePageUrl
        set(value) {
            preferences.homePageUrl = value
        }

    var isDesktopModeDefault: Boolean
        get() = preferences.isDesktopModeDefault
        set(value) {
            preferences.isDesktopModeDefault = value
        }

    var isJavaScriptEnabled: Boolean
        get() = preferences.isJavaScriptEnabled
        set(value) {
            preferences.isJavaScriptEnabled = value
        }

    var isDoNotTrackEnabled: Boolean
        get() = preferences.isDoNotTrackEnabled
        set(value) {
            preferences.isDoNotTrackEnabled = value
        }

    var isSmartPrivateProtectionEnabled: Boolean
        get() = preferences.isSmartPrivateProtectionEnabled
        set(value) {
            preferences.isSmartPrivateProtectionEnabled = value
        }

    var isAdBlockEnabled: Boolean
        get() = preferences.isAdBlockEnabled
        set(value) {
            preferences.isAdBlockEnabled = value
        }

    var adBlockWhitelistedDomains: Set<String>
        get() = preferences.adBlockWhitelistedDomains
        set(value) {
            preferences.adBlockWhitelistedDomains = value
        }

    // Custom shortcuts
    fun getCustomShortcuts(): List<ShortcutItem> {
        val json = preferences.customShortcutsJson
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<ShortcutItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ShortcutItem(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        url = obj.optString("url"),
                        initial = obj.optString("initial", "W"),
                        isCustom = true
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomShortcut(title: String, url: String) {
        val current = getCustomShortcuts().toMutableList()
        val initial = if (title.isNotBlank()) title.take(1).uppercase() else "W"
        current.add(
            ShortcutItem(
                id = System.currentTimeMillis().toString(),
                title = title.ifBlank { url },
                url = url,
                initial = initial,
                isCustom = true
            )
        )
        saveShortcutsList(current)
    }

    fun deleteCustomShortcut(id: String) {
        val current = getCustomShortcuts().filterNot { it.id == id }
        saveShortcutsList(current)
    }

    private fun saveShortcutsList(list: List<ShortcutItem>) {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("url", item.url)
                put("initial", item.initial)
            }
            array.put(obj)
        }
        preferences.customShortcutsJson = array.toString()
    }

    // Normal Tabs persistence
    fun saveNormalTabs(tabs: List<BrowserTab>) {
        try {
            val array = JSONArray()
            tabs.filterNot { it.isIncognito }.forEach { tab ->
                val obj = JSONObject().apply {
                    put("id", tab.id)
                    put("url", tab.url)
                    put("title", tab.title)
                    put("isDesktopMode", tab.isDesktopMode)
                }
                array.put(obj)
            }
            preferences.savedTabsJson = array.toString()
        } catch (e: Exception) {
            // Ignore persistence errors
        }
    }

    fun loadSavedNormalTabs(): List<BrowserTab> {
        val json = preferences.savedTabsJson
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<BrowserTab>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    BrowserTab(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        url = obj.optString("url", ""),
                        title = obj.optString("title", "New Tab"),
                        displayUrl = obj.optString("url", ""),
                        isDesktopMode = obj.optBoolean("isDesktopMode", false),
                        isIncognito = false
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
