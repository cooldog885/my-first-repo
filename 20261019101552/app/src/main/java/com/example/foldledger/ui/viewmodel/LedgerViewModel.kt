package com.example.foldledger.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.foldledger.data.entities.CategoryEntity
import com.example.foldledger.data.entities.Transaction
import com.example.foldledger.data.repository.LedgerRepository
import com.example.foldledger.ui.model.Categories
import com.example.foldledger.ui.model.Category
import com.example.foldledger.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LedgerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LedgerRepository.get(app)

    /** 全部记录（按时间倒序） */
    val transactions: LiveData<List<Transaction>> = repo.allTransactions.asLiveData()

    /** 全部分类（预置 + 自定义），供录入页构建选择 */
    val categories: LiveData<List<CategoryEntity>> = repo.categories.asLiveData()

    /** 录入表单草稿，跨 Tab 切换 / 折叠展开保持 */
    val draft = MutableLiveData(Draft())

    /** 正在编辑的记录 id；为空表示新增 */
    var editingId: Long? = null
        private set

    /** 编辑事件信号：非空时录入页应回填该记录（用于双栏模式实时刷新） */
    val editEvent = androidx.lifecycle.MutableLiveData<Transaction?>(null)

    /** 每日支出上限（元） */
    var dailyLimit: Double = 200.0
        private set

    init {
        loadDailyLimit(app)
        ensureSeeded()
        viewModelScope.launch {
            repo.categories.collect { list ->
                Categories.customMap = list.filter { !it.isPreset }
                    .associate { it.key to Category(it.key, it.label, it.emoji, it.color) }
            }
        }
    }

    /** 首次启动播种 9 个预置分类 */
    private fun ensureSeeded() {
        viewModelScope.launch(Dispatchers.IO) {
            if (repo.presetCount() == 0) {
                repo.seedPresets(
                    Categories.EXPENSE.map {
                        CategoryEntity(
                            key = it.key,
                            label = it.label,
                            emoji = it.emoji,
                            color = it.color,
                            isPreset = true
                        )
                    }
                )
            }
        }
    }

    /** 新增自定义分类 */
    fun addCategory(name: String, emoji: String, color: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.addCategory(
                CategoryEntity(
                    key = "c${System.currentTimeMillis()}",
                    label = name,
                    emoji = emoji,
                    color = color,
                    isPreset = false
                )
            )
        }
    }

    /** 删除自定义分类（预置类不可删） */
    fun deleteCategory(e: CategoryEntity) {
        if (e.isPreset) return
        viewModelScope.launch(Dispatchers.IO) { repo.deleteCategory(e) }
    }

    private fun loadDailyLimit(app: Application) {
        val sp = app.getSharedPreferences("ledger_prefs", Application.MODE_PRIVATE)
        dailyLimit = sp.getString("daily_limit", "200")?.toDoubleOrNull() ?: 200.0
    }

    fun saveDailyLimit(value: Double) {
        dailyLimit = if (value > 0) value else 0.0
        getApplication<Application>().getSharedPreferences("ledger_prefs", Application.MODE_PRIVATE)
            .edit().putString("daily_limit", dailyLimit.toString()).apply()
    }

    fun startEdit(t: Transaction) {
        editingId = t.id
        draft.value = Draft(
            type = t.type,
            amountText = if (t.amount % 1.0 == 0.0) t.amount.toLong().toString()
            else t.amount.toString(),
            category = t.category,
            paymentMethod = t.paymentMethod,
            date = t.date,
            note = t.note
        )
        editEvent.value = t
    }

    fun resetDraft() {
        editingId = null
        draft.value = Draft()
        editEvent.value = null
    }

    fun upsert(draft: Draft): Result<Transaction> {
        val amount = draft.amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) return Result.failure(IllegalArgumentException("金额无效"))
        if (draft.type == "expense" && draft.category.isBlank())
            return Result.failure(IllegalArgumentException("请选择分类"))

        val date = DateUtils.dayStart(draft.date)
        val t = Transaction(
            id = editingId ?: 0,
            type = draft.type,
            amount = amount,
            category = if (draft.type == "expense") draft.category else Categories.INCOME_KEY,
            paymentMethod = if (draft.type == "expense") draft.paymentMethod else "",
            note = draft.note.trim(),
            date = date
        )
        viewModelScope.launch {
            if (editingId != null) repo.update(t) else repo.insert(t)
        }
        return Result.success(t)
    }

    fun delete(t: Transaction) = viewModelScope.launch { repo.delete(t) }

    /** 批量导入——追加为新记录 */
    fun importTransactions(list: List<Transaction>) {
        if (list.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            list.forEach { repo.insert(it) }
        }
    }

    /** 批量导入——先清空现有数据再写入 */
    fun replaceTransactions(list: List<Transaction>) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteAll()
            list.forEach { repo.insert(it) }
        }
    }

    /** 批量导入——按 (类型,金额,日期,分类,支付方式,备注) 去重，仅写入不存在的记录 */
    fun importUniqueTransactions(list: List<Transaction>) {
        if (list.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repo.allTransactions.first()
            val seen = existing.map { dedupeKey(it) }.toSet()
            list.filter { dedupeKey(it) !in seen }.forEach { repo.insert(it) }
        }
    }

    private fun dedupeKey(t: Transaction): String =
        "${t.type}|${t.amount}|${DateUtils.dayStart(t.date)}|${t.category}|${t.paymentMethod}|${t.note}"

    /** 今日（含即将新增）支出合计，用于超限判断 */
    suspend fun todayExpenseWith(added: Double): Double {
        val now = System.currentTimeMillis()
        val base = repo.sumExpenseOnDay(DateUtils.dayStart(now), DateUtils.dayEnd(now))
        return base + added
    }

    /** 本月收入 / 支出合计，用于赤字预警 */
    suspend fun monthSummary(): Pair<Double, Double> {
        val now = System.currentTimeMillis()
        val inc = repo.sumIncome(DateUtils.startOfMonth(now), DateUtils.endOfMonth(now))
        val exp = repo.sumExpense(DateUtils.startOfMonth(now), DateUtils.endOfMonth(now))
        return inc to exp
    }

    data class Draft(
        val type: String = "expense",
        val amountText: String = "",
        val category: String = "",
        val paymentMethod: String = "wechat",
        val date: Long = System.currentTimeMillis(),
        val note: String = ""
    )
}
