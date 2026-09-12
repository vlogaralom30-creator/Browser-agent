package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.example.model.CrawlBotState
import com.example.model.CrawlBotStatus
import com.example.model.CrawlMatchItem
import com.example.model.YoutubeInteractionItem
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

class SiteCrawlerEngine {

    private val _botState = MutableStateFlow(CrawlBotState())
    val botState: StateFlow<CrawlBotState> = _botState.asStateFlow()

    private var crawlJob: Job? = null

    // --- Website Crawler Bot (Original Logic Restored & Intact) ---

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
            botMode = "crawler",
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

                _botState.update {
                    it.copy(
                        currentUrl = nextUrl,
                        visitedUrls = it.visitedUrls + nextUrl,
                        queueUrls = it.queueUrls - nextUrl,
                        currentAction = "Loading page (${it.totalPagesCrawled + 1}/$maxPages): $nextUrl..."
                    )
                }

                withContext(Dispatchers.Main) {
                    loadUrlAction(nextUrl)
                }

                delay(3000)

                if (_botState.value.status != CrawlBotStatus.RUNNING) break

                _botState.update { it.copy(currentAction = "Scanning & Highlighting '$keyword'...") }

                val webView = getWebViewProvider()
                if (webView != null) {
                    val jsScript = buildHighlightAndExtractScript(keyword)
                    var jsResultJson: String? = null

                    withContext(Dispatchers.Main) {
                        webView.evaluateJavascript(jsScript) { res ->
                            jsResultJson = res
                        }
                    }

                    delay(1500)

                    if (!jsResultJson.isNullOrBlank() && jsResultJson != "null") {
                        parseAndApplyScanResults(jsResultJson!!, baseDomain)
                    }
                } else {
                    Log.w("SiteCrawlerEngine", "WebView was null during crawling step")
                }

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
            _botState.update { it.copy(status = CrawlBotStatus.RUNNING, currentAction = "Resuming Bot...") }
            if (state.botMode == "youtube") {
                startYtBot(
                    query = state.ytSearchQuery,
                    criteria = state.ytSelectionCriteria,
                    like = state.ytPerformLike,
                    comment = state.ytPerformComment,
                    commentText = state.ytCommentText,
                    scope = scope,
                    loadUrlAction = loadUrlAction,
                    getWebViewProvider = getWebViewProvider
                )
            } else {
                val baseDomain = extractDomain(state.startUrl)
                crawlJob = scope.launch(Dispatchers.Main) {
                    runCrawlerLoop(state.targetKeyword, baseDomain, state.maxPages, loadUrlAction, getWebViewProvider)
                }
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
                    if (node.nodeType === 3) {
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

                var firstMark = document.querySelector('mark.naxxivo-bot-highlight');
                if (firstMark) {
                    firstMark.scrollIntoView({behavior: 'smooth', block: 'center'});
                } else {
                    window.scrollBy({top: Math.min(window.innerHeight * 0.7, 500), behavior: 'smooth'});
                }

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


    // --- YouTube Browser Automation Bot (Specialized Implementation) ---

    fun startYtBot(
        query: String,
        criteria: String, // "highest_views" or "newest"
        like: Boolean,
        comment: Boolean,
        commentText: String,
        scope: CoroutineScope,
        loadUrlAction: (String) -> Unit,
        getWebViewProvider: () -> WebView?
    ) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return

        stopCrawl()

        // Fetch past context/history from SharedPreferences
        val webView = getWebViewProvider()
        val context = webView?.context
        val loadedHistory = if (context != null) loadYtHistory(context) else emptyList()

        _botState.value = CrawlBotState(
            status = CrawlBotStatus.RUNNING,
            botMode = "youtube",
            ytSearchQuery = trimmedQuery,
            ytSelectionCriteria = criteria,
            ytPerformLike = like,
            ytPerformComment = comment,
            ytCommentText = commentText,
            ytHistory = loadedHistory,
            ytSessionLogs = listOf("YouTube Automation Bot initialized successfully.", "Retrieved ${loadedHistory.size} previous interactions from local memory storage.")
        )

        crawlJob = scope.launch(Dispatchers.Main) {
            runYtAutomationFlow(trimmedQuery, criteria, like, comment, commentText, loadUrlAction, getWebViewProvider)
        }
    }

    private suspend fun runYtAutomationFlow(
        query: String,
        criteria: String,
        like: Boolean,
        comment: Boolean,
        commentText: String,
        loadUrlAction: (String) -> Unit,
        getWebViewProvider: () -> WebView?
    ) {
        val currentLogs = _botState.value.ytSessionLogs.toMutableList()

        fun log(msg: String) {
            Log.d("SiteCrawlerEngine", "[YouTube Bot] $msg")
            currentLogs.add(msg)
            _botState.update { it.copy(ytSessionLogs = currentLogs.toList(), currentAction = msg) }
        }

        try {
            val webView = getWebViewProvider()
            if (webView == null) {
                log("Error: WebView is currently unavailable.")
                _botState.update { it.copy(status = CrawlBotStatus.ERROR, errorMessage = "WebView not found.") }
                return
            }

            // Step 1: Navigation
            log("Navigating programmatically to YouTube Mobile...")
            withContext(Dispatchers.Main) {
                loadUrlAction("https://m.youtube.com")
            }
            delay(5000)

            // Step 2: Locate and Click Search Bar
            log("Locating the YouTube Search Bar...")
            val searchBtnScript = """
                (function() {
                    function highlightElement(el) {
                        if (!el) return;
                        el.style.outline = '4px solid #FF1744';
                        el.style.outlineOffset = '2px';
                        el.style.transition = 'outline 0.3s ease-in-out';
                        el.scrollIntoView({behavior: 'smooth', block: 'center'});
                        setTimeout(function() { el.style.outline = ''; }, 2000);
                    }

                    var searchBtn = document.querySelector('button.header-search-button, button[aria-label="Search YouTube"], .cxx-search-btn, button[aria-label="Search"], .yt-spec-button-shape-next[aria-label*="Search"]');
                    if (searchBtn) {
                        highlightElement(searchBtn);
                        searchBtn.click();
                        return "clicked_reveal";
                    }
                    return "visible_or_none";
                })();
            """.trimIndent()

            var searchBtnRes: String? = null
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(searchBtnScript) { res -> searchBtnRes = res }
            }
            delay(1500)
            log("Revealing search field... Response: $searchBtnRes")

            // Step 3: Type Search Query Programmatically & Submit
            log("Typing search query: '$query'...")
            val escapedQuery = JSONObject.quote(query)
            val typeAndSubmitScript = """
                (function() {
                    function highlightElement(el) {
                        if (!el) return;
                        el.style.outline = '4px solid #FF1744';
                        el.style.outlineOffset = '2px';
                        el.style.transition = 'outline 0.3s ease-in-out';
                        el.scrollIntoView({behavior: 'smooth', block: 'center'});
                        setTimeout(function() { el.style.outline = ''; }, 2000);
                    }

                    var input = document.querySelector('input.search-input, input[name="search"], input[type="search"], .search-box input, input[aria-label="Search YouTube"], input[aria-label="Search"]');
                    if (input) {
                        highlightElement(input);
                        input.value = $escapedQuery;
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                        
                        // Fire a soft-keyboard Search submit event for reliable SPA transitions
                        var keyEvent = new KeyboardEvent('keydown', {
                            bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 13
                        });
                        input.dispatchEvent(keyEvent);

                        // Form submit as first fallback
                        var form = input.closest('form');
                        if (form) {
                            form.submit();
                            return "submitted_form";
                        }
                        var submitBtn = document.querySelector('button.search-icon, button.search-button, button[type="submit"], button[aria-label="Search"]');
                        if (submitBtn) {
                            highlightElement(submitBtn);
                            submitBtn.click();
                            return "clicked_submit";
                        }
                        return "typed_and_keyboard_event_dispatched";
                    }
                    return "not_found";
                })();
            """.trimIndent()

            var submitRes: String? = null
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(typeAndSubmitScript) { res -> submitRes = res }
            }
            delay(5000) // Wait for results to load fully
            log("Query submitted. Render status: $submitRes")

            // Scroll down slightly to make sure results are fully fetched and rendered
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript("window.scrollBy({top: 400, behavior: 'smooth'});") {}
            }
            delay(2000)

