package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.webkit.CookieManager

data class YouTubeLoginStatus(
    val isWebLoggedIn: Boolean,
    val isAppInstalled: Boolean,
    val isStudioInstalled: Boolean
) {
    val canUpload: Boolean
        get() = isWebLoggedIn || isAppInstalled || isStudioInstalled
}

object YouTubeAuthHelper {

    private const val YT_DOMAIN = "https://youtube.com"
    private const val M_YT_DOMAIN = "https://m.youtube.com"
    private const val STUDIO_YT_DOMAIN = "https://studio.youtube.com"
    private const val GOOGLE_DOMAIN = "https://accounts.google.com"

    fun checkStatus(context: Context): YouTubeLoginStatus {
        val webLoggedIn = isYouTubeLoggedIn()
        val appInstalled = isAppInstalled(context, "com.google.android.youtube")
        val studioInstalled = isAppInstalled(context, "com.google.android.apps.youtube.creator")

        return YouTubeLoginStatus(
            isWebLoggedIn = webLoggedIn,
            isAppInstalled = appInstalled,
            isStudioInstalled = studioInstalled
        )
    }

    fun isYouTubeLoggedIn(): Boolean {
        return try {
            val cookieManager = CookieManager.getInstance()
            val cookies = listOfNotNull(
                cookieManager.getCookie(YT_DOMAIN),
                cookieManager.getCookie(M_YT_DOMAIN),
                cookieManager.getCookie(STUDIO_YT_DOMAIN),
                cookieManager.getCookie(GOOGLE_DOMAIN)
            ).joinToString(";")

            // Google/YouTube authentication cookies
            cookies.contains("LOGIN_INFO=") ||
            cookies.contains("SAPISID=") ||
            cookies.contains("APISID=") ||
            cookies.contains("HSID=") ||
            (cookies.contains("SID=") && cookies.contains("SSID="))
        } catch (e: Exception) {
            false
        }
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
