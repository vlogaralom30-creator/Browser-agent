package com.example.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import com.example.data.DownloadEntity
import com.example.data.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DownloadHandler {
    fun startDownload(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        onDownloadStarted: (DownloadEntity) -> Unit
    ) {
        try {
            var fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            if (fileName.isNullOrBlank()) {
                fileName = "download_${System.currentTimeMillis()}"
            }

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                if (!userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                setDescription("Downloading $fileName")
                setTitle(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                if (!mimeType.isNullOrBlank()) {
                    setMimeType(mimeType)
                }
            }

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (manager != null) {
                manager.enqueue(request)
                Toast.makeText(context, "Downloading $fileName...", Toast.LENGTH_SHORT).show()

                val entity = DownloadEntity(
                    fileName = fileName,
                    url = url,
                    filePath = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/$fileName",
                    fileSize = if (contentLength > 0) contentLength else 0,
                    mimeType = mimeType ?: "",
                    status = DownloadStatus.DOWNLOADING,
                    progress = 0,
                    timestamp = System.currentTimeMillis()
                )
                onDownloadStarted(entity)
            } else {
                Toast.makeText(context, "Download manager not available", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
        }
    }
}
