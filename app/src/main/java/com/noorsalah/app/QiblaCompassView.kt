package com.noorsalah.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * A self-contained compass face. The compass rose rotates with true heading,
 * while the Qibla pointer is drawn in device coordinates.
 */
class QiblaCompassView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private var heading = 0f
    private var qiblaBearing = 0f
    private var hasReading = false
    private var lightTheme = false

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(7f * density, 0f, 3f * density, Color.argb(150, 0, 0, 0))
    }
    private val pointerOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.15f * density
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pointerPath = Path()

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        contentDescription = "بوصلة تحديد اتجاه القبلة"
    }

    fun updateReadings(
        trueHeading: Float,
        bearing: Float,
        readingAvailable: Boolean,
        useLightTheme: Boolean
    ) {
        heading = normalize(trueHeading)
        qiblaBearing = normalize(bearing)
        hasReading = readingAvailable
        lightTheme = useLightTheme
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height).toFloat()
        if (side <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = side * 0.475f
        val dark = !lightTheme
        val gold = if (dark) Color.rgb(222, 185, 105) else Color.rgb(151, 111, 42)
        val paleGold = if (dark) Color.rgb(247, 222, 164) else Color.rgb(190, 151, 78)
        val green = if (dark) Color.rgb(7, 49, 39) else Color.rgb(226, 237, 226)
        val deepGreen = if (dark) Color.rgb(3, 29, 25) else Color.rgb(244, 248, 241)
        val muted = if (dark) Color.rgb(190, 201, 185) else Color.rgb(91, 111, 96)
        val faint = if (dark) Color.argb(105, 222, 185, 105) else Color.argb(115, 151, 111, 42)

        canvas.save()
        canvas.translate(cx, cy)

        outerPaint.shader = RadialGradient(
            0f, -radius * 0.18f, radius * 1.12f,
            intArrayOf(green, deepGreen, if (dark) Color.rgb(2, 20, 18) else Color.rgb(215, 229, 216)),
            floatArrayOf(0f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(0f, 0f, radius, outerPaint)
        outerPaint.shader = null

        ringPaint.color = gold
        ringPaint.alpha = 225
        ringPaint.strokeWidth = 2.1f * density
        canvas.drawCircle(0f, 0f, radius * 0.985f, ringPaint)
        ringPaint.strokeWidth = 1f * density
        ringPaint.alpha = 150
        canvas.drawCircle(0f, 0f, radius * 0.94f, ringPaint)
        ringPaint.alpha = 105
        canvas.drawCircle(0f, 0f, radius * 0.78f, ringPaint)

        // The rose and all geographic labels rotate opposite to the phone.
        canvas.save()
        canvas.rotate(-heading)
        drawCompassRose(canvas, radius, gold, paleGold, muted, faint)
        if (hasReading) drawQiblaMarker(canvas, radius, gold, paleGold, dark)
        canvas.restore()

        // Never show a directional pointer until the device provides a valid heading.
        if (hasReading) drawQiblaPointer(canvas, radius, gold, paleGold, dark)
        drawCenterMedallion(canvas, radius, gold, paleGold, dark)

        canvas.restore()
    }

    private fun drawCompassRose(
        canvas: Canvas,
        radius: Float,
        gold: Int,
        paleGold: Int,
        muted: Int,
        faint: Int
    ) {
        val tickRadius = radius * 0.865f
        for (degree in 0 until 360 step 5) {
            val major = degree % 30 == 0
            val medium = degree % 10 == 0
            val inner = when {
                major -> radius * 0.755f
                medium -> radius * 0.79f
                else -> radius * 0.825f
            }
            tickPaint.color = if (major) gold else faint
            tickPaint.strokeWidth = when {
                major -> 2.1f * density
                medium -> 1.35f * density
                else -> 0.8f * density
            }
            val angle = Math.toRadians(degree.toDouble() - 90.0)
            val x1 = cos(angle).toFloat() * inner
            val y1 = sin(angle).toFloat() * inner
            val x2 = cos(angle).toFloat() * tickRadius
            val y2 = sin(angle).toFloat() * tickRadius
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }

        val labels = listOf("N", "E", "S", "W")
        val labelRadius = radius * 0.665f
        textPaint.textSize = radius * 0.105f
        labels.forEachIndexed { index, label ->
            val angle = Math.toRadians(index * 90.0 - 90.0)
            val x = cos(angle).toFloat() * labelRadius
            val y = sin(angle).toFloat() * labelRadius - (textPaint.ascent() + textPaint.descent()) / 2f
            textPaint.color = if (index == 0) paleGold else muted
            textPaint.textSize = if (index == 0) radius * 0.12f else radius * 0.095f
            canvas.drawText(label, x, y, textPaint)
        }

        // Fine inner compass ornament.
        detailPaint.color = faint
        detailPaint.strokeWidth = 0.9f * density
        canvas.drawCircle(0f, 0f, radius * 0.59f, detailPaint)
        canvas.drawCircle(0f, 0f, radius * 0.555f, detailPaint)

        // Discreet degree labels between the cardinal directions.
        textPaint.color = muted
        textPaint.textSize = radius * 0.043f
        for (degree in 30..330 step 30) {
            if (degree % 90 == 0) continue
            val angle = Math.toRadians(degree.toDouble() - 90.0)
            val labelRadius = radius * 0.705f
            val x = cos(angle).toFloat() * labelRadius
            val y = sin(angle).toFloat() * labelRadius - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(degree.toString(), x, y, textPaint)
        }

        // Eight-point Islamic geometric star, kept subtle so it never obscures the pointer.
        val starPath = Path()
        for (point in 0 until 16) {
            val angle = Math.toRadians(point * 22.5 - 90.0)
            val starRadius = radius * if (point % 2 == 0) 0.445f else 0.385f
            val x = cos(angle).toFloat() * starRadius
            val y = sin(angle).toFloat() * starRadius
            if (point == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
        detailPaint.color = faint
        detailPaint.strokeWidth = 0.8f * density
        canvas.drawPath(starPath, detailPaint)
        for (point in 0 until 8) {
            val angle = Math.toRadians(point * 45.0 - 90.0)
            val inner = radius * 0.31f
            val outer = radius * 0.385f
            canvas.drawLine(
                cos(angle).toFloat() * inner, sin(angle).toFloat() * inner,
                cos(angle).toFloat() * outer, sin(angle).toFloat() * outer,
                detailPaint
            )
        }

        val northStar = Path().apply {
            moveTo(0f, -radius * 0.49f)
            lineTo(radius * 0.045f, -radius * 0.39f)
            lineTo(0f, -radius * 0.415f)
            lineTo(-radius * 0.045f, -radius * 0.39f)
            close()
        }
        centerPaint.color = paleGold
        canvas.drawPath(northStar, centerPaint)
    }

    private fun drawQiblaMarker(
        canvas: Canvas,
        radius: Float,
        gold: Int,
        paleGold: Int,
        dark: Boolean
    ) {
        val angle = Math.toRadians(qiblaBearing.toDouble() - 90.0)
        val r = radius * 0.905f
        val x = cos(angle).toFloat() * r
        val y = sin(angle).toFloat() * r
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(qiblaBearing)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gold
            alpha = 48
        }
        canvas.drawCircle(0f, 0f, 12f * density, halo)
        centerPaint.color = if (dark) Color.rgb(8, 22, 19) else Color.rgb(250, 248, 237)
        canvas.drawRoundRect(
            -7f * density, -8f * density, 7f * density, 8f * density,
            2f * density, 2f * density, centerPaint
        )
        centerPaint.color = gold
        canvas.drawRect(-7f * density, -2f * density, 7f * density, 1f * density, centerPaint)
        canvas.drawRect(-7f * density, -6f * density, 7f * density, -4f * density, centerPaint)
        canvas.drawRect(-7f * density, 5f * density, 7f * density, 7f * density, centerPaint)
        ringPaint.color = paleGold
        ringPaint.alpha = 245
        ringPaint.strokeWidth = 1.1f * density
        canvas.drawRoundRect(
            -7f * density, -8f * density, 7f * density, 8f * density,
            2f * density, 2f * density, ringPaint
        )
        canvas.restore()
    }

    private fun drawQiblaPointer(
        canvas: Canvas,
        radius: Float,
        gold: Int,
        paleGold: Int,
        dark: Boolean
    ) {
        canvas.save()
        canvas.rotate(qiblaBearing - heading)

        pointerPath.reset()
        pointerPath.moveTo(0f, -radius * 0.49f)
        pointerPath.cubicTo(
            radius * 0.035f, -radius * 0.34f,
            radius * 0.055f, -radius * 0.20f,
            radius * 0.075f, -radius * 0.075f
        )
        pointerPath.lineTo(radius * 0.025f, radius * 0.015f)
        pointerPath.lineTo(0f, -radius * 0.025f)
        pointerPath.lineTo(-radius * 0.025f, radius * 0.015f)
        pointerPath.lineTo(-radius * 0.075f, -radius * 0.075f)
        pointerPath.cubicTo(
            -radius * 0.055f, -radius * 0.20f,
            -radius * 0.035f, -radius * 0.34f,
            0f, -radius * 0.49f
        )
        pointerPath.close()

        pointerPaint.shader = LinearGradient(
            -radius * 0.08f, 0f, radius * 0.08f, 0f,
            intArrayOf(paleGold, gold, Color.rgb(132, 91, 35)),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(pointerPath, pointerPaint)
        pointerPaint.shader = null
        pointerOutline.color = if (dark) Color.rgb(255, 235, 185) else Color.rgb(120, 82, 32)
        canvas.drawPath(pointerPath, pointerOutline)

        // Ornamental shaft and the lower counterweight.
        detailPaint.color = if (dark) Color.rgb(255, 226, 161) else Color.rgb(126, 91, 43)
        detailPaint.strokeWidth = 1.4f * density
        canvas.drawLine(0f, -radius * 0.12f, 0f, radius * 0.27f, detailPaint)
        canvas.drawCircle(0f, radius * 0.30f, radius * 0.055f, pointerOutline)
        centerPaint.color = gold
        canvas.drawCircle(0f, radius * 0.30f, radius * 0.032f, centerPaint)
        centerPaint.color = if (dark) Color.rgb(18, 67, 51) else Color.rgb(238, 242, 226)
        canvas.drawCircle(0f, radius * 0.30f, radius * 0.015f, centerPaint)
        canvas.restore()
    }

    private fun drawCenterMedallion(
        canvas: Canvas,
        radius: Float,
        gold: Int,
        paleGold: Int,
        dark: Boolean
    ) {
        centerPaint.color = if (dark) Color.rgb(4, 35, 29) else Color.rgb(247, 249, 241)
        canvas.drawCircle(0f, 0f, radius * 0.095f, centerPaint)
        ringPaint.color = gold
        ringPaint.alpha = 240
        ringPaint.strokeWidth = 2f * density
        canvas.drawCircle(0f, 0f, radius * 0.095f, ringPaint)
        centerPaint.color = paleGold
        canvas.drawCircle(0f, 0f, radius * 0.025f, centerPaint)
        ringPaint.alpha = 100
        ringPaint.strokeWidth = 0.8f * density
        canvas.drawCircle(0f, 0f, radius * 0.125f, ringPaint)
    }

    private fun normalize(value: Float): Float = ((value % 360f) + 360f) % 360f
}
