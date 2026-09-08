package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppThemeMode
import com.example.data.agent.AgentConfigEntity
import com.example.model.SearchEngine
import com.example.ui.BrowserScreen
import com.example.ui.BrowserUiState
import com.example.ui.BrowserViewModel
import com.example.ui.agent.ModelSelectionDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: BrowserUiState,
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val agentRepo = viewModel.agentRepository
    val apiKeys by agentRepo.allApiKeys.collectAsState(initial = emptyList())
    val config by agentRepo.configFlow.collectAsState(initial = AgentConfigEntity())

    var showSearchEngineDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showHomePageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showModelSelectorDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateToScreen(BrowserScreen.BROWSER) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsCategoryHeader("Search & Navigation")

            SettingsClickableItem(
                icon = Icons.Default.Search,
                title = "Search Engine",
                subtitle = state.searchEngine.displayName,
                onClick = { showSearchEngineDialog = true }
            )

            SettingsClickableItem(
                icon = Icons.Default.Home,
                title = "Home Page",
                subtitle = if (state.homePageUrl.isBlank()) "New Tab Page" else state.homePageUrl,
                onClick = { showHomePageDialog = true }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            SettingsCategoryHeader("Appearance")

            SettingsClickableItem(
                icon = Icons.Default.Palette,
                title = "Theme",
                subtitle = when (state.themeMode) {
                    AppThemeMode.SYSTEM -> "System default"
                    AppThemeMode.LIGHT -> "Light theme"
                    AppThemeMode.DARK -> "Dark theme"
                },
                onClick = { showThemeDialog = true }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            SettingsCategoryHeader("AI Agent & Gemini API Keys")

            val activeKeysCount = apiKeys.count { it.isEnabled }
            SettingsClickableItem(
                icon = Icons.Default.Key,
                title = "Gemini API Keys",
                subtitle = if (apiKeys.isEmpty()) "No API key added • Tap to add manually"
                else "$activeKeysCount active key${if (activeKeysCount == 1) "" else "s"} configured",
                onClick = { viewModel.navigateToScreen(BrowserScreen.API_KEYS) }
            )

            SettingsClickableItem(
                icon = Icons.Default.AutoAwesome,
                title = "Active AI Model",
                subtitle = (config?.activeModel ?: "gemini-2.0-flash").removePrefix("models/"),
                onClick = { showModelSelectorDialog = true }
            )

            SettingsClickableItem(
                icon = Icons.Default.BookmarkBorder,
                title = "Saved AI Prompts Library",
                subtitle = "View and manage extracted prompts",
                onClick = { viewModel.navigateToScreen(BrowserScreen.SAVED_PROMPTS) }
            )

            SettingsSwitchItem(
                icon = Icons.Default.Refresh,
                title = "Auto-Failover Across Keys",
                subtitle = "Automatically switch API keys if rate limits or quota are reached",
                checked = config?.isAutoFailoverEnabled ?: true,
                onCheckedChange = { enabled ->
                    coroutineScope.launch {
                        val current = config ?: AgentConfigEntity()
                        agentRepo.updateConfig(current.copy(isAutoFailoverEnabled = enabled))
                    }
                }
            )

            SettingsSwitchItem(
                icon = Icons.Default.Visibility,
                title = "Screen & Vision Analysis",
                subtitle = "Allow AI Agent to inspect page screenshots for visual context",
                checked = config?.isVisionEnabled ?: true,
                onCheckedChange = { enabled ->
                    coroutineScope.launch {
                        val current = config ?: AgentConfigEntity()
                        agentRepo.updateConfig(current.copy(isVisionEnabled = enabled))
                    }
                }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            SettingsCategoryHeader("Advanced & Web")

            SettingsSwitchItem(
                icon = Icons.Default.Computer,
                title = "Desktop Site by Default",
                subtitle = "Always request desktop version of websites",
                checked = state.isDesktopModeDefault,
                onCheckedChange = { viewModel.setDesktopModeDefault(it) }
            )

            SettingsSwitchItem(
                icon = Icons.Default.Code,
                title = "Enable JavaScript",
                subtitle = "Allow websites to run interactive scripts",
                checked = state.isJavaScriptEnabled,
                onCheckedChange = { viewModel.setJavaScriptEnabled(it) }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            SettingsCategoryHeader("Privacy & Security")

            SettingsClickableItem(
                icon = Icons.Default.DeleteSweep,
                title = "Clear Browsing Data",
                subtitle = "Clear history, cookies, and cache",
                onClick = { viewModel.showClearDataDialog(true) }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            SettingsCategoryHeader("About")

            SettingsClickableItem(
                icon = Icons.Default.Info,
                title = "About Browser",
                subtitle = "Version 1.0 • Android WebView",
                onClick = { showAboutDialog = true }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Search Engine Selection Dialog
    if (showSearchEngineDialog) {
        AlertDialog(
            onDismissRequest = { showSearchEngineDialog = false },
            title = { Text("Search Engine") },
            text = {
                Column {
                    SearchEngine.entries.forEach { engine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSearchEngine(engine)
                                    showSearchEngineDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.searchEngine == engine,
                                onClick = {
                                    viewModel.setSearchEngine(engine)
                                    showSearchEngineDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = engine.displayName, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchEngineDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Theme Selection Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme") },
            text = {
                Column {
                    AppThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            AppThemeMode.SYSTEM -> "System default"
                            AppThemeMode.LIGHT -> "Light"
                            AppThemeMode.DARK -> "Dark"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.themeMode == mode,
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = label, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Home page Dialog
    if (showHomePageDialog) {
        var tempUrl by remember { mutableStateOf(state.homePageUrl) }
        AlertDialog(
            onDismissRequest = { showHomePageDialog = false },
            title = { Text("Home Page URL") },
            text = {
                Column {
                    Text(
                        text = "Leave empty to use the default New Tab Page.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        placeholder = { Text("https://example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setHomePageUrl(tempUrl.trim())
                        showHomePageDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHomePageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Browser") },
            text = {
                Column {
                    Text(
                        text = "Browser for Android",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.0 (Build 2026)\nPowered by Android System WebView & Jetpack Compose.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Fast, clean, modern browsing with tab management, incognito privacy, bookmarks, history, and downloads.",
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showModelSelectorDialog) {
        ModelSelectionDialog(
            currentModel = config?.activeModel ?: "gemini-2.0-flash",
            agentRepository = agentRepo,
            onModelSelected = { selectedModel ->
                coroutineScope.launch {
                    val current = config ?: AgentConfigEntity()
                    agentRepo.updateConfig(current.copy(activeModel = selectedModel))
                }
                showModelSelectorDialog = false
            },
            onDismiss = { showModelSelectorDialog = false }
        )
    }
}

@Composable
private fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
