package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ReelShareHelper {

    const val FB_PROFILE_REELS_URL = "https://m.facebook.com/reel/create"
    const val FB_PAGE_REELS_URL = "https://business.facebook.com/latest/reels_composer"

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun copyCaptionToClipboard(context: Context, caption: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Reel Caption", caption)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Caption & hashtags copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to copy: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun playVideo(context: Context, filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(context, "Video file not found on device", Toast.LENGTH_SHORT).show()
                return
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open video player: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareReel(context: Context, filePath: String, caption: String) {
        try {
            val file = File(filePath)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                if (file.exists() && file.length() > 0) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                }
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Share Reel (Facebook / Instagram / Social)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Share error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Upload to Facebook Profile:
     * Attempts to open Facebook app directly with video attached and caption copied.
     * If app is not present or user selects web, opens Facebook Reels Creator in browser.
     */
    fun uploadToFacebookProfile(
        context: Context,
        filePath: String,
        caption: String,
        forceWeb: Boolean = false,
        onOpenWebFallback: (String) -> Unit
    ) {
        copyCaptionToClipboard(context, caption)

        val file = File(filePath)
        val isKatanaInstalled = isAppInstalled(context, "com.facebook.katana")
        val isLiteInstalled = isAppInstalled(context, "com.facebook.lite")

        if (!forceWeb && (isKatanaInstalled || isLiteInstalled) && file.exists()) {
            try {
                val targetPackage = if (isKatanaInstalled) "com.facebook.katana" else "com.facebook.lite"
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    setPackage(targetPackage)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(
                    context,
                    "Facebook app launched! Caption copied — paste in your Reel description 📋",
                    Toast.LENGTH_LONG
                ).show()
                return
            } catch (e: Exception) {
                // Fallback to web
            }
        }

        // Open in browser
        onOpenWebFallback(FB_PROFILE_REELS_URL)
        Toast.makeText(
            context,
            "Opened Facebook Reels in browser! Caption copied to clipboard. Tap upload to select video 🚀",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Upload to Facebook Page (Meta Business Suite):
     * Attempts to open Meta Business Suite app if installed.
     * Otherwise navigates to Meta Business Suite Web Reels composer in Naxxivo Browser.
     */
    fun uploadToFacebookPage(
        context: Context,
        filePath: String,
        caption: String,
        forceWeb: Boolean = false,
        onOpenWebFallback: (String) -> Unit
    ) {
        copyCaptionToClipboard(context, caption)

        val file = File(filePath)
        val isBusinessSuiteInstalled = isAppInstalled(context, "com.facebook.pages.app") ||
                isAppInstalled(context, "com.facebook.creatorstudio")

        if (!forceWeb && isBusinessSuiteInstalled && file.exists()) {
            try {
                val targetPackage = if (isAppInstalled(context, "com.facebook.pages.app")) {
                    "com.facebook.pages.app"
                } else {
                    "com.facebook.creatorstudio"
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    setPackage(targetPackage)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(
                    context,
                    "Meta Business Suite launched for your Page! Caption copied 📋",
                    Toast.LENGTH_LONG
                ).show()
                return
            } catch (e: Exception) {
                // Fallback to web
            }
        }

        // Open in browser
        onOpenWebFallback(FB_PAGE_REELS_URL)
        Toast.makeText(
            context,
            "Opened Meta Business Suite Reels composer! Caption copied. Tap Add Video to select file 🚀",
            Toast.LENGTH_LONG
        ).show()
    }
}
