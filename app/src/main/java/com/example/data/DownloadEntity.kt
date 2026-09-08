package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val url: String,
    val filePath: String,
    val fileSize: Long = 0,
    val mimeType: String = "",
    val status: DownloadStatus = DownloadStatus.DOWNLOADING,
    val progress: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