            // Step 4: Analyze Search Results and Parse Metadata
            log("Parsing video titles, view counts, and upload dates...")
            val extractVideosScript = """
                (function() {
                    var list = [];
                    var items = document.querySelectorAll('ytm-video-with-context-renderer, ytm-compact-video-renderer, ytd-video-renderer, .media-item, a[href*="/watch"]');
                    
                    items.forEach(function(item) {
                        var titleEl = item.querySelector('h3, .media-item-title, .compact-media-item-headline, #video-title');
                        var title = titleEl ? titleEl.innerText : '';
                        
                        var linkEl = item.querySelector('a[href*="/watch"]') || (item.tagName === 'A' && item.href.includes('/watch') ? item : null);
                        var url = linkEl ? linkEl.href : '';
                        
                        var metaText = '';
                        var metaEls = item.querySelectorAll('.subhead, .metadata, #metadata-line span, .ytm-badge-and-byline-renderer');
                        metaEls.forEach(function(el) {
                            metaText += ' ' + el.innerText;
                        });
                        if (!metaText) {
                            metaText = item.innerText;
                        }
                        
                        if (title && url) {
                            // Parse View Counts
                            var views = 0;
                            var viewMatch = metaText.match(/([\d.,]+)\s*(K|M|B)?\s*views/i) || metaText.match(/([\d.,]+)\s*(thousand|million|billion)?\s*views/i);
                            if (viewMatch) {
                                var num = parseFloat(viewMatch[1].replace(/,/g, ''));
                                var multiplier = viewMatch[2] ? viewMatch[2].toLowerCase() : '';
                                if (multiplier === 'k' || multiplier === 'thousand') num *= 1000;
                                else if (multiplier === 'm' || multiplier === 'million') num *= 1000000;
                                else if (multiplier === 'b' || multiplier === 'billion') num *= 1000000000;
                                views = num;
                            }
                            
                            // Parse freshness/age weight
                            var freshness = 0;
                            if (/second/i.test(metaText)) freshness = 10000;
                            else if (/minute/i.test(metaText)) freshness = 9000;
                            else if (/hour/i.test(metaText)) freshness = 8000;
                            else if (/day/i.test(metaText)) freshness = 7000;
                            else if (/week/i.test(metaText)) freshness = 6000;
                            else if (/month/i.test(metaText)) freshness = 5000;
                            else if (/year/i.test(metaText)) freshness = 1000;
                            
                            list.push({
                                title: title.trim(),
                                url: url,
                                viewsText: viewMatch ? viewMatch[0] : 'Unknown views',
                                viewsCount: views,
                                freshness: freshness,
                                metaText: metaText.replace(/\s+/g, ' ').trim()
                            });
                        }
                    });
                    return JSON.stringify(list);
                })();
            """.trimIndent()

