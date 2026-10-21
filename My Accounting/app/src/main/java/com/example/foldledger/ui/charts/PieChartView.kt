package com.example.foldledger.ui.charts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** 纯 Canvas 实现的环形饼图 + 图例（无第三方依赖） */
class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Slice(val label: String, val value: Double, val color: Int)

    var slices: List<Slice> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF37474F.toInt()
        textSize = 28f
    }
    private val centerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF90A4AE.toInt()
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    private val centerValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF263238.toInt()
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    var centerLabel: String = "总支出"
        set(value) { field = value; invalidate() }
    var centerTotal: Double = 0.0
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (slices.isEmpty()) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("暂无支出数据", width / 2f, height / 2f, textPaint)
            return
        }
        val total = slices.sumOf { it.value }
        if (total <= 0) return

        val pad = 24f
        val legendH = (slices.size + 1) / 2 * 44f + 16f
        val pieAreaH = height - legendH
        val dia = min(width - pad * 2, pieAreaH - pad * 2)
        val cx = width / 2f
        val cy = pad + dia / 2f
        val r = dia / 2f
        val inner = r * 0.58f

        var start = -90f
        slices.forEach { s ->
            val sweep = (s.value / total * 360).toFloat()
            paint.color = s.color
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, start, sweep, true, paint)
            start += sweep
        }
        // 挖空中心
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, inner, paint)
        canvas.drawText(centerLabel, cx, cy - 8f, centerText)
        val totalStr = if (centerTotal % 1.0 == 0.0)
            centerTotal.toLong().toString() else "%.2f".format(centerTotal)
        canvas.drawText("¥$totalStr", cx, cy + 30f, centerValue)

        // 图例（两列）
        val lx = pad
        var ly = cy + r + 36f
        var col = 0
        slices.forEach { s ->
            val x = lx + if (col == 0) 0f else width / 2f
            paint.color = s.color
            canvas.drawRect(x, ly - 18f, x + 22f, ly + 4f, paint)
            val pct = (s.value / total * 100).toInt()
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("${s.label} $pct%", x + 30f, ly, textPaint)
            if (col == 0) col = 1 else { col = 0; ly += 44f }
        }
    }
}
