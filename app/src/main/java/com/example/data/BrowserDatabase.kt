package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.agent.AgentConfigDao
import com.example.data.agent.AgentConfigEntity
import com.example.data.agent.AgentConversationDao
import com.example.data.agent.AgentConversationEntity
import com.example.data.agent.AgentMemoryDao
import com.example.data.agent.AgentMemoryEntity
import com.example.data.agent.AgentMessageDao
import com.example.data.agent.AgentMessageEntity
import com.example.data.agent.AgentTaskDao
import com.example.data.agent.AgentTaskEntity
import com.example.data.agent.AgentTaskStepDao
import com.example.data.agent.AgentTaskStepEntity
import com.example.data.agent.ApiKeyDao
import com.example.data.agent.ApiKeyEntity
import com.example.data.agent.SavedPromptDao
import com.example.data.agent.SavedPromptEntity

@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
        ApiKeyEntity::class,
        AgentConfigEntity::class,
        AgentConversationEntity::class,
        AgentMessageEntity::class,
        AgentTaskEntity::class,
        AgentTaskStepEntity::class,
        AgentMemoryEntity::class,
        SavedPromptEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao

    // AI Agent DAOs
    abstract fun apiKeyDao(): ApiKeyDao
    abstract fun agentConfigDao(): AgentConfigDao
    abstract fun agentConversationDao(): AgentConversationDao
    abstract fun agentMessageDao(): AgentMessageDao
    abstract fun agentTaskDao(): AgentTaskDao
    abstract fun agentTaskStepDao(): AgentTaskStepDao
    abstract fun agentMemoryDao(): AgentMemoryDao
    abstract fun savedPromptDao(): SavedPromptDao

    companion object {
        @Volatile
        private var INSTANCE: BrowserDatabase? = null

        fun getDatabase(context: Context): BrowserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    "browser_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
