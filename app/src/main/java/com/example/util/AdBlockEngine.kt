package com.example.util

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlockEngine {

    // Known ad-serving, tracking, analytics, pop-up, and telemetric domains/subdomains
    private val AD_HOST_SUFFIXES = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "google-analytics.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "adnxs.com",
        "taboola.com",
        "taboolasyndication.com",
        "outbrain.com",
        "outbrainimg.com",
        "criteo.com",
        "criteo.net",
        "popads.net",
        "popcash.net",
        "rubiconproject.com",
        "amazon-adsystem.com",
        "adcolony.com",
        "applovin.com",
        "chartbeat.com",
        "scorecardresearch.com",
        "hotjar.com",
        "moatads.com",
        "adtech.de",
        "advertising.com",
        "openx.net",
        "pubmatic.com",
        "indexww.com",
        "casalemedia.com",
        "smartadserver.com",
        "yieldmo.com",
        "triplelift.com",
        "quantserve.com",
        "quantcount.com",
        "exoclick.com",
        "juicyads.com",
        "propellerads.com",
        "revcontent.com",
        "mediavine.com",
        "adthrive.com",
        "ezoic.com",
        "mixpanel.com",
        "segment.io",
        "amplitude.com",
        "branch.io",
        "adjust.com",
        "appsflyer.com",
        "flurry.com",
        "crashlytics.com",
        "admob.com",
        "mopub.com",
        "inmobi.com",
        "unity3d.com/ads",
        "unityads.unity3d.com",
        "ironsrc.com",
        "vungle.com",
        "tapjoy.com",
        "fyber.com",
        "adblade.com",
        "bidswitch.net",
        "contextweb.com",
        "media.net",
        "sovrn.com"
    )

    private val AD_EXACT_HOSTS = setOf(
        "ads.google.com",
        "adservice.google.com",
        "analytics.google.com",
        "pixel.facebook.com",
        "an.facebook.com",
        "ads-api.twitter.com",
        "analytics.twitter.com",
        "ads.linkedin.com",
        "analytics.tiktok.com",
        "ads.pinterest.com",
        "ads.reddit.com"
    )

    // Common URL path patterns for ad frames, tracking pixels, and scripts
    private val AD_PATH_PATTERNS = listOf(
        "/pagead/",
        "/adserv",
        "/adsystem",
        "/ads.js",
        "/banner.js",
        "/popunder",
        "/popup.js",
        "/telemetry/",
        "/analytics.js",
        "/pixel.gif",
        "/tracking.js",
        "/fbevents.js",
        "/gtm.js?id=gtm-",
        "/beacon.js"
    )

    fun isAdOrTracker(
        urlStr: String,
        siteHost: String? = null,
        whitelist: Set<String> = emptySet()
    ): Boolean {
        if (urlStr.isBlank() || urlStr.startsWith("data:") || urlStr.startsWith("blob:") || urlStr.startsWith("about:") || urlStr.startsWith("file:")) {
            return false
        }

        val uri = try {
            Uri.parse(urlStr)
        } catch (e: Exception) {
            return false
        }

        val host = uri.host?.lowercase() ?: return false

        // Check if the current website host is in user whitelist
        if (siteHost != null) {
            val cleanSiteHost = siteHost.lowercase().removePrefix("www.")
            if (whitelist.any { cleanSiteHost == it.lowercase().removePrefix("www.") || cleanSiteHost.endsWith("." + it.lowercase().removePrefix("www.")) }) {
                return false
            }
        }

        // Exact host match
        if (AD_EXACT_HOSTS.contains(host)) {
            return true
        }

        // Host suffix / subdomain match
        if (AD_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }) {
            return true
        }

        // Path pattern match
        val path = uri.path?.lowercase() ?: ""
        if (path.isNotBlank() && AD_PATH_PATTERNS.any { path.contains(it) }) {
            return true
        }

        // Specific query parameters or Facebook pixel scripts
        if (urlStr.contains("connect.facebook.net/en_US/fbevents.js") || urlStr.contains("facebook.net/tr")) {
            return true
        }

        return false
    }

    /**
     * Returns a zero-latency empty response (200 OK) to cleanly block ad subresources
     * without causing network connection error timeouts or broken page layout frames.
     */
    fun createEmptyResponse(): WebResourceResponse {
        val headers = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
            "Cache-Control" to "no-store, no-cache, must-revalidate"
        )
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            200,
            "OK",
            headers,
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
