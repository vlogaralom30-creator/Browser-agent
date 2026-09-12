package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BrowserTab
import com.example.ui.BrowserScreen
import com.example.ui.BrowserViewModel
import com.example.ui.theme.IncognitoPrimary
import com.example.ui.theme.IncognitoSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenu(
    tab: BrowserTab?,
    isIncognito: Boolean,
    isBookmarked: Boolean,
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isIncognito) IncognitoSurface else MaterialTheme.colorScheme.surface,
        contentColor = if (isIncognito) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Quick action icons row
            Surface(
                color = if (isIncognito) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            onDismiss()
                            viewModel.goBack()
                        },
                        enabled = tab?.canGoBack == true
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            onDismiss()
                            viewModel.goForward()
                        },
                        enabled = tab?.canGoForward == true
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.toggleBookmark(context)
                        },
                        enabled = tab?.isNewTab == false && tab.url.isNotBlank()
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            onDismiss()
                            viewModel.reload()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))



            // New Tab
            MenuItem(
                icon = Icons.Default.Add,
                title = "New Tab",
                onClick = {
                    onDismiss()
                    viewModel.openNewTab(isIncognito = false)
                }
            )

            // New Incognito Tab
            MenuItem(
                icon = Icons.Rounded.Shield,
                title = "New Incognito Tab",
                tint = if (isIncognito) IncognitoPrimary else MaterialTheme.colorScheme.onSurface,
                onClick = {
                    onDismiss()
                    viewModel.openNewTab(isIncognito = true)
                }
            )

            // Restore Recently Closed Tab
            if (uiState.canReopenClosedTab) {
                MenuItem(
                    icon = Icons.Default.Restore,
                    title = "Reopen Closed Tab",
                    onClick = {
                        onDismiss()
                        viewModel.reopenRecentlyClosedTab()
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Bookmarks
            MenuItem(
                icon = Icons.Default.Bookmarks,
                title = "Bookmarks",
                onClick = {
                    onDismiss()
                    viewModel.navigateToScreen(BrowserScreen.BOOKMARKS)
                }
            )

            // History
            MenuItem(
                icon = Icons.Default.History,
                title = "History",
                onClick = {
                    onDismiss()
                    viewModel.navigateToScreen(BrowserScreen.HISTORY)
                }
            )

            // Downloads
            MenuItem(
                icon = Icons.Default.Download,
                title = "Downloads",
                onClick = {
                    onDismiss()
                    viewModel.navigateToScreen(BrowserScreen.DOWNLOADS)
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Share URL
            MenuItem(
                icon = Icons.Default.Share,
                title = "Share...",
                enabled = tab?.isNewTab == false && tab.url.isNotBlank(),
                onClick = {
                    onDismiss()
                    val shareUrl = tab?.url ?: ""
                    if (shareUrl.isNotBlank()) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, tab?.title ?: "")
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share link"))
                    }
                }
            )

            // Copy Link
            MenuItem(
                icon = Icons.Default.ContentCopy,
                title = "Copy Link",
                enabled = tab?.isNewTab == false && tab.url.isNotBlank(),
                onClick = {
                    onDismiss()
                    val copyUrl = tab?.url ?: ""
                    if (copyUrl.isNotBlank()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("URL", copyUrl)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // Download Page for Offline Reading (.mhtml)
            MenuItem(
                icon = Icons.Default.Download,
                title = "Download Page (Save Offline)",
                enabled = tab?.isNewTab == false && tab.url.isNotBlank(),
                onClick = {
                    onDismiss()
                    viewModel.downloadCurrentPage(context)
                }
            )

            // Copy All Text from Page
            MenuItem(
                icon = Icons.Default.ContentCopy,
                title = "Copy All Page Text",
                enabled = tab?.isNewTab == false && tab.url.isNotBlank(),
                onClick = {
                    onDismiss()
                    viewModel.copyAllPageText(context)
                }
            )

            // Find in page
            MenuItem(
                icon = Icons.Default.FindInPage,
                title = "Find in Page",
                enabled = tab?.isNewTab == false && tab.url.isNotBlank(),
                onClick = {
                    onDismiss()
                    viewModel.startFindInPage()
                }
            )

            // Website Crawler Bot
            MenuItem(
                icon = Icons.Default.SmartToy,
                title = "Website Crawler Bot",
                onClick = {
                    onDismiss()
                    viewModel.showCrawlBotSheet(true)
                }
            )

            // Ad & Tracker Protection toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.setAdBlockEnabled(!uiState.isAdBlockEnabled)
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("menu_ad_block"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = if (uiState.isAdBlockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Block Ads & Trackers",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (uiState.isAdBlockEnabled) {
                            Text(
                                text = "${tab?.blockedAdsCount ?: 0} blocked on this page",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "Protection paused",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Switch(
                    checked = uiState.isAdBlockEnabled,
                    onCheckedChange = { viewModel.setAdBlockEnabled(it) }
                )
            }

            // Desktop site toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.toggleDesktopMode()
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("menu_desktop_site"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = null,
                        tint = if (tab?.isDesktopMode == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Desktop Site",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Checkbox(
                    checked = tab?.isDesktopMode == true,
                    onCheckedChange = null
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Clear browsing data / Private browsing controls
            if (isIncognito) {
                if (uiState.hasPrivatePin) {
                    MenuItem(
                        icon = Icons.Default.Lock,
                        title = "Lock Private Session",
                        onClick = {
                            onDismiss()
                            viewModel.lockPrivateSession()
                        }
                    )
                } else {
                    MenuItem(
                        icon = Icons.Default.Lock,
                        title = "Set Private Session PIN",
                        onClick = {
                            onDismiss()
                            viewModel.promptSetupPrivatePin()
                        }
                    )
                }

                MenuItem(
                    icon = Icons.Default.Close,
                    title = "Clear Private Browsing Data",
                    onClick = {
                        onDismiss()
                        viewModel.showClearPrivateDataDialog(true)
                    }
                )

                MenuItem(
                    icon = Icons.Default.DeleteSweep,
                    title = "Close Private Session",
                    onClick = {
                        onDismiss()
                        viewModel.closeAllTabs(true)
                    }
                )
            } else {
                MenuItem(
                    icon = Icons.Default.DeleteSweep,
                    title = "Clear Browsing Data...",
                    onClick = {
                        onDismiss()
                        viewModel.showClearDataDialog(true)
                    }
                )
            }

            // Settings
            MenuItem(
                icon = Icons.Default.Settings,
                title = "Settings",
                onClick = {
                    onDismiss()
                    viewModel.navigateToScreen(BrowserScreen.SETTINGS)
                }
            )
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    val opacity = if (enabled) 1f else 0.4f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint.copy(alpha = opacity),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = opacity)
        )
    }
}
