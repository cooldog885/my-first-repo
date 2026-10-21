package com.example.foldledger.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.foldledger.data.entities.CategorySum
import com.example.foldledger.data.entities.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY date DESC, created_at DESC")
    fun getAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Insert
    suspend fun insert(t: Transaction): Long

    @Update
    suspend fun update(t: Transaction)

    @Delete
    suspend fun delete(t: Transaction)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query(
        """
        SELECT category, COALESCE(SUM(amount),0) AS total
        FROM transactions
        WHERE type='expense' AND date BETWEEN :start AND :end
        GROUP BY category
        """
    )
    suspend fun sumByCategory(start: Long, end: Long): List<CategorySum>

    @Query(
        "SELECT COALESCE(SUM(amount),0) FROM transactions " +
            "WHERE type='expense' AND date BETWEEN :start AND :end"
    )
    suspend fun sumExpense(start: Long, end: Long): Double

    @Query(
        "SELECT COALESCE(SUM(amount),0) FROM transactions " +
            "WHERE type='income' AND date BETWEEN :start AND :end"
    )
    suspend fun sumIncome(start: Long, end: Long): Double

    @Query(
        "SELECT COALESCE(SUM(amount),0) FROM transactions " +
            "WHERE type='expense' AND date BETWEEN :dayStart AND :dayEnd"
    )
    suspend fun sumExpenseOnDay(dayStart: Long, dayEnd: Long): Double

    @Query(
        "SELECT * FROM transactions WHERE type='expense' " +
            "AND date BETWEEN :start AND :end ORDER BY amount DESC LIMIT :limit"
    )
    suspend fun topByAmount(start: Long, end: Long, limit: Int): List<Transaction>
}
