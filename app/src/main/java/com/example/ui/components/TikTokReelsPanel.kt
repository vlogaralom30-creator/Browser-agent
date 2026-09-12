package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import com.example.model.TikTokReelItem
import com.example.util.FacebookAuthHelper
import com.example.util.ReelShareHelper
import com.example.util.YouTubeAuthHelper
import com.example.util.YouTubeShareHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private val TikTokPink = Color(0xFFFE2C55)
private val TikTokCyan = Color(0xFF25F4EE)
private val TikTokDarkBg = Color(0xFF161823)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TikTokReelsPanel(
    botState: CrawlBotState,
    queryInput: String,
    onQueryChange: (String) -> Unit,
    selectedMinViews: Long,
    onMinViewsChange: (Long) -> Unit,
    selectedCriteria: String,
    onCriteriaChange: (String) -> Unit,
    selectedLimit: Float,
    onLimitChange: (Float) -> Unit,
    customHashtagsInput: String,
    onCustomHashtagsChange: (String) -> Unit,
    onStartTikTokBot: (query: String, limit: Int, minViews: Long, criteria: String, customHashtags: String) -> Unit,
    onPauseCrawl: () -> Unit,
    onResumeCrawl: () -> Unit,
    onStopCrawl: () -> Unit,
    onResetBot: () -> Unit,
    onClearTikTokHistory: () -> Unit,
    onDeleteTikTokReel: (String) -> Unit,
    onOpenUrl: (String) -> Unit = {},
    onDismissSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showAdvancedFilters by remember { mutableStateOf(false) }
    var fbStatus by remember { mutableStateOf(FacebookAuthHelper.checkStatus(context)) }
    var ytStatus by remember { mutableStateOf(YouTubeAuthHelper.checkStatus(context)) }
    var playingVideoItem by remember { mutableStateOf<TikTokReelItem?>(null) }
    var uploadTargetItem by remember { mutableStateOf<TikTokReelItem?>(null) }
    var uploadYouTubeTargetItem by remember { mutableStateOf<TikTokReelItem?>(null) }

    LaunchedEffect(botState.status) {
        fbStatus = FacebookAuthHelper.checkStatus(context)
        ytStatus = YouTubeAuthHelper.checkStatus(context)
    }

    if (playingVideoItem != null) {
        VideoPlayerDialog(
            item = playingVideoItem!!,
            onDismiss = { playingVideoItem = null },
            onUploadFacebookClick = {
                uploadTargetItem = playingVideoItem
                playingVideoItem = null
            },
            onUploadYouTubeClick = {
                uploadYouTubeTargetItem = playingVideoItem
                playingVideoItem = null
            }
        )
    }

    if (uploadTargetItem != null) {
        FacebookReelUploadDialog(
            item = uploadTargetItem!!,
            onDismiss = { uploadTargetItem = null },
            onOpenUrl = onOpenUrl,
            onDismissSheet = onDismissSheet,
            onWatchClick = {
                playingVideoItem = uploadTargetItem
                uploadTargetItem = null
            }
        )
    }

    if (uploadYouTubeTargetItem != null) {
        YouTubeShortsUploadDialog(
            item = uploadYouTubeTargetItem!!,
            onDismiss = { uploadYouTubeTargetItem = null },
            onOpenUrl = onOpenUrl,
            onDismissSheet = onDismissSheet,
            onWatchClick = {
                playingVideoItem = uploadYouTubeTargetItem
                uploadYouTubeTargetItem = null
            }
        )
    }

    val isTikTokRunning = botState.status == CrawlBotStatus.RUNNING && botState.botMode == "tiktok"
    val isTikTokPaused = botState.status == CrawlBotStatus.PAUSED && botState.botMode == "tiktok"
    val isTikTokActive = (isTikTokRunning || isTikTokPaused)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tiktok_reels_panel")
    ) {
        // --- 1. PROACTIVE SOCIAL CHANNELS (FACEBOOK & YOUTUBE) DIAGNOSTIC CARD ---
        val allConnected = fbStatus.canUpload && ytStatus.canUpload
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (allConnected) Color(0xFF10B981).copy(alpha = 0.09f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(
                1.dp,
                if (allConnected) Color(0xFF10B981).copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("social_diagnostic_banner")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (allConnected) Icons.Default.CheckCircle else Icons.Default.Share,
                            contentDescription = null,
                            tint = if (allConnected) Color(0xFF059669) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Social Channels (Reels & Shorts)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextButton(
                        onClick = {
                            fbStatus = FacebookAuthHelper.checkStatus(context)
                            ytStatus = YouTubeAuthHelper.checkStatus(context)
                            Toast.makeText(
                                context,
                                "FB: ${if (fbStatus.canUpload) "Ready" else "No login"} | YT: ${if (ytStatus.canUpload) "Ready" else "No login"}",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Verify Both", fontSize = 10.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Channel Status Badges Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Facebook Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (fbStatus.canUpload) Color(0xFF1877F2).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, if (fbStatus.canUpload) Color(0xFF1877F2).copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (fbStatus.canUpload) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (fbStatus.canUpload) Color(0xFF1877F2) else Color(0xFFEF4444),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = "Facebook Reels",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (fbStatus.canUpload) "Connected ✓" else "Not logged in",
                                    fontSize = 9.sp,
                                    color = if (fbStatus.canUpload) Color(0xFF1877F2) else Color(0xFFEF4444)
                                )
                            }
                        }
                    }

                    // YouTube Shorts Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (ytStatus.canUpload) Color(0xFFFF0000).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, if (ytStatus.canUpload) Color(0xFFFF0000).copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (ytStatus.canUpload) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (ytStatus.canUpload) Color(0xFFFF0000) else Color(0xFFEF4444),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = "YouTube Shorts",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (ytStatus.canUpload) "Connected ✓" else "Not logged in",
                                    fontSize = 9.sp,
                                    color = if (ytStatus.canUpload) Color(0xFFFF0000) else Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }

                // Action Buttons for logging in if missing
                if (!fbStatus.canUpload || !ytStatus.canUpload) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!fbStatus.canUpload) {
                            Button(
                                onClick = {
                                    onOpenUrl("https://m.facebook.com/login")
                                    onDismissSheet()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Text("🔑 FB Login", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!ytStatus.canUpload) {
                            Button(
                                onClick = {
                                    YouTubeShareHelper.openYouTubeLogin(onOpenUrl)
                                    onDismissSheet()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Text("🔑 YouTube Login", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        // --- INPUT & CONFIGURATION CARD ---
        if (!isTikTokActive) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = BorderStroke(1.dp, TikTokPink.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = TikTokPink.copy(alpha = 0.15f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MovieFilter,
                                    contentDescription = null,
                                    tint = TikTokPink,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TikTok Viral Hunter & Downloader",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scrapes viral videos, downloads HD without watermark & generates Facebook-ready captions",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Niche Search Input
                    OutlinedTextField(
                        value = queryInput,
                        onValueChange = onQueryChange,
                        label = { Text("Search Niche / Topic") },
                        placeholder = { Text("e.g. funny cats, life hacks, gadgets, motivation") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TikTokPink, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (queryInput.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tiktok_query_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Niche Suggestion Chips
                    Text(
                        text = "Quick Niches (Tap to Auto-Fill):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val niches = listOf(
                            "😂 Funny Pets" to "funny pets",
                            "💡 Life Hacks" to "life hacks",
                            "📱 Tech Gadgets" to "cool tech gadgets",
                            "💪 Motivation" to "fitness motivation",
                            "🍳 Food Recipes" to "easy food recipes",
                            "✨ Satisfying" to "oddly satisfying crafts"
                        )
                        niches.forEach { (label, value) ->
                            SuggestionChip(
                                onClick = { onQueryChange(value) },
                                label = { Text(label, fontSize = 11.sp) },
                                shape = RoundedCornerShape(10.dp),
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // --- HIGH-VISIBILITY IMMEDIATE LAUNCH BUTTON ---
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            if (queryInput.isNotBlank()) {
                                onStartTikTokBot(
                                    queryInput.trim(),
                                    selectedLimit.toInt(),
                                    selectedMinViews,
                                    selectedCriteria,
                                    customHashtagsInput.trim()
                                )
                            } else {
                                Toast.makeText(context, "Please enter a topic or tap a Quick Niche above!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = queryInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TikTokPink
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_tiktok_bot_button")
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (queryInput.isBlank()) "Enter Topic to Launch Hunter" else "🚀 Start TikTok Hunter (${selectedLimit.toInt()} Videos)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Toggle Advanced Settings
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvancedFilters = !showAdvancedFilters }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = TikTokPink, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Advanced Settings (Filter, Limit & Hashtags)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = if (showAdvancedFilters) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = showAdvancedFilters) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            // Viral Threshold (Min Views)
                            Text(
                                text = "Minimum Views Filter:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val thresholds = listOf(
                                    "10K+" to 10000L,
                                    "50K+" to 50000L,
                                    "100K+" to 100000L,
                                    "500K+" to 500000L,
                                    "1M+ Super Viral" to 1000000L
                                )
                                items(thresholds) { (label, count) ->
                                    FilterChip(
                                        selected = selectedMinViews == count,
                                        onClick = { onMinViewsChange(count) },
                                        label = { Text(label, fontSize = 11.sp, fontWeight = if (selectedMinViews == count) FontWeight.Bold else FontWeight.Normal) },
                                        shape = RoundedCornerShape(10.dp),
                                        leadingIcon = if (selectedMinViews == count) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                        } else null
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Sort Criteria
                            Text(
                                text = "Sort Criteria:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val criteriaOptions = listOf(
                                    "highest_views" to "🔥 Highest Views",
                                    "most_viral" to "⚡ Most Viral",
                                    "newest" to "🆕 Fresh"
                                )
                                criteriaOptions.forEach { (key, label) ->
                                    FilterChip(
                                        selected = selectedCriteria == key,
                                        onClick = { onCriteriaChange(key) },
                                        label = { Text(label, fontSize = 11.sp) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Number of videos slider
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Download Limit:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${selectedLimit.toInt()} Videos",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TikTokPink
                                )
                            }
                            Slider(
                                value = selectedLimit,
                                onValueChange = onLimitChange,
                                valueRange = 1f..10f,
                                steps = 8,
                                colors = SliderDefaults.colors(
                                    thumbColor = TikTokPink,
                                    activeTrackColor = TikTokPink
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Custom Hashtags
                            OutlinedTextField(
                                value = customHashtagsInput,
                                onValueChange = onCustomHashtagsChange,
                                label = { Text("Custom Facebook Reels Hashtags") },
                                placeholder = { Text("#viral #reels #foryou #trending #explore") },
                                leadingIcon = {
                                    Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                singleLine = false,
                                maxLines = 2,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tiktok_hashtags_input")
                            )
                        }
                    }
                }
            }
        }

        // --- LIVE DASHBOARD CARD (WHEN ACTIVE) ---
        if (isTikTokActive || (botState.botMode == "tiktok" && botState.status != CrawlBotStatus.IDLE)) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ),
                border = BorderStroke(1.dp, if (isTikTokRunning) TikTokPink else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = if (isTikTokRunning) TikTokPink else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(10.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isTikTokRunning) "TikTok Bot Active" else "Status: ${botState.status.name}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTikTokRunning) TikTokPink else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Progress fraction
                        if (botState.tiktokTotalTarget > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = TikTokPink.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Reel ${botState.tiktokCurrentIndex} of ${botState.tiktokTotalTarget}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TikTokPink,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Animated Progress Bar
                    if (botState.tiktokTotalTarget > 0) {
                        val progress = botState.tiktokCurrentIndex.toFloat() / max(1, botState.tiktokTotalTarget)
                        LinearProgressIndicator(
                            progress = { progress },
                            color = TikTokPink,
                            trackColor = TikTokPink.copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Live Status Step
                    val currentStep = botState.tiktokActiveStatusStep.ifBlank { botState.currentAction }
                    if (currentStep.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = TikTokPink,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentStep,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Active Target Video Preview
                    if (botState.tiktokActiveTitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "Current Target: ${botState.tiktokActiveTitle}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (botState.tiktokActiveViews.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "🔥 ${botState.tiktokActiveViews} views",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TikTokPink
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Control Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTikTokActive) {
                            OutlinedButton(
                                onClick = {
                                    if (isTikTokRunning) onPauseCrawl() else onResumeCrawl()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isTikTokRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isTikTokRunning) "Pause" else "Resume", fontSize = 12.sp)
                            }

                            Button(
                                onClick = onStopCrawl,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop", fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = onResetBot,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TikTokPink),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start New Hunter Task", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SAVED REPURPOSED REELS SECTION ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = TikTokPink,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Saved Reels for Facebook (${botState.tiktokDownloadedReels.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (botState.tiktokDownloadedReels.isNotEmpty()) {
                TextButton(onClick = onClearTikTokHistory) {
                    Text("Clear All", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Facebook Reels Web Studio Hub
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1877F2).copy(alpha = 0.08f)
            ),
            border = BorderStroke(1.dp, Color(0xFF1877F2).copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("facebook_reels_hub_card")
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = Color(0xFF1877F2),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Facebook Reels Studio Shortcuts",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1877F2)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Open Web Reels Creator in Naxxivo Browser for immediate uploading:",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            onOpenUrl(ReelShareHelper.FB_PROFILE_REELS_URL)
                            onDismissSheet()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Profile Creator", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = {
                            onOpenUrl(ReelShareHelper.FB_PAGE_REELS_URL)
                            onDismissSheet()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Page Suite", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (botState.tiktokDownloadedReels.isEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No Downloaded Reels Yet",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Run the TikTok Hunter to find viral videos, download without watermark, and automatically generate Facebook-ready copy.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                botState.tiktokDownloadedReels.forEach { item ->
                    TikTokReelCard(
                        item = item,
                        onDelete = { onDeleteTikTokReel(item.id) },
                        onWatchVideo = { playingVideoItem = item },
                        onUploadFacebookClick = { uploadTargetItem = item },
                        onUploadYouTubeClick = { uploadYouTubeTargetItem = item }
                    )
                }
            }
        }
    }
}

@Composable
fun TikTokReelCard(
    item: TikTokReelItem,
    onDelete: () -> Unit,
    onWatchVideo: () -> Unit,
    onUploadFacebookClick: () -> Unit,
    onUploadYouTubeClick: () -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val videoFile = remember(item.localFilePath) { File(item.localFilePath) }
    val fileSizeFormatted = remember(videoFile) {
        if (!videoFile.exists()) "0 KB"
        else {
            val bytes = videoFile.length()
            if (bytes > 1024 * 1024) {
                String.format(Locale.US, "%.2f MB", bytes.toDouble() / (1024 * 1024))
            } else {
                "${bytes / 1024} KB"
            }
        }
    }

    val formattedDate = remember(item.timestamp) {
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        sdf.format(Date(item.timestamp))
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: File Name + Views Badge + Delete
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onWatchVideo() }
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = TikTokPink,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = item.fileName.ifBlank { "Reel MP4" },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$formattedDate • $fileSizeFormatted • by ${item.author}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TikTokPink.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "🔥 ${item.viewsText.ifBlank { "Viral" }}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TikTokPink,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Original Title preview
            Text(
                text = "Original: \"${item.originalTitle}\"",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Spun Caption Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✍️ Spun Caption (Reels & Shorts):",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TikTokPink
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isExpanded) "Collapse" else "Expand",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(
                                onClick = { ReelShareHelper.copyCaptionToClipboard(context, item.spunTitle) },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.height(20.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy caption", modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Copy", fontSize = 9.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.spunTitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Watch, Facebook Reels, YouTube Shorts
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // In-App Video Preview Button
                OutlinedButton(
                    onClick = onWatchVideo,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(0.9f)
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(13.dp), tint = TikTokPink)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Watch", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }

                // Dedicated Upload to Facebook Reels Button
                Button(
                    onClick = onUploadFacebookClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1.05f)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("FB Reels", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // Dedicated Upload to YouTube Shorts Button
                Button(
                    onClick = onUploadYouTubeClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1.05f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("YT Shorts", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Facebook Reel Upload Dialog
 * Offers crystal-clear destinations:
 * 1. Facebook Profile (via app or browser web studio)
 * 2. Facebook Page (via Meta Business Suite app or browser web studio)
 * 3. System share sheet and file utilities
 */
@Composable
fun FacebookReelUploadDialog(
    item: TikTokReelItem,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismissSheet: () -> Unit,
    onWatchClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var showCopiedFeedback by remember { mutableStateOf(false) }
    val fbStatus = remember { FacebookAuthHelper.checkStatus(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1877F2).copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = Color(0xFF1877F2),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Upload Reel to Facebook", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Choose Profile or Page destination", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Facebook Login Status Card inside Dialog
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (fbStatus.canUpload) Color(0xFF10B981).copy(alpha = 0.10f) else Color(0xFFF59E0B).copy(alpha = 0.14f),
                    border = BorderStroke(
                        1.dp,
                        if (fbStatus.canUpload) Color(0xFF10B981).copy(alpha = 0.35f) else Color(0xFFF59E0B).copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (fbStatus.canUpload) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (fbStatus.canUpload) Color(0xFF059669) else Color(0xFFD97706),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (fbStatus.canUpload) "Facebook সেশন প্রস্তুত" else "⚠️ Facebook লগইন প্রয়োজন",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (fbStatus.canUpload) Color(0xFF065F46) else Color(0xFF92400E)
                            )
                        }
                        if (!fbStatus.canUpload) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Facebook অ্যাকাউন্ট লগইন করা নেই। নিচের বাটনে ট্যাপ করে লগইন করুন:",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    onOpenUrl("https://m.facebook.com/login")
                                    onDismiss()
                                    onDismissSheet()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                            ) {
                                Text("🔑 ব্রাউজারে Facebook Login করুন", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Caption preview & quick copy
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TikTokPink.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, TikTokPink.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✍️ Spun Caption & Hashtags",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TikTokPink
                            )
                            TextButton(
                                onClick = {
                                    ReelShareHelper.copyCaptionToClipboard(context, item.spunTitle)
                                    showCopiedFeedback = true
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (showCopiedFeedback) "Copied!" else "Copy", fontSize = 10.sp)
                            }
                        }
                        Text(
                            text = item.spunTitle,
                            fontSize = 11.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Destination 1: Facebook Profile
                Text(
                    text = "👤 Facebook Profile Reels",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1877F2)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            ReelShareHelper.uploadToFacebookProfile(
                                context = context,
                                filePath = item.localFilePath,
                                caption = item.spunTitle,
                                forceWeb = false,
                                onOpenWebFallback = { url ->
                                    onOpenUrl(url)
                                    onDismissSheet()
                                }
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("FB App", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            ReelShareHelper.uploadToFacebookProfile(
                                context = context,
                                filePath = item.localFilePath,
                                caption = item.spunTitle,
                                forceWeb = true,
                                onOpenWebFallback = { url ->
                                    onOpenUrl(url)
                                    onDismissSheet()
                                }
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Web Composer", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Destination 2: Facebook Page
                Text(
                    text = "🏢 Facebook Page (Meta Business Suite)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0866FF)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            ReelShareHelper.uploadToFacebookPage(
                                context = context,
                                filePath = item.localFilePath,
                                caption = item.spunTitle,
                                forceWeb = false,
                                onOpenWebFallback = { url ->
                                    onOpenUrl(url)
                                    onDismissSheet()
                                }
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0866FF)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Meta Suite App", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            ReelShareHelper.uploadToFacebookPage(
                                context = context,
                                filePath = item.localFilePath,
                                caption = item.spunTitle,
                                forceWeb = true,
                                onOpenWebFallback = { url ->
                                    onOpenUrl(url)
                                    onDismissSheet()
                                }
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Web Business", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Secondary actions: Watch & Other Apps
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onWatchClick()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = TikTokPink)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Watch Video", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            ReelShareHelper.shareReel(context, item.localFilePath, item.spunTitle)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Other Apps", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
