package com.example.foldledger.data.repository

import com.example.foldledger.data.AppDatabase
import com.example.foldledger.data.dao.CategoryDao
import com.example.foldledger.data.dao.TransactionDao
import com.example.foldledger.data.entities.CategoryEntity
import com.example.foldledger.data.entities.Transaction
import kotlinx.coroutines.flow.Flow

class LedgerRepository(private val dao: TransactionDao, private val categoryDao: CategoryDao) {

    val allTransactions: Flow<List<Transaction>> = dao.getAll()
    val categories: Flow<List<CategoryEntity>> = categoryDao.getAll()

    suspend fun insert(t: Transaction): Long = dao.insert(t)
    suspend fun update(t: Transaction) = dao.update(t)
    suspend fun delete(t: Transaction) = dao.delete(t)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun getById(id: Long) = dao.getById(id)

    suspend fun sumByCategory(start: Long, end: Long) = dao.sumByCategory(start, end)
    suspend fun sumExpense(start: Long, end: Long) = dao.sumExpense(start, end)
    suspend fun sumIncome(start: Long, end: Long) = dao.sumIncome(start, end)
    suspend fun sumExpenseOnDay(dayStart: Long, dayEnd: Long) = dao.sumExpenseOnDay(dayStart, dayEnd)
    suspend fun topByAmount(start: Long, end: Long, limit: Int) = dao.topByAmount(start, end, limit)

    suspend fun presetCount(): Int = categoryDao.presetCount()
    suspend fun seedPresets(list: List<CategoryEntity>) = list.forEach { categoryDao.insert(it) }
    suspend fun addCategory(e: CategoryEntity): Long = categoryDao.insert(e)
    suspend fun deleteCategory(e: CategoryEntity) = categoryDao.delete(e)
    suspend fun getCategoryByKey(key: String) = categoryDao.getByKey(key)

    companion object {
        @Volatile
        private var INSTANCE: LedgerRepository? = null

        fun get(context: android.content.Context): LedgerRepository =
            INSTANCE ?: synchronized(this) {
                val db = AppDatabase.get(context)
                INSTANCE ?: LedgerRepository(db.dao(), db.categoryDao())
                    .also { INSTANCE = it }
            }
    }
}
