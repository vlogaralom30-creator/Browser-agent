package com.example.data.agent

import android.content.Context
import com.example.data.BrowserDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class AgentRepository(context: Context) {
    private val db = BrowserDatabase.getDatabase(context)
    val apiKeyDao = db.apiKeyDao()
    val configDao = db.agentConfigDao()
    val conversationDao = db.agentConversationDao()
    val messageDao = db.agentMessageDao()
    val taskDao = db.agentTaskDao()
    val taskStepDao = db.agentTaskStepDao()
    val memoryDao = db.agentMemoryDao()
    val promptDao = db.savedPromptDao()

    val allApiKeys: Flow<List<ApiKeyEntity>> = apiKeyDao.getAllApiKeys()
    val allConversations: Flow<List<AgentConversationEntity>> = conversationDao.getAllConversations()
    val allTasks: Flow<List<AgentTaskEntity>> = taskDao.getAllTasks()
    val activeTaskFlow: Flow<AgentTaskEntity?> = taskDao.getActiveTaskFlow()
    val allMemories: Flow<List<AgentMemoryEntity>> = memoryDao.getAllMemories()
    val allPrompts: Flow<List<SavedPromptEntity>> = promptDao.getAllPrompts()
    val configFlow: Flow<AgentConfigEntity?> = configDao.getConfigFlow()

    suspend fun initializeDefaultsIfEmpty() {
        // Initialize default configuration if empty or update if using obsolete model
        val config = configDao.getConfig()
        if (config == null) {
            configDao.insertOrUpdateConfig(
                AgentConfigEntity(
                    id = 1,
                    activeModel = "gemini-2.0-flash",
                    permissionMode = "FULL_ACCESS",
                    isAutoFailoverEnabled = true,
                    isVisionEnabled = true,
                    maxStepsPerTask = 30
                )
            )
        } else if (config.activeModel.contains("gemini-2.5-flash")) {
            configDao.insertOrUpdateConfig(config.copy(activeModel = "gemini-2.0-flash"))
        }

        // Reset status for any keys that were rate limited or marked error due to obsolete model
        val allKeys = apiKeyDao.getAllApiKeysList()
        for (key in allKeys) {
            if (key.lastError?.contains("gemini-2.5-flash", ignoreCase = true) == true ||
                key.lastError?.contains("no longer available", ignoreCase = true) == true
            ) {
                apiKeyDao.updateApiKey(
                    key.copy(
                        status = "ACTIVE",
                        failCount = 0,
                        lastError = null
                    )
                )
            }
        }
    }

    suspend fun addApiKey(apiKey: String, label: String = "Gemini Key", isEnabled: Boolean = true): Long {
        val existingCount = apiKeyDao.getApiKeysCount()
        val entity = ApiKeyEntity(
            apiKey = apiKey.trim(),
            label = label.ifBlank { "Key ${existingCount + 1}" },
            isEnabled = isEnabled,
            status = "ACTIVE",
            priorityOrder = existingCount
        )
        return apiKeyDao.insertApiKey(entity)
    }

    suspend fun getActiveApiKey(): ApiKeyEntity? {
        val enabledKeys = apiKeyDao.getEnabledApiKeys()
        // Prefer working or active keys
        return enabledKeys.firstOrNull { it.status == "ACTIVE" || it.status == "WORKING" }
            ?: enabledKeys.firstOrNull { it.status != "INVALID" && it.status != "DISABLED" }
            ?: enabledKeys.firstOrNull()
    }

    suspend fun markKeySuccess(apiKeyId: Long) {
        val key = apiKeyDao.getApiKeyById(apiKeyId) ?: return
        apiKeyDao.updateApiKey(
            key.copy(
                status = "ACTIVE",
                successCount = key.successCount + 1,
                lastUsedAt = System.currentTimeMillis(),
                lastError = null
            )
        )
    }

    suspend fun markKeyFailure(apiKeyId: Long, errorMsg: String, isQuotaExhausted: Boolean = false) {
        val key = apiKeyDao.getApiKeyById(apiKeyId) ?: return
        val newStatus = if (isQuotaExhausted) "QUOTA_EXHAUSTED" else "RATE_LIMITED"
        apiKeyDao.updateApiKey(
            key.copy(
                status = newStatus,
                failCount = key.failCount + 1,
                lastUsedAt = System.currentTimeMillis(),
                lastError = errorMsg.take(200)
            )
        )
    }

    suspend fun updateConfig(config: AgentConfigEntity) {
        configDao.insertOrUpdateConfig(config)
    }

    suspend fun getConfig(): AgentConfigEntity {
        return configDao.getConfig() ?: AgentConfigEntity()
    }

    // Memory methods
    suspend fun saveMemory(key: String, value: String, category: String = "TASK_STATE", sourceWebsite: String? = null) {
        val existing = memoryDao.getMemoryByKey(key)
        val entity = existing?.copy(
            value = value,
            category = category,
            sourceWebsite = sourceWebsite ?: existing.sourceWebsite,
            updatedAt = System.currentTimeMillis()
        ) ?: AgentMemoryEntity(
            key = key,
            category = category,
            value = value,
            sourceWebsite = sourceWebsite,
            updatedAt = System.currentTimeMillis()
        )
        memoryDao.insertOrUpdateMemory(entity)
    }

    suspend fun getFormattedMemoryContext(): String {
        val all = memoryDao.getAllMemories().firstOrNull() ?: emptyList()
        if (all.isEmpty()) return ""
        val sb = StringBuilder("=== AGENT PERSISTENT MEMORY ===\n")
        all.take(20).forEach { mem ->
            sb.append("- [${mem.category}] ${mem.key}: ${mem.value}\n")
        }
        sb.append("===============================\n")
        return sb.toString()
    }

    // Prompts methods
    suspend fun savePrompt(
        promptText: String,
        category: String = "General",
        tags: String = "",
        sourceWebsite: String = "",
        sourceUrl: String = "",
        notes: String = ""
    ): Boolean {
        val trimmed = promptText.trim()
        if (trimmed.isBlank()) return false
        val existing = promptDao.getPromptByText(trimmed)
        if (existing != null) {
            // Already saved, prevent duplicate
            return false
        }
        val entity = SavedPromptEntity(
            prompt = trimmed,
            category = category.ifBlank { "General" },
            tags = tags,
            sourceWebsite = sourceWebsite,
            sourceUrl = sourceUrl,
            notes = notes,
            createdAt = System.currentTimeMillis()
        )
        val id = promptDao.insertPrompt(entity)
        return id > 0
    }

    // Conversations
    suspend fun createOrGetActiveConversation(): AgentConversationEntity {
        val existing = conversationDao.getAllConversations().firstOrNull()?.firstOrNull { it.isActive }
        if (existing != null) return existing
        val newConv = AgentConversationEntity(
            id = UUID.randomUUID().toString(),
            title = "New Agent Chat",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
        )
        conversationDao.insertConversation(newConv)
        return newConv
    }
}
