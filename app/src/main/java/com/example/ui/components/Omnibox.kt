package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BrowserTab
import com.example.ui.theme.IncognitoPrimary
import com.example.ui.theme.IncognitoSurface
import com.example.ui.theme.IncognitoSurfaceVariant
import com.example.util.UrlUtils

@Composable
fun Omnibox(
    tab: BrowserTab?,
    tabsCount: Int,
    isIncognito: Boolean,
    onHomeClick: () -> Unit,
    onSubmitUrl: (String) -> Unit,
    onTabSwitcherClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSecurityInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember(tab?.url) {
        val initial = when {
            tab?.isNewTab == true -> ""
            tab?.url?.startsWith("browser://search") == true -> tab.displayUrl
            else -> tab?.url ?: ""
        }
        mutableStateOf(TextFieldValue(text = initial))
    }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val isSecure = remember(tab?.url) { UrlUtils.isSecure(tab?.url ?: "") }
    val displayHost = remember(tab?.url, tab?.displayUrl) {
        if (tab?.url?.startsWith("browser://search") == true) {
            "Search: ${tab.displayUrl}"
        } else {
            UrlUtils.getDisplayHost(tab?.url ?: "")
        }
    }

    val barBackground = if (isIncognito) IncognitoSurface else MaterialTheme.colorScheme.surface
    val inputBackground = if (isIncognito) IncognitoSurfaceVariant else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryContentColor = if (isIncognito) Color(0xFFC4C7C5) else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barBackground)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home button
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    onHomeClick()
                },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("home_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Main Omnibox input container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(inputBackground)
                    .testTag("omnibox_input_container"),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lock / Search Icon / Incognito Icon
                    if (isIncognito) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "Incognito",
                            tint = IncognitoPrimary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onSecurityInfoClick() }
                        )
                    } else if (tab?.isNewTab == true || tab?.url.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = secondaryContentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isSecure) Icons.Filled.Lock else Icons.Outlined.Info,
                            contentDescription = if (isSecure) "Secure Connection" else "Not Secure",
                            tint = if (isSecure) MaterialTheme.colorScheme.primary else Color(0xFFE57373),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onSecurityInfoClick() }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Text Field
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (!isFocused && (tab?.isNewTab == true || tab?.url.isNullOrBlank())) {
                            Text(
                                text = "Search or type URL",
                                color = secondaryContentColor,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                        } else if (!isFocused && tab?.url?.isNotBlank() == true) {
                            Text(
                                text = displayHost,
                                color = contentColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    isFocused = focusState.isFocused
                                    if (focusState.isFocused) {
                                        val currentUrl = if (tab?.isNewTab == true || tab?.url == "about:blank") "" else tab?.url ?: ""
                                        textFieldValue = TextFieldValue(
                                            text = currentUrl,
                                            selection = TextRange(0, currentUrl.length)
                                        )
                                    }
                                }
                                .testTag("omnibox_text_field"),
                            textStyle = TextStyle(
                                color = if (isFocused) contentColor else Color.Transparent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(if (isIncognito) IncognitoPrimary else MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    if (textFieldValue.text.isNotBlank()) {
                                        onSubmitUrl(textFieldValue.text)
                                    }
                                }
                            )
                        )
                    }

                    // Shield badge displaying blocked ads count
                    if (!isFocused && (tab?.blockedAdsCount ?: 0) > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { onSecurityInfoClick() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "Blocked Ads",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${tab?.blockedAdsCount}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Clear button when focused and not empty
                    if (isFocused && textFieldValue.text.isNotBlank()) {
                        IconButton(
                            onClick = { textFieldValue = TextFieldValue("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = secondaryContentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Tab counter button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                        onTabSwitcherClick()
                    }
                    .testTag("tab_switcher_button"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (isIncognito) IncognitoPrimary else contentColor
                    ),
                    color = Color.Transparent,
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (tabsCount > 99) ":D" else tabsCount.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isIncognito) IncognitoPrimary else contentColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // More Menu button
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    onMenuClick()
                },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Web loading progress bar
        AnimatedVisibility(
            visible = tab?.isLoading == true && tab.progress in 1..99,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                progress = { (tab?.progress ?: 0) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = if (isIncognito) IncognitoPrimary else MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }
    }
}
