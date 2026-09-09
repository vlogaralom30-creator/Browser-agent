package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BrowserTab
import com.example.ui.BrowserScreen
import com.example.ui.BrowserUiState
import com.example.ui.BrowserViewModel
import com.example.ui.theme.IncognitoAccent
import com.example.ui.theme.IncognitoBackground
import com.example.ui.theme.IncognitoPrimary
import com.example.ui.theme.IncognitoSurface
import com.example.ui.theme.IncognitoSurfaceVariant
import com.example.util.UrlUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherScreen(
    state: BrowserUiState,
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val isIncognito = state.isIncognitoMode
    val currentTabs = if (isIncognito) state.incognitoTabs else state.normalTabs
    val activeTabId = if (isIncognito) state.activeIncognitoTabId else state.activeNormalTabId

    val backgroundColor = if (isIncognito) IncognitoBackground else MaterialTheme.colorScheme.background
    val barColor = if (isIncognito) IncognitoSurface else MaterialTheme.colorScheme.surface
    val textColor = if (isIncognito) Color.White else MaterialTheme.colorScheme.onBackground

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {
        // Top Toolbar
        Surface(
            color = barColor,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Done / Back to active tab
                IconButton(
                    onClick = { viewModel.navigateToScreen(BrowserScreen.BROWSER) },
                    modifier = Modifier.testTag("tab_switcher_done")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done",
                        tint = if (isIncognito) IncognitoPrimary else MaterialTheme.colorScheme.primary
                    )
                }

                // Tabs Selector (Normal vs Incognito)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isIncognito) IncognitoSurfaceVariant else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Regular tabs button
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (!isIncognito) MaterialTheme.colorScheme.surface else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.setIncognitoMode(false) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tab,
                                contentDescription = "Standard Tabs",
                                tint = if (!isIncognito) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${state.normalTabs.size}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isIncognito) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    }

                    // Incognito tabs button
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isIncognito) IncognitoPrimary else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.setIncognitoMode(true) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "Incognito Tabs",
                                tint = if (isIncognito) Color.Black else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${state.incognitoTabs.size}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isIncognito) Color.Black else Color.Gray
                            )
                        }
                    }
                }

                // Actions: Add New Tab & More Menu
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.openNewTab(isIncognito = isIncognito) },
                        modifier = Modifier.testTag("tab_switcher_add")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Tab",
                            tint = if (isIncognito) IncognitoPrimary else MaterialTheme.colorScheme.primary
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Tab options",
                                tint = textColor
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (isIncognito) {
                                if (state.hasPrivatePin) {
                                    DropdownMenuItem(
                                        text = { Text("Lock Private Session") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            viewModel.lockPrivateSession()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Remove PIN Protection") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.LockOpen,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            viewModel.removePrivatePin()
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Set Private Session PIN") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            viewModel.promptSetupPrivatePin()
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = {
                                        Text(if (state.isPrivateResumeEnabled) "Resume Session: ON" else "Resume Session: OFF")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.togglePrivateResume(!state.isPrivateResumeEnabled)
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Close Private Session") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.closeAllTabs(true)
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Clear Private Browsing Data") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.showClearPrivateDataDialog(true)
                                    }
                                )
                            } else {
                                if (state.canReopenClosedTab) {
                                    DropdownMenuItem(
                                        text = { Text("Reopen closed tab") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Restore,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            viewModel.reopenRecentlyClosedTab()
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text("Close all tabs") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.closeAllTabs(false)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Content Area: Either Locked State or Tab Grid
        if (isIncognito && state.isPrivateSessionLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
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
                            modifier = Modifier.testTag("unlock_private_session_button")
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
        } else {
            // Tab Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                items(currentTabs, key = { it.id }) { tab ->
                    TabCard(
                        tab = tab,
                        isActive = tab.id == activeTabId,
                        isIncognito = isIncognito,
                        onSelect = {
                            viewModel.switchTab(tab.id, isIncognito)
                        },
                        onClose = {
                            viewModel.closeTab(tab.id, isIncognito)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabCard(
    tab: BrowserTab,
    isActive: Boolean,
    isIncognito: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBackground = if (isIncognito) IncognitoSurface else MaterialTheme.colorScheme.surface
    val activeBorderColor = if (isIncognito) IncognitoPrimary else MaterialTheme.colorScheme.primary
    val displayHost = remember(tab.url) { UrlUtils.getDisplayHost(tab.url) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = if (isActive) BorderStroke(2.5.dp, activeBorderColor) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 6.dp else 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clickable { onSelect() }
            .testTag("tab_card_${tab.id}")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Icon, Title, Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (tab.isIncognito) Icons.Rounded.Shield else Icons.Default.Language,
                        contentDescription = null,
                        tint = if (tab.isIncognito) IncognitoPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (tab.isNewTab) "New Tab" else tab.title.ifBlank { displayHost },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("tab_card_close_${tab.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close tab",
                        tint = if (isIncognito) Color(0xFFC4C7C5) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Body Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isIncognito) IncognitoSurfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isIncognito) IncognitoPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (tab.isNewTab) "+" else displayHost.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = if (isIncognito) IncognitoPrimary else MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (tab.isNewTab) "New Tab" else displayHost,
                        fontSize = 11.sp,
                        color = if (isIncognito) IncognitoAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
