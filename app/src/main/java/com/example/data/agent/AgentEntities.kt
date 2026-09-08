package com.example.data.agent

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "api_keys")
data class ApiKeyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val apiKey: String,
    val label: String,
    val isEnabled: Boolean = true,
    val status: String = "ACTIVE", // ACTIVE, RATE_LIMITED, QUOTA_EXHAUSTED, INVALID, DISABLED
    val failCount: Int = 0,
    val successCount: Int = 0,
    val lastUsedAt: Long = 0L,
    val lastError: String? = null,
    val priorityOrder: Int = 0
) {
    fun getMaskedKey(): String {
        return if (apiKey.length > 8) {
            val start = apiKey.take(6)
            val end = apiKey.takeLast(4)
            "$start...$end"
        } else {
            "****"
        }
    }
}

@Entity(tableName = "agent_config")
data class AgentConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    val activeModel: String = "gemini-2.0-flash",
    val permissionMode: String = "FULL_ACCESS", // FULL_ACCESS, CONFIRM_ALL
    val isAutoFailoverEnabled: Boolean = true,
    val isVisionEnabled: Boolean = true,
    val maxStepsPerTask: Int = 30
)

@Entity(tableName = "agent_conversations")
data class AgentConversationEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

@Entity(
    tableName = "agent_messages",
    indices = [Index(value = ["conversationId"])]
)
data class AgentMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: String,
    val role: String, // "user", "assistant", "system", "tool"
    val content: String,
    val toolCallsJson: String? = null,
    val toolResponsesJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "agent_tasks",
    indices = [Index(value = ["conversationId"])]
)
data class AgentTaskEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val goal: String,
    val status: String, // PENDING, RUNNING, PAUSED, COMPLETED, FAILED, WAITING_CONFIRMATION
    val progress: String = "0%",
    val currentStepIndex: Int = 0,
    val totalEstimatedSteps: Int = 1,
    val targetUrl: String? = null,
    val lastCheckpointJson: String? = null,
    val pendingSensitiveAction: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "agent_task_steps",
    indices = [Index(value = ["taskId"])]
)
data class AgentTaskStepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: String,
    val stepNumber: Int,
    val actionType: String,
    val actionDescription: String,
    val actionPayloadJson: String = "{}",
    val resultSummary: String? = null,
    val isSuccess: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "agent_memories",
    indices = [Index(value = ["key"], unique = true)]
)
data class AgentMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String,
    val category: String, // PREFERENCE, WEBSITE_STATE, TASK_STATE, LEARNED_FACT
    val value: String,
    val sourceWebsite: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "saved_prompts",
    indices = [Index(value = ["prompt"], unique = true)]
)
data class SavedPromptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val prompt: String,
    val category: String = "General",
    val tags: String = "",
    val sourceWebsite: String = "",
    val sourceUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val notes: String = ""
)
