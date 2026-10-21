package com.example.foldledger.ui.charts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** 纯 Canvas 实现的折线图（带坐标轴 / 网格 / 数据点） */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Point(val label: String, val value: Double)

    var points: List<Point> = emptyList()
        set(value) { field = value; invalidate() }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCFD8DC.toInt()
        strokeWidth = 2f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFECEFF1.toInt()
        strokeWidth = 1f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF42A5F5.toInt()
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E88E5.toInt()
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF607D8B.toInt()
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    private val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF37474F.toInt()
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val padL = 56f
        val padR = 20f
        val padT = 28f
        val padB = 44f
        val plotW = width - padL - padR
        val plotH = height - padT - padB

        if (points.isEmpty()) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("暂无数据", width / 2f, height / 2f, labelPaint)
            return
        }

        val maxV = (points.maxOfOrNull { it.value } ?: 0.0).coerceAtLeast(1.0)
        val stepX = if (points.size > 1) plotW / (points.size - 1) else plotW / 2

        // 横向网格 + Y 轴刻度
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = padT + plotH * i / gridLines
            canvas.drawLine(padL, y, width - padR, y, gridPaint)
            val v = maxV * (gridLines - i) / gridLines
            labelPaint.textAlign = Paint.Align.RIGHT
            val txt = if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(v)
            canvas.drawText(txt, padL - 8f, y + 8f, labelPaint)
        }

        // 折线
        val pts = points.mapIndexed { i, p ->
            val x = padL + stepX * i
            val y = padT + plotH * (1 - p.value / maxV).toFloat()
            x to y
        }
        for (i in 1 until pts.size) {
            canvas.drawLine(pts[i - 1].first, pts[i - 1].second, pts[i].first, pts[i].second, linePaint)
        }
        pts.forEachIndexed { i, (x, y) ->
            canvas.drawCircle(x, y, 6f, dotPaint)
            val v = points[i].value
            val vt = if (v % 1.0 == 0.0) v.toLong().toString() else "%.0f".format(v)
            val vy = (y - 14f).coerceAtLeast(padT + 8f)
            canvas.drawText(vt, x, vy, valPaint)
            // X 轴标签（隔一个显示避免拥挤）
            if (points.size <= 6 || i % 2 == 0) {
                labelPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(points[i].label, x, height - padB + 28f, labelPaint)
            }
        }
    }
}
