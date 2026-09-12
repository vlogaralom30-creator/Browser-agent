package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CrawlBotState
import com.example.model.CrawlBotStatus
import com.example.model.CrawlMatchItem
import com.example.model.YoutubeInteractionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrawlBotSheet(
    botState: CrawlBotState,
    currentTabUrl: String,
    onStartCrawl: (keyword: String, startUrl: String, maxPages: Int) -> Unit,
    onPauseCrawl: () -> Unit,
    onResumeCrawl: () -> Unit,
    onStopCrawl: () -> Unit,
    onResetBot: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    onStartYtBot: (query: String, criteria: String, like: Boolean, comment: Boolean, commentText: String) -> Unit = { _, _, _, _, _ -> },
    onClearYtHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Web Crawler states
    var keywordInput by remember(botState.targetKeyword) {
        mutableStateOf(if (botState.targetKeyword.isNotBlank() && botState.botMode == "crawler") botState.targetKeyword else "")
    }
    var startUrlInput by remember(currentTabUrl, botState.startUrl) {
        mutableStateOf(if (botState.startUrl.isNotBlank() && botState.botMode == "crawler") botState.startUrl else currentTabUrl)
    }
    var selectedMaxPages by remember(botState.maxPages) { mutableStateOf(botState.maxPages) }

    // YouTube Bot states
    var ytQueryInput by remember(botState.ytSearchQuery) {
        mutableStateOf(if (botState.ytSearchQuery.isNotBlank() && botState.botMode == "youtube") botState.ytSearchQuery else "")
    }
    var selectedCriteria by remember(botState.ytSelectionCriteria) { mutableStateOf(botState.ytSelectionCriteria) }
    var performLike by remember(botState.ytPerformLike) { mutableStateOf(botState.ytPerformLike) }
    var performComment by remember(botState.ytPerformComment) { mutableStateOf(botState.ytPerformComment) }
    var commentTextInput by remember(botState.ytCommentText) { mutableStateOf(botState.ytCommentText) }

    // Active Tab Mode
    var selectedTab by remember(botState.botMode) {
        mutableStateOf(if (botState.botMode == "crawler") 1 else 0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.testTag("crawl_bot_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Sheet Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (selectedTab == 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Default.SmartToy else Icons.Default.SmartToy,
                            contentDescription = "Bot",
                            tint = if (selectedTab == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectedTab == 0) "YouTube Automation Bot" else "Website Crawler Bot",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (selectedTab == 0) "Search, filter, auto-play, like & comment on YouTube videos" else "Auto-scans pages & finds word locations with direct links",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Chip
                val isYtRunning = botState.status != CrawlBotStatus.IDLE && botState.botMode == "youtube"
                val isCrawlerRunning = botState.status != CrawlBotStatus.IDLE && botState.botMode == "crawler"
                val currentRunningMode = if (isYtRunning) 0 else if (isCrawlerRunning) 1 else -1

                val activeStatus = if (currentRunningMode == selectedTab) botState.status else CrawlBotStatus.IDLE

                val (statusColor, statusText) = when (activeStatus) {
                    CrawlBotStatus.RUNNING -> MaterialTheme.colorScheme.primary to "RUNNING"
                    CrawlBotStatus.PAUSED -> MaterialTheme.colorScheme.tertiary to "PAUSED"
                    CrawlBotStatus.COMPLETED -> MaterialTheme.colorScheme.secondary to "COMPLETED"
                    CrawlBotStatus.STOPPED -> MaterialTheme.colorScheme.error to "STOPPED"
                    CrawlBotStatus.ERROR -> MaterialTheme.colorScheme.error to "FAILED"
                    else -> MaterialTheme.colorScheme.outline to "READY"
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(statusColor.copy(alpha = 0.4f))
                    )
                ) {
                    Text(
                        text = statusText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Switching (Only enable if bot is not running)
            val isBotBusy = botState.status == CrawlBotStatus.RUNNING || botState.status == CrawlBotStatus.PAUSED
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = if (selectedTab == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { if (!isBotBusy) selectedTab = 0 },
                    enabled = !isBotBusy,
                    text = { Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("YouTube Bot", fontWeight = FontWeight.Bold)
                    } },
                    selectedContentColor = MaterialTheme.colorScheme.error,
                    unselectedContentColor = MaterialTheme.colorScheme.outline
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { if (!isBotBusy) selectedTab = 1 },
                    enabled = !isBotBusy,
                    text = { Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Web Crawler", fontWeight = FontWeight.Bold)
                    } },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- PAGE RENDER SECTION ---
            if (selectedTab == 0) {
                // ==================== YOUTUBE AUTOMATION PANEL ====================
                if (botState.status == CrawlBotStatus.IDLE || botState.status == CrawlBotStatus.STOPPED || botState.status == CrawlBotStatus.COMPLETED || botState.status == CrawlBotStatus.ERROR || botState.botMode != "youtube") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Launch YouTube Engagement Task",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Search Query Input
                            OutlinedTextField(
                                value = ytQueryInput,
                                onValueChange = { ytQueryInput = it },
                                label = { Text("YouTube Search Query") },
                                placeholder = { Text("e.g. funny cat videos, coding tutorial") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.error,
                                    focusedLabelColor = MaterialTheme.colorScheme.error
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Heuristics Selection
                            Text(
                                text = "Video Selection Filter Heuristics",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { selectedCriteria = "highest_views" }
                                ) {
                                    RadioButton(
                                        selected = selectedCriteria == "highest_views",
                                        onClick = { selectedCriteria = "highest_views" },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.error)
                                    )
                                    Text("Highest Views", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { selectedCriteria = "newest" }
                                ) {
                                    RadioButton(
                                        selected = selectedCriteria == "newest",
                                        onClick = { selectedCriteria = "newest" },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.error)
                                    )
                                    Text("Newest Uploads", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Social Engagement Switch Rows
                            Text(
                                text = "Autonomous Actions on Target",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Auto-Like Selected Video", fontSize = 12.sp)
                                }
                                Switch(
                                    checked = performLike,
                                    onCheckedChange = { performLike = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.error, checkedTrackColor = MaterialTheme.colorScheme.errorContainer)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Comment, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Auto-Comment on Video", fontSize = 12.sp)
                                }
                                Switch(
                                    checked = performComment,
                                    onCheckedChange = { performComment = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.error, checkedTrackColor = MaterialTheme.colorScheme.errorContainer)
                                )
                            }

                            AnimatedVisibility(visible = performComment) {
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    OutlinedTextField(
                                        value = commentTextInput,
                                        onValueChange = { commentTextInput = it },
                                        label = { Text("Comment Content Text") },
                                        placeholder = { Text("e.g. This is an awesome video, thanks!") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.error,
                                            focusedLabelColor = MaterialTheme.colorScheme.error
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    if (ytQueryInput.isNotBlank()) {
                                        onStartYtBot(
                                            ytQueryInput.trim(),
                                            selectedCriteria,
                                            performLike,
                                            performComment,
                                            commentTextInput.trim()
                                        )
                                    } else {
                                        Toast.makeText(context, "Please enter a search query", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = ytQueryInput.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Launch YouTube Bot", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // YouTube Execution / Progress Panel
                if (botState.status != CrawlBotStatus.IDLE && botState.botMode == "youtube") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                        ),
                        border = borderSpacerColor(MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Active YouTube Task State Logs",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (botState.status == CrawlBotStatus.RUNNING) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = botState.currentAction,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // If playing video, display active target details
                            if (botState.ytActiveVideoTitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "Target Video: ${botState.ytActiveVideoTitle}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${botState.ytActiveVideoViews} • ${botState.ytActiveVideoDate}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = botState.ytActiveVideoUrl,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Stop Button
                            if (botState.status == CrawlBotStatus.RUNNING) {
                                OutlinedButton(
                                    onClick = onStopCrawl,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Stop Bot Process", fontSize = 12.sp)
                                }
                            } else if (botState.status == CrawlBotStatus.COMPLETED || botState.status == CrawlBotStatus.STOPPED || botState.status == CrawlBotStatus.ERROR) {
                                Button(
                                    onClick = onResetBot,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Start New Task", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // YouTube Memory Storage List (Active local persistence)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stored Action Memory (${botState.ytHistory.size} actions)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (botState.ytHistory.isNotEmpty()) {
                        IconButton(
                            onClick = onClearYtHistory,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Memory", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (botState.ytHistory.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Text(
                            text = "No stored memory interactions yet. Start a task!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(botState.ytHistory.reversed()) { historyItem ->
                            YtHistoryCard(historyItem = historyItem, onOpenUrl = { url ->
                                onOpenUrl(url)
                                onDismiss()
                            })
                        }
                    }
                }

            } else {
                // ==================== WEBSITE CRAWLER PANEL ====================
                if (botState.status == CrawlBotStatus.IDLE || botState.status == CrawlBotStatus.STOPPED || botState.botMode != "crawler") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Configure Crawler Bot",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Target Keyword Input
                            OutlinedTextField(
                                value = keywordInput,
                                onValueChange = { keywordInput = it },
                                label = { Text("Target Word / Phrase to Search") },
                                placeholder = { Text("e.g. Admission, Pricing, Registration") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ManageSearch,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("crawl_keyword_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Start Website URL Input
                            OutlinedTextField(
                                value = startUrlInput,
                                onValueChange = { startUrlInput = it },
                                label = { Text("Start Website URL") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("crawl_url_input")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Max Pages Selector
                            Text(
                                text = "Max Pages Limit: $selectedMaxPages pages",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val limits = listOf(5, 10, 20, 35, 50)
                                items(limits) { limit ->
                                    FilterChip(
                                        selected = selectedMaxPages == limit,
                                        onClick = { selectedMaxPages = limit },
                                        label = { Text("$limit Pages", fontSize = 12.sp) },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    if (keywordInput.isNotBlank() && startUrlInput.isNotBlank()) {
                                        onStartCrawl(keywordInput.trim(), startUrlInput.trim(), selectedMaxPages)
                                    } else {
                                        Toast.makeText(context, "Please enter both keyword and start URL", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = keywordInput.isNotBlank() && startUrlInput.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("start_crawl_bot_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Launch Crawler Bot", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Crawler Bot Progress card
                if (botState.status != CrawlBotStatus.IDLE && botState.botMode == "crawler") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (botState.status == CrawlBotStatus.RUNNING) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = botState.currentAction,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Live Metrics Row
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                MetricBadge(
                                    icon = Icons.Default.FindInPage,
                                    label = "Crawled",
                                    value = "${botState.totalPagesCrawled} / ${botState.maxPages}"
                                )
                                MetricBadge(
                                    icon = Icons.Default.CenterFocusStrong,
                                    label = "Matched Pages",
                                    value = "${botState.totalMatchesFound}"
                                )
                                MetricBadge(
                                    icon = Icons.Default.Queue,
                                    label = "Queue Links",
                                    value = "${botState.queueUrls.size}"
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Controls Row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (botState.status == CrawlBotStatus.RUNNING) {
                                    OutlinedButton(
                                        onClick = onPauseCrawl,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Pause", fontSize = 12.sp)
                                    }
                                } else if (botState.status == CrawlBotStatus.PAUSED) {
                                    Button(
                                        onClick = onResumeCrawl,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Resume", fontSize = 12.sp)
                                    }
                                }

                                if (botState.status == CrawlBotStatus.RUNNING || botState.status == CrawlBotStatus.PAUSED) {
                                    OutlinedButton(
                                        onClick = onStopCrawl,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Stop Bot", fontSize = 12.sp)
                                    }
                                }

                                if (botState.status == CrawlBotStatus.COMPLETED || botState.status == CrawlBotStatus.STOPPED || botState.status == CrawlBotStatus.ERROR) {
                                    Button(
                                        onClick = onResetBot,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("New Bot Search", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Results Header & List
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Discovered Match Pages (${botState.results.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (botState.targetKeyword.isNotBlank()) {
                        Text(
                            text = "Word: '${botState.targetKeyword}'",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (botState.results.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Text(
                            text = if (botState.status == CrawlBotStatus.RUNNING && botState.botMode == "crawler") {
                                "Bot is crawling pages... Matches will appear here live!"
                            } else {
                                "No matched pages discovered yet."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(botState.results, key = { it.pageUrl }) { matchItem ->
                            CrawlMatchCard(
                                matchItem = matchItem,
                                onOpenUrl = { url ->
                                    onOpenUrl(url)
                                    onDismiss()
                                },
                                context = context
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun borderSpacerColor(color: Color) = CardDefaults.outlinedCardBorder().copy(
    brush = androidx.compose.ui.graphics.SolidColor(color.copy(alpha = 0.4f))
)

@Composable
private fun MetricBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = value,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun CrawlMatchCard(
    matchItem: CrawlMatchItem,
    onOpenUrl: (String) -> Unit,
    context: Context
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = matchItem.pageTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "${matchItem.matchCount} matches",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = matchItem.pageUrl,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (matchItem.snippets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    matchItem.snippets.take(2).forEach { snippet ->
                        Text(
                            text = "\"$snippet\"",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onOpenUrl(matchItem.pageUrl) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Visit Direct Page", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Page URL", matchItem.pageUrl)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Page link copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Link",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun YtHistoryCard(
    historyItem: YoutubeInteractionItem,
    onOpenUrl: (String) -> Unit
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Query: '${historyItem.query}'",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val formattedDate = android.text.format.DateFormat.format("hh:mm a", historyItem.timestamp).toString()
                Text(
                    text = formattedDate,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = historyItem.videoTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Views: ${historyItem.viewCountText}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Action Badges (Likes / Comments)
                if (historyItem.isLiked) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Liked", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (historyItem.isCommented) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Comment, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Commented", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (historyItem.commentText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Comment: \"${historyItem.commentText}\"",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(6.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onOpenUrl(historyItem.videoUrl) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Replay Video", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("YouTube Video Link", historyItem.videoUrl)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Video URL copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Link",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
