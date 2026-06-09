package com.example.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DocEntity::class, ChatConversation::class, ChatMessageEntity::class, AiProviderConfig::class], version = 2, exportSchema = false)
abstract class DocDatabase : RoomDatabase() {
    abstract fun docDao(): DocDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: DocDatabase? = null

        fun getDatabase(context: Context): DocDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DocDatabase::class.java,
                    "jcdocs_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
