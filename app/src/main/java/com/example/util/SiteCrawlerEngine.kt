package com.example.util

import android.util.Log
import android.webkit.WebView
import com.example.model.CrawlBotState
import com.example.model.CrawlBotStatus
import com.example.model.CrawlMatchItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI

class SiteCrawlerEngine {

    private val _botState = MutableStateFlow(CrawlBotState())
    val botState: StateFlow<CrawlBotState> = _botState.asStateFlow()

    private var crawlJob: Job? = null

    fun startCrawl(
        keyword: String,
        startUrl: String,
        maxPages: Int = 10,
        scope: CoroutineScope,
        loadUrlAction: (String) -> Unit,
        getWebViewProvider: () -> WebView?
    ) {
        val trimmedKeyword = keyword.trim()
        if (trimmedKeyword.isEmpty() || startUrl.isBlank()) return

        stopCrawl()

        val baseDomain = extractDomain(startUrl)

        _botState.value = CrawlBotState(
            status = CrawlBotStatus.RUNNING,
            targetKeyword = trimmedKeyword,
            startUrl = startUrl,
            maxPages = maxPages,
            currentUrl = startUrl,
            currentAction = "Initializing Bot & Navigating to Start Page...",
            visitedUrls = emptyList(),
            queueUrls = listOf(startUrl),
            results = emptyList()
        )

        crawlJob = scope.launch(Dispatchers.Main) {
            runCrawlerLoop(trimmedKeyword, baseDomain, maxPages, loadUrlAction, getWebViewProvider)
        }
    }

