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

    /**
     * Extracts canonical host: lowercase, trimmed, without scheme, www, port, or path.
     */
    fun extractCanonicalHost(urlOrInput: String): String {
        val trimmed = urlOrInput.trim().lowercase()
        if (trimmed.isEmpty() || trimmed.startsWith("about:") || trimmed.startsWith("javascript:")) {
            return ""
        }
        val withoutScheme = when {
            trimmed.startsWith("https://") -> trimmed.substring(8)
            trimmed.startsWith("http://") -> trimmed.substring(7)
            else -> trimmed
        }
        val pathIdx = withoutScheme.indexOfAny(charArrayOf('/', '?', '#'))
        val hostAndPort = if (pathIdx != -1) withoutScheme.substring(0, pathIdx) else withoutScheme
        val portIdx = hostAndPort.indexOf(':')
        var host = if (portIdx != -1) hostAndPort.substring(0, portIdx) else hostAndPort
        if (host.startsWith("www.")) {
            host = host.substring(4)
        }
        return host.trim()
    }

    /**
     * Checks whether the given URL/input matches any domain in the private domains set.
     * Matches exact host (e.g. example.com) or subdomains (e.g. m.example.com, video.example.com).
     */
    fun matchesPrivateDomain(urlOrHost: String, privateDomains: Set<String>): Boolean {
        if (privateDomains.isEmpty()) return false
        val host = extractCanonicalHost(urlOrHost)
        if (host.isBlank()) return false

        return privateDomains.any { configuredDomain ->
            val clean = extractCanonicalHost(configuredDomain)
            if (clean.isBlank()) false
            else host == clean || host.endsWith(".$clean")
        }
    }
}
