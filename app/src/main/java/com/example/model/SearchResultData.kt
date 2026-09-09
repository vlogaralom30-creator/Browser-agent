package com.example.model

data class SearchResultItem(
    val title: String,
    val url: String,
    val snippet: String,
    val displayHost: String,
    val faviconUrl: String? = null
)

data class InstantAnswer(
    val title: String,
    val description: String?,
    val extract: String,
    val sourceName: String = "Instant Knowledge",
    val sourceUrl: String? = null,
    val imageUrl: String? = null
)

data class SearchResultData(
    val query: String,
    val instantAnswer: InstantAnswer? = null,
    val aiSummary: String? = null,
    val webResults: List<SearchResultItem> = emptyList(),
    val relatedQueries: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
