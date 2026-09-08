package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.BrowserScreen
import com.example.ui.BrowserViewModel
import com.example.ui.agent.ActionConfirmationDialog
import com.example.ui.agent.AgentScreen
import com.example.ui.agent.ApiKeyManagerScreen
import com.example.ui.agent.SavedPromptsScreen
import com.example.ui.components.BottomNavBar
import com.example.ui.components.BrowserMenu
import com.example.ui.components.BrowserWebView
import com.example.ui.components.FindInPageBar
import com.example.ui.components.Omnibox
import com.example.ui.theme.IncognitoBackground
import com.example.ui.theme.IncognitoSurface

@Composable
fun BrowserMainScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.agentController.pendingConfirmation.collectAsState()
    val context = LocalContext.current
    var showMenuSheet by remember { mutableStateOf(false) }

    val currentTab = state.currentTab
    val isIncognito = state.isIncognitoMode

    // System Back button handling
    BackHandler {
        when {
            state.findInPageQuery != null -> {
                viewModel.closeFindInPage()
            }
            state.currentScreen != BrowserScreen.BROWSER -> {
                viewModel.navigateToScreen(BrowserScreen.BROWSER)
            }
            currentTab?.canGoBack == true -> {
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

                    // Web Content / New Tab Page
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (currentTab != null) {
                            if (currentTab.isNewTab) {
                                NewTabPage(
                                    isIncognito = isIncognito,
                                    searchEngine = state.searchEngine,
                                    customShortcuts = state.customShortcuts,
                                    onOpenUrl = { url -> viewModel.loadUrl(url) },
                                    onAddShortcut = { title, url -> viewModel.addCustomShortcut(title, url) },
                                    onDeleteCustomShortcut = { id -> viewModel.deleteCustomShortcut(id) }
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
                        onBookmarkClick = { viewModel.toggleBookmark() },
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

    // Security / Page info Dialog
    if (state.showPageInfoDialog) {
        PageInfoDialog(
            tab = currentTab,
            isIncognito = isIncognito,
            onDismiss = { viewModel.showPageInfoDialog(false) }
        )
    }

    // Top-Level Sensitive Confirmation Dialog
    pendingConfirmation?.let { req ->
        ActionConfirmationDialog(
            request = req,
            onAllow = { viewModel.agentController.approveSensitiveAction() },
            onCancel = { viewModel.agentController.rejectSensitiveAction() }
        )
    }
}
