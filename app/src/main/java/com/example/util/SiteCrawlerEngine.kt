package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.util.Log
import android.webkit.WebView
import com.example.model.CrawlBotState
import com.example.model.CrawlBotStatus
import com.example.model.CrawlMatchItem
import com.example.model.VideoInteractionItem
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
            if (state.botMode == "video") {
                startVideoBot(
                    query = state.videoSearchQuery,
                    limit = state.videoLimit,
                    criteria = state.videoSelectionCriteria,
                    like = state.videoPerformLike,
                    comment = state.videoPerformComment,
                    copyLink = state.videoPerformCopyLink,
                    commentText = state.videoCommentText,
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

    fun startVideoBot(
        query: String,
        limit: Int,
        criteria: String, // "best_match", "newest", "highest_views", "oldest"
        like: Boolean,
        comment: Boolean,
        copyLink: Boolean,
        commentText: String,
        scope: CoroutineScope,
        loadUrlAction: (String) -> Unit,
        getWebViewProvider: () -> WebView?
    ) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return

        stopCrawl()

        val webView = getWebViewProvider()
        val context = webView?.context
        val loadedHistory = if (context != null) loadVideoHistory(context) else emptyList()

        _botState.value = CrawlBotState(
            status = CrawlBotStatus.RUNNING,
            botMode = "video",
            videoSearchQuery = trimmedQuery,
            videoSelectionCriteria = criteria,
            videoLimit = limit,
            videoPerformLike = like,
            videoPerformComment = comment,
            videoPerformCopyLink = copyLink,
            videoCommentText = commentText,
            videoHistory = loadedHistory,
            videoCurrentIndex = 0,
            videoTotalTarget = 0,
            videoSessionLogs = listOf("Video Action Bot initialized successfully. Limit: $limit videos.", "Retrieved ${loadedHistory.size} previous interactions.")
        )

        crawlJob = scope.launch(Dispatchers.Main) {
            runVideoAutomationFlow(trimmedQuery, limit, criteria, like, comment, copyLink, commentText, loadUrlAction, getWebViewProvider)
        }
    }

    fun confirmCommentAction() {
        if (_botState.value.status == CrawlBotStatus.WAITING_CONFIRMATION) {
            _botState.update { it.copy(status = CrawlBotStatus.RUNNING, currentAction = "User confirmed comment. Proceeding...") }
        }
    }

    fun denyCommentAction() {
        if (_botState.value.status == CrawlBotStatus.WAITING_CONFIRMATION) {
            _botState.update { it.copy(status = CrawlBotStatus.RUNNING, currentAction = "User denied comment. Skipping...", videoPerformComment = false) }
        }
    }

    private suspend fun runVideoAutomationFlow(
        query: String,
        limit: Int,
        criteria: String,
        like: Boolean,
        comment: Boolean,
        copyLink: Boolean,
        commentText: String,
        loadUrlAction: (String) -> Unit,
        getWebViewProvider: () -> WebView?
    ) {
        val currentLogs = _botState.value.videoSessionLogs.toMutableList()

        fun log(msg: String) {
            Log.d("SiteCrawlerEngine", "[Video Bot] $msg")
            currentLogs.add(msg)
            _botState.update { it.copy(videoSessionLogs = currentLogs.toList(), currentAction = msg) }
        }

        suspend fun showToast(msg: String) {
            val ctx = getWebViewProvider()?.context
            if (ctx != null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        try {
            val webView = getWebViewProvider()
            if (webView == null) {
                log("Error: WebView is currently unavailable.")
                _botState.update { it.copy(status = CrawlBotStatus.ERROR, errorMessage = "WebView not found.") }
                return
            }

            // Step 1: SEARCH (Find Search Bar)
            log("Locating search bar on the current page...")
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

                    // Try to find a search input directly
                    var input = document.querySelector('input[type="search"], input[name="search_query"], input[name="q"], input[name="search"], input[id*="search"], input[aria-label*="Search"]');
                    if (input) {
                        highlightElement(input);
                        return "found_input_directly";
                    }

                    // Try to find a search button to reveal input
                    var searchBtn = document.querySelector('button.header-search-button, button[aria-label*="Search"], .search-btn, [role="button"][aria-label*="Search"], svg[aria-label="Search"]');
                    if (searchBtn) {
                        highlightElement(searchBtn);
                        searchBtn.click();
                        return "clicked_reveal";
                    }
                    return "not_found";
                })();
            """.trimIndent()

            var searchBtnRes: String? = null
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(searchBtnScript) { res -> searchBtnRes = res }
            }
            delay(1500)
            log("Search element reveal result: $searchBtnRes")

            // Wait if we couldn't find it, try fallback to YouTube if it's completely alien
            if (searchBtnRes?.contains("not_found") == true && !webView.url.toString().contains("youtube.com")) {
                 log("Could not find search bar on current page. Falling back to YouTube.")
                 withContext(Dispatchers.Main) {
                     loadUrlAction("https://m.youtube.com")
                 }
                 delay(5000)
            }

            // Step 2: Type Search Query
            log("Entering search query: '$query'...")
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

                    var input = document.querySelector('input.search-input, input[name="search_query"], input[name="search"], input[type="search"], input[name="q"], input[aria-label*="Search"]');
                    if (input) {
                        highlightElement(input);
                        input.value = $escapedQuery;
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                        
                        var keyEvent = new KeyboardEvent('keydown', {
                            bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 13
                        });
                        input.dispatchEvent(keyEvent);

                        var form = input.closest('form');
                        if (form) {
                            form.submit();
                            return "submitted_form";
                        }
                        var submitBtn = document.querySelector('button.search-icon, button.search-button, button[type="submit"], button[aria-label*="Search"]');
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
            delay(5000) // Wait for results to load
            log("Search executed. Status: $submitRes")

            // Auto Scroll to fetch more results
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript("window.scrollBy({top: 800, behavior: 'smooth'});") {}
            }
            delay(2000)

            // Step 3: Parse Results (Title, Channel, Views, Upload Date, Duration, URL)
            log("Extracting video metadata (Title, Channel, Views, Date, Duration)...")
            val extractVideosScript = """
                (function() {
                    var list = [];
                    var items = document.querySelectorAll('ytm-video-with-context-renderer, ytm-compact-video-renderer, ytd-video-renderer, .media-item, a[href*="/watch"], article');
                    
                    items.forEach(function(item) {
                        var titleEl = item.querySelector('h3, .media-item-title, .compact-media-item-headline, #video-title');
                        var title = titleEl ? titleEl.innerText : '';
                        
                        var linkEl = item.querySelector('a[href*="/watch"]') || (item.tagName === 'A' && item.href.includes('/watch') ? item : null);
                        if (!linkEl) linkEl = item.querySelector('a'); // fallback for generic sites
                        var url = linkEl ? linkEl.href : '';
                        
                        var metaText = '';
                        var metaEls = item.querySelectorAll('.subhead, .metadata, #metadata-line span, .ytm-badge-and-byline-renderer, .channel-name');
                        metaEls.forEach(function(el) { metaText += ' ' + el.innerText; });
                        if (!metaText) metaText = item.innerText;
                        
                        var channelEl = item.querySelector('.ytm-badge-and-byline-item-byline, .channel-name, ytd-channel-name');
                        var channel = channelEl ? channelEl.innerText : 'Unknown Channel';
                        
                        if (title && url) {
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
                            
                            var freshness = 0;
                            if (/second/i.test(metaText)) freshness = 10000;
                            else if (/minute/i.test(metaText)) freshness = 9000;
                            else if (/hour/i.test(metaText)) freshness = 8000;
                            else if (/day/i.test(metaText)) freshness = 7000;
                            else if (/week/i.test(metaText)) freshness = 6000;
                            else if (/month/i.test(metaText)) freshness = 5000;
                            else if (/year/i.test(metaText)) {
                                var yrMatch = metaText.match(/([\d]+)\s*year/i);
                                freshness = yrMatch ? 1000 - parseInt(yrMatch[1]) : 1000;
                            }
                            
                            list.push({
                                title: title.trim(),
                                channel: channel.trim(),
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
                            channel = obj.optString("channel", "Unknown Channel"),
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
                log("Error: No videos found. Aborting.")
                showToast("No videos found to process.")
                _botState.update { it.copy(status = CrawlBotStatus.ERROR, errorMessage = "No video results found.") }
                return
            }

            // Step 4: Compare & Select Video
            log("Applying criteria: ${criteria.uppercase()}...")
            val candidateList = parsedVideos
            val targetVideos = when (criteria) {
                "newest" -> candidateList.sortedWith(compareByDescending<ParsedVideoItem> { it.freshness }.thenByDescending { it.viewsCount })
                "oldest" -> candidateList.sortedWith(compareBy<ParsedVideoItem> { it.freshness }.thenByDescending { it.viewsCount })
                "highest_views" -> candidateList.sortedByDescending { it.viewsCount }
                else -> candidateList // best_match
            }.take(limit)

            log("Found ${targetVideos.size} videos matching criteria to process.")
            showToast("Search complete. Found ${targetVideos.size} videos.")
            
            _botState.update { it.copy(videoTotalTarget = targetVideos.size) }

            for ((index, targetVideo) in targetVideos.withIndex()) {
                if (_botState.value.status != CrawlBotStatus.RUNNING && _botState.value.status != CrawlBotStatus.WAITING_CONFIRMATION) {
                    log("Bot stopped or paused. Exiting video loop.")
                    break
                }
                
                log("--- Processing Video ${index + 1} of ${targetVideos.size} ---")
                log("Selected: '${targetVideo.title}' by ${targetVideo.channel} (${targetVideo.viewsText})")
                _botState.update {
                    it.copy(
                        videoActiveTitle = targetVideo.title,
                        videoActiveChannel = targetVideo.channel,
                        videoActiveUrl = targetVideo.url,
                        videoActiveViews = targetVideo.viewsText,
                        videoActiveDate = targetVideo.metaText,
                        videoCurrentIndex = index + 1
                    )
                }

                // Step 5: Navigate to Video
                log("Navigating to selected video...")
                withContext(Dispatchers.Main) {
                    loadUrlAction(targetVideo.url)
                }
                delay(7000)

                // Verify URL
                val currentUrlRes = withContext(Dispatchers.Main) { webView.url ?: targetVideo.url }
                log("Verified target URL: $currentUrlRes")
                showToast("Opened video: ${targetVideo.title}")

                // Step 6: Action - Copy Link
                var copiedResult = false
                if (_botState.value.videoPerformCopyLink) {
                    log("Copying verified link to clipboard...")
                    withContext(Dispatchers.Main) {
                        val clipboard = webView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Video Link", currentUrlRes)
                        clipboard.setPrimaryClip(clip)
                    }
                    copiedResult = true
                    log("Copied link successfully.")
                    showToast("Copied link to clipboard")
                }

                // Step 7: Action - Like
                var likedResult = false
                if (_botState.value.videoPerformLike) {
                    log("Executing Like action...")
                    val likeScript = """
                        (function() {
                            var likeBtn = document.querySelector('button[aria-label*="like this video"], button[aria-label*="Like"], button.like-button-renderer, [role="button"][aria-label*="Like"], .yt-spec-button-shape-next[aria-label*="Like"]');
                            if (likeBtn) {
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
                    log("Like Result: $likeRes")
                    if (likedResult) {
                        showToast("Liked video: ${targetVideo.title}")
                    }
                }

                // Step 8: Action - Comment with User Confirmation
                var commentedResult = false
                if (_botState.value.videoPerformComment && commentText.isNotBlank()) {
                    log("Preparing to comment. Requesting User Confirmation...")
                    _botState.update { it.copy(status = CrawlBotStatus.WAITING_CONFIRMATION, currentAction = "Waiting for User Confirmation to post comment...") }
                    
                    // Wait until status changes from WAITING_CONFIRMATION
                    while (_botState.value.status == CrawlBotStatus.WAITING_CONFIRMATION) {
                        delay(500)
                    }

                    // If user didn't stop and didn't deny (videoPerformComment is still true)
                    if (_botState.value.status == CrawlBotStatus.RUNNING && _botState.value.videoPerformComment) {
                        log("User Confirmed. Posting comment...")
                        val escapedComment = org.json.JSONObject.quote(commentText)
                        val commentScript = """
                            (function() {
                                window.scrollTo(0, 500);
                                var box = document.querySelector('ytm-comment-simplebox-renderer, textarea, .comment-simplebox-text, [placeholder*="Add a comment"], .ytm-comments-header-renderer');
                                if (box) {
                                    box.click();
                                    var input = document.querySelector('textarea, input.comment-simplebox-text, [placeholder*="Add a comment"], .yt-spec-button-shape-next[aria-label*="Comment"] input');
                                    if (input) {
                                        input.value = ${escapedComment};
                                        input.dispatchEvent(new Event('input', { bubbles: true }));
                                        input.dispatchEvent(new Event('change', { bubbles: true }));
                                        
                                        var submit = document.querySelector('button.comment-simplebox-submit, button[aria-label="Comment"], #submit-button, .yt-spec-button-shape-next--filled[aria-label="Comment"]');
                                        if (submit) {
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
                        log("Comment Action Result: $commentRes")
                        if (commentedResult) {
                            showToast("Commented on video: ${targetVideo.title}")
                        }
                    } else {
                        log("Comment Action Denied/Skipped by user.")
                    }
                }

                // Step 9: Report & Memory
                log("Saving interaction to local history...")
                val newInteraction = VideoInteractionItem(
                    query = query,
                    selectedVideoTitle = targetVideo.title,
                    channel = targetVideo.channel,
                    viewsText = targetVideo.viewsText,
                    uploadDateText = targetVideo.metaText,
                    videoUrl = targetVideo.url,
                    timestamp = System.currentTimeMillis(),
                    actionSearched = true,
                    actionOpened = true,
                    actionLiked = likedResult,
                    actionCopied = copiedResult,
                    actionCommented = commentedResult,
                    actionResult = "Success"
                )

                val updatedHistory = _botState.value.videoHistory + newInteraction
                _botState.update {
                    it.copy(videoHistory = updatedHistory)
                }

                withContext(Dispatchers.IO) {
                    saveVideoHistory(webView.context, updatedHistory)
                }
                
                log("Finished Video ${index + 1}.")
                delay(3000)
            }

            _botState.update {
                it.copy(
                    status = CrawlBotStatus.COMPLETED,
                    currentAction = "Video Automation completed! Processed ${targetVideos.size} videos."
                )
            }
            log("Bot finished successfully.")
            showToast("Video automation complete!")

        } catch (e: Exception) {
            log("Automation Error: ${e.localizedMessage}")
            _botState.update { it.copy(status = CrawlBotStatus.ERROR, errorMessage = e.localizedMessage) }
        }
    }

    private data class ParsedVideoItem(
        val title: String,
        val channel: String,
        val url: String,
        val viewsText: String,
        val viewsCount: Long,
        val freshness: Int,
        val metaText: String
    )
    private fun loadVideoHistory(context: Context): List<VideoInteractionItem> {
        val prefs = context.getSharedPreferences("video_bot_prefs", Context.MODE_PRIVATE)
        val rawJson = prefs.getString("history", "[]") ?: "[]"
        val list = mutableListOf<VideoInteractionItem>()
        try {
            val arr = org.json.JSONArray(rawJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(VideoInteractionItem(
                    query = obj.optString("query"),
                    selectedVideoTitle = obj.optString("selectedVideoTitle"),
                    channel = obj.optString("channel"),
                    viewsText = obj.optString("viewsText"),
                    uploadDateText = obj.optString("uploadDateText"),
                    videoUrl = obj.optString("videoUrl"),
                    timestamp = obj.optLong("timestamp"),
                    actionSearched = obj.optBoolean("actionSearched"),
                    actionOpened = obj.optBoolean("actionOpened"),
                    actionLiked = obj.optBoolean("actionLiked"),
                    actionCopied = obj.optBoolean("actionCopied"),
                    actionCommented = obj.optBoolean("actionCommented"),
                    actionResult = obj.optString("actionResult")
                ))
            }
        } catch (e: Exception) {
            Log.e("SiteCrawlerEngine", "Failed to load history", e)
        }
        return list
    }

    private fun saveVideoHistory(context: Context, history: List<VideoInteractionItem>) {
        val prefs = context.getSharedPreferences("video_bot_prefs", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray()
        history.forEach { item ->
            val obj = org.json.JSONObject()
            obj.put("query", item.query)
            obj.put("selectedVideoTitle", item.selectedVideoTitle)
            obj.put("channel", item.channel)
            obj.put("viewsText", item.viewsText)
            obj.put("uploadDateText", item.uploadDateText)
            obj.put("videoUrl", item.videoUrl)
            obj.put("timestamp", item.timestamp)
            obj.put("actionSearched", item.actionSearched)
            obj.put("actionOpened", item.actionOpened)
            obj.put("actionLiked", item.actionLiked)
            obj.put("actionCopied", item.actionCopied)
            obj.put("actionCommented", item.actionCommented)
            obj.put("actionResult", item.actionResult)
            arr.put(obj)
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun clearVideoHistory(context: Context) {
        val prefs = context.getSharedPreferences("video_bot_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("history").apply()
        _botState.update { it.copy(videoHistory = emptyList()) }
    }

    // --- TikTok Viral Repurposing Bot Methods ---

    fun startTikTokBot(
        query: String,
        limit: Int,
        minViews: Long,
        criteria: String,
        customHashtags: String,
        scope: CoroutineScope,
        loadUrlAction: (String) -> Unit,
        getWebViewProvider: () -> WebView?
    ) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return

        stopCrawl()

        val webView = getWebViewProvider()
        val context = webView?.context
        val loadedReels = if (context != null) TikTokViralEngine.loadTikTokHistory(context) else emptyList()

        _botState.value = CrawlBotState(
            status = CrawlBotStatus.RUNNING,
            botMode = "tiktok",
            tiktokQuery = trimmedQuery,
            tiktokLimit = limit,
            tiktokMinViews = minViews,
            tiktokSortCriteria = criteria,
            tiktokCustomHashtags = customHashtags,
            tiktokDownloadedReels = loadedReels,
            tiktokCurrentIndex = 0,
            tiktokTotalTarget = limit,
            tiktokLogs = listOf("TikTok Viral Hunter Bot initialized. Target: $limit reels for '$trimmedQuery'."),
            tiktokActiveStatusStep = "Initializing TikTok Viral Engine..."
        )

        crawlJob = scope.launch(Dispatchers.Main) {
            TikTokViralEngine.runTikTokAutomationFlow(
                query = trimmedQuery,
                limit = limit,
                minViews = minViews,
                criteria = criteria,
                customHashtags = customHashtags,
                loadUrlAction = loadUrlAction,
                getWebViewProvider = getWebViewProvider,
                botState = _botState
            )
        }
    }

    fun clearTikTokHistory(context: Context) {
        TikTokViralEngine.clearTikTokHistory(context)
        _botState.update { it.copy(tiktokDownloadedReels = emptyList()) }
    }

    fun deleteTikTokReel(context: Context, id: String) {
        val updated = TikTokViralEngine.deleteTikTokReel(context, id)
        _botState.update { it.copy(tiktokDownloadedReels = updated) }
    }
}
