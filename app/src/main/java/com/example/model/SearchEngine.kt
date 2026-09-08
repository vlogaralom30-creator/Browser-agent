package com.example.model

enum class SearchEngine(
    val displayName: String,
    val searchUrlPrefix: String,
    val homeUrl: String
) {
    GOOGLE(
        displayName = "Google",
        searchUrlPrefix = "https://www.google.com/search?q=",
        homeUrl = "https://www.google.com"
    ),
    DUCKDUCKGO(
        displayName = "DuckDuckGo",
        searchUrlPrefix = "https://duckduckgo.com/?q=",
        homeUrl = "https://duckduckgo.com"
    ),
    BING(
        displayName = "Bing",
        searchUrlPrefix = "https://www.bing.com/search?q=",
        homeUrl = "https://www.bing.com"
    ),
    ECOSIA(
        displayName = "Ecosia",
        searchUrlPrefix = "https://www.ecosia.org/search?q=",
        homeUrl = "https://www.ecosia.org"
    );

    companion object {
        fun fromName(name: String): SearchEngine {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: GOOGLE
        }
    }
}
