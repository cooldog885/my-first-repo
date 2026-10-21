package com.example.foldledger.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.foldledger.data.entities.Transaction
import com.example.foldledger.ui.model.Categories
import com.example.foldledger.ui.model.PaymentMethods
import java.text.SimpleDateFormat
import java.util.Locale

object ExportCsv {

    /** 导出全部记录为 CSV 到「下载」目录，返回 (Uri, 文件名)；失败返回 null */
    fun export(context: Context, list: List<Transaction>): Pair<Uri, String>? {
        val name = "ledger_export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(System.currentTimeMillis())}.csv"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { os ->
                // 写 BOM 以便 Excel 正确识别 UTF-8
                os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                os.bufferedWriter().use { w ->
                    w.appendLine("日期,类型,分类,金额,支付方式,备注")
                    list.sortedByDescending { it.date }.forEach { t ->
                        val date = DateUtils.formatYMD(t.date)
                        val type = if (t.type == "income") "收入" else "支出"
                        val cat = Categories.labelOf(t.category)
                        val pay = PaymentMethods.labelOf(t.paymentMethod)
                        val note = t.note.replace("\"", "\"\"")
                        w.appendLine("\"$date\",\"$type\",\"$cat\",${t.amount},\"$pay\",\"$note\"")
                    }
                }
            }
            uri to name
        } catch (e: Exception) {
            null
        }
    }
}
