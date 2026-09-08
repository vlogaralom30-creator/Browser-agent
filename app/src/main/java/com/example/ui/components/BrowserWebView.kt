package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
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
import com.example.ui.BrowserViewModel
import com.example.util.DownloadHandler

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

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

    // Retain and configure WebView per tab ID
    val webView = remember(tab.id) {
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
                mediaPlaybackRequiresUserGesture = false
            }

            if (tab.isIncognito) {
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                clearHistory()
                clearFormData()
                clearCache(true)
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
        webView.settings.apply {
            javaScriptEnabled = isJavaScriptEnabled
            if (tab.isDesktopMode) {
                userAgentString = DESKTOP_USER_AGENT
                useWideViewPort = true
                loadWithOverviewMode = true
            } else {
                userAgentString = null // Reset to default mobile user agent
                useWideViewPort = true
                loadWithOverviewMode = true
            }
        }
    }

    // Set clients
    LaunchedEffect(webView, tab.id) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:") || url.startsWith("file:")) {
                    return false
                }
                // Handle external apps (e.g. mailto, tel, market)
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    return true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url != null) {
                    viewModel.onPageStarted(tab.id, url)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null) {
                    val canBack = view?.canGoBack() ?: false
                    val canForward = view?.canGoForward() ?: false
                    val title = view?.title
                    viewModel.onPageFinished(tab.id, url, title, canBack, canForward)
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
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

    // Manage WebView lifecycle to prevent image buffer exhaustion and register with agent
    DisposableEffect(tab.id) {
        viewModel.registerWebView(tab.id, webView)
        webView.onResume()
        onDispose {
            viewModel.unregisterWebView(tab.id)
            webView.onPause()
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            if (tab.isIncognito) {
                webView.clearCache(true)
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
            // Ensure onResume when active
            it.onResume()
        },
        modifier = modifier.fillMaxSize()
    )
}
