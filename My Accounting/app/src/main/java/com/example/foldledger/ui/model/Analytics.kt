package com.example.foldledger.ui.model

import com.example.foldledger.data.entities.Transaction
import com.example.foldledger.ui.model.Categories
import com.example.foldledger.ui.model.Category
import com.example.foldledger.util.DateUtils

object Analytics {

    /** 指定时间范围内各分类支出合计（按金额倒序） */
    fun categorySums(
        list: List<Transaction>,
        start: Long,
        end: Long
    ): List<Pair<Category, Double>> {
        return list.filter { it.type == "expense" && it.date in start..end }
            .groupBy { it.category }
            .mapNotNull { (key, items) ->
                val c = Categories.get(key) ?: return@mapNotNull null
                c to items.sumOf { it.amount }
            }
            .sortedByDescending { it.second }
    }

    /** 近 n 个月每月支出总额（时间正序） */
    fun monthlyTrend(list: List<Transaction>, months: Int = 6): List<Pair<String, Double>> {
        val ranges = DateUtils.lastMonths(months)
        return ranges.map { r ->
            val sum = list.filter { it.type == "expense" && it.date in r.start..r.end }
                .sumOf { it.amount }
            r.label to sum
        }
    }

    /** 分类 TOP n（全部时间） */
    fun topCategories(list: List<Transaction>, n: Int): List<Pair<Category, Double>> =
        categorySums(list, 0, Long.MAX_VALUE).take(n)

    /** 单笔最大金额记录 TOP n（全部时间） */
    fun topAmounts(list: List<Transaction>, n: Int): List<Transaction> =
        list.filter { it.type == "expense" }
            .sortedByDescending { it.amount }
            .take(n)

    /** 本月收入、支出 */
    fun monthSummary(list: List<Transaction>): Pair<Double, Double> {
        val now = System.currentTimeMillis()
        val s = DateUtils.startOfMonth(now)
        val e = DateUtils.endOfMonth(now)
        val inc = list.filter { it.type == "income" && it.date in s..e }.sumOf { it.amount }
        val exp = list.filter { it.type == "expense" && it.date in s..e }.sumOf { it.amount }
        return inc to exp
    }

    /** 今日支出合计 */
    fun todayExpense(list: List<Transaction>, now: Long = System.currentTimeMillis()): Double {
        val s = DateUtils.dayStart(now)
        val e = DateUtils.dayEnd(now)
        return list.filter { it.type == "expense" && it.date in s..e }.sumOf { it.amount }
    }
}
