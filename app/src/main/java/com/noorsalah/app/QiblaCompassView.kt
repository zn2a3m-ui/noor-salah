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
        drawQiblaMarker(canvas, radius, gold, paleGold)
        canvas.restore()

        // The pointer is independent of the rose and always indicates Qibla.
        drawQiblaPointer(canvas, radius, gold, paleGold, dark)
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

    private fun drawQiblaMarker(canvas: Canvas, radius: Float, gold: Int, paleGold: Int) {
        val angle = Math.toRadians(qiblaBearing.toDouble() - 90.0)
        val r = radius * 0.91f
        val x = cos(angle).toFloat() * r
        val y = sin(angle).toFloat() * r
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(qiblaBearing)
        centerPaint.color = gold
        val marker = Path().apply {
            moveTo(0f, -7f * density)
            lineTo(5f * density, 0f)
            lineTo(0f, 7f * density)
            lineTo(-5f * density, 0f)
            close()
        }
        canvas.drawPath(marker, centerPaint)
        centerPaint.color = paleGold
        canvas.drawCircle(0f, 0f, 1.5f * density, centerPaint)
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
