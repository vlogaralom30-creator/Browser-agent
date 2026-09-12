package com.example.util

import java.util.regex.Pattern

object TitleSpinnerEngine {

    private val VIRAL_HOOKS = listOf(
        "Wait till the very end! 😱",
        "You won't believe how this ended... 🔥",
        "This literally blew my mind! 🤯",
        "Best moment you'll see all day! ✨",
        "Wait for it... absolutely worth it 😂",
        "Can someone explain this? 🤔",
        "Tag someone who needs to see this! 👇",
        "This never gets old 😂",
        "Save this before it gets taken down! 📌",
        "The ending had me in tears 😂🔥",
        "Did not see that coming at all! 💀",
        "Watch this twice to understand 😳",
        "Pure genius right here 🧠⚡",
        "I was NOT expecting that ending! 💥",
        "Top tier content right here 💯"
    )

    private val CALL_TO_ACTIONS = listOf(
        "Follow for more viral reels! 🚀",
        "Drop a ❤️ if you enjoyed this!",
        "What do you think? Comment below! 💬",
        "Share this with your best friend! 📲",
        "Double tap if this made your day! 💖"
    )

    fun cleanOriginalTitle(title: String): String {
        var cleaned = title
            // Remove URLs
            .replace(Regex("https?://\\S+\\b"), "")
            // Remove user handles like @username
            .replace(Regex("@[A-Za-z0-9_.]+"), "")
            // Remove existing hashtags
            .replace(Regex("#[A-Za-z0-9_]+"), "")
            // Remove common TikTok spam phrases
            .replace(Regex("(?i)\\b(part\\s*\\d+|link\\s*in\\s*bio|foryoupage|fyp|tiktok|duet|stitch|viral)\\b"), "")
            // Normalize spaces
            .replace(Regex("\\s+"), " ")
            .trim()

        // Strip leading/trailing weird punctuation
        cleaned = cleaned.trimStart('-', ':', '|', '.', ',', ' ')
        cleaned = cleaned.trimEnd('-', ':', '|', '.', ',', ' ')

        return if (cleaned.isBlank()) "Must watch viral moment" else cleaned
    }

    fun generateSpunTitle(originalTitle: String, customHashtags: String): String {
        val cleaned = cleanOriginalTitle(originalTitle)
        val hook = VIRAL_HOOKS.random()
        val cta = CALL_TO_ACTIONS.random()

        // Format custom hashtags
        val tagsFormatted = customHashtags
            .split(" ", ",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.startsWith("#")) it else "#$it" }
            .distinct()
            .joinToString(" ")

        val finalHashtags = if (tagsFormatted.isNotBlank()) tagsFormatted else "#viral #reels #foryou #trending #explore"

        // Capitalize first letter of cleaned text
        val formattedTitle = cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        return buildString {
            append(hook)
            append(" ")
            append(formattedTitle)
            append("\n\n")
            append(cta)
            append("\n\n")
            append(finalHashtags)
        }
    }
}
