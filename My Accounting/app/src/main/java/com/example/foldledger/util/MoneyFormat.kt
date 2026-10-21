package com.example.foldledger.util

import java.util.Locale

object MoneyFormat {
    /** 格式化金额，去掉多余小数位 */
    fun format(value: Double): String {
        val v = if (value.isNaN()) 0.0 else value
        return if (v % 1.0 == 0.0) {
            String.format(Locale.CHINA, "%,.0f", v)
        } else {
            String.format(Locale.CHINA, "%,.2f", v)
        }
    }

    fun formatSigned(value: Double): String =
        (if (value >= 0) "+" else "-") + format(kotlin.math.abs(value))
}
