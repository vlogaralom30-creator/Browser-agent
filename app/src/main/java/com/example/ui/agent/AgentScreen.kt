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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.agent.AgentConfigEntity
import com.example.ui.BrowserScreen
import com.example.ui.BrowserViewModel

data class UpcomingFeature(
    val title: String,
    val titleBangla: String,
    val description: String,
    val descriptionBangla: String,
    val icon: ImageVector,
    val badge: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val agentRepository = viewModel.agentRepository
    val config by agentRepository.configFlow.collectAsState(initial = AgentConfigEntity())
    var showModelSelector by remember { mutableStateOf(false) }
    var isSubscribed by remember { mutableStateOf(false) }

    val upcomingFeatures = listOf(
        UpcomingFeature(
            title = "Autonomous Web Navigation",
            titleBangla = "স্বয়ংক্রিয় ব্রাউজিং ও ওয়েবসাইট নেভিগেশন",
            description = "Ask AI to open any site, search topics, fill forms, scroll pages, and extract data automatically.",
            descriptionBangla = "যেকোনো ওয়েবসাইট ওপেন, সার্চ, অটোমেটিক ফর্ম ফিলআপ ও ডাটা এক্সট্রাক্ট করার ক্ষমতা।",
            icon = Icons.Default.Language,
            badge = "CORE AGENT"
        ),
        UpcomingFeature(
            title = "YouTube Studio & Creator Automation",
            titleBangla = "ইউটিউব স্টুডিও অ্যাসিস্ট্যান্ট",
            description = "Autonomously update video titles, descriptions, tags, and settings in YouTube Studio.",
            descriptionBangla = "ইউটিউব ভিডিওর টাইটেল, ডেসক্রিপশন ও ট্যাগ স্বয়ংক্রিয়ভাবে আপডেট করা।",
            icon = Icons.Default.VideoLibrary,
            badge = "STUDIO"
        ),
        UpcomingFeature(
            title = "OpenRouter Multi-Model Engine",
            titleBangla = "মাল্টি AI মডেল ওপেনরাউটার সাপোর্ট",
            description = "Powered by OpenRouter: Ling 3.0 Flash Sante, Gemini 2.0, DeepSeek R1, Llama 3.3, Nemotron & Gemma.",
            descriptionBangla = "Ling 3.0, Gemini 2.0, DeepSeek R1, Llama 3.3 সহ বিশ্বের সেরা ফ্রি AI মডেল।",
            icon = Icons.Default.Psychology,
            badge = "OPENROUTER"
        ),
        UpcomingFeature(
            title = "Smart API Key Failover Vault",
            titleBangla = "অটো ফেলওভার ও সিকিউর কি ম্যানেজার",
            description = "Add multiple API keys. Auto failover to the next key upon rate limits without losing task progress.",
            descriptionBangla = "মাল্টিপল API কি সাপোর্ট। লিমিট শেষ হলে স্বয়ংক্রিয়ভাবে পরবর্তী কি-তে সুইচ করবে।",
            icon = Icons.Default.Key,
            badge = "SECURITY"
        ),
        UpcomingFeature(
            title = "Full Access & Safety Guards",
            titleBangla = "ফুল অ্যাক্সেস মোড ও সিকিউরিটি গার্ড",
            description = "Perform normal browser actions autonomously, while strictly asking confirmation before delete or publish.",
            descriptionBangla = "স্বয়ংক্রিয় কাজ করার পাশাপাশি ডিলিট বা পাবলিশের মতো সংবেদনশীল কাজে পারমিশন চাইবে।",
            icon = Icons.Default.Security,
            badge = "SAFETY"
        ),
        UpcomingFeature(
            title = "Persistent Task Checkpoint Memory",
            titleBangla = "পারসিস্টেন্ট টাস্ক মেমোরি ও রিজিউম",
            description = "Tasks persist in Room Database. Pause/resume anytime and say 'Continue' to pick up right where left off.",
            descriptionBangla = "টাস্ক পজ ও রিজিউম করার সুবিধা। অ্যাপ বন্ধ থাকলেও আগের জায়গা থেকেই কাজ শুরু করবে।",
            icon = Icons.Default.Memory,
            badge = "MEMORY"
        ),
        UpcomingFeature(
            title = "AI Prompt Extractor & Saved Library",
            titleBangla = "স্মার্ট প্রম্পট কানেক্টর ও লোকাল লাইব্রেরি",
            description = "Extract cinematic AI image prompts from webpages and save them into your searchable offline library.",
            descriptionBangla = "ওয়েবসাইট থেকে AI ইমেজ প্রম্পট বা কনটেন্ট সরাসরি লোকাল ডাটাবেসে সেভ করা।",
            icon = Icons.Default.BookmarkBorder,
            badge = "PROMPTS"
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AI Browser Agent",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.clickable { showModelSelector = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = (config?.activeModel ?: "ling-3.0-flash-sante").removePrefix("models/"),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
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
                    IconButton(
                        onClick = { viewModel.navigateToScreen(BrowserScreen.SAVED_PROMPTS) },
                        modifier = Modifier.testTag("agent_prompts_button")
                    ) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = "Saved Prompts")
                    }

                    IconButton(
                        onClick = { viewModel.navigateToScreen(BrowserScreen.API_KEYS) },
                        modifier = Modifier.testTag("agent_api_keys_button")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = "API Keys")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))

                // Hero Maintenance / Coming Soon Banner Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Offline Badge
                        Surface(
                            color = Color(0xFFD32F2F).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD32F2F))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFD32F2F))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI SYSTEM OFFLINE • WORK IN PROGRESS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD32F2F)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "AI System - Coming Soon!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "কাজ চলছে... খুব শীঘ্রই আসছে!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "আমাদের অটোনোমাস AI এজেন্ট সিস্টেমের আপগ্রেড ও ডেভেলপমেন্ট কাজ দ্রুত গতিতে চলছে। খুব শীঘ্রই একঝাঁক ধামাকা ফিচার নিয়ে আপনার সার্ভিসে হাজির হচ্ছে Next-Gen AI Browser Assistant!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Notify Button
                        Button(
                            onClick = {
                                isSubscribed = true
                                Toast.makeText(
                                    context,
                                    "ধন্যবাদ! AI এজেন্ট চালু হওয়ার সাথে সাথেই নোটিফিকেশন পাবেন।",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSubscribed) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("notify_me_button")
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSubscribed) "✓ আপনি নোটিফিকেশনে সাবস্ক্রাইবড" else "🔔 আপডেট জানতে সাবস্ক্রাইব করুন",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "আসন্ন এক্সসাইটিং ফিচারসমূহ (Features)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "ROADMAP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Feature List Items
            items(upcomingFeatures) { feature ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = feature.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = feature.titleBangla,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = feature.badge,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            Text(
                                text = feature.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = feature.descriptionBangla,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                // Quick Action Buttons
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "প্রস্তুতি নিন (Quick Access)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.navigateToScreen(BrowserScreen.API_KEYS) },
                                modifier = Modifier.weight(1f).testTag("setup_api_keys_btn")
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("API Key সেটিংস")
                            }

                            OutlinedButton(
                                onClick = { viewModel.navigateToScreen(BrowserScreen.SAVED_PROMPTS) },
                                modifier = Modifier.weight(1f).testTag("view_prompts_btn")
                            ) {
                                Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("সেভড প্রম্পটস")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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
