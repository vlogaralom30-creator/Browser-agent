package com.example

import com.example.util.UrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPrivateProtectionTest {

    private val privateDomains = setOf(
        "pornhub.com",
        "xvideos.com",
        "onlyfans.com",
        "customadultdomain.net"
    )

    @Test
    fun testCanonicalHostExtraction() {
        assertEquals("pornhub.com", UrlUtils.extractCanonicalHost("https://www.pornhub.com/video/12345"))
        assertEquals("pornhub.com", UrlUtils.extractCanonicalHost("HTTP://PORNHUB.COM/"))
        assertEquals("m.pornhub.com", UrlUtils.extractCanonicalHost("https://m.pornhub.com:8080/test?q=1"))
        assertEquals("onlyfans.com", UrlUtils.extractCanonicalHost("onlyfans.com"))
        assertEquals("onlyfans.com", UrlUtils.extractCanonicalHost("www.onlyfans.com"))
        assertEquals("en.wikipedia.org", UrlUtils.extractCanonicalHost("https://en.wikipedia.org/wiki/Main_Page"))
        assertEquals("wikipedia.org", UrlUtils.extractCanonicalHost("https://wikipedia.org/wiki/Main_Page"))
    }

    @Test
    fun testSmartPrivateDomainMatching() {
        // Direct matching
        assertTrue(UrlUtils.matchesPrivateDomain("https://www.pornhub.com", privateDomains))
        assertTrue(UrlUtils.matchesPrivateDomain("https://pornhub.com/page", privateDomains))
        assertTrue(UrlUtils.matchesPrivateDomain("http://onlyfans.com/creator", privateDomains))

        // Subdomain matching
        assertTrue(UrlUtils.matchesPrivateDomain("https://mobile.xvideos.com/video", privateDomains))
        assertTrue(UrlUtils.matchesPrivateDomain("https://static.customadultdomain.net/img.jpg", privateDomains))

        // Normal everyday sites MUST NOT match
        assertFalse(UrlUtils.matchesPrivateDomain("https://www.google.com", privateDomains))
        assertFalse(UrlUtils.matchesPrivateDomain("https://github.com", privateDomains))
        assertFalse(UrlUtils.matchesPrivateDomain("https://wikipedia.org", privateDomains))
        assertFalse(UrlUtils.matchesPrivateDomain("https://notpornhub.com", privateDomains))
        assertFalse(UrlUtils.matchesPrivateDomain("https://fakeonlyfans.com.org", privateDomains))
        assertFalse(UrlUtils.matchesPrivateDomain("about:blank", privateDomains))
    }

    @Test
    fun testEmptyOrInvalidInputs() {
        assertFalse(UrlUtils.matchesPrivateDomain("", privateDomains))
        assertFalse(UrlUtils.matchesPrivateDomain("   ", privateDomains))
        assertFalse(UrlUtils.matchesPrivateDomain("https://google.com", emptySet()))
    }
}
