package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import com.example.data.BookmarkEntity
import com.example.data.BrowserDatabase
import com.example.data.BrowserPreferences
import com.example.data.BrowserRepository
import com.example.data.DownloadEntity
import com.example.data.DownloadStatus
import com.example.data.HistoryEntity
import com.example.data.AppThemeMode
import com.example.model.BrowserTab
import com.example.model.ContextMenuData
import com.example.model.PageErrorCategory
import com.example.model.SearchEngine
import com.example.model.ShortcutItem
import com.example.model.WebPermissionRequest
import com.example.util.SecurePrivateSessionManager
import com.example.util.UrlUtils
import com.example.data.SearchRepository
import com.example.model.SearchResultData
import com.example.model.CrawlBotState
import com.example.model.CrawlBotStatus
import com.example.util.SiteCrawlerEngine
import android.net.Uri
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.util.UserAgentHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

enum class BrowserScreen {
    BROWSER,
    TAB_SWITCHER,
    BOOKMARKS,
    HISTORY,
    DOWNLOADS,
    SETTINGS
}

enum class PrivatePinPromptMode {
    UNLOCK,
    SETUP
}

data class BrowserUiState(
    val normalTabs: List<BrowserTab> = emptyList(),
    val incognitoTabs: List<BrowserTab> = emptyList(),
    val activeNormalTabId: String = "",
    val activeIncognitoTabId: String = "",
    val isIncognitoMode: Boolean = false,
    val currentScreen: BrowserScreen = BrowserScreen.BROWSER,
    val searchEngine: SearchEngine = SearchEngine.NAXXIVO,
    val searchResultsMap: Map<String, SearchResultData> = emptyMap(),
    val searchLoadingMap: Map<String, Boolean> = emptyMap(),
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val isDesktopModeDefault: Boolean = false,
    val isJavaScriptEnabled: Boolean = true,
    val homePageUrl: String = "",
    val customShortcuts: List<ShortcutItem> = emptyList(),
    val showClearDataDialog: Boolean = false,
    val showAddShortcutDialog: Boolean = false,
    val showPageInfoDialog: Boolean = false,
    val findInPageQuery: String? = null,
    val findInPageMatchIndex: Int = 0,
    val findInPageMatchCount: Int = 0,
    val isCurrentTabBookmarked: Boolean = false,
    val canReopenClosedTab: Boolean = false,
    val contextMenuData: ContextMenuData? = null,
    val previewUrl: String? = null,
    val previewTitle: String? = null,
    val pendingWebPermission: WebPermissionRequest? = null,
    val pendingSslWarningTabId: String? = null,
    val isPrivateSessionLocked: Boolean = false,
    val isPrivateResumeEnabled: Boolean = false,
    val hasPrivatePin: Boolean = false,
    val showPrivatePinPrompt: Boolean = false,
    val privatePinPromptMode: PrivatePinPromptMode = PrivatePinPromptMode.UNLOCK,
    val privatePinErrorMessage: String? = null,
    val showClearPrivateDataDialog: Boolean = false,
    val isSmartPrivateProtectionEnabled: Boolean = true,
    val smartPrivateDomains: Set<String> = emptySet(),
    val smartPrivateNotification: String? = null,
    val showSmartPrivateDomainsDialog: Boolean = false,
    val isAdBlockEnabled: Boolean = true,
    val adBlockWhitelistedDomains: Set<String> = emptySet(),
    val showCrawlBotSheet: Boolean = false,
    val crawlBotState: CrawlBotState = CrawlBotState()
) {
    val activeTabs: List<BrowserTab>
        get() = if (isIncognitoMode) incognitoTabs else normalTabs

    val activeTabId: String
        get() = if (isIncognitoMode) activeIncognitoTabId else activeNormalTabId

    val currentTab: BrowserTab?
        get() = activeTabs.find { it.id == activeTabId } ?: activeTabs.firstOrNull()

    val totalTabsCount: Int
        get() = if (isIncognitoMode) incognitoTabs.size else normalTabs.size
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BrowserDatabase.getDatabase(application)
    private val preferences = BrowserPreferences(application)
    val repository = BrowserRepository(
        database.bookmarkDao(),
        database.historyDao(),
        database.downloadDao(),
        preferences
    )

    val searchRepository = SearchRepository()

    // Active WebView registry for real browser action execution
    private val registeredWebViews = mutableMapOf<String, WebView>()

    fun registerWebView(tabId: String, webView: WebView) {
        registeredWebViews[tabId] = webView
    }

    fun unregisterWebView(tabId: String) {
        registeredWebViews.remove(tabId)
    }

    fun destroyWebView(tabId: String) {
        val wv = registeredWebViews.remove(tabId)
        wv?.let {
            try {
                it.stopLoading()
                it.loadUrl("about:blank")
                (it.parent as? ViewGroup)?.removeView(it)
                it.destroy()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun getWebViewForTab(tabId: String?): WebView? {
        return if (tabId != null) registeredWebViews[tabId] else null
    }

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryEntity>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<DownloadEntity>> = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // WebViews command channel (e.g. goBack, reload, find)
    private val _webAction = MutableStateFlow<WebAction?>(null)
    val webAction: StateFlow<WebAction?> = _webAction.asStateFlow()

    sealed interface WebAction {
        data class LoadUrl(val tabId: String, val url: String) : WebAction
        data class GoBack(val tabId: String) : WebAction
        data class GoForward(val tabId: String) : WebAction
        data class Reload(val tabId: String) : WebAction
        data class StopLoading(val tabId: String) : WebAction
        data class FindInPage(val query: String, val forward: Boolean = true) : WebAction
        data class ClearFindInPage(val tabId: String) : WebAction
    }

    // Fullscreen HTML5 Video Support
    private val _customFullScreenView = MutableStateFlow<View?>(null)
    val customFullScreenView: StateFlow<View?> = _customFullScreenView.asStateFlow()

    private val _customViewCallback = MutableStateFlow<WebChromeClient.CustomViewCallback?>(null)
    val customViewCallback: StateFlow<WebChromeClient.CustomViewCallback?> = _customViewCallback.asStateFlow()

    // Private Session Security & Persistence Manager
    val privateSessionManager = SecurePrivateSessionManager(application)

    // Recently closed tabs stack
    private val recentlyClosedTabs = mutableListOf<BrowserTab>()

    val siteCrawlerEngine = SiteCrawlerEngine()

    init {
        val savedTabs = repository.loadSavedNormalTabs()
        val initialTabs = if (savedTabs.isNotEmpty()) savedTabs else listOf(BrowserTab(isIncognito = false))
        val initialActiveId = initialTabs.first().id

        // Load encrypted private session tabs if enabled
        val resumeEnabled = privateSessionManager.isPrivateResumeEnabled()
        val hasPin = privateSessionManager.isPinLockConfigured()
        val restoredPrivateTabs = if (resumeEnabled) {
            privateSessionManager.loadEncryptedPrivateSession()
        } else {
            emptyList()
        }
        val isLockedInitial = hasPin && (restoredPrivateTabs.isNotEmpty() || privateSessionManager.hasSavedPrivateSession())
        val smartPrivateEnabled = repository.isSmartPrivateProtectionEnabled
        val smartDomains = privateSessionManager.getSmartPrivateDomains()
        val adBlockEnabled = repository.isAdBlockEnabled
        val adBlockWhitelist = repository.adBlockWhitelistedDomains

        _uiState.update {
            it.copy(
                normalTabs = initialTabs,
                activeNormalTabId = initialActiveId,
                incognitoTabs = restoredPrivateTabs,
                activeIncognitoTabId = restoredPrivateTabs.firstOrNull()?.id ?: "",
                isPrivateResumeEnabled = resumeEnabled,
                hasPrivatePin = hasPin,
                isPrivateSessionLocked = isLockedInitial,
                isSmartPrivateProtectionEnabled = smartPrivateEnabled,
                smartPrivateDomains = smartDomains,
                isAdBlockEnabled = adBlockEnabled,
                adBlockWhitelistedDomains = adBlockWhitelist,
                searchEngine = repository.searchEngine,
                themeMode = repository.themeMode,
                isDesktopModeDefault = repository.isDesktopModeDefault,
                isJavaScriptEnabled = repository.isJavaScriptEnabled,
                homePageUrl = repository.homePageUrl,
                customShortcuts = repository.getCustomShortcuts()
            )
        }

        viewModelScope.launch {
            siteCrawlerEngine.botState.collect { botState ->
                _uiState.update { it.copy(crawlBotState = botState) }
            }
        }
    }

    fun showCrawlBotSheet(show: Boolean) {
        _uiState.update { it.copy(showCrawlBotSheet = show) }
    }

    fun startCrawlBot(keyword: String, startUrl: String, maxPages: Int) {
        val currentTabId = _uiState.value.currentTab?.id
        siteCrawlerEngine.startCrawl(
            keyword = keyword,
            startUrl = startUrl,
            maxPages = maxPages,
            scope = viewModelScope,
            loadUrlAction = { url -> loadUrl(url) },
            getWebViewProvider = { if (currentTabId != null) getWebViewForTab(currentTabId) else null }
        )
    }

    fun pauseCrawlBot() = siteCrawlerEngine.pauseCrawl()

    fun resumeCrawlBot() {
        val currentTabId = _uiState.value.currentTab?.id
        siteCrawlerEngine.resumeCrawl(
            scope = viewModelScope,
            loadUrlAction = { url -> loadUrl(url) },
            getWebViewProvider = { if (currentTabId != null) getWebViewForTab(currentTabId) else null }
        )
    }

    fun stopCrawlBot() = siteCrawlerEngine.stopCrawl()

    fun resetCrawlBot() = siteCrawlerEngine.resetBot()

    fun startYoutubeBot(
        query: String,
        criteria: String,
        like: Boolean,
        comment: Boolean,
        commentText: String
    ) {
        val currentTabId = _uiState.value.currentTab?.id
        siteCrawlerEngine.startYtBot(
            query = query,
            criteria = criteria,
            like = like,
            comment = comment,
            commentText = commentText,
            scope = viewModelScope,
            loadUrlAction = { url -> loadUrl(url) },
            getWebViewProvider = { if (currentTabId != null) getWebViewForTab(currentTabId) else null }
        )
    }

    fun clearYoutubeHistory(context: android.content.Context) {
        siteCrawlerEngine.clearYtHistory(context)
    }

    private fun persistPrivateSessionIfNeeded(tabs: List<BrowserTab>) {
        if (_uiState.value.isPrivateResumeEnabled) {
            privateSessionManager.saveEncryptedPrivateSession(tabs)
        }
    }

    fun openNewTab(url: String = "", isIncognito: Boolean = _uiState.value.isIncognitoMode): BrowserTab {
        val newTab = BrowserTab(
            url = url,
            isIncognito = isIncognito,
            isDesktopMode = _uiState.value.isDesktopModeDefault
        )
        _uiState.update { state ->
            if (isIncognito) {
                val updatedTabs = state.incognitoTabs + newTab
                persistPrivateSessionIfNeeded(updatedTabs)
                state.copy(
                    incognitoTabs = updatedTabs,
                    activeIncognitoTabId = newTab.id,
                    isIncognitoMode = true,
                    currentScreen = BrowserScreen.BROWSER
                )
            } else {
                val updatedTabs = state.normalTabs + newTab
                repository.saveNormalTabs(updatedTabs)
                state.copy(
                    normalTabs = updatedTabs,
                    activeNormalTabId = newTab.id,
                    isIncognitoMode = false,
                    currentScreen = BrowserScreen.BROWSER
                )
            }
        }
        if (url.isNotBlank()) {
            _webAction.value = WebAction.LoadUrl(newTab.id, url)
        }
        checkCurrentBookmark()
        return newTab
    }

    fun closeTab(tabId: String, isIncognito: Boolean) {
        destroyWebView(tabId)
        val tabToClose = if (isIncognito) {
            _uiState.value.incognitoTabs.find { it.id == tabId }
        } else {
            _uiState.value.normalTabs.find { it.id == tabId }
        }

        if (tabToClose != null && !tabToClose.isNewTab && !isIncognito && !isSmartPrivateUrl(tabToClose.url)) {
            recentlyClosedTabs.add(tabToClose)
            if (recentlyClosedTabs.size > 15) {
                recentlyClosedTabs.removeAt(0)
            }
        }

        _uiState.update { state ->
            val canReopen = recentlyClosedTabs.isNotEmpty()
            if (isIncognito) {
                val newTabs = state.incognitoTabs.filterNot { it.id == tabId }
                val newActiveId = if (state.activeIncognitoTabId == tabId) {
                    newTabs.lastOrNull()?.id ?: ""
                } else state.activeIncognitoTabId
                if (newTabs.isEmpty()) {
                    val fallback = BrowserTab(isIncognito = true)
                    val result = listOf(fallback)
                    persistPrivateSessionIfNeeded(result)
                    state.copy(
                        incognitoTabs = result,
                        activeIncognitoTabId = fallback.id,
                        canReopenClosedTab = canReopen
                    )
                } else {
                    persistPrivateSessionIfNeeded(newTabs)
                    state.copy(
                        incognitoTabs = newTabs,
                        activeIncognitoTabId = newActiveId,
                        canReopenClosedTab = canReopen
                    )
                }
            } else {
                val newTabs = state.normalTabs.filterNot { it.id == tabId }
                val newActiveId = if (state.activeNormalTabId == tabId) {
                    newTabs.lastOrNull()?.id ?: ""
                } else state.activeNormalTabId
                repository.saveNormalTabs(newTabs)
                if (newTabs.isEmpty()) {
                    val fallback = BrowserTab(isIncognito = false)
                    repository.saveNormalTabs(listOf(fallback))
                    state.copy(
                        normalTabs = listOf(fallback),
                        activeNormalTabId = fallback.id,
                        canReopenClosedTab = canReopen
                    )
                } else {
                    state.copy(
                        normalTabs = newTabs,
                        activeNormalTabId = newActiveId,
                        canReopenClosedTab = canReopen
                    )
                }
            }
        }
        checkCurrentBookmark()
    }

    fun reopenRecentlyClosedTab() {
        if (recentlyClosedTabs.isEmpty()) return
        val restored = recentlyClosedTabs.removeAt(recentlyClosedTabs.size - 1)
        openNewTab(url = restored.url, isIncognito = restored.isIncognito)
        _uiState.update { it.copy(canReopenClosedTab = recentlyClosedTabs.isNotEmpty()) }
    }

    fun closeAllTabs(isIncognito: Boolean) {
        val tabsToClose = if (isIncognito) _uiState.value.incognitoTabs else _uiState.value.normalTabs
        tabsToClose.forEach { destroyWebView(it.id) }
        if (isIncognito) {
            val fresh = BrowserTab(isIncognito = true)
            val result = listOf(fresh)
            persistPrivateSessionIfNeeded(result)
            _uiState.update {
                it.copy(
                    incognitoTabs = result,
                    activeIncognitoTabId = fresh.id
                )
            }
        } else {
            val fresh = BrowserTab(isIncognito = false)
            repository.saveNormalTabs(listOf(fresh))
            _uiState.update {
                it.copy(
                    normalTabs = listOf(fresh),
                    activeNormalTabId = fresh.id
                )
            }
        }
        checkCurrentBookmark()
    }

    fun switchTab(tabId: String, isIncognito: Boolean) {
        if (isIncognito && _uiState.value.hasPrivatePin && _uiState.value.isPrivateSessionLocked) {
            _uiState.update {
                it.copy(
                    showPrivatePinPrompt = true,
                    privatePinPromptMode = PrivatePinPromptMode.UNLOCK,
                    privatePinErrorMessage = null
                )
            }
            return
        }
        _uiState.update {
            if (isIncognito) {
                it.copy(
                    activeIncognitoTabId = tabId,
                    isIncognitoMode = true,
                    currentScreen = BrowserScreen.BROWSER
                )
            } else {
                it.copy(
                    activeNormalTabId = tabId,
                    isIncognitoMode = false,
                    currentScreen = BrowserScreen.BROWSER
                )
            }
        }
        checkCurrentBookmark()
    }

    fun setIncognitoMode(isIncognito: Boolean) {
        if (isIncognito && _uiState.value.hasPrivatePin && _uiState.value.isPrivateSessionLocked) {
            _uiState.update {
                it.copy(
                    showPrivatePinPrompt = true,
                    privatePinPromptMode = PrivatePinPromptMode.UNLOCK,
                    privatePinErrorMessage = null
                )
            }
            return
        }
        _uiState.update { state ->
            if (isIncognito && state.incognitoTabs.isEmpty()) {
                val fresh = BrowserTab(isIncognito = true)
                state.copy(
                    isIncognitoMode = true,
                    incognitoTabs = listOf(fresh),
                    activeIncognitoTabId = fresh.id
                )
            } else {
                state.copy(isIncognitoMode = isIncognito)
            }
        }
        checkCurrentBookmark()
    }

    fun navigateToScreen(screen: BrowserScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun loadUrl(input: String) {
        val current = _uiState.value.currentTab ?: return
        val finalUrl = UrlUtils.resolveInputToUrl(input, _uiState.value.searchEngine)
        if (finalUrl.isBlank()) return

        // Smart Private Site Protection: intercept in standard tabs
        if (!_uiState.value.isIncognitoMode && isSmartPrivateUrl(finalUrl)) {
            handleSmartPrivateRouting(finalUrl, isFromCurrentBlankTab = current.isNewTab)
            return
        }

        if (finalUrl.startsWith("browser://search")) {
            val query = try {
                Uri.parse(finalUrl).getQueryParameter("q") ?: input
            } catch (e: Exception) {
                input
            }
            updateTabState(current.id) { tab ->
                tab.copy(url = finalUrl, displayUrl = query, title = "Search: $query", isLoading = false, errorDescription = null)
            }
            executeNativeSearch(current.id, query)
            return
        }

        updateTabState(current.id) { tab ->
            tab.copy(url = finalUrl, displayUrl = finalUrl, isLoading = true, progress = 10, errorDescription = null)
        }
        _webAction.value = WebAction.LoadUrl(current.id, finalUrl)
        checkCurrentBookmark()
    }

    fun executeNativeSearch(tabId: String, query: String) {
        _uiState.update { state ->
            state.copy(searchLoadingMap = state.searchLoadingMap + (tabId to true))
        }
        viewModelScope.launch {
            val result = searchRepository.performSearch(query)
            _uiState.update { state ->
                state.copy(
                    searchResultsMap = state.searchResultsMap + (tabId to result),
                    searchLoadingMap = state.searchLoadingMap + (tabId to false)
                )
            }
        }
    }

    fun goBack() {
        val current = _uiState.value.currentTab ?: return
        val wv = registeredWebViews[current.id]
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
            val canBack = wv.canGoBack()
            val canForward = wv.canGoForward()
            updateTabHistoryState(current.id, canBack, canForward, wv.url)
            return
        }
        if (current.canGoBack) {
            _webAction.value = WebAction.GoBack(current.id)
        }
    }

    fun goForward() {
        val current = _uiState.value.currentTab ?: return
        val wv = registeredWebViews[current.id]
        if (wv != null && wv.canGoForward()) {
            wv.goForward()
            val canBack = wv.canGoBack()
            val canForward = wv.canGoForward()
            updateTabHistoryState(current.id, canBack, canForward, wv.url)
            return
        }
        if (current.canGoForward) {
            _webAction.value = WebAction.GoForward(current.id)
        }
    }

    fun updateTabHistoryState(tabId: String, canGoBack: Boolean, canGoForward: Boolean, url: String? = null) {
        updateTabState(tabId) {
            val resolvedUrl = if (!url.isNullOrBlank() && url != "about:blank") url else it.url
            it.copy(
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                url = resolvedUrl,
                displayUrl = resolvedUrl
            )
        }
        checkCurrentBookmark()
    }

    fun reload() {
        val current = _uiState.value.currentTab ?: return
        if (current.isLoading) {
            _webAction.value = WebAction.StopLoading(current.id)
        } else {
            _webAction.value = WebAction.Reload(current.id)
        }
    }

    fun goHome() {
        val homeUrl = _uiState.value.homePageUrl
        if (homeUrl.isNotBlank()) {
            loadUrl(homeUrl)
        } else {
            // Load new tab page
            val current = _uiState.value.currentTab ?: return
            updateTabState(current.id) {
                it.copy(url = "", displayUrl = "", title = "New Tab", progress = 0, isLoading = false, canGoBack = false, canGoForward = false)
            }
            _webAction.value = WebAction.LoadUrl(current.id, "about:blank")
        }
    }

    fun toggleDesktopMode() {
        val current = _uiState.value.currentTab ?: return
        val newMode = !current.isDesktopMode
        updateTabState(current.id) { it.copy(isDesktopMode = newMode) }
        val wv = registeredWebViews[current.id]
        if (wv != null) {
            UserAgentHelper.switchUserAgent(wv, newMode)
            if (wv.url != null && wv.url != "about:blank") {
                wv.reload()
            }
        } else {
            _webAction.value = WebAction.Reload(current.id)
        }
    }

    fun toggleBookmark(context: Context? = null) {
        val current = _uiState.value.currentTab ?: return
        val rawUrl = current.url.trim()
        if (rawUrl.isBlank() || current.isNewTab) {
            context?.let { Toast.makeText(it, "Cannot bookmark an empty page", Toast.LENGTH_SHORT).show() }
            return
        }

        viewModelScope.launch {
            val isAlreadyBookmarked = repository.isBookmarked(rawUrl)
            if (isAlreadyBookmarked) {
                repository.removeBookmark(rawUrl)
                _uiState.update { it.copy(isCurrentTabBookmarked = false) }
                context?.let { Toast.makeText(it, "Bookmark removed", Toast.LENGTH_SHORT).show() }
            } else {
                repository.addBookmark(current.title.ifBlank { rawUrl }, rawUrl)
                _uiState.update { it.copy(isCurrentTabBookmarked = true) }
                context?.let { Toast.makeText(it, "Bookmark saved", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    // Website Page Download for Offline Viewing (.mhtml)
    fun downloadCurrentPage(context: Context) {
        val current = _uiState.value.currentTab ?: return
        if (current.url.isBlank() || current.isNewTab) {
            Toast.makeText(context, "No active webpage to download", Toast.LENGTH_SHORT).show()
            return
        }
        val wv = registeredWebViews[current.id]
        if (wv == null) {
            Toast.makeText(context, "Page is still loading. Please wait.", Toast.LENGTH_SHORT).show()
            return
        }

        val cleanTitle = (current.title.ifBlank { "saved_page" })
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(35)
        val timestamp = System.currentTimeMillis()
        val fileName = "${cleanTitle}_$timestamp.mhtml"
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val destinationFile = File(downloadsDir, fileName)

        Toast.makeText(context, "Saving webpage for offline viewing...", Toast.LENGTH_SHORT).show()

        wv.saveWebArchive(destinationFile.absolutePath, false) { savedPath: String? ->
            if (savedPath != null) {
                val fileSize = destinationFile.length().coerceAtLeast(1024L)
                val entity = DownloadEntity(
                    fileName = fileName,
                    filePath = savedPath,
                    url = current.url,
                    mimeType = "multipart/related",
                    fileSize = fileSize,
                    status = DownloadStatus.COMPLETED,
                    progress = 100
                )
                viewModelScope.launch {
                    repository.addDownload(entity)
                }
                Toast.makeText(context, "Page downloaded: $fileName", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed to download webpage", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Extract and Copy All Page Text to Clipboard
    fun copyAllPageText(context: Context) {
        val current = _uiState.value.currentTab ?: return
        val wv = registeredWebViews[current.id]
        if (wv == null) {
            Toast.makeText(context, "Page is not ready", Toast.LENGTH_SHORT).show()
            return
        }

        wv.evaluateJavascript(
            "(function() { return document.body ? document.body.innerText : ''; })();"
        ) { rawResult ->
            if (rawResult != null && rawResult != "null") {
                val text = try {
                    org.json.JSONTokener(rawResult).nextValue()?.toString() ?: rawResult
                } catch (e: Exception) {
                    rawResult.trim('"')
                        .replace("\\n", "\n")
                        .replace("\\t", "\t")
                        .replace("\\\"", "\"")
                }
                if (text.isNotBlank()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("Page Content", text)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, "Page text copied (${text.length} chars)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No text content found on this page", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Failed to read page text", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Link Preview Panel (Peek preview)
    fun openPreview(url: String, title: String? = null) {
        _uiState.update { it.copy(previewUrl = url, previewTitle = title) }
    }

    fun closePreview() {
        _uiState.update { it.copy(previewUrl = null, previewTitle = null) }
    }

    fun openPreviewInNewTab() {
        val url = _uiState.value.previewUrl ?: return
        val isIncognito = _uiState.value.isIncognitoMode
        closePreview()
        openNewTab(url = url, isIncognito = isIncognito)
    }

    fun onPageStarted(tabId: String, url: String) {
        updateTabState(tabId) {
            it.copy(
                url = url,
                displayUrl = url,
                isLoading = true,
                progress = 15
            )
        }
        checkCurrentBookmark()
    }

    fun onPageFinished(tabId: String, url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
        val finalTitle = title?.ifBlank { url } ?: url
        updateTabState(tabId) {
            it.copy(
                url = url,
                displayUrl = url,
                title = if (url == "about:blank" || url.isBlank()) "New Tab" else finalTitle,
                isLoading = false,
                progress = 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }

        // Add to history if not incognito and not new tab and not a smart private domain
        val tab = _uiState.value.activeTabs.find { it.id == tabId }
        if (tab != null && !tab.isIncognito && url.isNotBlank() && url != "about:blank" && !isSmartPrivateUrl(url)) {
            viewModelScope.launch {
                repository.addHistory(finalTitle, url)
            }
        } else if (tab != null && tab.isIncognito) {
            persistPrivateSessionIfNeeded(_uiState.value.incognitoTabs)
        }
        checkCurrentBookmark()
    }

    fun onProgressChanged(tabId: String, progress: Int) {
        updateTabState(tabId) {
            it.copy(
                progress = progress,
                isLoading = progress < 100
            )
        }
    }

    fun onReceivedTitle(tabId: String, title: String) {
        if (title.isNotBlank()) {
            updateTabState(tabId) { it.copy(title = title) }
        }
    }

    private fun updateTabState(tabId: String, transform: (BrowserTab) -> BrowserTab) {
        _uiState.update { state ->
            val normal = state.normalTabs.map { if (it.id == tabId) transform(it) else it }
            val incognito = state.incognitoTabs.map { if (it.id == tabId) transform(it) else it }
            state.copy(normalTabs = normal, incognitoTabs = incognito)
        }
    }

    private fun checkCurrentBookmark() {
        val current = _uiState.value.currentTab
        if (current == null || current.url.isBlank() || current.isNewTab) {
            _uiState.update { it.copy(isCurrentTabBookmarked = false) }
            return
        }
        viewModelScope.launch {
            val bookmarked = repository.isBookmarked(current.url)
            _uiState.update { it.copy(isCurrentTabBookmarked = bookmarked) }
        }
    }

    // Find in Page
    fun startFindInPage() {
        _uiState.update { it.copy(findInPageQuery = "") }
    }

    fun updateFindQuery(query: String) {
        _uiState.update { it.copy(findInPageQuery = query) }
        _webAction.value = WebAction.FindInPage(query, true)
    }

    fun findNext() {
        val query = _uiState.value.findInPageQuery ?: return
        _webAction.value = WebAction.FindInPage(query, true)
    }

    fun findPrevious() {
        val query = _uiState.value.findInPageQuery ?: return
        _webAction.value = WebAction.FindInPage(query, false)
    }

    fun closeFindInPage() {
        val currentId = _uiState.value.activeTabId
        _uiState.update { it.copy(findInPageQuery = null, findInPageMatchIndex = 0, findInPageMatchCount = 0) }
        _webAction.value = WebAction.ClearFindInPage(currentId)
    }

    fun onFindResult(activeMatchOrdinal: Int, numberOfMatches: Int) {
        _uiState.update {
            it.copy(
                findInPageMatchIndex = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0,
                findInPageMatchCount = numberOfMatches
            )
        }
    }

    // Download tracking
    fun onDownloadStarted(download: DownloadEntity) {
        viewModelScope.launch {
            repository.addDownload(download)
        }
    }

    // Settings actions
    fun setSearchEngine(engine: SearchEngine) {
        repository.searchEngine = engine
        _uiState.update { it.copy(searchEngine = engine) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        repository.themeMode = mode
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setHomePageUrl(url: String) {
        repository.homePageUrl = url
        _uiState.update { it.copy(homePageUrl = url) }
    }

    fun setDesktopModeDefault(enabled: Boolean) {
        repository.isDesktopModeDefault = enabled
        _uiState.update { it.copy(isDesktopModeDefault = enabled) }
    }

    fun setJavaScriptEnabled(enabled: Boolean) {
        repository.isJavaScriptEnabled = enabled
        _uiState.update { it.copy(isJavaScriptEnabled = enabled) }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        repository.isAdBlockEnabled = enabled
        _uiState.update { it.copy(isAdBlockEnabled = enabled) }
    }

    fun toggleAdBlockForCurrentDomain() {
        val currentTab = _uiState.value.currentTab ?: return
        val host = try {
            Uri.parse(currentTab.url).host?.lowercase()?.removePrefix("www.")
        } catch (e: Exception) { null }
        if (host.isNullOrBlank()) return

        val currentWhitelist = _uiState.value.adBlockWhitelistedDomains.toMutableSet()
        if (currentWhitelist.contains(host)) {
            currentWhitelist.remove(host)
        } else {
            currentWhitelist.add(host)
        }
        repository.adBlockWhitelistedDomains = currentWhitelist
        _uiState.update { it.copy(adBlockWhitelistedDomains = currentWhitelist) }

        // Reload active tab to reflect changes
        reload()
    }

    fun incrementBlockedAdsCount(tabId: String) {
        updateTabState(tabId) { tab ->
            tab.copy(blockedAdsCount = tab.blockedAdsCount + 1)
        }
    }

    fun resetBlockedAdsCount(tabId: String) {
        updateTabState(tabId) { tab ->
            tab.copy(blockedAdsCount = 0)
        }
    }

    // Custom shortcuts
    fun addCustomShortcut(title: String, url: String) {
        val resolvedUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        repository.saveCustomShortcut(title, resolvedUrl)
        _uiState.update { it.copy(customShortcuts = repository.getCustomShortcuts()) }
    }

    fun deleteCustomShortcut(id: String) {
        repository.deleteCustomShortcut(id)
        _uiState.update { it.copy(customShortcuts = repository.getCustomShortcuts()) }
    }

    // Clear Browsing Data
    fun showClearDataDialog(show: Boolean) {
        _uiState.update { it.copy(showClearDataDialog = show) }
    }

    // Page Error handling
    fun onPageError(
        tabId: String,
        errorCode: Int,
        description: String,
        failedUrl: String? = null,
        isSsl: Boolean = false,
        category: PageErrorCategory? = null
    ) {
        updateTabState(tabId) {
            it.copy(
                isLoading = false,
                errorCode = errorCode,
                errorDescription = description,
                failedUrl = failedUrl ?: it.url,
                isSslError = isSsl,
                errorCategory = category ?: if (isSsl) PageErrorCategory.SSL_SECURITY else PageErrorCategory.GENERIC
            )
        }
    }

    fun clearPageError(tabId: String) {
        updateTabState(tabId) {
            it.copy(
                errorDescription = null,
                errorCode = 0,
                isSslError = false,
                failedUrl = null,
                errorCategory = null
            )
        }
    }

    fun onRenderProcessCrash(tabId: String, failedUrl: String? = null) {
        registeredWebViews.remove(tabId)
        updateTabState(tabId) {
            it.copy(
                isLoading = false,
                errorCode = -999,
                errorDescription = "The web page rendering process encountered an issue. Tap retry to recover.",
                failedUrl = failedUrl ?: it.url,
                errorCategory = PageErrorCategory.GENERIC,
                renderRevision = it.renderRevision + 1
            )
        }
    }

    // --- Private Session Security & Controls ---

    fun lockPrivateSession() {
        _uiState.update { it.copy(isPrivateSessionLocked = true) }
    }

    fun promptUnlockPrivateSession() {
        _uiState.update {
            it.copy(
                showPrivatePinPrompt = true,
                privatePinPromptMode = PrivatePinPromptMode.UNLOCK,
                privatePinErrorMessage = null
            )
        }
    }

    fun promptSetupPrivatePin() {
        _uiState.update {
            it.copy(
                showPrivatePinPrompt = true,
                privatePinPromptMode = PrivatePinPromptMode.SETUP,
                privatePinErrorMessage = null
            )
        }
    }

    fun dismissPrivatePinPrompt() {
        _uiState.update {
            it.copy(showPrivatePinPrompt = false, privatePinErrorMessage = null)
        }
    }

    fun submitPrivatePin(pin: String): Boolean {
        val mode = _uiState.value.privatePinPromptMode
        if (mode == PrivatePinPromptMode.SETUP) {
            if (pin.length < 4) {
                _uiState.update { it.copy(privatePinErrorMessage = "PIN must be at least 4 digits") }
                return false
            }
            val success = privateSessionManager.setPinLock(pin)
            if (success) {
                _uiState.update {
                    it.copy(
                        hasPrivatePin = true,
                        isPrivateSessionLocked = false,
                        showPrivatePinPrompt = false,
                        privatePinErrorMessage = null
                    )
                }
                return true
            } else {
                _uiState.update { it.copy(privatePinErrorMessage = "Failed to store PIN securely") }
                return false
            }
        } else {
            val valid = privateSessionManager.verifyPin(pin)
            if (valid) {
                _uiState.update {
                    it.copy(
                        isPrivateSessionLocked = false,
                        showPrivatePinPrompt = false,
                        privatePinErrorMessage = null
                    )
                }
                return true
            } else {
                _uiState.update { it.copy(privatePinErrorMessage = "Incorrect PIN. Please try again.") }
                return false
            }
        }
    }

    fun removePrivatePin() {
        privateSessionManager.removePinLock()
        _uiState.update {
            it.copy(
                hasPrivatePin = false,
                isPrivateSessionLocked = false
            )
        }
    }

    fun togglePrivateResume(enabled: Boolean) {
        privateSessionManager.setPrivateResumeEnabled(enabled)
        _uiState.update { it.copy(isPrivateResumeEnabled = enabled) }
        if (enabled) {
            privateSessionManager.saveEncryptedPrivateSession(_uiState.value.incognitoTabs)
        } else {
            privateSessionManager.clearEncryptedPrivateSession()
        }
    }

    fun showClearPrivateDataDialog(show: Boolean) {
        _uiState.update { it.copy(showClearPrivateDataDialog = show) }
    }

    fun clearPrivateBrowsingData() {
        val tabs = _uiState.value.incognitoTabs
        tabs.forEach { destroyWebView(it.id) }
        privateSessionManager.clearEncryptedPrivateSession()
        val fresh = BrowserTab(isIncognito = true)
        _uiState.update {
            it.copy(
                incognitoTabs = listOf(fresh),
                activeIncognitoTabId = fresh.id,
                isPrivateSessionLocked = false,
                showClearPrivateDataDialog = false
            )
        }
    }

    fun updateVideoPlaybackPosition(tabId: String, seconds: Float) {
        updateTabState(tabId) { it.copy(videoPlaybackSeconds = seconds) }
        if (_uiState.value.isPrivateResumeEnabled) {
            privateSessionManager.saveEncryptedPrivateSession(_uiState.value.incognitoTabs)
        }
    }

    // Fullscreen HTML5 Video
    fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        _customFullScreenView.value = view
        _customViewCallback.value = callback
    }

    fun hideCustomView() {
        _customViewCallback.value?.onCustomViewHidden()
        _customFullScreenView.value = null
        _customViewCallback.value = null
    }

    // Context Menu
    fun showContextMenu(data: ContextMenuData) {
        _uiState.update { it.copy(contextMenuData = data) }
    }

    fun dismissContextMenu() {
        _uiState.update { it.copy(contextMenuData = null) }
    }

    // Web Permissions
    fun requestWebPermission(request: WebPermissionRequest) {
        _uiState.update { it.copy(pendingWebPermission = request) }
    }

    fun dismissWebPermission() {
        _uiState.update { it.copy(pendingWebPermission = null) }
    }

    // Bookmark Editing
    fun editBookmark(bookmark: BookmarkEntity, newTitle: String, newUrl: String) {
        viewModelScope.launch {
            repository.updateBookmark(bookmark.copy(title = newTitle, url = newUrl))
            checkCurrentBookmark()
        }
    }

    // Download updating
    fun updateDownloadStatus(download: DownloadEntity, newStatus: DownloadStatus, progress: Int = 100) {
        viewModelScope.launch {
            repository.updateDownload(download.copy(status = newStatus, progress = progress))
        }
    }

    fun clearBrowsingData(clearHistory: Boolean, clearCookies: Boolean, clearCache: Boolean) {
        viewModelScope.launch {
            if (clearHistory) {
                repository.clearAllHistory()
            }
            if (clearCookies) {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
            if (clearCache) {
                WebStorage.getInstance().deleteAllData()
            }
            _uiState.update { it.copy(showClearDataDialog = false) }
        }
    }

    fun showAddShortcutDialog(show: Boolean) {
        _uiState.update { it.copy(showAddShortcutDialog = show) }
    }

    fun showPageInfoDialog(show: Boolean) {
        _uiState.update { it.copy(showPageInfoDialog = show) }
    }

    // --- Smart Private Site Protection Logic ---

    fun isSmartPrivateUrl(url: String): Boolean {
        if (!_uiState.value.isSmartPrivateProtectionEnabled) return false
        return UrlUtils.matchesPrivateDomain(url, _uiState.value.smartPrivateDomains)
    }

    fun handleSmartPrivateRouting(url: String, isFromCurrentBlankTab: Boolean = false) {
        val current = _uiState.value.currentTab
        // Clean up empty normal tab if applicable so user isn't left with an orphaned blank tab
        if (isFromCurrentBlankTab && current != null && current.isNewTab && !_uiState.value.isIncognitoMode) {
            val remainingNormal = _uiState.value.normalTabs.filterNot { it.id == current.id }
            val normalTabsToSet = if (remainingNormal.isEmpty()) listOf(BrowserTab(isIncognito = false)) else remainingNormal
            repository.saveNormalTabs(normalTabsToSet)
            _uiState.update {
                it.copy(
                    normalTabs = normalTabsToSet,
                    activeNormalTabId = normalTabsToSet.first().id
                )
            }
        }

        // If private session is locked with PIN, request unlock prompt
        if (_uiState.value.hasPrivatePin && _uiState.value.isPrivateSessionLocked) {
            _uiState.update {
                it.copy(
                    showPrivatePinPrompt = true,
                    privatePinPromptMode = PrivatePinPromptMode.UNLOCK,
                    privatePinErrorMessage = "Unlock Private Session to view protected site"
                )
            }
        }

        // Open in Incognito tab directly
        openNewTab(url = url, isIncognito = true)

        _uiState.update {
            it.copy(
                smartPrivateNotification = "Opened in Private Mode via Smart Site Protection"
            )
        }
    }

    fun clearSmartPrivateNotification() {
        _uiState.update { it.copy(smartPrivateNotification = null) }
    }

    fun setSmartPrivateProtectionEnabled(enabled: Boolean) {
        repository.isSmartPrivateProtectionEnabled = enabled
        _uiState.update { it.copy(isSmartPrivateProtectionEnabled = enabled) }
    }

    fun addSmartPrivateDomain(domain: String) {
        val updated = privateSessionManager.addSmartPrivateDomain(domain)
        _uiState.update { it.copy(smartPrivateDomains = updated) }
    }

    fun removeSmartPrivateDomain(domain: String) {
        val updated = privateSessionManager.removeSmartPrivateDomain(domain)
        _uiState.update { it.copy(smartPrivateDomains = updated) }
    }

    fun resetSmartPrivateDomains() {
        val updated = privateSessionManager.resetSmartPrivateDomainsToDefault()
        _uiState.update { it.copy(smartPrivateDomains = updated) }
    }

    fun showSmartPrivateDomainsDialog(show: Boolean) {
        _uiState.update { it.copy(showSmartPrivateDomainsDialog = show) }
    }

    fun resetWebAction() {
        _webAction.value = null
    }
}