            var videoJsonRaw: String? = null
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(extractVideosScript) { res -> videoJsonRaw = res }
            }
            delay(2000)

            val parsedVideos = mutableListOf<ParsedVideoItem>()
            if (!videoJsonRaw.isNullOrBlank() && videoJsonRaw != "null") {
                val cleanJson = if (videoJsonRaw!!.startsWith("\"") && videoJsonRaw!!.endsWith("\"")) {
                    org.json.JSONTokener(videoJsonRaw!!).nextValue().toString()
                } else {
                    videoJsonRaw!!
                }
                try {
                    val arr = JSONArray(cleanJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        parsedVideos.add(ParsedVideoItem(
                            title = obj.getString("title"),
                            url = obj.getString("url"),
                            viewsText = obj.getString("viewsText"),
                            viewsCount = obj.getLong("viewsCount"),
                            freshness = obj.getInt("freshness"),
                            metaText = obj.getString("metaText")
                        ))
                    }
                } catch (e: Exception) {
                    Log.e("SiteCrawlerEngine", "JSON parsing failed", e)
                }
            }

            if (parsedVideos.isEmpty()) {
                log("Warning: No video metadata could be parsed. Attempting fallback navigation...")
                // Fallback: try to navigate to first /watch href on page
                val fallbackScript = """
                    (function() {
                        var watchLink = document.querySelector('a[href*="/watch"]');
                        return watchLink ? watchLink.href : null;
                    })();
                """.trimIndent()
                var fallbackUrl: String? = null
                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript(fallbackScript) { res -> fallbackUrl = res }
                }
                delay(1500)

                if (!fallbackUrl.isNullOrBlank() && fallbackUrl != "null") {
                    val cleanUrl = fallbackUrl!!.replace("\"", "")
                    log("Fallback navigation found: $cleanUrl")
                    parsedVideos.add(ParsedVideoItem("Suggested Video", cleanUrl, "Unknown views", 0, 0, "Unknown Date"))
                } else {
                    log("Error: No YouTube videos found on page.")
                    _botState.update { it.copy(status = CrawlBotStatus.ERROR, errorMessage = "No video watch link found.") }
                    return
                }
            }

            // Step 5: Evaluate & Select Target based on Criteria
            log("Evaluating target based on selection criteria: ${criteria.uppercase()}...")

            // Normalizing YouTube URLs before comparison to prevent query param bypasses (like &t=, &feature= etc.)
            val normalizeYtUrl = { url: String ->
                try {
                    val uri = android.net.Uri.parse(url)
                    val videoId = uri.getQueryParameter("v")
                    if (videoId != null) {
                        "https://www.youtube.com/watch?v=$videoId"
                    } else {
                        url.substringBefore("?").substringBefore("&")
                    }
                } catch (e: Exception) {
                    url
                }
            }

            // Retrieve visited history URLs to avoid duplication with robust normalized matches
            val visitedUrlsSet = _botState.value.ytHistory.map { normalizeYtUrl(it.videoUrl) }.toSet()
            val unvisitedVideos = parsedVideos.filter { normalizeYtUrl(it.url) !in visitedUrlsSet }

            // Choose list to sort (prefer unvisited to avoid duplicates as requested, fallback to all)
            val candidateList = if (unvisitedVideos.isNotEmpty()) {
                log("Filtering out ${parsedVideos.size - unvisitedVideos.size} previously visited videos to prevent duplicates.")
                unvisitedVideos
            } else {
                log("All found videos have been visited before. Re-evaluating complete results.")
                parsedVideos
            }

            val targetVideo = when (criteria) {
                "newest" -> {
                    candidateList.sortedWith(compareByDescending<ParsedVideoItem> { it.freshness }.thenByDescending { it.viewsCount }).first()
                }
                else -> { // highest_views
                    candidateList.sortedByDescending { it.viewsCount }.first()
                }
            }

            log("Selected target: '${targetVideo.title}' (${targetVideo.viewsText}) [Freshness Rank: ${targetVideo.freshness}]")

            // Step 6: Navigation to Watch page
            log("Initiating playback navigation...")
            withContext(Dispatchers.Main) {
                loadUrlAction(targetVideo.url)
            }
            _botState.update {
                it.copy(
                    ytActiveVideoTitle = targetVideo.title,
                    ytActiveVideoUrl = targetVideo.url,
                    ytActiveVideoViews = targetVideo.viewsText,
                    ytActiveVideoDate = targetVideo.metaText
                )
            }
            delay(6000) // Allow video page to buffer and start

            // Step 7: Engagement - Link Extraction & Copy
            log("Extracting and copying video URL to the system clipboard...")
            val finalUrl = targetVideo.url
            withContext(Dispatchers.Main) {
                val clipboard = webView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("YouTube Video Link", finalUrl)
                clipboard.setPrimaryClip(clip)
            }
            log("Successfully copied link: $finalUrl")

            // Step 8: Engagement - Like
            var likedResult = false
            if (like) {
                log("Sending automatic 'Like' signal to YouTube page element...")
                val likeScript = """
                    (function() {
                        function highlightElement(el) {
                            if (!el) return;
                            el.style.outline = '4px solid #FF1744';
                            el.style.outlineOffset = '2px';
                            el.style.transition = 'outline 0.3s ease-in-out';
                            el.scrollIntoView({behavior: 'smooth', block: 'center'});
                            setTimeout(function() { el.style.outline = ''; }, 2500);
                        }

                        var likeBtn = document.querySelector('button[aria-label*="like this video"], button[aria-label*="Like"], button.like-button-renderer, ytd-toggle-button-renderer button, [role="button"][aria-label*="Like"], .yt-spec-button-shape-next[aria-label*="Like"]');
                        if (likeBtn) {
                            highlightElement(likeBtn);
                            likeBtn.click();
                            return "clicked";
                        }
                        return "not_found";
                    })();
                """.trimIndent()
                var likeRes: String? = null
                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript(likeScript) { res -> likeRes = res }
                }
                delay(1500)
                likedResult = likeRes?.contains("clicked") == true
                log("Like action trigger: $likeRes")
            }

            // Step 9: Engagement - Comment
            var commentedResult = false
            if (comment && commentText.isNotBlank()) {
                log("Attempting to scroll and publish comment: '$commentText'...")
                val escapedComment = JSONObject.quote(commentText)
                val commentScript = """
                    (function() {
                        function highlightElement(el) {
                            if (!el) return;
                            el.style.outline = '4px solid #FF1744';
                            el.style.outlineOffset = '2px';
                            el.style.transition = 'outline 0.3s ease-in-out';
                            el.scrollIntoView({behavior: 'smooth', block: 'center'});
                            setTimeout(function() { el.style.outline = ''; }, 2500);
                        }

                        window.scrollTo(0, 450);
                        var box = document.querySelector('ytm-comment-simplebox-renderer, textarea, .comment-simplebox-text, [placeholder*="Add a comment"], .ytm-comment-simplebox-reply-device, .ytm-comments-header-renderer');
                        if (box) {
                            highlightElement(box);
                            box.click();
                            var input = document.querySelector('textarea, input.comment-simplebox-text, .comment-simplebox-text, [placeholder*="Add a comment"], .yt-spec-button-shape-next[aria-label*="Comment"] input');
                            if (input) {
                                highlightElement(input);
                                input.value = $escapedComment;
                                input.dispatchEvent(new Event('input', { bubbles: true }));
                                input.dispatchEvent(new Event('change', { bubbles: true }));
                                
                                var submit = document.querySelector('button.comment-simplebox-submit, button[aria-label="Comment"], #submit-button, .ytm-comment-simplebox-submit-button, .yt-spec-button-shape-next--filled[aria-label="Comment"]');
                                if (submit) {
                                    highlightElement(submit);
                                    submit.click();
                                    return "submitted";
                                }
                            }
                        }
                        return "box_clicked_but_unfilled";
                    })();
                """.trimIndent()
                var commentRes: String? = null
                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript(commentScript) { res -> commentRes = res }
                }
                delay(2500)
                commentedResult = commentRes?.contains("submitted") == true
                log("Comment action trigger: $commentRes")
            }

            // Step 10: Session Logging & Memory Persistence
            log("Saving interaction metadata to Local History State...")
            val newInteraction = YoutubeInteractionItem(
                query = query,
                videoTitle = targetVideo.title,
                videoUrl = targetVideo.url,
                viewCountText = targetVideo.viewsText,
                timestamp = System.currentTimeMillis(),
                isLiked = likedResult || like, // count true if triggered
                isCommented = commentedResult || comment,
                commentText = if (comment) commentText else ""
            )

            val updatedHistory = _botState.value.ytHistory + newInteraction
            _botState.update {
                it.copy(
                    status = CrawlBotStatus.COMPLETED,
                    ytHistory = updatedHistory,
                    currentAction = "YouTube Automation completed successfully!"
                )
            }

            // Persist history to SharedPreferences
            withContext(Dispatchers.IO) {
                saveYtHistory(webView.context, updatedHistory)
            }

            log("YouTube Automation finished! Task completed successfully.")

        } catch (e: Exception) {
            log("Automation Error: ${e.localizedMessage ?: "Unknown Exception"}")
            _botState.update {
                it.copy(
                    status = CrawlBotStatus.ERROR,
                    errorMessage = e.localizedMessage,
                    currentAction = "Automation failed: ${e.localizedMessage}"
                )
            }
        }
    }

    private data class ParsedVideoItem(
        val title: String,
        val url: String,
        val viewsText: String,
        val viewsCount: Long,
        val freshness: Int,
        val metaText: String
    )

    private fun loadYtHistory(context: Context): List<YoutubeInteractionItem> {
        val prefs = context.getSharedPreferences("youtube_bot_prefs", Context.MODE_PRIVATE)
        val rawJson = prefs.getString("history", "[]") ?: "[]"
        val list = mutableListOf<YoutubeInteractionItem>()
        try {
            val arr = org.json.JSONArray(rawJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(YoutubeInteractionItem(
                    query = obj.optString("query"),
                    videoTitle = obj.optString("videoTitle"),
                    videoUrl = obj.optString("videoUrl"),
                    viewCountText = obj.optString("viewCountText"),
                    timestamp = obj.optLong("timestamp"),
                    isLiked = obj.optBoolean("isLiked"),
                    isCommented = obj.optBoolean("isCommented"),
                    commentText = obj.optString("commentText")
                ))
            }
        } catch (e: Exception) {
            Log.e("SiteCrawlerEngine", "Failed to load history", e)
        }
        return list
    }

    private fun saveYtHistory(context: Context, history: List<YoutubeInteractionItem>) {
        val prefs = context.getSharedPreferences("youtube_bot_prefs", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray()
        history.forEach { item ->
            val obj = org.json.JSONObject()
            obj.put("query", item.query)
            obj.put("videoTitle", item.videoTitle)
            obj.put("videoUrl", item.videoUrl)
            obj.put("viewCountText", item.viewCountText)
            obj.put("timestamp", item.timestamp)
            obj.put("isLiked", item.isLiked)
            obj.put("isCommented", item.isCommented)
            obj.put("commentText", item.commentText)
            arr.put(obj)
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun clearYtHistory(context: Context) {
        val prefs = context.getSharedPreferences("youtube_bot_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("history").apply()
        _botState.update { it.copy(ytHistory = emptyList()) }
    }
}
