package com.example.foldledger.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd EEE", Locale.CHINA)
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.CHINA)
    private val monthLabel = SimpleDateFormat("M月", Locale.CHINA)

    /** 某时间戳所在那一天的 0 点（按本地时区） */
    fun dayStart(ts: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun dayEnd(ts: Long): Long = dayStart(ts) + 24L * 60 * 60 * 1000 - 1

    fun startOfMonth(ts: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun endOfMonth(ts: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = startOfMonth(ts) }
        c.add(Calendar.MONTH, 1)
        return c.timeInMillis - 1
    }

    /** 返回包含当前月在内的最近 n 个月（含起止时间戳），按月先后排列 */
    fun lastMonths(n: Int, from: Long = System.currentTimeMillis()): List<MonthRange> {
        val list = mutableListOf<MonthRange>()
        val c = Calendar.getInstance().apply { timeInMillis = startOfMonth(from) }
        repeat(n) {
            val start = c.timeInMillis
            val end = endOfMonth(start)
            list.add(MonthRange(start, end, monthLabel.format(Date(start))))
            c.add(Calendar.MONTH, -1)
        }
        return list.reversed()
    }

    fun formatDate(ts: Long): String = dayFormat.format(Date(ts))
    fun formatYMD(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(ts))
    fun formatMonth(ts: Long): String = monthFormat.format(Date(ts))

    fun buildDate(ts: Long): Date = Date(ts)
}

data class MonthRange(val start: Long, val end: Long, val label: String)
