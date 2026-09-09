package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.ContextMenuData
import com.example.ui.BrowserViewModel
import com.example.util.DownloadHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserContextMenuSheet(
    data: ContextMenuData,
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when (data) {
                is ContextMenuData.Link -> {
                    val hostDomain = remember(data.url) {
                        try {
                            Uri.parse(data.url).host ?: data.url
                        } catch (e: Exception) {
                            data.url
                        }
                    }
                    val displayInitial = remember(data.title, hostDomain) {
                        val text = (data.title ?: hostDomain).trim()
                        if (text.isNotEmpty()) text.first().uppercaseChar().toString() else "G"
                    }

                    // Top Header Card matching Chrome screenshot 1
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayInitial,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = data.title?.ifBlank { hostDomain } ?: hostDomain,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = data.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ContextMenuItem(
                        icon = Icons.Default.OpenInNew,
                        label = "Open in new tab",
                        testTag = "ctx_open_new_tab"
                    ) {
                        onDismiss()
                        viewModel.openNewTab(url = data.url, isIncognito = false)
                    }

                    ContextMenuItem(
                        icon = Icons.Default.Tab,
                        label = "Open in background tab",
                        testTag = "ctx_open_background_tab"
                    ) {
                        onDismiss()
                        viewModel.openNewTab(url = data.url, isIncognito = false)
                    }

                    ContextMenuItem(
                        icon = Icons.Default.Security,
                        label = "Open in Incognito tab",
                        testTag = "ctx_open_incognito"
                    ) {
                        onDismiss()
                        viewModel.openNewTab(url = data.url, isIncognito = true)
                    }

                    // Preview page (Interactive Preview Panel)
                    ContextMenuItem(
                        icon = Icons.Default.Visibility,
                        label = "Preview page",
                        badge = "Preview",
                        testTag = "ctx_preview_page"
                    ) {
                        onDismiss()
                        viewModel.openPreview(url = data.url, title = data.title)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = DividerDefaults.color.copy(alpha = 0.35f)
                    )

                    ContextMenuItem(
                        icon = Icons.Default.Link,
                        label = "Copy link address",
                        testTag = "ctx_copy_link"
                    ) {
                        onDismiss()
                        copyToClipboard(context, "Link Address", data.url)
                    }

                    if (!data.linkText.isNullOrBlank()) {
                        ContextMenuItem(
                            icon = Icons.Default.ContentCopy,
                            label = "Copy link text",
                            testTag = "ctx_copy_link_text"
                        ) {
                            onDismiss()
                            copyToClipboard(context, "Link Text", data.linkText)
                        }
                    }

                    ContextMenuItem(
                        icon = Icons.Default.Download,
                        label = "Download link",
                        testTag = "ctx_download_link"
                    ) {
                        onDismiss()
                        DownloadHandler.startDownload(
                            context = context,
                            url = data.url,
                            userAgent = null,
                            contentDisposition = null,
                            mimeType = null,
                            contentLength = 0,
                            onDownloadStarted = { entity ->
                                viewModel.onDownloadStarted(entity)
                            }
                        )
                    }

                    ContextMenuItem(
                        icon = Icons.Default.BookmarkAdd,
                        label = "Add to bookmarks",
                        testTag = "ctx_add_reading_list"
                    ) {
                        onDismiss()
                        viewModel.toggleBookmark(context)
                    }

                    ContextMenuItem(
                        icon = Icons.Default.Share,
                        label = "Share link",
                        testTag = "ctx_share_link"
                    ) {
                        onDismiss()
                        shareText(context, data.url)
                    }
                }

                is ContextMenuData.Image -> {
                    // Header card matching Chrome screenshot 2 with actual thumbnail
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = data.imageUrl,
                                    contentDescription = "Image Preview",
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = data.title ?: "Web Image",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = data.imageUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ContextMenuItem(
                        icon = Icons.Default.OpenInNew,
                        label = "Open image in new tab",
                        testTag = "ctx_open_image_new_tab"
                    ) {
                        onDismiss()
                        viewModel.openNewTab(url = data.imageUrl, isIncognito = false)
                    }

                    ContextMenuItem(
                        icon = Icons.Default.ContentCopy,
                        label = "Copy image link",
                        testTag = "ctx_copy_image_link"
                    ) {
                        onDismiss()
                        copyToClipboard(context, "Image URL", data.imageUrl)
                    }

                    ContextMenuItem(
                        icon = Icons.Default.Download,
                        label = "Download image",
                        testTag = "ctx_download_image"
                    ) {
                        onDismiss()
                        DownloadHandler.startDownload(
                            context = context,
                            url = data.imageUrl,
                            userAgent = null,
                            contentDisposition = null,
                            mimeType = "image/*",
                            contentLength = 0,
                            onDownloadStarted = { entity ->
                                viewModel.onDownloadStarted(entity)
                            }
                        )
                    }

                    // Search image with Google Lens
                    ContextMenuItem(
                        icon = Icons.Default.Search,
                        label = "Search image with Google Lens",
                        badge = "New",
                        testTag = "ctx_search_lens"
                    ) {
                        onDismiss()
                        val encodedUrl = Uri.encode(data.imageUrl)
                        val lensUrl = "https://lens.google.com/uploadbyurl?url=$encodedUrl"
                        viewModel.openNewTab(url = lensUrl, isIncognito = false)
                    }

                    ContextMenuItem(
                        icon = Icons.Default.Share,
                        label = "Share image",
                        testTag = "ctx_share_image"
                    ) {
                        onDismiss()
                        shareText(context, data.imageUrl)
                    }
                }

                is ContextMenuData.ImageLink -> {
                    // Hybrid Linked Image Header
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = data.imageUrl,
                                    contentDescription = "Linked Image",
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = data.title ?: "Linked Image",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = data.linkUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ContextMenuItem(
                        icon = Icons.Default.OpenInNew,
                        label = "Open link in new tab",
                        testTag = "ctx_open_link_new_tab"
                    ) {
                        onDismiss()
                        viewModel.openNewTab(url = data.linkUrl, isIncognito = false)
                    }

                    ContextMenuItem(
                        icon = Icons.Default.Visibility,
                        label = "Preview page",
                        badge = "Preview",
                        testTag = "ctx_preview_linked_page"
                    ) {
                        onDismiss()
                        viewModel.openPreview(url = data.linkUrl, title = data.title)
                    }

                    ContextMenuItem(
                        icon = Icons.Default.Image,
                        label = "Open image in new tab",
                        testTag = "ctx_open_image_tab"
                    ) {
                        onDismiss()
                        viewModel.openNewTab(url = data.imageUrl, isIncognito = false)
                    }

                    ContextMenuItem(
                        icon = Icons.Default.Download,
                        label = "Download image",
                        testTag = "ctx_download_linked_image"
                    ) {
                        onDismiss()
                        DownloadHandler.startDownload(
                            context = context,
                            url = data.imageUrl,
                            userAgent = null,
                            contentDisposition = null,
                            mimeType = "image/*",
                            contentLength = 0,
                            onDownloadStarted = { entity ->
                                viewModel.onDownloadStarted(entity)
                            }
                        )
                    }

                    ContextMenuItem(
                        icon = Icons.Default.Search,
                        label = "Search image with Google Lens",
                        badge = "New",
                        testTag = "ctx_search_lens_hybrid"
                    ) {
                        onDismiss()
                        val encodedUrl = Uri.encode(data.imageUrl)
                        val lensUrl = "https://lens.google.com/uploadbyurl?url=$encodedUrl"
                        viewModel.openNewTab(url = lensUrl, isIncognito = false)
                    }

                    ContextMenuItem(
                        icon = Icons.Default.Link,
                        label = "Copy link address",
                        testTag = "ctx_copy_link_address"
                    ) {
                        onDismiss()
                        copyToClipboard(context, "Link Address", data.linkUrl)
                    }

                    ContextMenuItem(
                        icon = Icons.Default.Share,
                        label = "Share link",
                        testTag = "ctx_share_link"
                    ) {
                        onDismiss()
                        shareText(context, data.linkUrl)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    label: String,
    badge: String? = null,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (badge != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share via")
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share link", Toast.LENGTH_SHORT).show()
    }
}
