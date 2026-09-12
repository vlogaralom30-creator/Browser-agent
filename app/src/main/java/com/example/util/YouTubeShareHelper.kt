package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object YouTubeShareHelper {

    fun formatShortsTitle(originalTitle: String): String {
        val baseTitle = originalTitle.trim()
        val tags = listOf("#Shorts", "#viral", "#trending", "#reels")
        val missingTags = tags.filter { !baseTitle.contains(it, ignoreCase = true) }
        return if (missingTags.isNotEmpty()) {
            "$baseTitle " + missingTags.joinToString(" ")
        } else {
            baseTitle
        }
    }

    fun copyCaptionToClipboard(context: Context, text: String, notify: Boolean = true) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("YouTube Shorts Caption", text)
        clipboard.setPrimaryClip(clip)
        if (notify) {
            Toast.makeText(context, "Shorts ক্যাপশন কপি হয়েছে! (#Shorts যুক্ত)", Toast.LENGTH_SHORT).show()
        }
    }

    fun uploadToYouTubeApp(context: Context, filePath: String, caption: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists() || file.length() == 0L) {
                Toast.makeText(context, "ভিডিও ফাইল খুঁজে পাওয়া যায়নি!", Toast.LENGTH_SHORT).show()
                return false
            }

            // Always copy title and tags to clipboard first
            copyCaptionToClipboard(context, caption, notify = false)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, caption)
                putExtra(Intent.EXTRA_SUBJECT, caption)
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            Toast.makeText(
                context,
                "ইউটিউব ওপেন হচ্ছে... ক্যাপশন ক্লিপবোর্ডে কপি করা হয়েছে!",
                Toast.LENGTH_LONG
            ).show()
            true
        } catch (e: Exception) {
            // Fallback: general chooser
            try {
                val file = File(filePath)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val chooserIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(chooserIntent, "Share as YouTube Short"))
                true
            } catch (err: Exception) {
                Toast.makeText(context, "ইউটিউব অ্যাপ চালু করা যায়নি: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                false
            }
        }
    }

    fun openYouTubeStudio(onOpenUrl: (String) -> Unit) {
        onOpenUrl("https://studio.youtube.com")
    }

    fun openYouTubeLogin(onOpenUrl: (String) -> Unit) {
        onOpenUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https://m.youtube.com")
    }
}
