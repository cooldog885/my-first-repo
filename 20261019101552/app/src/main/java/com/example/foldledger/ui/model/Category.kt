package com.example.foldledger.ui.model

import androidx.annotation.ColorInt

/** 9 个预置支出分类 */
data class Category(
    val key: String,
    val label: String,
    val emoji: String,
    @ColorInt val color: Int
)

object Categories {
    val EXPENSE: List<Category> = listOf(
        Category("food", "餐饮", "🍔", 0xFFE57373.toInt()),
        Category("transport", "交通", "🚌", 0xFF4FC3F7.toInt()),
        Category("shopping", "购物", "🛍️", 0xFFBA68C8.toInt()),
        Category("housing", "居住", "🏠", 0xFF81C784.toInt()),
        Category("entertainment", "娱乐", "🎮", 0xFFFFB74D.toInt()),
        Category("medical", "医疗", "💊", 0xFF4DB6AC.toInt()),
        Category("education", "教育", "📚", 0xFF7986CB.toInt()),
        Category("communication", "通讯", "📱", 0xFFA1887F.toInt()),
        Category("other", "其他", "📦", 0xFF90A4AE.toInt())
    )

    private val presetMap = EXPENSE.associateBy { it.key }

    /** 自定义分类运行时缓存（由 ViewModel 从数据库刷新） */
    var customMap: Map<String, Category> = emptyMap()

    fun get(key: String): Category? = presetMap[key] ?: customMap[key]
    fun labelOf(key: String): String = get(key)?.label ?: key
    fun colorOf(key: String): Int = get(key)?.color ?: 0xFF90A4AE.toInt()
    fun emojiOf(key: String): String = get(key)?.emoji ?: "📦"

    /** 按 key 或显示名反查 key（导入 CSV 时把中文分类名还原为内部 key） */
    fun keyOfLabel(s: String): String? {
        if (s.isBlank()) return null
        presetMap[s]?.let { return it.key }
        customMap[s]?.let { return it.key }
        presetMap.values.firstOrNull { it.label == s }?.let { return it.key }
        customMap.values.firstOrNull { it.label == s }?.let { return it.key }
        return null
    }

    const val INCOME_KEY = "income"
    const val INCOME_LABEL = "收入"
}
