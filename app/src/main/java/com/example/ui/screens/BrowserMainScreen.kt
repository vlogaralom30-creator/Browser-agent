package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.BrowserScreen
import com.example.ui.BrowserViewModel
import com.example.ui.agent.ActionConfirmationDialog
import com.example.ui.agent.AgentPointerOverlay
import com.example.ui.agent.AgentScreen
import com.example.ui.agent.ApiKeyManagerScreen
import com.example.ui.agent.SavedPromptsScreen
import com.example.ui.components.BottomNavBar
import com.example.ui.components.BrowserContextMenuSheet
import com.example.ui.components.BrowserMenu
import com.example.ui.components.BrowserWebView
import com.example.ui.components.ClearPrivateDataDialog
import com.example.ui.components.CrawlBotSheet
import com.example.ui.components.CrawlBotOverlayBadge
import com.example.ui.components.FindInPageBar
import com.example.ui.components.LinkPreviewSheet
import com.example.ui.components.Omnibox
import com.example.ui.components.PageErrorView
import com.example.ui.components.PrivatePinDialog
import com.example.ui.components.SmartPrivateDomainsDialog
import com.example.ui.components.WebPermissionDialog
import com.example.util.UrlUtils
import com.example.ui.theme.IncognitoAccent
import com.example.ui.theme.IncognitoBackground
import com.example.ui.theme.IncognitoPrimary
import com.example.ui.theme.IncognitoSurface
import com.example.ui.theme.IncognitoSurfaceVariant

