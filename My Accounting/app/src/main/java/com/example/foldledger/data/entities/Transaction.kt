package com.example.foldledger.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条记账记录。
 * type: "expense" 支出 | "income" 收入
 * category: 分类 key（收入固定为 "income"）
 * paymentMethod: 支付方式 key（收入为空）
 * date: 该笔发生日期的 0 点时间戳（按天分组）
 */
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amount: Double,
    val category: String,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    val note: String,
    val date: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/** GROUP BY 查询返回的分类汇总行 */
data class CategorySum(
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "total") val total: Double
)
