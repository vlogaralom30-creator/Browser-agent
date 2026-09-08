package com.example.ui

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.BrowserAgentController
import com.example.data.BookmarkEntity
import com.example.data.BrowserDatabase
import com.example.data.BrowserPreferences
import com.example.data.BrowserRepository
import com.example.data.DownloadEntity
import com.example.data.HistoryEntity
import com.example.data.AppThemeMode
import com.example.data.agent.AgentRepository
import com.example.model.BrowserTab
import com.example.model.SearchEngine
import com.example.model.ShortcutItem
import com.example.util.UrlUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BrowserScreen {
    BROWSER,
    TAB_SWITCHER,
    BOOKMARKS,
    HISTORY,
    DOWNLOADS,
    SETTINGS,
    AI_AGENT,
    SAVED_PROMPTS,
    API_KEYS
}

data class BrowserUiState(
    val normalTabs: List<BrowserTab> = emptyList(),
    val incognitoTabs: List<BrowserTab> = emptyList(),
    val activeNormalTabId: String = "",
    val activeIncognitoTabId: String = "",
    val isIncognitoMode: Boolean = false,
    val currentScreen: BrowserScreen = BrowserScreen.BROWSER,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
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
    val isCurrentTabBookmarked: Boolean = false
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

    // Agent Repository & Controller
    val agentRepository = AgentRepository(application)
    val agentController = BrowserAgentController(
        context = application,
        agentRepository = agentRepository,
        viewModel = this,
        scope = viewModelScope
    )

    // Active WebView registry for real browser action execution
    private val registeredWebViews = mutableMapOf<String, WebView>()

    fun registerWebView(tabId: String, webView: WebView) {
        registeredWebViews[tabId] = webView
    }

    fun unregisterWebView(tabId: String) {
        registeredWebViews.remove(tabId)
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

    init {
        val initialTab = BrowserTab(isIncognito = false)
        _uiState.update {
            it.copy(
                normalTabs = listOf(initialTab),
                activeNormalTabId = initialTab.id,
                searchEngine = repository.searchEngine,
                themeMode = repository.themeMode,
                isDesktopModeDefault = repository.isDesktopModeDefault,
                isJavaScriptEnabled = repository.isJavaScriptEnabled,
                homePageUrl = repository.homePageUrl,
                customShortcuts = repository.getCustomShortcuts()
            )
        }

        viewModelScope.launch {
            agentRepository.initializeDefaultsIfEmpty()
        }
    }

    fun openNewTab(url: String = "", isIncognito: Boolean = _uiState.value.isIncognitoMode) {
        val newTab = BrowserTab(
            url = url,
            isIncognito = isIncognito,
            isDesktopMode = _uiState.value.isDesktopModeDefault
        )
        _uiState.update { state ->
            if (isIncognito) {
                val updatedTabs = state.incognitoTabs + newTab
                state.copy(
                    incognitoTabs = updatedTabs,
                    activeIncognitoTabId = newTab.id,
                    isIncognitoMode = true,
                    currentScreen = BrowserScreen.BROWSER
                )
            } else {
                val updatedTabs = state.normalTabs + newTab
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
    }

    fun closeTab(tabId: String, isIncognito: Boolean) {
        _uiState.update { state ->
            if (isIncognito) {
                val newTabs = state.incognitoTabs.filterNot { it.id == tabId }
                val newActiveId = if (state.activeIncognitoTabId == tabId) {
                    newTabs.lastOrNull()?.id ?: ""
                } else state.activeIncognitoTabId
                if (newTabs.isEmpty()) {
                    val fallback = BrowserTab(isIncognito = true)
                    state.copy(
                        incognitoTabs = listOf(fallback),
                        activeIncognitoTabId = fallback.id
                    )
                } else {
                    state.copy(
                        incognitoTabs = newTabs,
                        activeIncognitoTabId = newActiveId
                    )
                }
            } else {
                val newTabs = state.normalTabs.filterNot { it.id == tabId }
                val newActiveId = if (state.activeNormalTabId == tabId) {
                    newTabs.lastOrNull()?.id ?: ""
                } else state.activeNormalTabId
                if (newTabs.isEmpty()) {
                    val fallback = BrowserTab(isIncognito = false)
                    state.copy(
                        normalTabs = listOf(fallback),
                        activeNormalTabId = fallback.id
                    )
                } else {
                    state.copy(
                        normalTabs = newTabs,
                        activeNormalTabId = newActiveId
                    )
                }
            }
        }
        checkCurrentBookmark()
    }

    fun closeAllTabs(isIncognito: Boolean) {
        if (isIncognito) {
            val fresh = BrowserTab(isIncognito = true)
            _uiState.update {
                it.copy(
                    incognitoTabs = listOf(fresh),
                    activeIncognitoTabId = fresh.id
                )
            }
        } else {
            val fresh = BrowserTab(isIncognito = false)
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

        updateTabState(current.id) { tab ->
            tab.copy(url = finalUrl, displayUrl = finalUrl, isLoading = true, progress = 10)
        }
        _webAction.value = WebAction.LoadUrl(current.id, finalUrl)
        checkCurrentBookmark()
    }

    fun goBack() {
        val current = _uiState.value.currentTab ?: return
        if (current.canGoBack) {
            _webAction.value = WebAction.GoBack(current.id)
        }
    }

    fun goForward() {
        val current = _uiState.value.currentTab ?: return
        if (current.canGoForward) {
            _webAction.value = WebAction.GoForward(current.id)
        }
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
                it.copy(url = "", displayUrl = "", title = "New Tab", progress = 0, isLoading = false)
            }
            _webAction.value = WebAction.LoadUrl(current.id, "about:blank")
        }
    }

    fun toggleDesktopMode() {
        val current = _uiState.value.currentTab ?: return
        val newMode = !current.isDesktopMode
        updateTabState(current.id) { it.copy(isDesktopMode = newMode) }
        _webAction.value = WebAction.Reload(current.id)
    }

    fun toggleBookmark() {
        val current = _uiState.value.currentTab ?: return
        if (current.url.isBlank() || current.isNewTab) return

        viewModelScope.launch {
            if (_uiState.value.isCurrentTabBookmarked) {
                repository.removeBookmark(current.url)
                _uiState.update { it.copy(isCurrentTabBookmarked = false) }
            } else {
                repository.addBookmark(current.title.ifBlank { current.url }, current.url)
                _uiState.update { it.copy(isCurrentTabBookmarked = true) }
            }
        }
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

        // Add to history if not incognito and not new tab
        val tab = _uiState.value.activeTabs.find { it.id == tabId }
        if (tab != null && !tab.isIncognito && url.isNotBlank() && url != "about:blank") {
            viewModelScope.launch {
                repository.addHistory(finalTitle, url)
            }
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

    fun resetWebAction() {
        _webAction.value = null
    }
}
