package com.example.model

enum class CrawlBotStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    STOPPED,
    ERROR,
    WAITING_CONFIRMATION
}

data class CrawlMatchItem(
    val pageTitle: String,
    val pageUrl: String,
    val matchCount: Int,
    val snippets: List<String>
)

data class VideoInteractionItem(
    val query: String,
    val selectedVideoTitle: String,
    val channel: String,
    val viewsText: String,
    val uploadDateText: String,
    val videoUrl: String,
    val timestamp: Long,
    val actionSearched: Boolean,
    val actionOpened: Boolean,
    val actionLiked: Boolean,
    val actionCopied: Boolean,
    val actionCommented: Boolean,
    val actionResult: String
)

data class TikTokReelItem(
    val id: String = System.currentTimeMillis().toString(),
    val originalTitle: String = "",
    val spunTitle: String = "",
    val author: String = "",
    val viewsText: String = "",
    val viewsCount: Long = 0L,
    val likesText: String = "",
    val sourceVideoUrl: String = "",
    val downloadUrl: String = "",
    val localFilePath: String = "",
    val fileName: String = "",
    val customTags: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isDownloaded: Boolean = true,
    val readyForFacebook: Boolean = true
)

data class CrawlBotState(
    val status: CrawlBotStatus = CrawlBotStatus.IDLE,
    val botMode: String = "tiktok", // "tiktok", "video", or "crawler"
    
    // Website Crawler fields
    val targetKeyword: String = "",
    val startUrl: String = "",
    val maxPages: Int = 10,
    val currentUrl: String = "",
    val currentTitle: String = "",
    val currentAction: String = "",
    val totalPagesCrawled: Int = 0,
    val totalMatchesFound: Int = 0,
    val visitedUrls: List<String> = emptyList(),
    val queueUrls: List<String> = emptyList(),
    val results: List<CrawlMatchItem> = emptyList(),
    val errorMessage: String? = null,

    // Video Automation Bot fields (YouTube)
    val videoSearchQuery: String = "",
    val videoSelectionCriteria: String = "best_match", // "best_match", "newest", "highest_views", "oldest"
    val videoLimit: Int = 10,
    val videoPerformLike: Boolean = false,
    val videoPerformComment: Boolean = false,
    val videoPerformCopyLink: Boolean = false,
    val videoCommentText: String = "",
    val videoSessionLogs: List<String> = emptyList(),
    val videoHistory: List<VideoInteractionItem> = emptyList(),
    val videoActiveTitle: String = "",
    val videoActiveUrl: String = "",
    val videoActiveViews: String = "",
    val videoActiveDate: String = "",
    val videoActiveChannel: String = "",
    val videoCurrentIndex: Int = 0,
    val videoTotalTarget: Int = 0,

    // TikTok Viral Bot fields (Reels Hunter & Downloader)
    val tiktokQuery: String = "",
    val tiktokMinViews: Long = 50000L,
    val tiktokSortCriteria: String = "highest_views", // "highest_views", "most_viral", "newest"
    val tiktokLimit: Int = 5,
    val tiktokCustomHashtags: String = "#viral #reels #foryou #trending #explore",
    val tiktokDownloadedReels: List<TikTokReelItem> = emptyList(),
    val tiktokCurrentIndex: Int = 0,
    val tiktokTotalTarget: Int = 0,
    val tiktokActiveTitle: String = "",
    val tiktokActiveViews: String = "",
    val tiktokActiveStatusStep: String = "",
    val tiktokLogs: List<String> = emptyList()
)
