package com.example.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import com.example.ui.BrowserViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

data class ActionExecutionResult(
    val isSuccess: Boolean,
    val summary: String,
    val data: JSONObject = JSONObject(),
    val screenshotBase64: String? = null
)

class BrowserActionExecutor(
    private val viewModel: BrowserViewModel
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun getActiveWebView(): WebView? {
        val activeTabId = viewModel.uiState.value.let {
            if (it.isIncognitoMode) it.activeIncognitoTabId else it.activeNormalTabId
        }
        return viewModel.getWebViewForTab(activeTabId)
    }

    suspend fun executeJavaScript(script: String): String = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String>()
        val webView = getActiveWebView()
        if (webView == null) {
            return@withContext "Error: No active WebView found."
        }

        webView.evaluateJavascript("(function() { try { $script } catch(e) { return 'ERROR: ' + e.message; } })()") { result ->
            val unquoted = if (result != null && result.startsWith("\"") && result.endsWith("\"") && result.length >= 2) {
                try {
                    JSONObject("{ \"res\": $result }").getString("res")
                } catch (e: Exception) {
                    result.removeSurrounding("\"")
                }
            } else {
                result ?: ""
            }
            deferred.complete(unquoted)
        }

        withTimeoutOrNull(10000) { deferred.await() } ?: "Timeout executing script."
    }

    suspend fun openUrl(url: String): ActionExecutionResult = withContext(Dispatchers.Main) {
        viewModel.loadUrl(url)
        ActionExecutionResult(
            isSuccess = true,
            summary = "Navigating to URL: $url"
        )
    }

    suspend fun clickElement(selector: String?, textMatch: String?, xpath: String?): ActionExecutionResult = withContext(Dispatchers.Main) {
        val escapedSelector = selector?.replace("'", "\\'") ?: ""
        val escapedText = textMatch?.replace("'", "\\'") ?: ""
        val escapedXPath = xpath?.replace("'", "\\'") ?: ""

        val script = """
            var target = null;
            if ('$escapedSelector') {
                target = document.querySelector('$escapedSelector');
            }
            if (!target && '$escapedText') {
                var text = '$escapedText'.toLowerCase();
                var elements = Array.from(document.querySelectorAll('button, a, [role="button"], input[type="submit"], input[type="button"], span, div, p, h1, h2, h3, li'));
                for (var i = 0; i < elements.length; i++) {
                    var el = elements[i];
                    if (el.innerText && el.innerText.toLowerCase().includes(text)) {
                        target = el;
                        break;
                    }
                }
            }
            if (!target && '$escapedXPath') {
                var xpathResult = document.evaluate('$escapedXPath', document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                target = xpathResult.singleNodeValue;
            }

            if (target) {
                target.scrollIntoView({ behavior: 'smooth', block: 'center' });
                
                // Visual feedback highlight
                var origOutline = target.style.outline;
                var origBoxShadow = target.style.boxShadow;
                target.style.outline = '3px solid #3F51B5';
                target.style.boxShadow = '0 0 10px #3F51B5';
                
                setTimeout(function() {
                    target.style.outline = origOutline;
                    target.style.boxShadow = origBoxShadow;
                }, 1000);

                target.focus();
                target.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: window }));
                target.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: window }));
                target.click();

                var tag = target.tagName;
                var text = (target.innerText || target.value || '').substring(0, 50).trim();
                return JSON.stringify({ success: true, element: tag, text: text });
            } else {
                return JSON.stringify({ success: false, error: 'Element not found with selector: $escapedSelector or text: $escapedText' });
            }
        """.trimIndent()

        val rawResult = executeJavaScript(script)
        try {
            val json = JSONObject(rawResult)
            val isSuccess = json.optBoolean("success", false)
            if (isSuccess) {
                ActionExecutionResult(
                    isSuccess = true,
                    summary = "Successfully clicked <${json.optString("element")}> '${json.optString("text")}'",
                    data = json
                )
            } else {
                ActionExecutionResult(
                    isSuccess = false,
                    summary = json.optString("error", "Failed to find element to click."),
                    data = json
                )
            }
        } catch (e: Exception) {
            ActionExecutionResult(isSuccess = false, summary = "Click failed: $rawResult")
        }
    }

    suspend fun typeText(selector: String, text: String, clearFirst: Boolean = true, pressEnter: Boolean = false): ActionExecutionResult = withContext(Dispatchers.Main) {
        val escapedSelector = selector.replace("'", "\\'")
        val escapedText = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")

        val script = """
            var el = document.querySelector('$escapedSelector');
            if (el) {
                el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                el.focus();
                
                // Visual highlight
                el.style.outline = '2px solid #2196F3';
                setTimeout(function() { el.style.outline = ''; }, 1000);

                if ($clearFirst) {
                    el.value = '';
                }
                
                el.value = '$escapedText';
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));

                if ($pressEnter) {
                    el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                    el.dispatchEvent(new KeyboardEvent('keypress', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                    el.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                    if (el.form) {
                        el.form.dispatchEvent(new Event('submit', { bubbles: true }));
                    }
                }
                return JSON.stringify({ success: true, typed: '$escapedText' });
            } else {
                return JSON.stringify({ success: false, error: 'Input element not found for selector: $escapedSelector' });
            }
        """.trimIndent()

        val rawResult = executeJavaScript(script)
        try {
            val json = JSONObject(rawResult)
            val isSuccess = json.optBoolean("success", false)
            ActionExecutionResult(
                isSuccess = isSuccess,
                summary = if (isSuccess) "Typed into '$selector': '${text.take(40)}...'" else json.optString("error"),
                data = json
            )
        } catch (e: Exception) {
            ActionExecutionResult(isSuccess = false, summary = "Typing failed: $rawResult")
        }
    }

    suspend fun scrollPage(direction: String, amount: Int = 500): ActionExecutionResult = withContext(Dispatchers.Main) {
        val script = when (direction.lowercase()) {
            "top" -> "window.scrollTo({ top: 0, behavior: 'smooth' }); return JSON.stringify({ scrollY: window.scrollY });"
            "bottom" -> "window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' }); return JSON.stringify({ scrollY: window.scrollY });"
            "up" -> "window.scrollBy({ top: -$amount, behavior: 'smooth' }); return JSON.stringify({ scrollY: window.scrollY });"
            else -> "window.scrollBy({ top: $amount, behavior: 'smooth' }); return JSON.stringify({ scrollY: window.scrollY });"
        }

        val rawResult = executeJavaScript(script)
        ActionExecutionResult(
            isSuccess = true,
            summary = "Scrolled $direction by $amount px."
        )
    }

    suspend fun extractPageContent(mode: String = "summary"): ActionExecutionResult = withContext(Dispatchers.Main) {
        val script = """
            var title = document.title || '';
            var url = window.location.href || '';
            
            // Extract Interactive elements (buttons, inputs, links)
            var interactives = [];
            var buttons = document.querySelectorAll('button, input[type="button"], input[type="submit"], [role="button"], a');
            for (var i = 0; i < Math.min(buttons.length, 35); i++) {
                var b = buttons[i];
                var t = (b.innerText || b.value || b.getAttribute('aria-label') || '').trim();
                var id = b.id ? '#' + b.id : '';
                var cls = b.className && typeof b.className === 'string' ? '.' + b.className.split(' ').slice(0, 2).join('.') : '';
                if (t.length > 0 && t.length < 80) {
                    interactives.push({ tag: b.tagName.toLowerCase(), selector: (id || cls || b.tagName.toLowerCase()), text: t });
                }
            }

            // Extract Input fields
            var inputs = [];
            var inputEls = document.querySelectorAll('input:not([type="hidden"]), textarea, select');
            for (var j = 0; j < Math.min(inputEls.length, 15); j++) {
                var inp = inputEls[j];
                var name = inp.name || inp.id || inp.placeholder || inp.getAttribute('aria-label') || '';
                var sel = inp.id ? '#' + inp.id : (inp.name ? 'input[name="' + inp.name + '"]' : inp.tagName.toLowerCase());
                inputs.push({ selector: sel, type: inp.type || 'text', placeholder: inp.placeholder || '', currentVal: inp.value || '' });
            }

            // Extract Headings & Visible Body Text
            var headings = Array.from(document.querySelectorAll('h1, h2, h3')).slice(0, 10).map(function(h) { return h.innerText.trim(); }).filter(function(h) { return h.length > 0; });
            var bodySample = (document.body ? document.body.innerText : '').substring(0, 2500).replace(/\s+/g, ' ').trim();

            return JSON.stringify({
                title: title,
                url: url,
                headings: headings,
                interactiveCount: buttons.length,
                interactives: interactives,
                inputs: inputs,
                bodyTextSample: bodySample
            });
        """.trimIndent()

        val rawResult = executeJavaScript(script)
        try {
            val json = JSONObject(rawResult)
            ActionExecutionResult(
                isSuccess = true,
                summary = "Read page '${json.optString("title")}' (${json.optString("url")}). Found ${json.optJSONArray("interactives")?.length() ?: 0} interactive elements and ${json.optJSONArray("inputs")?.length() ?: 0} inputs.",
                data = json
            )
        } catch (e: Exception) {
            ActionExecutionResult(isSuccess = false, summary = "Error parsing page content: $rawResult")
        }
    }

    suspend fun extractAiPrompts(categoryFilter: String? = null, maxCount: Int = 30): ActionExecutionResult = withContext(Dispatchers.Main) {
        val script = """
            var results = [];
            
            // 1. Check prompt specific containers, classes, and attributes
            var selectors = [
                '[data-prompt]', '.prompt-text', '.prompt', '.prompt-card', 'pre', 'code', 
                '.copy-prompt', '[aria-label*="prompt" i]', 'p', 'blockquote'
            ];
            
            var foundSet = new Set();

            for (var s = 0; s < selectors.length; s++) {
                var els = document.querySelectorAll(selectors[s]);
                for (var i = 0; i < els.length; i++) {
                    var el = els[i];
                    var text = (el.getAttribute('data-prompt') || el.innerText || '').trim();
                    
                    // Filter prompts: usually > 20 chars, contains descriptive commas or keywords like 8k, lighting, cinematic, photorealistic, etc.
                    if (text.length >= 25 && text.length <= 1200) {
                        var isLikelyPrompt = text.includes(',') || 
                                              text.match(/(cinematic|photorealistic|hyperrealistic|8k|4k|unreal engine|octane render|portrait|lighting|cyberpunk|anime|illustration|masterpiece|ultra detailed|vray)/i);
                        
                        if (isLikelyPrompt && !foundSet.has(text)) {
                            foundSet.add(text);
                            
                            // Categorize prompt
                            var cat = 'General';
                            if (text.match(/cinematic|movie|film|dramatic lighting/i)) cat = 'Cinematic';
                            else if (text.match(/photo|portrait|photography|8k resolution|dslr/i)) cat = 'Photorealistic';
                            else if (text.match(/anime|manga|studio ghibli|makoto shinkai/i)) cat = 'Anime';
                            else if (text.match(/cyberpunk|futuristic|neon|sci-fi/i)) cat = 'Sci-Fi';
                            else if (text.match(/3d render|octane|unreal engine|blender/i)) cat = '3D Render';

                            results.push({
                                prompt: text,
                                category: cat,
                                length: text.length
                            });
                            
                            if (results.length >= $maxCount) break;
                        }
                    }
                }
                if (results.length >= $maxCount) break;
            }

            return JSON.stringify({
                count: results.length,
                url: window.location.href,
                prompts: results
            });
        """.trimIndent()

        val rawResult = executeJavaScript(script)
        try {
            val json = JSONObject(rawResult)
            val count = json.optInt("count", 0)
            ActionExecutionResult(
                isSuccess = true,
                summary = "Extracted $count AI image prompts from the webpage.",
                data = json
            )
        } catch (e: Exception) {
            ActionExecutionResult(isSuccess = false, summary = "Failed to extract prompts: $rawResult")
        }
    }

    suspend fun captureScreenshot(): Bitmap? = withContext(Dispatchers.Main) {
        val webView = getActiveWebView() ?: return@withContext null
        try {
            val width = webView.width.coerceAtLeast(1)
            val height = webView.height.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            webView.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    suspend fun selectDropdown(selector: String, value: String): ActionExecutionResult = withContext(Dispatchers.Main) {
        val escapedSelector = selector.replace("'", "\\'")
        val escapedValue = value.replace("'", "\\'")

        val script = """
            var select = document.querySelector('$escapedSelector');
            if (select && select.tagName === 'SELECT') {
                var found = false;
                for (var i = 0; i < select.options.length; i++) {
                    var opt = select.options[i];
                    if (opt.value === '$escapedValue' || opt.text.toLowerCase().includes('$escapedValue'.toLowerCase())) {
                        select.selectedIndex = i;
                        found = true;
                        break;
                    }
                }
                select.dispatchEvent(new Event('change', { bubbles: true }));
                return JSON.stringify({ success: found, selected: select.value });
            } else {
                return JSON.stringify({ success: false, error: 'Select element not found: $escapedSelector' });
            }
        """.trimIndent()

        val rawResult = executeJavaScript(script)
        ActionExecutionResult(
            isSuccess = rawResult.contains("\"success\":true"),
            summary = "Selected option '$value' in $selector"
        )
    }
}
