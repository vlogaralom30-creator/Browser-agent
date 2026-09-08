package com.example.data.agent

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys ORDER BY priorityOrder ASC, id ASC")
    fun getAllApiKeys(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys ORDER BY priorityOrder ASC, id ASC")
    suspend fun getAllApiKeysList(): List<ApiKeyEntity>

    @Query("SELECT * FROM api_keys WHERE isEnabled = 1 ORDER BY priorityOrder ASC, id ASC")
    suspend fun getEnabledApiKeys(): List<ApiKeyEntity>

    @Query("SELECT * FROM api_keys WHERE id = :id")
    suspend fun getApiKeyById(id: Long): ApiKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApiKey(apiKey: ApiKeyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApiKeys(apiKeys: List<ApiKeyEntity>)

    @Update
    suspend fun updateApiKey(apiKey: ApiKeyEntity)

    @Delete
    suspend fun deleteApiKey(apiKey: ApiKeyEntity)

    @Query("DELETE FROM api_keys WHERE id = :id")
    suspend fun deleteApiKeyById(id: Long)

    @Query("SELECT COUNT(*) FROM api_keys")
    suspend fun getApiKeysCount(): Int
}

@Dao
interface AgentConfigDao {
    @Query("SELECT * FROM agent_config WHERE id = 1")
    fun getConfigFlow(): Flow<AgentConfigEntity?>

    @Query("SELECT * FROM agent_config WHERE id = 1")
    suspend fun getConfig(): AgentConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfig(config: AgentConfigEntity)
}

@Dao
interface AgentConversationDao {
    @Query("SELECT * FROM agent_conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<AgentConversationEntity>>

    @Query("SELECT * FROM agent_conversations WHERE id = :id")
    suspend fun getConversationById(id: String): AgentConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: AgentConversationEntity)

    @Update
    suspend fun updateConversation(conversation: AgentConversationEntity)

    @Delete
    suspend fun deleteConversation(conversation: AgentConversationEntity)

    @Query("DELETE FROM agent_conversations")
    suspend fun clearAllConversations()
}

@Dao
interface AgentMessageDao {
    @Query("SELECT * FROM agent_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<AgentMessageEntity>>

    @Query("SELECT * FROM agent_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesList(conversationId: String): List<AgentMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AgentMessageEntity): Long

    @Query("DELETE FROM agent_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)
}

@Dao
interface AgentTaskDao {
    @Query("SELECT * FROM agent_tasks ORDER BY updatedAt DESC")
    fun getAllTasks(): Flow<List<AgentTaskEntity>>

    @Query("SELECT * FROM agent_tasks WHERE id = :id")
    suspend fun getTaskById(id: String): AgentTaskEntity?

    @Query("SELECT * FROM agent_tasks WHERE status IN ('RUNNING', 'PENDING', 'WAITING_CONFIRMATION') ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getActiveTask(): AgentTaskEntity?

    @Query("SELECT * FROM agent_tasks WHERE status IN ('RUNNING', 'PENDING', 'WAITING_CONFIRMATION') ORDER BY updatedAt DESC LIMIT 1")
    fun getActiveTaskFlow(): Flow<AgentTaskEntity?>

    @Query("SELECT * FROM agent_tasks ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestTask(): AgentTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: AgentTaskEntity)

    @Update
    suspend fun updateTask(task: AgentTaskEntity)

    @Delete
    suspend fun deleteTask(task: AgentTaskEntity)

    @Query("DELETE FROM agent_tasks")
    suspend fun clearAllTasks()
}

@Dao
interface AgentTaskStepDao {
    @Query("SELECT * FROM agent_task_steps WHERE taskId = :taskId ORDER BY stepNumber ASC")
    fun getStepsForTask(taskId: String): Flow<List<AgentTaskStepEntity>>

    @Query("SELECT * FROM agent_task_steps WHERE taskId = :taskId ORDER BY stepNumber ASC")
    suspend fun getStepsListForTask(taskId: String): List<AgentTaskStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: AgentTaskStepEntity): Long

    @Query("DELETE FROM agent_task_steps WHERE taskId = :taskId")
    suspend fun deleteStepsForTask(taskId: String)
}

@Dao
interface AgentMemoryDao {
    @Query("SELECT * FROM agent_memories ORDER BY updatedAt DESC")
    fun getAllMemories(): Flow<List<AgentMemoryEntity>>

    @Query("SELECT * FROM agent_memories WHERE category = :category ORDER BY updatedAt DESC")
    suspend fun getMemoriesByCategory(category: String): List<AgentMemoryEntity>

    @Query("SELECT * FROM agent_memories WHERE `key` = :key LIMIT 1")
    suspend fun getMemoryByKey(key: String): AgentMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMemory(memory: AgentMemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: AgentMemoryEntity)

    @Query("DELETE FROM agent_memories WHERE `key` = :key")
    suspend fun deleteMemoryByKey(key: String)

    @Query("DELETE FROM agent_memories")
    suspend fun clearAllMemories()
}

@Dao
interface SavedPromptDao {
    @Query("SELECT * FROM saved_prompts ORDER BY isFavorite DESC, createdAt DESC")
    fun getAllPrompts(): Flow<List<SavedPromptEntity>>

    @Query("SELECT * FROM saved_prompts WHERE category = :category ORDER BY createdAt DESC")
    fun getPromptsByCategory(category: String): Flow<List<SavedPromptEntity>>

    @Query("SELECT * FROM saved_prompts WHERE prompt LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchPrompts(query: String): Flow<List<SavedPromptEntity>>

    @Query("SELECT * FROM saved_prompts WHERE prompt = :prompt LIMIT 1")
    suspend fun getPromptByText(prompt: String): SavedPromptEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPrompt(prompt: SavedPromptEntity): Long

    @Update
    suspend fun updatePrompt(prompt: SavedPromptEntity)

    @Delete
    suspend fun deletePrompt(prompt: SavedPromptEntity)

    @Query("DELETE FROM saved_prompts WHERE id = :id")
    suspend fun deletePromptById(id: Long)

    @Query("DELETE FROM saved_prompts")
    suspend fun clearAllPrompts()
}
