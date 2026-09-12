package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.CrawlBotState
import com.example.model.CrawlBotStatus
import com.example.model.YoutubeInteractionItem
import com.example.util.SiteCrawlerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Browser", appName)
    }

    @Test
    fun `test youtube bot state initialization`() {
        val state = CrawlBotState()
        assertEquals("youtube", state.botMode)
        assertEquals(CrawlBotStatus.IDLE, state.status)
        assertEquals("", state.ytSearchQuery)
        assertTrue(state.ytHistory.isEmpty())
        assertTrue(state.ytSessionLogs.isEmpty())
    }

    @Test
    fun `test youtube history item creation`() {
        val item = YoutubeInteractionItem(
            query = "retro synthwave",
            videoTitle = "Lofi Beats to Code/Relax",
            videoUrl = "https://youtube.com/watch?v=123",
            viewCountText = "2.5M views",
            timestamp = System.currentTimeMillis(),
            isLiked = true,
            isCommented = true,
            commentText = "Absolute masterpiece!"
        )

        assertEquals("retro synthwave", item.query)
        assertEquals("Lofi Beats to Code/Relax", item.videoTitle)
        assertEquals("https://youtube.com/watch?v=123", item.videoUrl)
        assertEquals("2.5M views", item.viewCountText)
        assertTrue(item.isLiked)
        assertTrue(item.isCommented)
        assertEquals("Absolute masterpiece!", item.commentText)
    }

    @Test
    fun `test site crawler engine instance`() {
        val engine = SiteCrawlerEngine()
        val state = engine.botState.value
        assertNotNull(state)
        assertEquals(CrawlBotStatus.IDLE, state.status)
    }
}
