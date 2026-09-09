package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BrowserTab
import com.example.ui.theme.IncognitoPrimary
import com.example.util.UrlUtils

@Composable
fun PageInfoDialog(
    tab: BrowserTab?,
    isIncognito: Boolean,
    isSmartPrivateDomain: Boolean = false,
    onToggleSmartPrivateDomain: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isSecure = UrlUtils.isSecure(tab?.url ?: "")
    val host = UrlUtils.getDisplayHost(tab?.url ?: "")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (isIncognito) IncognitoPrimary.copy(alpha = 0.2f)
                    else if (isSecure) MaterialTheme.colorScheme.primaryContainer
                    else Color(0xFFFFEBEE),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isIncognito) Icons.Rounded.Shield
                            else if (isSecure) Icons.Filled.Lock
                            else Icons.Outlined.Info,
                            contentDescription = null,
                            tint = if (isIncognito) IncognitoPrimary
                            else if (isSecure) MaterialTheme.colorScheme.primary
                            else Color(0xFFD32F2F),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = host,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column {
                if (isIncognito) {
                    Text(
                        text = "Incognito Mode Active",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your browsing activity is private and will not be recorded in your history or cookies on this device.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = if (isSecure) "Connection is secure" else "Connection is not secure",
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSecure) MaterialTheme.colorScheme.primary else Color(0xFFD32F2F),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isSecure)
                        "Your information (for example, passwords or credit card numbers) is private when it is sent to this site."
                    else
                        "You should not enter any sensitive information on this site (for example, passwords or credit cards), because it could be stolen by attackers.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (onToggleSmartPrivateDomain != null && host.isNotBlank() && host != "Search or type URL") {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Always Open in Private Mode",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (isSmartPrivateDomain) "Site is on Smart Private list" else "Route automatically to Private Mode",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isSmartPrivateDomain,
                            onCheckedChange = { onToggleSmartPrivateDomain(it) },
                            modifier = Modifier.testTag("toggle_smart_private_site")
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}

