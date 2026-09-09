package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.BrowserTab
import com.example.model.ContextMenuData
import com.example.model.WebPermissionRequest
import com.example.ui.BrowserViewModel
import com.example.util.DownloadHandler
import com.example.util.UserAgentHelper

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    tab: BrowserTab,
    viewModel: BrowserViewModel,
    isJavaScriptEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // File chooser callback holder
    var filePathCallback: ValueCallback<Array<Uri>>? = remember { null }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            filePathCallback?.onReceiveValue(uris.toTypedArray())
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    // Retain and configure WebView per tab ID and render revision
    val webView = remember(tab.id, tab.renderRevision) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Let system handle layer rendering without forcing redundant buffer queues
            setLayerType(View.LAYER_TYPE_NONE, null)

            settings.apply {
                javaScriptEnabled = isJavaScriptEnabled
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                allowFileAccess = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = true
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = true
                setGeolocationEnabled(true)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }
            }

            // Apply standard desktop Chrome or mobile User-Agent
            UserAgentHelper.switchUserAgent(this, tab.isDesktopMode)

            if (tab.isIncognito) {
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                clearHistory()
                clearFormData()
            }

            // Long-press Context Menu for Links, Images, and Image-Links
            setOnLongClickListener {
                val hit = hitTestResult
                when (hit.type) {
                    WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                        val linkUrl = hit.extra
                        if (!linkUrl.isNullOrBlank()) {
                            val msg = android.os.Message.obtain()
                            msg.target = object : android.os.Handler(android.os.Looper.getMainLooper()) {
                                override fun handleMessage(m: android.os.Message) {
                                    val url = m.data.getString("url") ?: linkUrl
                                    val title = m.data.getString("title")
                                    viewModel.showContextMenu(
                                        ContextMenuData.Link(
                                            url = url,
                                            title = title,
                                            linkText = title
                                        )
                                    )
                                }
                            }
                            requestFocusNodeHref(msg)
                            true
                        } else false
                    }
                    WebView.HitTestResult.IMAGE_TYPE -> {
                        val imageUrl = hit.extra
                        if (!imageUrl.isNullOrBlank()) {
                            viewModel.showContextMenu(ContextMenuData.Image(imageUrl = imageUrl))
                            true
                        } else false
                    }
                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                        val linkUrl = hit.extra
                        val msg = android.os.Message.obtain()
                        msg.target = object : android.os.Handler(android.os.Looper.getMainLooper()) {
                            override fun handleMessage(m: android.os.Message) {
                                val url = m.data.getString("url") ?: linkUrl ?: ""
                                val src = m.data.getString("src") ?: linkUrl ?: ""
                                val title = m.data.getString("title")
                                viewModel.showContextMenu(
                                    ContextMenuData.ImageLink(
                                        linkUrl = url,
                                        imageUrl = src,
                                        title = title,
                                        linkText = title
                                    )
                                )
                            }
                        }
                        requestFocusNodeHref(msg)
                        true
                    }
                    else -> false
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                DownloadHandler.startDownload(
                    context = context,
                    url = url,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition,
                    mimeType = mimetype,
                    contentLength = contentLength,
                    onDownloadStarted = { entity ->
                        viewModel.onDownloadStarted(entity)
                    }
                )
            }

            setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                if (isDoneCounting) {
                    viewModel.onFindResult(activeMatchOrdinal, numberOfMatches)
                }
            }
        }
    }

    // Update settings when tab or state changes
    LaunchedEffect(tab.isDesktopMode, isJavaScriptEnabled) {
        webView.settings.javaScriptEnabled = isJavaScriptEnabled
        val targetUserAgent = UserAgentHelper.getUserAgent(context, tab.isDesktopMode)
        if (webView.settings.userAgentString != targetUserAgent) {
            UserAgentHelper.switchUserAgent(webView, tab.isDesktopMode)
            if (webView.url != null && webView.url != "about:blank") {
                webView.reload()
            }
        }
    }

    // Set clients
    LaunchedEffect(webView, tab.id) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // Smart Private Site Protection: Intercept sensitive sites in normal tabs
                if (!tab.isIncognito && viewModel.isSmartPrivateUrl(url)) {
                    view?.stopLoading()
                    viewModel.handleSmartPrivateRouting(url, isFromCurrentBlankTab = false)
                    return true
                }

                if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:") || url.startsWith("file:") || url.startsWith("javascript:")) {
                    return false
                }
                // Handle external apps safely (e.g. mailto, tel, market, intent)
                try {
                    val intent = if (url.startsWith("intent:")) {
                        Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    } else {
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    }
                    intent.addCategory(Intent.CATEGORY_BROWSABLE)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    // Try fallback URL if available
                    try {
                        if (url.startsWith("intent:")) {
                            val parsed = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                            val fallbackUrl = parsed.getStringExtra("browser_fallback_url")
                            if (!fallbackUrl.isNullOrBlank()) {
                                view?.loadUrl(fallbackUrl)
                                return true
                            }
                        }
                    } catch (ignored: Exception) {}
                    return true
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                val canBack = view?.canGoBack() ?: false
                val canForward = view?.canGoForward() ?: false
                viewModel.updateTabHistoryState(tab.id, canBack, canForward, url ?: view?.url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                viewModel.clearPageError(tab.id)
                if (url != null) {
                    val canBack = view?.canGoBack() ?: false
                    val canForward = view?.canGoForward() ?: false
                    viewModel.onPageStarted(tab.id, url)
                    viewModel.updateTabHistoryState(tab.id, canBack, canForward, url)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null) {
                    val canBack = view?.canGoBack() ?: false
                    val canForward = view?.canGoForward() ?: false
                    val title = view?.title
                    viewModel.onPageFinished(tab.id, url, title, canBack, canForward)

                    // Restore video playback position if available
                    val resumeSeconds = tab.videoPlaybackSeconds
                    if (resumeSeconds != null && resumeSeconds > 2f) {
                        view?.evaluateJavascript(
                            "(function() { try { const v = document.querySelector('video'); if (v && v.readyState >= 1 && !isNaN(v.duration)) { v.currentTime = $resumeSeconds; } } catch(e) {} })();",
                            null
                        )
                    }
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    val code = error?.errorCode ?: -1
                    val desc = error?.description?.toString() ?: "Could not load web page"
                    val category = when (code) {
                        WebViewClient.ERROR_HOST_LOOKUP -> com.example.model.PageErrorCategory.DNS_FAILURE
                        WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_IO -> com.example.model.PageErrorCategory.CONNECTION_REFUSED_OR_BLOCKED
                        WebViewClient.ERROR_TIMEOUT -> com.example.model.PageErrorCategory.TIMEOUT
                        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> com.example.model.PageErrorCategory.SSL_SECURITY
                        else -> com.example.model.PageErrorCategory.GENERIC
                    }
                    viewModel.onPageError(
                        tabId = tab.id,
                        errorCode = code,
                        description = desc,
                        failedUrl = request.url.toString(),
                        category = category
                    )
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true && errorResponse != null) {
                    val statusCode = errorResponse.statusCode
                    if (statusCode in 400..599) {
                        val category = when (statusCode) {
                            403, 451 -> com.example.model.PageErrorCategory.HTTP_RESTRICTED
                            in 500..599 -> com.example.model.PageErrorCategory.HTTP_SERVER_ERROR
                            else -> com.example.model.PageErrorCategory.GENERIC
                        }
                        val reason = when (statusCode) {
                            403 -> "HTTP 403 Forbidden: Access to this page is refused by the website server or hosting network."
                            451 -> "HTTP 451 Unavailable For Legal Reasons: Access is restricted by administrative or regulatory policies."
                            502 -> "HTTP 502 Bad Gateway: The upstream server failed to respond."
                            503 -> "HTTP 503 Service Unavailable: The server is temporarily overloaded or undergoing maintenance."
                            504 -> "HTTP 504 Gateway Timeout: The gateway timed out waiting for the server."
                            else -> "HTTP $statusCode error occurred while fetching the requested webpage."
                        }
                        viewModel.onPageError(
                            tabId = tab.id,
                            errorCode = statusCode,
                            description = reason,
                            failedUrl = request.url.toString(),
                            category = category
                        )
                    }
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                // Never bypass SSL silently: block connection and report security warning to user
                handler?.cancel()
                val reason = when (error?.primaryError) {
                    SslError.SSL_EXPIRED -> "The security certificate has expired."
                    SslError.SSL_IDMISMATCH -> "The security certificate hostname does not match."
                    SslError.SSL_NOTYETVALID -> "The security certificate is not yet valid."
                    SslError.SSL_UNTRUSTED -> "The security certificate authority is untrusted."
                    else -> "The security certificate is invalid."
                }
                viewModel.onPageError(
                    tabId = tab.id,
                    errorCode = error?.primaryError ?: -1,
                    description = "$reason For your privacy and security, this connection was aborted.",
                    failedUrl = view?.url,
                    isSsl = true
                )
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                val didCrash = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    detail?.didCrash() ?: false
                } else {
                    false
                }
                Log.w("BrowserWebView", "WebView render process exited: didCrash=$didCrash")
                (view?.parent as? ViewGroup)?.removeView(view)
                view?.post {
                    try {
                        view.destroy()
                    } catch (ignored: Exception) {}
                }
                viewModel.onRenderProcessCrash(tab.id, tab.url)
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                viewModel.onProgressChanged(tab.id, newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (title != null) {
                    viewModel.onReceivedTitle(tab.id, title)
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallbackParam: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = filePathCallbackParam
                try {
                    val mimeTypes = fileChooserParams?.acceptTypes ?: arrayOf("*/*")
                    fileChooserLauncher.launch(if (mimeTypes.isNotEmpty() && mimeTypes[0].isNotBlank()) mimeTypes else arrayOf("*/*"))
                    return true
                } catch (e: Exception) {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    return false
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view != null && callback != null) {
                    viewModel.showCustomView(view, callback)
                }
            }

            override fun onHideCustomView() {
                viewModel.hideCustomView()
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val hitTest = view?.hitTestResult
                val popupUrl = hitTest?.extra
                if (!popupUrl.isNullOrBlank()) {
                    viewModel.loadUrl(popupUrl)
                    return true
                }
                return false
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val originStr = request.origin.toString()
                val resources = request.resources.toList()
                viewModel.requestWebPermission(
                    WebPermissionRequest.DeviceResource(
                        origin = originStr,
                        resources = resources,
                        onGrant = {
                            request.grant(request.resources)
                            viewModel.dismissWebPermission()
                        },
                        onDeny = {
                            request.deny()
                            viewModel.dismissWebPermission()
                        }
                    )
                )
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (origin == null || callback == null) return
                viewModel.requestWebPermission(
                    WebPermissionRequest.Geolocation(
                        origin = origin,
                        onGrant = {
                            callback.invoke(origin, true, false)
                            viewModel.dismissWebPermission()
                        },
                        onDeny = {
                            callback.invoke(origin, false, false)
                            viewModel.dismissWebPermission()
                        }
                    )
                )
            }
        }
    }

    // Initial load if tab has URL and hasn't loaded yet
    LaunchedEffect(tab.id, tab.url) {
        if (tab.url.isNotBlank() && tab.url != "about:blank" && (webView.url == null || webView.url == "about:blank")) {
            webView.loadUrl(tab.url)
        }
    }

    // Handle commands from ViewModel
    val action = viewModel.webAction
    LaunchedEffect(action.value) {
        val currentAction = action.value ?: return@LaunchedEffect
        when (currentAction) {
            is BrowserViewModel.WebAction.LoadUrl -> {
                if (currentAction.tabId == tab.id) {
                    webView.loadUrl(currentAction.url)
                    viewModel.resetWebAction()
                }
            }
            is BrowserViewModel.WebAction.GoBack -> {
                if (currentAction.tabId == tab.id && webView.canGoBack()) {
                    webView.goBack()
                    viewModel.resetWebAction()
                }
            }
            is BrowserViewModel.WebAction.GoForward -> {
                if (currentAction.tabId == tab.id && webView.canGoForward()) {
                    webView.goForward()
                    viewModel.resetWebAction()
                }
            }
            is BrowserViewModel.WebAction.Reload -> {
                if (currentAction.tabId == tab.id) {
                    webView.reload()
                    viewModel.resetWebAction()
                }
            }
            is BrowserViewModel.WebAction.StopLoading -> {
                if (currentAction.tabId == tab.id) {
                    webView.stopLoading()
                    viewModel.resetWebAction()
                }
            }
            is BrowserViewModel.WebAction.FindInPage -> {
                webView.findAllAsync(currentAction.query)
                viewModel.resetWebAction()
            }
            is BrowserViewModel.WebAction.ClearFindInPage -> {
                if (currentAction.tabId == tab.id) {
                    webView.clearMatches()
                    viewModel.resetWebAction()
                }
            }
        }
    }

    // Manage WebView lifecycle to prevent buffer/codec exhaustion and register with agent
    DisposableEffect(tab.id, tab.renderRevision) {
        viewModel.registerWebView(tab.id, webView)
        webView.onResume()
        onDispose {
            viewModel.unregisterWebView(tab.id)
            // Pause any media playback immediately to release MediaCodec decoder instances
            try {
                webView.evaluateJavascript(
                    "try { document.querySelectorAll('video, audio').forEach(function(m) { m.pause(); }); } catch(e) {}",
                    null
                )
            } catch (ignored: Exception) {}
            webView.onPause()
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            if (tab.isIncognito) {
                webView.clearHistory()
                webView.clearFormData()
            }
        }
    }

    AndroidView(
        factory = {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.onResume()
            webView
        },
        update = {
            it.onResume()
        },
        modifier = modifier.fillMaxSize()
    )
}
