import re

with open('app/src/main/java/com/example/util/SiteCrawlerEngine.kt', 'r') as f:
    content = f.read()

# Target block to replace
target = """            val targetVideo = when (criteria) {
                "newest" -> candidateList.sortedWith(compareByDescending<ParsedVideoItem> { it.freshness }.thenByDescending { it.viewsCount }).first()
                "oldest" -> candidateList.sortedWith(compareBy<ParsedVideoItem> { it.freshness }.thenByDescending { it.viewsCount }).first()
                "highest_views" -> candidateList.sortedByDescending { it.viewsCount }.first()
                else -> candidateList.first() // best_match
            }
            
            log("Selected: '${targetVideo.title}' by ${targetVideo.channel} (${targetVideo.viewsText})")
"""
target = content[content.find("            val candidateList = parsedVideos"):]
end_target_idx = target.find('log("Bot finished successfully.")\n') + len('log("Bot finished successfully.")\n')
block_to_replace = target[:end_target_idx]

replacement = """            val candidateList = parsedVideos
            val targetVideos = when (criteria) {
                "newest" -> candidateList.sortedWith(compareByDescending<ParsedVideoItem> { it.freshness }.thenByDescending { it.viewsCount })
                "oldest" -> candidateList.sortedWith(compareBy<ParsedVideoItem> { it.freshness }.thenByDescending { it.viewsCount })
                "highest_views" -> candidateList.sortedByDescending { it.viewsCount }
                else -> candidateList // best_match
            }.take(limit)

            log("Found ${targetVideos.size} videos matching criteria to process.")

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
                        videoActiveDate = targetVideo.metaText
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
                }

                // Step 7: Action - Like
                var likedResult = false
                if (_botState.value.videoPerformLike) {
                    log("Executing Like action...")
                    val likeScript = \"\"\"
                        (function() {
                            var likeBtn = document.querySelector('button[aria-label*="like this video"], button[aria-label*="Like"], button.like-button-renderer, [role="button"][aria-label*="Like"], .yt-spec-button-shape-next[aria-label*="Like"]');
                            if (likeBtn) {
                                likeBtn.click();
                                return "clicked";
                            }
                            return "not_found";
                        })();
                    \"\"\".trimIndent()
                    var likeRes: String? = null
                    withContext(Dispatchers.Main) {
                        webView.evaluateJavascript(likeScript) { res -> likeRes = res }
                    }
                    delay(1500)
                    likedResult = likeRes?.contains("clicked") == true
                    log("Like Result: $likeRes")
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
                        val commentScript = \"\"\"
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
                        \"\"\".trimIndent()
                        var commentRes: String? = null
                        withContext(Dispatchers.Main) {
                            webView.evaluateJavascript(commentScript) { res -> commentRes = res }
                        }
                        delay(2500)
                        commentedResult = commentRes?.contains("submitted") == true
                        log("Comment Action Result: $commentRes")
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
"""

new_content = content.replace(block_to_replace, replacement)
with open('app/src/main/java/com/example/util/SiteCrawlerEngine.kt', 'w') as f:
    f.write(new_content)