    private suspend fun runCrawlerLoop(
        keyword: String,
        baseDomain: String,
        maxPages: Int,
        loadUrlAction: (String) -> Unit,
        getWebViewProvider: () -> WebView?
    ) {
        try {
            while (_botState.value.status == CrawlBotStatus.RUNNING) {
                val state = _botState.value
                val nextUrl = state.queueUrls.firstOrNull { it !in state.visitedUrls }

                if (nextUrl == null || state.totalPagesCrawled >= maxPages) {
                    _botState.update {
                        it.copy(
                            status = CrawlBotStatus.COMPLETED,
                            currentAction = "Crawl finished! Crawled ${it.totalPagesCrawled} pages, found ${it.totalMatchesFound} matching pages."
                        )
                    }
                    break
                }

                // Mark URL as visited and set as current URL
                _botState.update {
                    it.copy(
                        currentUrl = nextUrl,
                        visitedUrls = it.visitedUrls + nextUrl,
                        queueUrls = it.queueUrls - nextUrl,
                        currentAction = "Loading page (${it.totalPagesCrawled + 1}/$maxPages): $nextUrl..."
                    )
                }

                // Load URL if needed
                withContext(Dispatchers.Main) {
                    loadUrlAction(nextUrl)
                }

                // Wait for page load and initial render
                delay(3000)

                if (_botState.value.status != CrawlBotStatus.RUNNING) break

                // Perform visual scroll, keyword highlight & DOM link extraction via JS
                _botState.update { it.copy(currentAction = "Scanning & Highlighting '$keyword'...") }

                val webView = getWebViewProvider()
                if (webView != null) {
                    val jsScript = buildHighlightAndExtractScript(keyword)
                    var jsResultJson: String? = null

                    // Evaluate JS on Main thread
                    withContext(Dispatchers.Main) {
                        webView.evaluateJavascript(jsScript) { res ->
                            jsResultJson = res
                        }
                    }

                    // Wait for JS evaluation to return
                    delay(1500)

                    if (!jsResultJson.isNullOrBlank() && jsResultJson != "null") {
                        parseAndApplyScanResults(jsResultJson!!, baseDomain)
                    }
                } else {
                    Log.w("SiteCrawlerEngine", "WebView was null during crawling step")
                }

                // Give a short visual pause so user sees scrolling/highlighting in action
                delay(2000)
            }
        } catch (e: Exception) {
            Log.e("SiteCrawlerEngine", "Crawler error encountered", e)
            _botState.update {
                it.copy(
                    status = CrawlBotStatus.ERROR,
                    errorMessage = e.localizedMessage,
                    currentAction = "Crawl paused due to error: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    private fun parseAndApplyScanResults(jsonRaw: String, baseDomain: String) {
        try {
            // Clean unescaped quotes if returned as raw JS string
            val cleanJson = if (jsonRaw.startsWith("\"") && jsonRaw.endsWith("\"")) {
                org.json.JSONTokener(jsonRaw).nextValue().toString()
            } else {
                jsonRaw
            }

            val json = JSONObject(cleanJson)
            val title = json.optString("title", _botState.value.currentUrl)
            val url = json.optString("url", _botState.value.currentUrl)
            val matchCount = json.optInt("matchCount", 0)

            val snippetsArr = json.optJSONArray("snippets")
            val snippets = mutableListOf<String>()
            if (snippetsArr != null) {
                for (i in 0 until snippetsArr.length()) {
                    snippets.add(snippetsArr.optString(i))
                }
            }

            val linksArr = json.optJSONArray("links")
            val newLinks = mutableListOf<String>()
            if (linksArr != null) {
                for (i in 0 until linksArr.length()) {
                    val link = linksArr.optString(i)
                    if (link.isNotBlank() && extractDomain(link) == baseDomain) {
                        newLinks.add(link)
                    }
                }
            }

            _botState.update { state ->
                val currentVisited = state.visitedUrls.toSet()
                val currentQueue = state.queueUrls.toMutableList()

                newLinks.forEach { l ->
                    if (l !in currentVisited && l !in currentQueue && l != state.startUrl) {
                        currentQueue.add(l)
                    }
                }

                val updatedResults = state.results.toMutableList()
                var updatedMatchesCount = state.totalMatchesFound

                if (matchCount > 0) {
                    val existingIdx = updatedResults.indexOfFirst { it.pageUrl == url }
                    val item = CrawlMatchItem(
                        pageTitle = title,
                        pageUrl = url,
                        matchCount = matchCount,
                        snippets = snippets
                    )
                    if (existingIdx >= 0) {
                        updatedResults[existingIdx] = item
                    } else {
                        updatedResults.add(item)
                        updatedMatchesCount++
                    }
                }

                state.copy(
                    currentTitle = title,
                    totalPagesCrawled = state.totalPagesCrawled + 1,
                    totalMatchesFound = updatedMatchesCount,
                    queueUrls = currentQueue,
                    results = updatedResults,
                    currentAction = if (matchCount > 0) {
                        "Found $matchCount matches on '$title'!"
                    } else {
                        "No matches on '$title'. Queue size: ${currentQueue.size}"
                    }
                )
            }

        } catch (e: Exception) {
            Log.e("SiteCrawlerEngine", "Failed to parse JS scan output", e)
        }
    }

    fun pauseCrawl() {
        if (_botState.value.status == CrawlBotStatus.RUNNING) {
            _botState.update { it.copy(status = CrawlBotStatus.PAUSED, currentAction = "Crawler Bot paused by user.") }
        }
    }

    fun resumeCrawl(
        scope: CoroutineScope,
        loadUrlAction: (String) -> Unit,
        getWebViewProvider: () -> WebView?
    ) {
        if (_botState.value.status == CrawlBotStatus.PAUSED) {
            val state = _botState.value
            _botState.update { it.copy(status = CrawlBotStatus.RUNNING, currentAction = "Resuming Crawler Bot...") }
            val baseDomain = extractDomain(state.startUrl)
            crawlJob = scope.launch(Dispatchers.Main) {
                runCrawlerLoop(state.targetKeyword, baseDomain, state.maxPages, loadUrlAction, getWebViewProvider)
            }
        }
    }

    fun stopCrawl() {
        crawlJob?.cancel()
        crawlJob = null
        if (_botState.value.status == CrawlBotStatus.RUNNING || _botState.value.status == CrawlBotStatus.PAUSED) {
            _botState.update { it.copy(status = CrawlBotStatus.STOPPED, currentAction = "Bot stopped.") }
        }
    }

    fun resetBot() {
        stopCrawl()
        _botState.value = CrawlBotState()
    }

    private fun extractDomain(urlStr: String): String {
        return try {
            val uri = URI(urlStr)
            val host = uri.host ?: urlStr
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            urlStr
        }
    }

    private fun buildHighlightAndExtractScript(keyword: String): String {
        val escapedKeyword = JSONObject.quote(keyword)
        return """
            (function() {
                var keyword = $escapedKeyword;
                if (!keyword) return JSON.stringify({matchCount: 0, snippets: [], links: []});

                var count = 0;
                var snippets = [];
                var escapedKw = keyword.replace(/[.*+?^${'$'}()|[\]\\]/g, '\\${'$'}&');
                var regex = new RegExp('(' + escapedKw + ')', 'gi');

                // Remove previous bot highlights
                var oldMarks = document.querySelectorAll('mark.naxxivo-bot-highlight');
                for (var k = 0; k < oldMarks.length; k++) {
                    var m = oldMarks[k];
                    var parent = m.parentNode;
                    if (parent) {
                        parent.replaceChild(document.createTextNode(m.textContent), m);
                        parent.normalize();
                    }
                }

                function highlightNode(node) {
                    if (node.nodeType === 3) { // Text Node
                        var text = node.nodeValue;
                        var match = regex.exec(text);
                        if (match) {
                            var mark = document.createElement('mark');
                            mark.className = 'naxxivo-bot-highlight';
                            mark.style.backgroundColor = '#FFEB3B';
                            mark.style.color = '#000000';
                            mark.style.padding = '2px 4px';
                            mark.style.borderRadius = '4px';
                            mark.style.boxShadow = '0 0 10px #FFD54F';
                            mark.style.fontWeight = 'bold';

                            var split = node.splitText(match.index);
                            split.nodeValue = split.nodeValue.substring(match[0].length);
                            mark.appendChild(document.createTextNode(match[0]));
                            node.parentNode.insertBefore(mark, split);

                            count++;

                            var parentText = node.parentNode ? (node.parentNode.innerText || node.parentNode.textContent) : text;
                            if (parentText && snippets.length < 5) {
                                var cleanText = parentText.replace(/\s+/g, ' ').trim();
                                if (cleanText.length > 100) {
                                    var idx = cleanText.toLowerCase().indexOf(keyword.toLowerCase());
                                    var start = Math.max(0, idx - 30);
                                    var end = Math.min(cleanText.length, idx + keyword.length + 30);
                                    cleanText = (start > 0 ? '...' : '') + cleanText.substring(start, end) + (end < cleanText.length ? '...' : '');
                                }
                                if (cleanText && snippets.indexOf(cleanText) === -1) {
                                    snippets.push(cleanText);
                                }
                            }
                            return 1;
                        }
                    } else if (node.nodeType === 1 && node.childNodes && !/(script|style|textarea|input|mark)/i.test(node.tagName)) {
                        for (var i = 0; i < node.childNodes.length; i++) {
                            i += highlightNode(node.childNodes[i]);
                        }
                    }
                    return 0;
                }

                highlightNode(document.body);

                // Scroll highlighted item or scroll page down visually
                var firstMark = document.querySelector('mark.naxxivo-bot-highlight');
                if (firstMark) {
                    firstMark.scrollIntoView({behavior: 'smooth', block: 'center'});
                } else {
                    window.scrollBy({top: Math.min(window.innerHeight * 0.7, 500), behavior: 'smooth'});
                }

                // Gather same-domain internal links
                var currentOrigin = window.location.origin;
                var linksSet = [];
                var rawLinks = document.querySelectorAll('a[href]');
                for (var j = 0; j < rawLinks.length; j++) {
                    var href = rawLinks[j].href;
                    if (href && href.startsWith(currentOrigin) && !href.includes('#') && !href.startsWith('javascript:')) {
                        if (linksSet.indexOf(href) === -1 && href !== window.location.href) {
                            linksSet.push(href);
                        }
                    }
                }

                return JSON.stringify({
                    title: document.title || window.location.href,
                    url: window.location.href,
                    matchCount: count,
                    snippets: snippets,
                    links: linksSet
                });
            })();
        """.trimIndent()
    }
}
