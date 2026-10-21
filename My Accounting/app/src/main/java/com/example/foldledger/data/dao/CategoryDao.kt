package com.example.foldledger.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.foldledger.data.entities.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY is_preset DESC, id ASC")
    fun getAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE cat_key = :key LIMIT 1")
    suspend fun getByKey(key: String): CategoryEntity?

    @Insert
    suspend fun insert(c: CategoryEntity): Long

    @Delete
    suspend fun delete(c: CategoryEntity)

    @Query("SELECT COUNT(*) FROM categories WHERE is_preset = 1")
    suspend fun presetCount(): Int
}
