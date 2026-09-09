package com.example.ui.agent

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ai.ActivityType
import com.example.ai.AgentActivityItem
import com.example.ai.AgentStatus
import com.example.ai.YouTubeVideoItem
import com.example.data.agent.AgentConfigEntity
import com.example.data.agent.AgentMessageEntity
import com.example.ui.BrowserScreen
import com.example.ui.BrowserViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val agentController = viewModel.agentController
    val agentRepository = viewModel.agentRepository

    val config by agentRepository.configFlow.collectAsState(initial = AgentConfigEntity())
    val agentStatus by agentController.agentStatus.collectAsState()
    val currentTask by agentController.currentTask.collectAsState()
    val activityLogs by agentController.activityLogs.collectAsState()
    val pointerState by agentController.pointerState.collectAsState()
    val pendingConfirmation by agentController.pendingConfirmation.collectAsState()
    val collectedVideos by agentController.collectedVideoResults.collectAsState()

    val conversations by agentRepository.allConversations.collectAsState(initial = emptyList())
    val activeConversationId = remember(conversations) {
        conversations.firstOrNull { it.isActive }?.id ?: "default_conv"
    }

    val dbMessages by agentRepository.messageDao.getMessagesForConversation(activeConversationId)
        .collectAsState(initial = emptyList())

    var userInputText by remember { mutableStateOf("") }
    var showModelSelector by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Auto scroll down on new message or activity
    LaunchedEffect(dbMessages.size, activityLogs.size, collectedVideos.size) {
        if (dbMessages.isNotEmpty() || activityLogs.isNotEmpty()) {
            listState.animateScrollToItem((dbMessages.size + activityLogs.size + collectedVideos.size).coerceAtLeast(0))
        }
    }

    val quickPrompts = listOf(
        "▶️ Arijit Singh er latest song play koro",
        "🔥 YouTube-এ সবচেয়ে বেশি ভিউ হওয়া গান কোনটা?",
        "📰 Tech news article খুঁজে দাও",
        "⚡ 10টা জনপ্রিয় ইউটিউব ভিডিও দেখাও"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Naxxivo AI Agent",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2E7D32))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (agentStatus == AgentStatus.RUNNING) "OPERATING BROWSER..." else "ONLINE • HUMAN CONTROL",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (agentStatus == AgentStatus.RUNNING) MaterialTheme.colorScheme.primary else Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("agent_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Model Chip
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clickable { showModelSelector = true }
                            .padding(end = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = (config?.activeModel ?: "ling-3.0").removePrefix("models/").take(10),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Pointer Overlay Toggle
                    IconButton(
                        onClick = {
                            agentController.setPointerVisible(!pointerState.isVisible)
                            Toast.makeText(
                                context,
                                if (!pointerState.isVisible) "Visual Cursor Enabled" else "Visual Cursor Disabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.testTag("toggle_pointer_button")
                    ) {
                        Icon(
                            imageVector = if (pointerState.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Pointer Overlay",
                            tint = if (pointerState.isVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // API Keys
                    IconButton(
                        onClick = { viewModel.navigateToScreen(BrowserScreen.API_KEYS) },
                        modifier = Modifier.testTag("agent_api_keys_button")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = "API Keys")
                    }

                    // Saved Prompts
                    IconButton(
                        onClick = { viewModel.navigateToScreen(BrowserScreen.SAVED_PROMPTS) },
                        modifier = Modifier.testTag("agent_prompts_button")
                    ) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = "Saved Prompts")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Quick Suggestion Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    items(quickPrompts) { prompt ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable {
                                userInputText = prompt
                            }
                        ) {
                            Text(
                                text = prompt,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Telegram Input Box
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = userInputText,
                        onValueChange = { userInputText = it },
                        placeholder = {
                            Text(
                                "Ask AI Agent... e.g. Arijit Singh er latest song play koro",
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("agent_chat_input"),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        singleLine = false,
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (userInputText.isNotBlank()) {
                                    val goal = userInputText
                                    userInputText = ""
                                    agentController.startNewTask(goal)
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (agentStatus == AgentStatus.RUNNING) {
                        IconButton(
                            onClick = { agentController.stopCurrentTask(pauseOnly = false) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .testTag("agent_stop_task_button")
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop Task",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (userInputText.isNotBlank()) {
                                    val goal = userInputText
                                    userInputText = ""
                                    agentController.startNewTask(goal)
                                }
                            },
                            enabled = userInputText.isNotBlank(),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (userInputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .testTag("agent_send_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Goal",
                                tint = if (userInputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (dbMessages.isEmpty() && activityLogs.isEmpty() && agentStatus == AgentStatus.IDLE) {
                // Telegram Welcome Screen
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                                        )
                                    ),
                                    RoundedCornerShape(24.dp)
                                )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Naxxivo AI Browser Chatbot",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Automated Human-Like Web Navigation, YouTube Control & Smart Result Comparison",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                Surface(
                                    color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32))
                                ) {
                                    Text(
                                        text = "⚡ Try: \"Arijit Singh er latest song play koro\"",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Telegram Active Chat Stream
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // DB Chat Messages
                    items(dbMessages) { msg ->
                        TelegramMessageBubble(
                            message = msg,
                            onPlayVideo = { url ->
                                viewModel.loadUrl(url)
                                onBack()
                            }
                        )
                    }

                    // Live Status Step Stream Card
                    if (agentStatus == AgentStatus.RUNNING || activityLogs.isNotEmpty()) {
                        item {
                            TelegramLiveStatusCard(
                                currentTaskGoal = currentTask?.goal ?: "Executing task...",
                                logs = activityLogs,
                                agentStatus = agentStatus,
                                pointerText = pointerState.actionText
                            )
                        }
                    }

                    // Collected YouTube Video Results Card
                    if (collectedVideos.isNotEmpty()) {
                        item {
                            YouTubeResultsCardList(
                                videos = collectedVideos,
                                onPlayVideo = { url ->
                                    viewModel.loadUrl(url)
                                    onBack()
                                }
                            )
                        }
                    }
                }
            }

            // Sensitive Confirmation Modal
            if (pendingConfirmation != null) {
                val req = pendingConfirmation!!
                AlertDialog(
                    onDismissRequest = { agentController.rejectSensitiveAction() },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SmartButton,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = req.actionName,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    text = {
                        Column {
                            Text(
                                text = req.warningMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = req.actionDetails,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { agentController.approveSensitiveAction() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Confirm & Execute")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { agentController.rejectSensitiveAction() }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }

    if (showModelSelector) {
        ModelSelectionDialog(
            currentModel = config?.activeModel ?: "inclusionai/ling-3.0-flash-sante:free",
            agentRepository = agentRepository,
            onModelSelected = { selectedModel ->
                val current = config ?: AgentConfigEntity()
                coroutineScope.launch {
                    agentRepository.updateConfig(current.copy(activeModel = selectedModel))
                }
            },
            onDismiss = { showModelSelector = false }
        )
    }
}

@Composable
fun TelegramMessageBubble(
    message: AgentMessageEntity,
    onPlayVideo: (String) -> Unit
) {
    val isUser = message.role == "user"
    val timeFormatted = remember(message.timestamp) {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = timeFormatted,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun TelegramLiveStatusCard(
    currentTaskGoal: String,
    logs: List<com.example.ai.AgentActivityItem>,
    agentStatus: com.example.ai.AgentStatus,
    pointerText: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (agentStatus == com.example.ai.AgentStatus.RUNNING) MaterialTheme.colorScheme.primary else Color(0xFF2E7D32)
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AGENT ACTION PROGRESS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (agentStatus == com.example.ai.AgentStatus.RUNNING) {
                    Text(
                        text = "ACTIVE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Target Goal: $currentTaskGoal",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (pointerText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "👉 Cursor: $pointerText",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Last 5 action step logs
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                logs.takeLast(5).forEach { log ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (log.type) {
                                com.example.ai.ActivityType.SUCCESS -> Icons.Default.Check
                                com.example.ai.ActivityType.ACT -> Icons.Default.PlayArrow
                                else -> Icons.Default.AutoAwesome
                            },
                            contentDescription = null,
                            tint = when (log.type) {
                                com.example.ai.ActivityType.SUCCESS -> Color(0xFF2E7D32)
                                com.example.ai.ActivityType.ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = log.message,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun YouTubeResultsCardList(
    videos: List<com.example.ai.YouTubeVideoItem>,
    onPlayVideo: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🎯 Extracted YouTube Results (${videos.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "RESULT MEMORY",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                videos.take(5).forEach { video ->
                    YouTubeVideoCardItem(video = video, onPlayVideo = onPlayVideo)
                }
            }
        }
    }
}

@Composable
fun YouTubeVideoCardItem(
    video: com.example.ai.YouTubeVideoItem,
    onPlayVideo: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(width = 90.dp, height = 60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (video.thumbnail.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(video.thumbnail)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Video Thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (video.duration.isNotBlank()) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = video.duration,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title & Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = video.channel,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (video.viewsFormatted.isNotBlank() || video.viewsRaw.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "👁️ ${if (video.viewsFormatted.isNotBlank()) video.viewsFormatted else video.viewsRaw}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (video.uploadedRaw.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "📅 ${video.uploadedRaw}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Play / Open Button
            Button(
                onClick = { onPlayVideo(video.url) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().height(34.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "▶️ Play in Browser",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
