package com.example.model

import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "",
    val title: String = "New Tab",
    val displayUrl: String = "",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isIncognito: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isBookmarked: Boolean = false,
    val errorDescription: String? = null,
    val errorCode: Int = 0,
    val isSslError: Boolean = false,
    val failedUrl: String? = null,
    val errorCategory: PageErrorCategory? = null,
    val videoPlaybackSeconds: Float? = null,
    val renderRevision: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isNewTab: Boolean
        get() = url.isBlank() || url == "about:blank" || url == "browser://newtab"
}
