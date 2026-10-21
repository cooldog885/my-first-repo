package com.example.foldledger.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 分类表：预置 9 类 + 用户自定义类 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "cat_key") val key: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "emoji") val emoji: String,
    @ColumnInfo(name = "color") val color: Int,
    @ColumnInfo(name = "is_preset") val isPreset: Boolean = false
)
