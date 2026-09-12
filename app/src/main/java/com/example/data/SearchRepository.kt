package com.example.data

import android.net.Uri
import android.util.Log
import com.example.model.InstantAnswer
import com.example.model.SearchResultData
import com.example.model.SearchResultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

class SearchRepository {
    suspend fun performSearch(query: String): SearchResultData = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@withContext SearchResultData(query = "")
        }

        var instantAnswer: InstantAnswer? = null
        var webResults: List<SearchResultItem> = emptyList()
        var relatedQueries: List<String> = emptyList()
        var errorMsg: String? = null

        try {
            // 1. Fetch Web Results from DuckDuckGo HTML
            webResults = fetchDuckDuckGoResults(trimmed)
        } catch (e: Exception) {
            Log.e("SearchRepository", "Error fetching web results", e)
            errorMsg = e.localizedMessage ?: "Failed to load web search results"
        }

        try {
            // 2. Fetch Knowledge Panel / Instant Answer from Wikipedia Summary API
            instantAnswer = fetchWikipediaSummary(trimmed)
        } catch (e: Exception) {
            Log.w("SearchRepository", "Failed to fetch instant answer", e)
        }

        try {
            // 3. Fetch Related Queries
            relatedQueries = fetchRelatedQueries(trimmed)
        } catch (e: Exception) {
            Log.w("SearchRepository", "Failed to fetch related queries", e)
        }

        SearchResultData(
            query = trimmed,
            instantAnswer = instantAnswer,
            aiSummary = null,
            webResults = webResults,
            relatedQueries = relatedQueries,
            isLoading = false,
            errorMessage = if (webResults.isEmpty() && instantAnswer == null) errorMsg ?: "No search results found for '$trimmed'" else null
        )
    }

    private fun fetchDuckDuckGoResults(query: String): List<SearchResultItem> {
        val url = URL("https://html.duckduckgo.com/html/")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 6000
        connection.readTimeout = 6000
        connection.doOutput = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:110.0) Gecko/110.0 Firefox/110.0")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

        val postData = "q=" + URLEncoder.encode(query, "UTF-8")
        OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
            writer.write(postData)
            writer.flush()
        }

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            return emptyList()
        }

        val html = connection.inputStream.bufferedReader().use(BufferedReader::readText)
        return parseDuckDuckGoHtml(html)
    }

    private fun parseDuckDuckGoHtml(html: String): List<SearchResultItem> {
        val results = mutableListOf<SearchResultItem>()
        // Pattern matches: <h2 ...><a href="...">Title</a></h2> ... <a class="result__snippet"...>Snippet</a>
        val pattern = Pattern.compile(
            "<h2[^>]*>\\s*<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>\\s*</h2>.*?<a class=\"result__snippet\"[^>]*>(.*?)</a>",
            Pattern.DOTALL
        )
        val matcher = pattern.matcher(html)

        while (matcher.find() && results.size < 15) {
            val rawUrl = matcher.group(1) ?: continue
            val rawTitle = matcher.group(2) ?: ""
            val rawSnippet = matcher.group(3) ?: ""

            var realUrl = rawUrl
            if (rawUrl.contains("uddg=")) {
                val uri = Uri.parse("https://duckduckgo.com$rawUrl")
                uri.getQueryParameter("uddg")?.let { realUrl = it }
            }

            val title = cleanHtmlText(rawTitle)
            val snippet = cleanHtmlText(rawSnippet)
            val displayHost = extractHost(realUrl)

            if (title.isNotBlank() && realUrl.startsWith("http")) {
                val faviconUrl = "https://www.google.com/s2/favicons?domain=$displayHost&sz=64"
                results.add(
                    SearchResultItem(
                        title = title,
                        url = realUrl,
                        snippet = snippet,
                        displayHost = displayHost,
                        faviconUrl = faviconUrl
                    )
                )
            }
        }
        return results
    }

    private fun fetchWikipediaSummary(query: String): InstantAnswer? {
        val firstTopic = query.split(" ").take(3).joinToString("_")
        val url = URL("https://en.wikipedia.org/api/rest_v1/page/summary/" + URLEncoder.encode(firstTopic, "UTF-8"))
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 4000
        connection.readTimeout = 4000
        connection.setRequestProperty("User-Agent", "NaxxivoBrowser/1.0 (Android; Mobile)")

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            return null
        }

        val jsonText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
        val json = JSONObject(jsonText)

        if (json.optString("type") == "disambiguation") {
            return null
        }

        val title = json.optString("title")
        val description = json.optString("description").takeIf { it.isNotBlank() }
        val extract = json.optString("extract")
        val thumbnail = json.optJSONObject("thumbnail")?.optString("source")
        val pageUrl = json.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page")

        if (title.isBlank() || extract.isBlank()) return null

        return InstantAnswer(
            title = title,
            description = description,
            extract = extract,
            sourceName = "Wikipedia",
            sourceUrl = pageUrl,
            imageUrl = thumbnail
        )
    }

    private fun fetchRelatedQueries(query: String): List<String> {
        val url = URL("https://suggestqueries.google.com/complete/search?client=chrome&q=" + URLEncoder.encode(query, "UTF-8"))
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            return emptyList()
        }

        val jsonText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
        val jsonArray = JSONArray(jsonText)
        val suggestionsJson = jsonArray.optJSONArray(1) ?: return emptyList()

        val list = mutableListOf<String>()
        for (i in 0 until suggestionsJson.length()) {
            val suggestion = suggestionsJson.optString(i)
            if (suggestion.isNotBlank() && !suggestion.equals(query, ignoreCase = true)) {
                list.add(suggestion)
                if (list.size >= 6) break
            }
        }
        return list
    }

    private fun cleanHtmlText(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractHost(urlStr: String): String {
        return try {
            val uri = Uri.parse(urlStr)
            val host = uri.host ?: urlStr
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            urlStr
        }
    }
}
