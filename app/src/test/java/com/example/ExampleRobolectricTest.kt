package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.CrawlBotState
import com.example.model.CrawlBotStatus
import com.example.model.VideoInteractionItem
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
    fun `test video bot state initialization`() {
        val state = CrawlBotState()
        assertEquals(CrawlBotStatus.IDLE, state.status)
        assertEquals("", state.videoSearchQuery)
        assertTrue(state.videoHistory.isEmpty())
        assertTrue(state.videoSessionLogs.isEmpty())
    }

    @Test
    fun `test video history item creation`() {
        val item = VideoInteractionItem(
            query = "retro synthwave",
            selectedVideoTitle = "Lofi Beats to Code/Relax",
            channel = "Lofi Girl",
            viewsText = "2.5M views",
            uploadDateText = "3 days ago",
            videoUrl = "https://youtube.com/watch?v=123",
            timestamp = System.currentTimeMillis(),
            actionSearched = true,
            actionOpened = true,
            actionLiked = true,
            actionCopied = true,
            actionCommented = true,
            actionResult = "Successfully liked and commented"
        )

        assertEquals("retro synthwave", item.query)
        assertEquals("Lofi Beats to Code/Relax", item.selectedVideoTitle)
        assertEquals("https://youtube.com/watch?v=123", item.videoUrl)
        assertEquals("2.5M views", item.viewsText)
        assertTrue(item.actionLiked)
        assertTrue(item.actionCommented)
        assertEquals("Successfully liked and commented", item.actionResult)
    }

    @Test
    fun `test tiktok reel item creation and defaults`() {
        val reel = com.example.model.TikTokReelItem(
            id = "reel_123",
            originalTitle = "Crazy Cat Jump",
            spunTitle = "Wait till the end! Crazy Cat Jump #viral",
            author = "catlover",
            viewsText = "1.5M",
            viewsCount = 1500000L,
            localFilePath = "/storage/emulated/0/Download/naxxivo_reel_123.mp4",
            fileName = "naxxivo_reel_123.mp4"
        )

        assertEquals("reel_123", reel.id)
        assertEquals("Crazy Cat Jump", reel.originalTitle)
        assertEquals(1500000L, reel.viewsCount)
        assertTrue(reel.isDownloaded)
        assertEquals("catlover", reel.author)
    }

    @Test
    fun `test site crawler engine instance`() {
        val engine = SiteCrawlerEngine()
        val state = engine.botState.value
        assertNotNull(state)
        assertEquals(CrawlBotStatus.IDLE, state.status)
    }
}
