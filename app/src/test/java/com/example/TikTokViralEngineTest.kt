package com.example

import com.example.util.TitleSpinnerEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokViralEngineTest {

    @Test
    fun cleanOriginalTitle_removesSpamAndHandles() {
        val raw = "Amazing cat playing with box! @coolcat #fyp #viral https://tiktok.com/123 part 1"
        val cleaned = TitleSpinnerEngine.cleanOriginalTitle(raw)

        assertFalse(cleaned.contains("@coolcat"))
        assertFalse(cleaned.contains("#fyp"))
        assertFalse(cleaned.contains("https"))
        assertTrue(cleaned.contains("Amazing cat"))
    }

    @Test
    fun generateSpunTitle_producesViralHookAndAppendsCustomHashtags() {
        val originalTitle = "Cat playing with robot vacuum #cat #funny"
        val customHashtags = "#reelsviral #trendingreels #fyp"
        val spun = TitleSpinnerEngine.generateSpunTitle(originalTitle, customHashtags)

        // Verifies hashtags are appended
        assertTrue(spun.contains("#reelsviral"))
        assertTrue(spun.contains("#trendingreels"))
        assertTrue(spun.contains("#fyp"))

        // Verifies core title words are kept
        assertTrue(spun.contains("Cat") || spun.contains("robot"))
    }
}
