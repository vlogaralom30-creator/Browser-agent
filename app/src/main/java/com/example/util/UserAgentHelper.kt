package com.example.util

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Helper utility to manage and switch WebView User-Agent strings and viewport
 * settings between mobile mode and standard desktop Chrome mode.
 */
object UserAgentHelper {

    // Standard Desktop Chrome User-Agent string (Windows x64 Chrome)
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    /**
     * Returns the desktop Chrome user-agent string.
     */
    fun getDesktopUserAgent(): String = DESKTOP_USER_AGENT

    /**
     * Returns the system default mobile user-agent string.
     */
    fun getMobileUserAgent(context: Context): String = WebSettings.getDefaultUserAgent(context)

    /**
     * Returns the appropriate user-agent string based on desktop mode status.
     */
    fun getUserAgent(context: Context, isDesktopMode: Boolean): String {
        return if (isDesktopMode) {
            DESKTOP_USER_AGENT
        } else {
            getMobileUserAgent(context)
        }
    }

    /**
     * Switches the WebView's user agent and viewport between mobile and standard desktop Chrome.
     */
    fun switchUserAgent(webView: WebView, isDesktopMode: Boolean) {
        val targetUserAgent = getUserAgent(webView.context, isDesktopMode)
        webView.settings.userAgentString = targetUserAgent
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = isDesktopMode
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
    }
}
