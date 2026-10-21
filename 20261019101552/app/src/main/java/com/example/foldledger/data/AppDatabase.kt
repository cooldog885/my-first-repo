package com.example.foldledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.foldledger.data.dao.CategoryDao
import com.example.foldledger.data.dao.TransactionDao
import com.example.foldledger.data.entities.CategoryEntity
import com.example.foldledger.data.entities.Transaction

@Database(entities = [Transaction::class, CategoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TransactionDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fold_ledger.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
