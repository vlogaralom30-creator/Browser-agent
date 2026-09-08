package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BrowserTab
import com.example.ui.theme.IncognitoPrimary
import com.example.ui.theme.IncognitoSurface

@Composable
fun BottomNavBar(
    tab: BrowserTab?,
    isIncognito: Boolean,
    isBookmarked: Boolean,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onNewTabClick: () -> Unit,
    onAgentClick: () -> Unit = {},
    onBookmarkClick: () -> Unit,
    onReloadClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barBackground = if (isIncognito) IncognitoSurface else MaterialTheme.colorScheme.surface
    val contentColor = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
    val disabledColor = if (isIncognito) Color(0x44FFFFFF) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val dividerColor = if (isIncognito) Color(0x22FFFFFF) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(barBackground)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(
            color = dividerColor,
            thickness = 0.5.dp,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back
            IconButton(
                onClick = onBackClick,
                enabled = tab?.canGoBack == true,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("nav_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (tab?.canGoBack == true) contentColor else disabledColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Forward
            IconButton(
                onClick = onForwardClick,
                enabled = tab?.canGoForward == true,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("nav_forward_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    tint = if (tab?.canGoForward == true) contentColor else disabledColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // AI Browser Agent (Sparkle Button)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onAgentClick)
                    .testTag("nav_agent_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Browser Agent",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // New Tab (+)
            IconButton(
                onClick = onNewTabClick,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("nav_new_tab_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Tab",
                    tint = if (isIncognito) IncognitoPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Bookmark Toggle
            IconButton(
                onClick = onBookmarkClick,
                enabled = tab?.isNewTab == false && tab.url.isNotBlank(),
                modifier = Modifier
                    .size(40.dp)
                    .testTag("nav_bookmark_button")
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (isBookmarked) "Bookmarked" else "Add Bookmark",
                    tint = if (isBookmarked) {
                        if (isIncognito) IncognitoPrimary else MaterialTheme.colorScheme.primary
                    } else if (tab?.isNewTab == false && tab.url.isNotBlank()) {
                        contentColor
                    } else {
                        disabledColor
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            // Reload or Stop
            IconButton(
                onClick = onReloadClick,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("nav_reload_button")
            ) {
                Icon(
                    imageVector = if (tab?.isLoading == true) Icons.Default.Close else Icons.Default.Refresh,
                    contentDescription = if (tab?.isLoading == true) "Stop Loading" else "Reload Page",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Share
            IconButton(
                onClick = onShareClick,
                enabled = tab?.isNewTab == false && tab.url.isNotBlank(),
                modifier = Modifier
                    .size(40.dp)
                    .testTag("nav_share_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share URL",
                    tint = if (tab?.isNewTab == false && tab.url.isNotBlank()) contentColor else disabledColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