@Composable
fun BrowserMainScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.agentController.pendingConfirmation.collectAsStateWithLifecycle()
    val pointerState by viewModel.agentController.pointerState.collectAsStateWithLifecycle()
    val agentStatus by viewModel.agentController.agentStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMenuSheet by remember { mutableStateOf(false) }

    val currentTab = state.currentTab
    val isIncognito = state.isIncognitoMode

    val customFullScreenView by viewModel.customFullScreenView.collectAsStateWithLifecycle()

    // System Back button handling
    BackHandler {
        when {
            state.previewUrl != null -> {
                viewModel.closePreview()
            }
            state.contextMenuData != null -> {
                viewModel.dismissContextMenu()
            }
            customFullScreenView != null -> {
                viewModel.hideCustomView()
            }
            state.findInPageQuery != null -> {
                viewModel.closeFindInPage()
            }
            state.currentScreen != BrowserScreen.BROWSER -> {
                viewModel.navigateToScreen(BrowserScreen.BROWSER)
            }
            currentTab?.canGoBack == true || viewModel.getWebViewForTab(currentTab?.id)?.canGoBack() == true -> {
                viewModel.goBack()
            }
            state.totalTabsCount > 1 -> {
                // If on new tab or cannot go back, go to tab switcher
                viewModel.navigateToScreen(BrowserScreen.TAB_SWITCHER)
            }
            else -> {
                // Let system exit
                (context as? android.app.Activity)?.finish()
            }
        }
    }

    AnimatedContent(
        targetState = state.currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            BrowserScreen.BROWSER -> {
                val screenBackground = if (isIncognito) IncognitoBackground else MaterialTheme.colorScheme.background

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(screenBackground)
                ) {
                    // Top Omnibox
                    Omnibox(
                        tab = currentTab,
                        tabsCount = state.totalTabsCount,
                        isIncognito = isIncognito,
                        onHomeClick = { viewModel.goHome() },
                        onSubmitUrl = { input -> viewModel.loadUrl(input) },
                        onTabSwitcherClick = { viewModel.navigateToScreen(BrowserScreen.TAB_SWITCHER) },
                        onMenuClick = { showMenuSheet = true },
                        onSecurityInfoClick = { viewModel.showPageInfoDialog(true) }
                    )

                    // Find in Page bar if active
                    state.findInPageQuery?.let { query ->
                        FindInPageBar(
                            query = query,
                            matchIndex = state.findInPageMatchIndex,
                            matchCount = state.findInPageMatchCount,
                            onQueryChange = { viewModel.updateFindQuery(it) },
                            onNext = { viewModel.findNext() },
                            onPrevious = { viewModel.findPrevious() },
                            onClose = { viewModel.closeFindInPage() }
                        )
                    }

                    // Web Content / New Tab Page / Locked View
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (isIncognito && state.isPrivateSessionLocked) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(containerColor = IncognitoSurface)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = IncognitoSurfaceVariant,
                                            modifier = Modifier.size(72.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = "Locked",
                                                    tint = IncognitoPrimary,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Text(
                                            text = "Private Session Locked",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = "Enter your PIN to decrypt and access your private tabs.",
                                            fontSize = 13.sp,
                                            color = IncognitoAccent,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(24.dp))

                                        Button(
                                            onClick = { viewModel.promptUnlockPrivateSession() },
                                            colors = ButtonDefaults.buttonColors(containerColor = IncognitoPrimary),
                                            modifier = Modifier.testTag("unlock_private_session_main_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LockOpen,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Unlock Session", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        } else if (currentTab != null) {
                            if (currentTab.isNewTab) {
                                NewTabPage(
                                    isIncognito = isIncognito,
                                    searchEngine = state.searchEngine,
                                    customShortcuts = state.customShortcuts,
                                    onOpenUrl = { url -> viewModel.loadUrl(url) },
                                    onAddShortcut = { title, url -> viewModel.addCustomShortcut(title, url) },
                                    onDeleteCustomShortcut = { id -> viewModel.deleteCustomShortcut(id) }
                                )
                            } else if (currentTab.url.startsWith("browser://search")) {
                                val searchQuery = try {
                                    Uri.parse(currentTab.url).getQueryParameter("q") ?: currentTab.displayUrl
                                } catch (e: Exception) {
                                    currentTab.displayUrl
                                }
                                val searchData = state.searchResultsMap[currentTab.id]
                                val isSearchLoading = state.searchLoadingMap[currentTab.id] ?: false
                                SearchResultsPage(
                                    query = searchQuery,
                                    searchResultData = searchData,
                                    isLoading = isSearchLoading,
                                    onSearchQuerySubmit = { query ->
                                        viewModel.loadUrl("browser://search?q=${Uri.encode(query)}")
                                    },
                                    onOpenUrl = { url -> viewModel.loadUrl(url) },
                                    onPreviewUrl = { url, title -> viewModel.openPreview(url, title) },
                                    onRetry = { viewModel.executeNativeSearch(currentTab.id, searchQuery) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (currentTab.errorDescription != null) {
                                PageErrorView(
                                    tab = currentTab,
                                    onRetry = {
                                        viewModel.clearPageError(currentTab.id)
                                        viewModel.reload()
                                    },
                                    onGoHome = {
                                        viewModel.clearPageError(currentTab.id)
                                        viewModel.goHome()
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                BrowserWebView(
                                    tab = currentTab,
                                    viewModel = viewModel,
                                    isJavaScriptEnabled = state.isJavaScriptEnabled,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // AI Agent Visual Action Pointer Overlay
                        if (agentStatus == com.example.ai.AgentStatus.RUNNING || agentStatus == com.example.ai.AgentStatus.PAUSED) {
                            AgentPointerOverlay(
                                pointerState = pointerState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Floating Crawl Bot Live Overlay Badge
                        CrawlBotOverlayBadge(
                            botState = state.crawlBotState,
                            onClickExpand = { viewModel.showCrawlBotSheet(true) },
                            onPause = { viewModel.pauseCrawlBot() },
                            onResume = { viewModel.resumeCrawlBot() },
                            onStop = { viewModel.stopCrawlBot() },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                        )
                    }

                    // Bottom Navigation Bar
                    BottomNavBar(
                        tab = currentTab,
                        isIncognito = isIncognito,
                        isBookmarked = state.isCurrentTabBookmarked,
                        onBackClick = { viewModel.goBack() },
                        onForwardClick = { viewModel.goForward() },
                        onNewTabClick = { viewModel.openNewTab(isIncognito = isIncognito) },
                        onAgentClick = { viewModel.navigateToScreen(BrowserScreen.AI_AGENT) },
                        onBookmarkClick = { viewModel.toggleBookmark(context) },
                        onReloadClick = { viewModel.reload() },
                        onShareClick = {
                            val shareUrl = currentTab?.url ?: ""
                            if (shareUrl.isNotBlank()) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, currentTab?.title ?: "")
                                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share link"))
                            }
                        }
                    )
                }
            }

            BrowserScreen.TAB_SWITCHER -> {
                TabSwitcherScreen(
                    state = state,
                    viewModel = viewModel
                )
            }

            BrowserScreen.BOOKMARKS -> {
                BookmarksScreen(viewModel = viewModel)
            }

            BrowserScreen.HISTORY -> {
                HistoryScreen(viewModel = viewModel)
            }

            BrowserScreen.DOWNLOADS -> {
                DownloadsScreen(viewModel = viewModel)
            }

            BrowserScreen.SETTINGS -> {
                SettingsScreen(
                    state = state,
                    viewModel = viewModel
                )
            }

            BrowserScreen.AI_AGENT -> {
                AgentScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateToScreen(BrowserScreen.BROWSER) }
                )
            }

            BrowserScreen.SAVED_PROMPTS -> {
                SavedPromptsScreen(
                    agentRepository = viewModel.agentRepository,
                    onBack = { viewModel.navigateToScreen(BrowserScreen.AI_AGENT) }
                )
            }

            BrowserScreen.API_KEYS -> {
                ApiKeyManagerScreen(
                    agentRepository = viewModel.agentRepository,
                    onBack = { viewModel.navigateToScreen(BrowserScreen.AI_AGENT) }
                )
            }
        }
    }

    // Modal Menu Sheet
    if (showMenuSheet) {
        BrowserMenu(
            tab = currentTab,
            isIncognito = isIncognito,
            isBookmarked = state.isCurrentTabBookmarked,
            viewModel = viewModel,
            onDismiss = { showMenuSheet = false }
        )
    }

    // Clear Data Dialog
    if (state.showClearDataDialog) {
        ClearDataDialog(
            onDismiss = { viewModel.showClearDataDialog(false) },
            onClear = { hist, cook, cache ->
                viewModel.clearBrowsingData(hist, cook, cache)
                Toast.makeText(context, "Browsing data cleared", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Private Session PIN Dialog (Setup or Unlock)
    if (state.showPrivatePinPrompt) {
        PrivatePinDialog(
            mode = state.privatePinPromptMode,
            errorMessage = state.privatePinErrorMessage,
            onSubmit = { pin ->
                val success = viewModel.submitPrivatePin(pin)
                if (success) {
                    val msg = if (state.privatePinPromptMode == com.example.ui.PrivatePinPromptMode.SETUP)
                        "Private session PIN enabled"
                    else
                        "Private session unlocked"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { viewModel.dismissPrivatePinPrompt() }
        )
    }

    // Clear Private Data Dialog
    if (state.showClearPrivateDataDialog) {
        ClearPrivateDataDialog(
            onConfirm = {
                viewModel.clearPrivateBrowsingData()
                Toast.makeText(context, "Private tabs & encrypted session cleared", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { viewModel.showClearPrivateDataDialog(false) }
        )
    }

    // Security / Page info Dialog
    if (state.showPageInfoDialog) {
        val currentUrl = currentTab?.url ?: ""
        val canonicalHost = UrlUtils.extractCanonicalHost(currentUrl)
        val isSmartDomain = UrlUtils.matchesPrivateDomain(currentUrl, state.smartPrivateDomains)

        PageInfoDialog(
            tab = currentTab,
            isIncognito = isIncognito,
            isSmartPrivateDomain = isSmartDomain,
            onToggleSmartPrivateDomain = { enable ->
                if (enable) {
                    viewModel.addSmartPrivateDomain(canonicalHost)
                } else {
                    viewModel.removeSmartPrivateDomain(canonicalHost)
                }
            },
            onDismiss = { viewModel.showPageInfoDialog(false) }
        )
    }

    // Smart Private Domains Management Dialog
    if (state.showSmartPrivateDomainsDialog) {
        SmartPrivateDomainsDialog(
            domains = state.smartPrivateDomains,
            isEnabled = state.isSmartPrivateProtectionEnabled,
            onAddDomain = { viewModel.addSmartPrivateDomain(it) },
            onRemoveDomain = { viewModel.removeSmartPrivateDomain(it) },
            onResetDefaults = { viewModel.resetSmartPrivateDomains() },
            onDismiss = { viewModel.showSmartPrivateDomainsDialog(false) }
        )
    }

    // Smart Private Site Protection Notification Banner
    state.smartPrivateNotification?.let { notificationMsg ->
        LaunchedEffect(notificationMsg) {
            kotlinx.coroutines.delay(4000)
            viewModel.clearSmartPrivateNotification()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = IncognitoPrimary,
                contentColor = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("smart_private_notification_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = notificationMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearSmartPrivateNotification() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Top-Level Sensitive Confirmation Dialog
    pendingConfirmation?.let { req ->
        ActionConfirmationDialog(
            request = req,
            onAllow = { viewModel.agentController.approveSensitiveAction() },
            onCancel = { viewModel.agentController.rejectSensitiveAction() }
        )
    }

    // Context Menu Bottom Sheet
    state.contextMenuData?.let { data ->
        BrowserContextMenuSheet(
            data = data,
            viewModel = viewModel,
            onDismiss = { viewModel.dismissContextMenu() }
        )
    }

    // Link Preview Panel (Peek preview)
    state.previewUrl?.let { previewUrl ->
        LinkPreviewSheet(
            url = previewUrl,
            initialTitle = state.previewTitle,
            isIncognito = isIncognito,
            onOpenInNewTab = { viewModel.openPreviewInNewTab() },
            onDismiss = { viewModel.closePreview() }
        )
    }

    // Web Permission Dialog
    state.pendingWebPermission?.let { request ->
        WebPermissionDialog(
            request = request,
            onDismiss = { viewModel.dismissWebPermission() }
        )
    }

    // Website Crawler Bot Modal Sheet
    if (state.showCrawlBotSheet) {
        CrawlBotSheet(
            botState = state.crawlBotState,
            currentTabUrl = currentTab?.url ?: "",
            onStartCrawl = { keyword, startUrl, maxPages ->
                viewModel.startCrawlBot(keyword, startUrl, maxPages)
            },
            onPauseCrawl = { viewModel.pauseCrawlBot() },
            onResumeCrawl = { viewModel.resumeCrawlBot() },
            onStopCrawl = { viewModel.stopCrawlBot() },
            onResetBot = { viewModel.resetCrawlBot() },
            onOpenUrl = { url -> viewModel.loadUrl(url) },
            onDismiss = { viewModel.showCrawlBotSheet(false) }
        )
    }

    // HTML5 Fullscreen Video / Custom View Overlay
    customFullScreenView?.let { customView ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { customView },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
