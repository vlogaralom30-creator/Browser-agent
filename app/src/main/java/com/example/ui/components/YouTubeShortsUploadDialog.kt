package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TikTokReelItem
import com.example.util.YouTubeAuthHelper
import com.example.util.YouTubeShareHelper

private val YouTubeRed = Color(0xFFFF0000)

@Composable
fun YouTubeShortsUploadDialog(
    item: TikTokReelItem,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismissSheet: () -> Unit,
    onWatchClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var showCopiedFeedback by remember { mutableStateOf(false) }
    var ytStatus by remember { mutableStateOf(YouTubeAuthHelper.checkStatus(context)) }
    val shortsCaption = remember(item.spunTitle) {
        YouTubeShareHelper.formatShortsTitle(item.spunTitle)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = YouTubeRed.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = YouTubeRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Upload to YouTube Shorts",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.fileName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // YouTube Login Status Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (ytStatus.canUpload) Color(0xFF10B981).copy(alpha = 0.10f) else Color(0xFFF59E0B).copy(alpha = 0.14f),
                    border = BorderStroke(
                        1.dp,
                        if (ytStatus.canUpload) Color(0xFF10B981).copy(alpha = 0.35f) else Color(0xFFF59E0B).copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (ytStatus.canUpload) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (ytStatus.canUpload) Color(0xFF059669) else Color(0xFFD97706),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (ytStatus.canUpload) "YouTube সেশন প্রস্তুত" else "⚠️ YouTube লগইন প্রয়োজন",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (ytStatus.canUpload) Color(0xFF065F46) else Color(0xFF92400E)
                            )
                        }
                        if (!ytStatus.canUpload) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ব্রাউজারে YouTube/Google চ্যানেল লগইন নেই। নিচের বাটনে ট্যাপ করে লগইন করুন:",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    YouTubeShareHelper.openYouTubeLogin(onOpenUrl)
                                    onDismiss()
                                    onDismissSheet()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                            ) {
                                Text("🔑 ব্রাউজারে YouTube Login করুন", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Caption preview & quick copy
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "YouTube Shorts Title & Tags:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = YouTubeRed
                            )
                            TextButton(
                                onClick = {
                                    YouTubeShareHelper.copyCaptionToClipboard(context, shortsCaption)
                                    showCopiedFeedback = true
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Copy", fontSize = 10.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = shortsCaption,
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        AnimatedVisibility(visible = showCopiedFeedback) {
                            Text(
                                text = "✓ ক্লিকে কপি হয়েছে! পেস্ট করার জন্য প্রস্তুত।",
                                fontSize = 10.sp,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action 1: Upload via YouTube App
                Button(
                    onClick = {
                        val success = YouTubeShareHelper.uploadToYouTubeApp(
                            context = context,
                            filePath = item.localFilePath,
                            caption = shortsCaption
                        )
                        if (success) {
                            onDismiss()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upload_yt_app_btn")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "YouTube App এ Shorts আপলোড করুন",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ভিডিও সহ অটো ওপেন হবে • টাইটেল ক্লিপবোর্ডে কপি হবে",
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action 2: Upload via YouTube Studio Web
                OutlinedButton(
                    onClick = {
                        YouTubeShareHelper.copyCaptionToClipboard(context, shortsCaption, notify = true)
                        YouTubeShareHelper.openYouTubeStudio(onOpenUrl)
                        onDismiss()
                        onDismissSheet()
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, YouTubeRed.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upload_yt_studio_btn")
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp), tint = YouTubeRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "YouTube Studio Web এ ওপেন করুন",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = YouTubeRed
                        )
                        Text(
                            text = "studio.youtube.com ওপেন হবে • টাইটেল কপি হবে",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
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
                        Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = YouTubeRed)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Watch Video", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            YouTubeShareHelper.copyCaptionToClipboard(context, shortsCaption)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Title", fontSize = 11.sp)
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
