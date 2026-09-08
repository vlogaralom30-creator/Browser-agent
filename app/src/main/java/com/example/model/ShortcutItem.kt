package com.example.model

data class ShortcutItem(
    val id: String,
    val title: String,
    val url: String,
    val initial: String,
    val isCustom: Boolean = false
) {
    companion object {
        val defaultShortcuts = listOf(
            ShortcutItem("google", "Google", "https://www.google.com", "G"),
            ShortcutItem("youtube", "YouTube", "https://www.youtube.com", "Y"),
            ShortcutItem("wikipedia", "Wikipedia", "https://www.wikipedia.org", "W"),
            ShortcutItem("github", "GitHub", "https://github.com", "G"),
            ShortcutItem("reddit", "Reddit", "https://www.reddit.com", "R"),
            ShortcutItem("news", "BBC News", "https://www.bbc.com/news", "B"),
            ShortcutItem("x", "X / Twitter", "https://x.com", "X"),
            ShortcutItem("amazon", "Amazon", "https://www.amazon.com", "A")
        )
    }
}
