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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var keywordInput by remember(botState.targetKeyword) {
        mutableStateOf(if (botState.targetKeyword.isNotBlank()) botState.targetKeyword else "")
    }
    var startUrlInput by remember(currentTabUrl, botState.startUrl) {
        mutableStateOf(if (botState.startUrl.isNotBlank()) botState.startUrl else currentTabUrl)
    }
    var selectedMaxPages by remember(botState.maxPages) { mutableStateOf(botState.maxPages) }

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
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Bot",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Website Crawler Bot",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Auto-scans pages & finds word locations with direct links",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Chip
                val (statusColor, statusText) = when (botState.status) {
                    CrawlBotStatus.RUNNING -> MaterialTheme.colorScheme.primary to "RUNNING"
                    CrawlBotStatus.PAUSED -> MaterialTheme.colorScheme.tertiary to "PAUSED"
                    CrawlBotStatus.COMPLETED -> MaterialTheme.colorScheme.secondary to "COMPLETED"
                    CrawlBotStatus.STOPPED -> MaterialTheme.colorScheme.error to "STOPPED"
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

            // Bot Configuration Panel (when not running or when resetting)
            if (botState.status == CrawlBotStatus.IDLE || botState.status == CrawlBotStatus.STOPPED) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Configure Bot Task",
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

            // Live Bot Progress & Controls Card (when RUNNING, PAUSED, COMPLETED, or STOPPED)
            if (botState.status != CrawlBotStatus.IDLE) {
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
                                    Icon(
                                        imageVector = Icons.Default.Pause,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pause", fontSize = 12.sp)
                                }
                            } else if (botState.status == CrawlBotStatus.PAUSED) {
                                Button(
                                    onClick = onResumeCrawl,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
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
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Stop Bot", fontSize = 12.sp)
                                }
                            }

                            if (botState.status == CrawlBotStatus.COMPLETED || botState.status == CrawlBotStatus.STOPPED) {
                                Button(
                                    onClick = onResetBot,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
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
                        text = if (botState.status == CrawlBotStatus.RUNNING) {
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
                        .heightIn(max = 300.dp)
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
