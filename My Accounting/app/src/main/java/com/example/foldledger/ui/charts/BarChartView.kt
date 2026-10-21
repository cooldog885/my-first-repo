package com.example.foldledger.ui.charts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** 纯 Canvas 实现横向条形图（用于分类 TOP5 排行） */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Bar(val label: String, val value: Double, val color: Int)

    var bars: List<Bar> = emptyList()
        set(value) { field = value; invalidate() }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF455A64.toInt()
        textSize = 26f
        textAlign = Paint.Align.LEFT
    }
    private val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF607D8B.toInt()
        textSize = 24f
        textAlign = Paint.Align.LEFT
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("暂无数据", width / 2f, height / 2f, labelPaint)
            return
        }
        val padL = 90f
        val padR = 16f
        val padT = 12f
        val rowH = (height - padT) / bars.size.toFloat()
        val barH = rowH * 0.5f
        val maxV = bars.maxOf { it.value }.coerceAtLeast(1.0)
        val plotW = width - padL - padR

        bars.forEachIndexed { i, b ->
            val y = padT + rowH * i + rowH / 2
            // 分类名
            labelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(b.label, padL - 12f, y + 8f, labelPaint)
            // 条
            barPaint.color = b.color
            val w = (b.value / maxV * plotW).toFloat()
            canvas.drawRoundRect(padL, y - barH / 2, padL + w, y + barH / 2, 6f, 6f, barPaint)
            // 数值
            val vt = if (b.value % 1.0 == 0.0) b.value.toLong().toString() else "%.1f".format(b.value)
            valPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(vt, padL + w + 10f, y + 8f, valPaint)
        }
    }
}
