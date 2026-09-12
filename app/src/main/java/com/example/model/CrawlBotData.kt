package com.example.model

enum class CrawlBotStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    STOPPED,
    ERROR
}

data class CrawlMatchItem(
    val pageTitle: String,
    val pageUrl: String,
    val matchCount: Int,
    val snippets: List<String>
)

data class YoutubeInteractionItem(
    val query: String,
    val videoTitle: String,
    val videoUrl: String,
    val viewCountText: String,
    val timestamp: Long,
    val isLiked: Boolean,
    val isCommented: Boolean,
    val commentText: String
)

data class CrawlBotState(
    val status: CrawlBotStatus = CrawlBotStatus.IDLE,
    val botMode: String = "youtube", // "youtube" or "crawler"
    
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

    // YouTube Automation Bot fields
    val ytSearchQuery: String = "",
    val ytSelectionCriteria: String = "highest_views", // "highest_views" or "newest"
    val ytPerformLike: Boolean = false,
    val ytPerformComment: Boolean = false,
    val ytCommentText: String = "",
    val ytSessionLogs: List<String> = emptyList(),
    val ytHistory: List<YoutubeInteractionItem> = emptyList(),
    val ytActiveVideoTitle: String = "",
    val ytActiveVideoUrl: String = "",
    val ytActiveVideoViews: String = "",
    val ytActiveVideoDate: String = ""
)
