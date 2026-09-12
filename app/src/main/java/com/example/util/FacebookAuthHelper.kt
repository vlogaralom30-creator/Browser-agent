package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.webkit.CookieManager

data class FacebookLoginStatus(
    val isWebLoggedIn: Boolean,
    val userId: String?,
    val isAppInstalled: Boolean,
    val isBusinessSuiteInstalled: Boolean
) {
    val canUpload: Boolean
        get() = isWebLoggedIn || isAppInstalled || isBusinessSuiteInstalled
}

object FacebookAuthHelper {

    private const val FB_DOMAIN = "https://facebook.com"
    private const val M_FB_DOMAIN = "https://m.facebook.com"
    private const val MBASIC_FB_DOMAIN = "https://mbasic.facebook.com"

    fun checkStatus(context: Context): FacebookLoginStatus {
        val webLoggedIn = isFacebookLoggedIn()
        val userId = getFacebookUserId()
        val appInstalled = isAppInstalled(context, "com.facebook.katana") || isAppInstalled(context, "com.facebook.lite")
        val suiteInstalled = isAppInstalled(context, "com.facebook.pages.app") || isAppInstalled(context, "com.facebook.creatorstudio")

        return FacebookLoginStatus(
            isWebLoggedIn = webLoggedIn,
            userId = userId,
            isAppInstalled = appInstalled,
            isBusinessSuiteInstalled = suiteInstalled
        )
    }

    fun isFacebookLoggedIn(): Boolean {
        return try {
            val cookieManager = CookieManager.getInstance()
            val cookies = listOfNotNull(
                cookieManager.getCookie(FB_DOMAIN),
                cookieManager.getCookie(M_FB_DOMAIN),
                cookieManager.getCookie(MBASIC_FB_DOMAIN)
            ).joinToString(";")

            // "c_user" indicates authenticated Facebook user ID cookie
            // "xs" is the session token cookie
            cookies.contains("c_user=") || (cookies.contains("xs=") && cookies.contains("datr="))
        } catch (e: Exception) {
            false
        }
    }

    fun getFacebookUserId(): String? {
        return try {
            val cookieManager = CookieManager.getInstance()
            val cookies = listOfNotNull(
                cookieManager.getCookie(FB_DOMAIN),
                cookieManager.getCookie(M_FB_DOMAIN)
            ).joinToString(";")

            val match = Regex("c_user=([0-9]+)").find(cookies)
            match?.groupValues?.getOrNull(1)
        } catch (e: Exception) {
            null
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
