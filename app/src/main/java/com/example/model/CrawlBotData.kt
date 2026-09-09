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

data class CrawlBotState(
    val status: CrawlBotStatus = CrawlBotStatus.IDLE,
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
    val errorMessage: String? = null
)
