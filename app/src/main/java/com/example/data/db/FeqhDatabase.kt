package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.FeqhDao
import com.example.data.model.Article
import com.example.data.model.TreeNode

@Database(entities = [Article::class, TreeNode::class], version = 2, exportSchema = false)
abstract class FeqhDatabase : RoomDatabase() {
    abstract fun feqhDao(): FeqhDao

    companion object {
        @Volatile
        private var INSTANCE: FeqhDatabase? = null

        fun getDatabase(context: Context): FeqhDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FeqhDatabase::class.java,
                    "feqhia_app.db"
                )
                .createFromAsset("databases/feqhia.db")
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
