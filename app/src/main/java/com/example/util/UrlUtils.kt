package com.example.util

import android.net.Uri
import android.util.Patterns
import com.example.model.SearchEngine
import java.util.regex.Pattern

object UrlUtils {
    private val DOMAIN_PATTERN = Pattern.compile(
        "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}(:\\d{1,5})?(/.*)?$"
    )
    private val IP_PATTERN = Pattern.compile(
        "^(\\d{1,3}\\.){3}\\d{1,3}(:\\d{1,5})?(/.*)?$"
    )
    private val LOCALHOST_PATTERN = Pattern.compile(
        "^(localhost|127\\.0\\.0\\.1)(:\\d{1,5})?(/.*)?$"
    )

    fun resolveInputToUrl(input: String, searchEngine: SearchEngine): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true) ||
            trimmed.startsWith("javascript:", ignoreCase = true)
        ) {
            return trimmed
        }

        // Check if it looks like a URL/domain
        if (!trimmed.contains(" ") && (
                DOMAIN_PATTERN.matcher(trimmed).matches() ||
                IP_PATTERN.matcher(trimmed).matches() ||
                LOCALHOST_PATTERN.matcher(trimmed).matches() ||
                Patterns.WEB_URL.matcher(trimmed).matches()
            )
        ) {
            return "https://$trimmed"
        }

        // Otherwise perform search
        val encodedQuery = Uri.encode(trimmed)
        return searchEngine.searchUrlPrefix + encodedQuery
    }

    fun getDisplayHost(url: String): String {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("browser:")) {
            return "Search or type URL"
        }
        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: url
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            url
        }
    }

    fun isSecure(url: String): Boolean {
        return url.startsWith("https://", ignoreCase = true)
    }
}
