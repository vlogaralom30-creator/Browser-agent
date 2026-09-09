package com.example.model

enum class PageErrorCategory(
    val title: String,
    val defaultDescription: String,
    val troubleshootingTip: String
) {
    DNS_FAILURE(
        title = "Server Not Found (DNS Failure)",
        defaultDescription = "The web address could not be resolved. The domain name may be misspelled, the server may no longer exist, or DNS resolution failed.",
        troubleshootingTip = "Check domain spelling, verify your Wi-Fi or cellular data, or check your device's Private DNS settings."
    ),
    CONNECTION_REFUSED_OR_BLOCKED(
        title = "Connection Refused or Blocked",
        defaultDescription = "The connection could not be established. The remote server may be offline, rejecting connections, or the network/ISP may be blocking access.",
        troubleshootingTip = "The website might be down, or this network may have firewall restrictions. Verify your network connection."
    ),
    TIMEOUT(
        title = "Connection Timed Out",
        defaultDescription = "The server took too long to respond. The website might be overloaded or experiencing network congestion.",
        troubleshootingTip = "Wait a moment and try refreshing, or check if your internet connection is running slowly."
    ),
    HTTP_RESTRICTED(
        title = "Access Restricted by Server or Policy",
        defaultDescription = "The server refused access (HTTP 403 or HTTP 451). This page may require authentication, or may be restricted by geographic, legal, or network administrative policies.",
        troubleshootingTip = "Administrative, ISP, or legal network restrictions cannot be bypassed. Verify that you have authorization to view this resource."
    ),
    HTTP_SERVER_ERROR(
        title = "Server Error",
        defaultDescription = "The website's server encountered an internal error or gateway misconfiguration (HTTP 500/502/503/504).",
        troubleshootingTip = "This is an issue on the website's servers. Please try again later."
    ),
    SSL_SECURITY(
        title = "Security Certificate Warning",
        defaultDescription = "The connection is not secure because the site's SSL certificate is invalid, expired, or self-signed. Connection aborted to protect your privacy.",
        troubleshootingTip = "To protect your passwords and private data, Naxxivo does not bypass invalid SSL certificates. Check your device date and time."
    ),
    OFFLINE(
        title = "No Internet Connection",
        defaultDescription = "Your device appears to be offline. Web pages cannot be loaded without an active network connection.",
        troubleshootingTip = "Turn off Airplane Mode or connect to Wi-Fi or cellular mobile data."
    ),
    GENERIC(
        title = "Unable to Load Page",
        defaultDescription = "An unexpected network or rendering issue prevented the page from loading.",
        troubleshootingTip = "Tap Try Again or return to the Home page."
    )
}
