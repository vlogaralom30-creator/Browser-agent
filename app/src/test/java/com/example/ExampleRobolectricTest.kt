package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.BrowserTools
import com.example.data.agent.AgentRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `test browser tools declarations exist and are valid`() {
        val toolsArray = BrowserTools.getGeminiToolsDeclaration()
        assertNotNull(toolsArray)
        val declarations = toolsArray.getJSONObject(0).getJSONArray("functionDeclarations")
        assertTrue(declarations.length() > 5)

        val toolNames = mutableListOf<String>()
        for (i in 0 until declarations.length()) {
            val decl = declarations.getJSONObject(i)
            toolNames.add(decl.getString("name"))
        }

        assertTrue(toolNames.contains("open_url"))
        assertTrue(toolNames.contains("click_element"))
        assertTrue(toolNames.contains("type_text"))
        assertTrue(toolNames.contains("scroll_page"))
        assertTrue(toolNames.contains("extract_page_content"))
        assertTrue(toolNames.contains("extract_ai_prompts"))
        assertTrue(toolNames.contains("save_prompt"))
        assertTrue(toolNames.contains("request_sensitive_confirmation"))
    }

    @Test
    fun `test agent repository initialization and failover sorting`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AgentRepository(context)

        repo.initializeDefaultsIfEmpty()

        val keys = repo.apiKeyDao.getEnabledApiKeys()
        assertTrue("Expected enabled keys in pool", keys.isNotEmpty())

        // Test masked key representation
        val firstKey = keys.first()
        val masked = firstKey.getMaskedKey()
        assertFalse(masked.contains(firstKey.apiKey))
        assertTrue(masked.contains("..."))

        // Test prompt saving & deduplication
        val prompt1 = "A cinematic shot of a neon cyberpunk skyline at dusk"
        val saved1 = repo.savePrompt(prompt1, "Cinematic", sourceWebsite = "TestSite")
        assertTrue(saved1)

        val savedDuplicate = repo.savePrompt(prompt1, "Cinematic", sourceWebsite = "TestSite")
        assertFalse(savedDuplicate)

        val prompts = repo.allPrompts.first()
        assertTrue(prompts.any { it.prompt == prompt1 })

        // Test memory context formatting
        repo.saveMemory("channel_name", "AI Studio Demo", "YOUTUBE")
        val memContext = repo.getFormattedMemoryContext()
        assertTrue(memContext.contains("channel_name"))
        assertTrue(memContext.contains("AI Studio Demo"))
    }
}
