package com.example.ui.agent

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.GeminiApiClient
import com.example.ai.GeminiModelInfo
import com.example.data.agent.AgentRepository
import kotlinx.coroutines.launch

@Composable
fun ModelSelectionDialog(
    currentModel: String,
    agentRepository: AgentRepository,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val geminiApiClient = remember { GeminiApiClient(agentRepository) }

    var modelsList by remember { mutableStateOf<List<GeminiModelInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") } // "All", "OpenRouter", "Groq", "Gemini"

    LaunchedEffect(Unit) {
        isLoading = true
        modelsList = geminiApiClient.fetchAvailableModels()
        isLoading = false
    }

    // Filter models based on search query and provider chip
    val filteredModels = remember(modelsList, searchQuery, selectedCategory) {
        modelsList.filter { modelInfo ->
            val matchesCategory = when (selectedCategory) {
                "OpenRouter" -> modelInfo.name.contains("/") || modelInfo.name.startsWith("openrouter") || modelInfo.displayName.contains("OpenRouter")
                "Groq" -> modelInfo.name.contains("llama") || modelInfo.name.contains("groq") || modelInfo.displayName.contains("Groq")
                "Gemini" -> modelInfo.name.startsWith("gemini") && !modelInfo.name.contains("/")
                else -> true
            }

            val matchesQuery = searchQuery.isBlank() ||
                modelInfo.name.contains(searchQuery, ignoreCase = true) ||
                modelInfo.displayName.contains(searchQuery, ignoreCase = true) ||
                modelInfo.description.contains(searchQuery, ignoreCase = true)

            matchesCategory && matchesQuery
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select AI Model", style = MaterialTheme.typography.titleMedium)
                }

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            modelsList = geminiApiClient.fetchAvailableModels()
                            isLoading = false
                        }
                    },
                    modifier = Modifier.size(32.dp).testTag("refresh_models_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Search Input Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search free models (e.g., ling, gemma, nemotron)...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("model_search_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Provider Category Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val categories = listOf("All", "OpenRouter", "Groq", "Gemini")
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("filter_chip_$category")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isLoading && modelsList.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Fetching models...", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (filteredModels.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No matching models found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().height(360.dp)
                    ) {
                        items(filteredModels) { modelInfo ->
                            val isSelected = currentModel == modelInfo.name || currentModel.removePrefix("models/") == modelInfo.name
                            val isTopModel = modelInfo.name == "openrouter/free" ||
                                modelInfo.name == "google/gemini-1.5-flash:free" ||
                                modelInfo.name == "meta-llama/llama-3.3-70b-instruct:free" ||
                                modelInfo.name == "deepseek/deepseek-r1:free" ||
                                modelInfo.name == "llama-3.1-8b-instant" ||
                                modelInfo.name == "gemini-1.5-flash" ||
                                modelInfo.name.contains("nemotron-3-super")

                            val isFreeModel = modelInfo.name.contains(":free") ||
                                modelInfo.displayName.contains("[FREE]", ignoreCase = true) ||
                                modelInfo.displayName.contains("Free", ignoreCase = true) ||
                                modelInfo.name == "openrouter/free"

                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onModelSelected(modelInfo.name)
                                        onDismiss()
                                    }
                                    .testTag("model_item_${modelInfo.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            onModelSelected(modelInfo.name)
                                            onDismiss()
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isTopModel) {
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = "Recommended",
                                                    tint = Color(0xFFFFB800),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = modelInfo.displayName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = if (isSelected || isTopModel) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isFreeModel) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "FREE",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = modelInfo.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
