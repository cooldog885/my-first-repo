package com.example.foldledger.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.foldledger.data.entities.Transaction
import com.example.foldledger.ui.model.Categories
import com.example.foldledger.ui.model.PaymentMethods
import java.text.SimpleDateFormat
import java.util.Locale

object ImportCsv {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    data class ParseResult(
        val transactions: List<Transaction>,
        val imported: Int,
        val skipped: Int,
        val fileName: String?
    )

    /** 解析导出的 CSV（带表头，字段用引号包裹）。返回可导入的记录与统计。 */
    fun parse(context: Context, uri: Uri): ParseResult {
        val fileName = queryName(context, uri)
        val list = mutableListOf<Transaction>()
        var imported = 0
        var skipped = 0

        context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().useLines { lines ->
                var first = true
                for (raw in lines) {
                    var line = raw.removePrefix("\uFEFF").trim()
                    if (line.isEmpty()) continue
                    // 跳过表头
                    if (first) {
                        first = false
                        if (line.startsWith("日期")) continue
                    }
                    val cols = parseLine(line)
                    if (cols.size < 6) { skipped++; continue }
                    val dateStr = cols[0]
                    val typeStr = cols[1]
                    val catStr = cols[2]
                    val amtStr = cols[3]
                    val payStr = cols[4]
                    val note = cols[5]
                    val date = runCatching { sdf.parse(dateStr)?.time }.getOrNull()
                    if (date == null) { skipped++; continue }
                    val amount = amtStr.toDoubleOrNull()
                    if (amount == null || amount <= 0) { skipped++; continue }

                    val type = if (typeStr.contains("收入")) "income" else "expense"
                    val category = if (type == "income") Categories.INCOME_KEY
                    else Categories.keyOfLabel(catStr) ?: "other"
                    val payment = if (type == "income") ""
                    else PaymentMethods.keyOfLabel(payStr) ?: "other"

                    list.add(
                        Transaction(
                            type = type,
                            amount = amount,
                            category = category,
                            paymentMethod = payment,
                            note = note.trim(),
                            date = DateUtils.dayStart(date)
                        )
                    )
                    imported++
                }
            }
        }
        return ParseResult(list, imported, skipped, fileName)
    }

    private fun queryName(context: Context, uri: Uri): String? {
        var name: String? = null
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) name = c.getString(i)
            }
        }
        return name
    }

    /** 解析一行 CSV：支持引号包裹、字段内逗号与转义双引号("") */
    private fun parseLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                when {
                    ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                        sb.append('"'); i += 2
                    }
                    ch == '"' -> { inQuotes = false; i++ }
                    else -> { sb.append(ch); i++ }
                }
            } else {
                when (ch) {
                    '"' -> { inQuotes = true; i++ }
                    ',' -> { out.add(sb.toString()); sb.setLength(0); i++ }
                    else -> { sb.append(ch); i++ }
                }
            }
        }
        out.add(sb.toString())
        return out
    }
}
