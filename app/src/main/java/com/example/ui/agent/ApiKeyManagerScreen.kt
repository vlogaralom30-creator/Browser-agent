package com.example.ui.agent

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.GeminiApiClient
import com.example.data.agent.AgentRepository
import com.example.data.agent.ApiKeyEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyManagerScreen(
    agentRepository: AgentRepository,
    onBack: () -> Unit
) {
    val apiKeys by agentRepository.allApiKeys.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val geminiApiClient = remember { GeminiApiClient(agentRepository) }

    var showAddDialog by remember { mutableStateOf(false) }
    var newKeyInput by remember { mutableStateOf("") }
    var newKeyLabel by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf("GEMINI") } // "GEMINI", "OPENROUTER", "OPENAI"
    var customBaseUrl by remember { mutableStateOf("") }
    var keyToDelete by remember { mutableStateOf<ApiKeyEntity?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    // Map of key ID to testing status: null = idle, "TESTING" = in progress, or result string
    val testStatusMap = remember { mutableStateMapOf<Long, String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "API Key Manager",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Automatic failover across ${apiKeys.count { it.isEnabled }} active keys",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("api_keys_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (apiKeys.isNotEmpty()) {
                        IconButton(
                            onClick = { showDeleteAllDialog = true },
                            modifier = Modifier.testTag("delete_all_keys_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete All Keys",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_api_key_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Key")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Keys are securely masked and stored in encrypted local storage. When a key hits rate limits or quota, the Agent automatically fails over to the next key without losing state.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            items(apiKeys, key = { it.id }) { keyEntity ->
                ApiKeyCard(
                    keyEntity = keyEntity,
                    testStatus = testStatusMap[keyEntity.id],
                    onToggleEnabled = { isEnabled ->
                        coroutineScope.launch {
                            agentRepository.apiKeyDao.updateApiKey(keyEntity.copy(isEnabled = isEnabled))
                        }
                    },
                    onTestKey = {
                        testStatusMap[keyEntity.id] = "TESTING"
                        coroutineScope.launch {
                            val (success, msg) = geminiApiClient.testApiKey(
                                apiKey = keyEntity.apiKey,
                                provider = keyEntity.provider,
                                baseUrl = keyEntity.baseUrl
                            )
                            testStatusMap[keyEntity.id] = if (success) "VALID: $msg" else "ERROR: $msg"
                            if (success) {
                                agentRepository.markKeySuccess(keyEntity.id)
                            } else {
                                agentRepository.markKeyFailure(keyEntity.id, msg)
                            }
                        }
                    },
                    onDelete = {
                        keyToDelete = keyEntity
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Add Key Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add AI API Key") },
            text = {
                Column {
                    Text(
                        text = "Support Groq Free (LPU speed), Google Gemini, OpenRouter, or OpenAI-compatible endpoints.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Provider Type", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "GROQ" to "Groq Free",
                            "GEMINI" to "Gemini",
                            "OPENROUTER" to "OpenRouter",
                            "OPENAI" to "Custom/OpenAI"
                        ).forEach { (provKey, provName) ->
                            OutlinedButton(
                                onClick = { selectedProvider = provKey },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selectedProvider == provKey) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text(provName, fontSize = 10.sp, fontWeight = if (selectedProvider == provKey) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newKeyLabel,
                        onValueChange = { newKeyLabel = it },
                        label = { Text("Label (optional)") },
                        placeholder = { Text(if (selectedProvider == "GROQ") "e.g. My Groq Free Key" else if (selectedProvider == "OPENROUTER") "e.g. OpenRouter Free Tier" else "e.g. Personal Gemini Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("api_key_label_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newKeyInput,
                        onValueChange = { input ->
                            newKeyInput = input
                            // Auto detect provider from key prefix
                            if (input.trim().startsWith("gsk_")) {
                                selectedProvider = "GROQ"
                            } else if (input.trim().startsWith("sk-or-")) {
                                selectedProvider = "OPENROUTER"
                            } else if (input.trim().startsWith("AIzaSy")) {
                                selectedProvider = "GEMINI"
                            }
                        },
                        label = { Text("API Key") },
                        placeholder = { Text(if (selectedProvider == "GROQ") "gsk_..." else if (selectedProvider == "OPENROUTER") "sk-or-v1-..." else if (selectedProvider == "GEMINI") "AIzaSy..." else "sk-...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("api_key_value_input")
                    )

                    if (selectedProvider == "OPENAI") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customBaseUrl,
                            onValueChange = { customBaseUrl = it },
                            label = { Text("Custom Base URL (optional)") },
                            placeholder = { Text("https://api.openai.com/v1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newKeyInput.isNotBlank()) {
                            val provLabel = when (selectedProvider) {
                                "GROQ" -> "Groq Key"
                                "OPENROUTER" -> "OpenRouter Key"
                                "OPENAI" -> "OpenAI Key"
                                else -> "Gemini Key"
                            }
                            val label = newKeyLabel.ifBlank { "$provLabel ${apiKeys.size + 1}" }
                            coroutineScope.launch {
                                agentRepository.apiKeyDao.insertApiKey(
                                    ApiKeyEntity(
                                        apiKey = newKeyInput.trim(),
                                        label = label,
                                        isEnabled = true,
                                        priorityOrder = apiKeys.size,
                                        provider = selectedProvider,
                                        baseUrl = customBaseUrl.trim()
                                    )
                                )
                                showAddDialog = false
                                newKeyInput = ""
                                newKeyLabel = ""
                                customBaseUrl = ""
                            }
                        }
                    },
                    enabled = newKeyInput.isNotBlank(),
                    modifier = Modifier.testTag("save_api_key_button")
                ) {
                    Text("Save Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    keyToDelete?.let { entity ->
        AlertDialog(
            onDismissRequest = { keyToDelete = null },
            title = { Text("Delete API Key") },
            text = { Text("Are you sure you want to remove '${entity.label}' (${entity.getMaskedKey()}) from the failover pool?") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            agentRepository.apiKeyDao.deleteApiKey(entity)
                            keyToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_key_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { keyToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete All Confirmation Dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All API Keys") },
            text = { Text("Are you sure you want to permanently delete ALL saved API keys?") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            agentRepository.deleteAllApiKeys()
                            showDeleteAllDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_all_keys_button")
                ) {
                    Text("Delete All Keys")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ApiKeyCard(
    keyEntity: ApiKeyEntity,
    testStatus: String?,
    onToggleEnabled: (Boolean) -> Unit,
    onTestKey: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (keyEntity.isEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("api_key_card_${keyEntity.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = keyEntity.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val provName = when (keyEntity.provider) {
                            "GROQ" -> "Groq Free"
                            "OPENROUTER" -> "OpenRouter"
                            "OPENAI" -> "OpenAI"
                            else -> "Gemini"
                        }
                        val badgeColor = when (keyEntity.provider) {
                            "GROQ" -> Color(0xFFF57C00)
                            "OPENROUTER" -> Color(0xFF512DA8)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Surface(
                            color = badgeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = provName,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = keyEntity.getMaskedKey(),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = keyEntity.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("toggle_key_switch_${keyEntity.id}")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status Badge & Stats Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusBadge(status = keyEntity.status, isEnabled = keyEntity.isEnabled)

                Text(
                    text = "✓ ${keyEntity.successCount} | ✗ ${keyEntity.failCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.weight(1f))

                // Test Button
                OutlinedButton(
                    onClick = onTestKey,
                    enabled = keyEntity.isEnabled && testStatus != "TESTING",
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).testTag("test_key_button_${keyEntity.id}")
                ) {
                    if (testStatus == "TESTING") {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test", fontSize = 11.sp)
                    }
                }

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp).testTag("delete_key_button_${keyEntity.id}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Test Output / Error message if available
            testStatus?.let { status ->
                Spacer(modifier = Modifier.height(6.dp))
                val isSuccess = status.startsWith("VALID")
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            keyEntity.lastError?.takeIf { testStatus == null }?.let { err ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Last error: $err",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StatusBadge(status: String, isEnabled: Boolean) {
    val (bgColor, textColor, text) = when {
        !isEnabled -> Triple(Color(0xFFE0E0E0), Color(0xFF616161), "Disabled")
        status == "ACTIVE" || status == "WORKING" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Active & Healthy")
        status == "RATE_LIMITED" -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "Rate Limited (Failing Over)")
        status == "QUOTA_EXHAUSTED" -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Quota Exhausted")
        status == "INVALID" -> Triple(Color(0xFFFFCDD2), Color(0xFFB71C1C), "Invalid Key")
        else -> Triple(Color(0xFFEDE7F6), Color(0xFF4A148C), status)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
