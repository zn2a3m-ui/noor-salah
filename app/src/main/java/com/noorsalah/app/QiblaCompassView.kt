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

/** Premium emerald-and-gold Qibla dial. The dial tracks true heading; the Qibla needle remains device-relative. */
class QiblaCompassView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private var heading = 0f
    private var qiblaBearing = 0f
    private var hasReading = false
    private var lightTheme = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create("serif", Typeface.BOLD) }
    private val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val needleEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val needlePath = Path()

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        contentDescription = "بوصلة مزخرفة لتحديد اتجاه القبلة"
    }

    fun updateReadings(trueHeading: Float, bearing: Float, readingAvailable: Boolean, useLightTheme: Boolean) {
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
        val radius = side * .475f
        val dark = !lightTheme
        val gold = if (dark) Color.rgb(220, 181, 99) else Color.rgb(143, 101, 35)
        val bright = if (dark) Color.rgb(255, 229, 169) else Color.rgb(177, 133, 59)
        val deep = if (dark) Color.rgb(2, 22, 19) else Color.rgb(224, 235, 224)
        val mid = if (dark) Color.rgb(7, 48, 38) else Color.rgb(244, 248, 239)
        val pale = if (dark) Color.rgb(212, 207, 177) else Color.rgb(77, 96, 79)
        val faint = if (dark) Color.argb(105, 225, 188, 111) else Color.argb(105, 143, 101, 35)

        canvas.save()
        canvas.translate(width / 2f, height / 2f)

        // Deep enamel face with a soft illuminated center.
        fill.shader = RadialGradient(0f, -radius * .22f, radius * 1.18f,
            intArrayOf(mid, deep, if (dark) Color.rgb(1, 13, 12) else Color.rgb(211, 225, 211)),
            floatArrayOf(0f, .72f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, radius, fill)
        fill.shader = null

        // Layered machined bezel.
        stroke.color = gold; stroke.alpha = 245; stroke.strokeWidth = 2.8f * density
        canvas.drawCircle(0f, 0f, radius * .985f, stroke)
        stroke.alpha = 115; stroke.strokeWidth = 1f * density
        canvas.drawCircle(0f, 0f, radius * .948f, stroke)
        stroke.alpha = 175; stroke.strokeWidth = 1.35f * density
        canvas.drawCircle(0f, 0f, radius * .895f, stroke)
        stroke.alpha = 75; stroke.strokeWidth = .8f * density
        canvas.drawCircle(0f, 0f, radius * .535f, stroke)

        // Tiny bezel studs make the outer ring feel like a crafted instrument.
        for (i in 0 until 60 step 5) {
            val a = Math.toRadians(i * 6.0 - 90.0)
            val r = radius * .965f
            fill.color = if (i % 10 == 0) bright else faint
            canvas.drawCircle(cos(a).toFloat() * r, sin(a).toFloat() * r,
                if (i % 10 == 0) 1.8f * density else 1.05f * density, fill)
        }

        canvas.save()
        canvas.rotate(-heading)
        drawGraduations(canvas, radius, gold, faint)
        drawCardinalOrnaments(canvas, radius, bright, pale, faint)
        drawRose(canvas, radius, gold, bright, faint)
        if (hasReading) drawKaabaBearing(canvas, radius, gold, bright, dark)
        canvas.restore()

        if (hasReading) drawQiblaNeedle(canvas, radius, gold, bright, dark)
        drawHub(canvas, radius, gold, bright, dark)
        if (!hasReading) {
            label.color = pale
            label.textSize = radius * .052f
            canvas.drawText("حرّك الهاتف للمعايرة", 0f, radius * .70f, label)
        }
        canvas.restore()
    }

    private fun drawGraduations(canvas: Canvas, r: Float, gold: Int, faint: Int) {
        for (degree in 0 until 360 step 3) {
            val major = degree % 30 == 0
            val medium = degree % 15 == 0
            val inner = when { major -> r * .765f; medium -> r * .795f; else -> r * .832f }
            val outer = r * .875f
            val a = Math.toRadians(degree - 90.0)
            tick.color = if (major) gold else faint
            tick.strokeWidth = when { major -> 2.0f * density; medium -> 1.15f * density; else -> .62f * density }
            canvas.drawLine(cos(a).toFloat() * inner, sin(a).toFloat() * inner,
                cos(a).toFloat() * outer, sin(a).toFloat() * outer, tick)
        }
        label.color = faint
        label.textSize = r * .043f
        for (degree in 15 until 360 step 15) {
            if (degree % 30 == 0) continue
            val a = Math.toRadians(degree - 90.0)
            val rr = r * .715f
            canvas.drawText(degree.toString(), cos(a).toFloat() * rr,
                sin(a).toFloat() * rr - (label.ascent() + label.descent()) / 2f, label)
        }
    }

    private fun drawCardinalOrnaments(canvas: Canvas, r: Float, bright: Int, pale: Int, faint: Int) {
        val names = listOf("N", "E", "S", "W")
        names.forEachIndexed { index, name ->
            val a = Math.toRadians(index * 90.0 - 90.0)
            val rr = r * .655f
            val x = cos(a).toFloat() * rr
            val y = sin(a).toFloat() * rr
            label.color = if (index == 0) bright else pale
            label.textSize = if (index == 0) r * .125f else r * .095f
            canvas.drawText(name, x, y - (label.ascent() + label.descent()) / 2f, label)
            // Fine diamond separator outside each cardinal letter.
            val dr = r * .595f
            val dx = cos(a).toFloat() * dr
            val dy = sin(a).toFloat() * dr
            fill.color = if (index == 0) bright else faint
            canvas.save(); canvas.translate(dx, dy); canvas.rotate(45f)
            canvas.drawRect(-2f * density, -2f * density, 2f * density, 2f * density, fill)
            canvas.restore()
        }
    }

    private fun drawRose(canvas: Canvas, r: Float, gold: Int, bright: Int, faint: Int) {
        // Eight-point compass star, alternating long and short blades.
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0 - 90.0)
            val path = Path()
            val tip = r * if (i % 2 == 0) .485f else .425f
            val base = r * .075f
            val px = cos(a).toFloat(); val py = sin(a).toFloat()
            val nx = -py; val ny = px
            path.moveTo(px * tip, py * tip)
            path.lineTo(px * base + nx * base * .58f, py * base + ny * base * .58f)
            path.lineTo(-px * base * .58f + nx * base * .58f, -py * base * .58f + ny * base * .58f)
            path.lineTo(-px * base - nx * base * .58f, -py * base - ny * base * .58f)
            path.close()
            fill.shader = LinearGradient(0f, -tip, 0f, tip,
                intArrayOf(if (i % 2 == 0) bright else gold, faint), null, Shader.TileMode.CLAMP)
            canvas.drawPath(path, fill)
            fill.shader = null
            needleEdge.color = gold; needleEdge.strokeWidth = .8f * density
            canvas.drawPath(path, needleEdge)
        }
        detail.color = faint; detail.strokeWidth = .8f * density
        canvas.drawCircle(0f, 0f, r * .365f, detail)
        canvas.drawCircle(0f, 0f, r * .335f, detail)
        // Eight fine rays behind the central medallion.
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            canvas.drawLine(cos(a).toFloat() * r * .27f, sin(a).toFloat() * r * .27f,
                cos(a).toFloat() * r * .325f, sin(a).toFloat() * r * .325f, detail)
        }
    }

    private fun drawKaabaBearing(canvas: Canvas, r: Float, gold: Int, bright: Int, dark: Boolean) {
        val a = Math.toRadians(qiblaBearing - 90.0)
        val x = cos(a).toFloat() * r * .91f
        val y = sin(a).toFloat() * r * .91f
        canvas.save(); canvas.translate(x, y)
        fill.color = Color.argb(55, 255, 215, 135)
        canvas.drawCircle(0f, 0f, 13f * density, fill)
        fill.color = if (dark) Color.rgb(5, 25, 21) else Color.rgb(249, 248, 236)
        canvas.drawRoundRect(-7f * density, -8f * density, 7f * density, 8f * density,
            2f * density, 2f * density, fill)
        fill.color = gold
        canvas.drawRect(-7f * density, -3f * density, 7f * density, 1f * density, fill)
        canvas.drawRect(-7f * density, -7f * density, 7f * density, -5f * density, fill)
        fill.color = bright
        canvas.drawRect(-7f * density, 5f * density, 7f * density, 7f * density, fill)
        stroke.color = bright; stroke.strokeWidth = 1.2f * density
        canvas.drawRoundRect(-7f * density, -8f * density, 7f * density, 8f * density,
            2f * density, 2f * density, stroke)
        canvas.restore()
    }

    private fun drawQiblaNeedle(canvas: Canvas, r: Float, gold: Int, bright: Int, dark: Boolean) {
        canvas.save()
        canvas.rotate(qiblaBearing - heading)
        needlePath.reset()
        // Long, sculpted spear with a split tip and ornamental shoulders.
        needlePath.moveTo(0f, -r * .555f)
        needlePath.cubicTo(r * .055f, -r * .40f, r * .052f, -r * .24f, r * .105f, -r * .105f)
        needlePath.lineTo(r * .035f, -r * .045f)
        needlePath.lineTo(0f, -r * .075f)
        needlePath.lineTo(-r * .035f, -r * .045f)
        needlePath.lineTo(-r * .105f, -r * .105f)
        needlePath.cubicTo(-r * .052f, -r * .24f, -r * .055f, -r * .40f, 0f, -r * .555f)
        needlePath.close()
        needle.shader = LinearGradient(-r * .11f, 0f, r * .11f, 0f,
            intArrayOf(bright, gold, Color.rgb(112, 72, 26)), null, Shader.TileMode.CLAMP)
        needle.setShadowLayer(8f * density, 0f, 3f * density, Color.argb(165, 0, 0, 0))
        canvas.drawPath(needlePath, needle)
        needle.shader = null
        needle.clearShadowLayer()
        needleEdge.color = if (dark) Color.rgb(255, 239, 198) else Color.rgb(107, 72, 29)
        needleEdge.strokeWidth = 1.25f * density
        canvas.drawPath(needlePath, needleEdge)
        detail.color = if (dark) Color.rgb(255, 239, 196) else Color.rgb(117, 79, 30)
        detail.strokeWidth = 1.4f * density
        canvas.drawLine(0f, -r * .12f, 0f, r * .265f, detail)
        fill.color = gold
        canvas.drawCircle(0f, r * .29f, r * .052f, fill)
        stroke.color = bright; stroke.strokeWidth = 1.5f * density
        canvas.drawCircle(0f, r * .29f, r * .052f, stroke)
        fill.color = if (dark) Color.rgb(9, 52, 40) else Color.rgb(237, 242, 225)
        canvas.drawCircle(0f, r * .29f, r * .025f, fill)
        canvas.restore()
    }

    private fun drawHub(canvas: Canvas, r: Float, gold: Int, bright: Int, dark: Boolean) {
        fill.color = if (dark) Color.rgb(3, 30, 25) else Color.rgb(248, 249, 240)
        canvas.drawCircle(0f, 0f, r * .105f, fill)
        stroke.color = gold; stroke.strokeWidth = 2.3f * density
        canvas.drawCircle(0f, 0f, r * .105f, stroke)
        stroke.color = bright; stroke.strokeWidth = .8f * density
        canvas.drawCircle(0f, 0f, r * .125f, stroke)
        fill.shader = RadialGradient(-r * .02f, -r * .025f, r * .075f,
            intArrayOf(bright, gold, Color.rgb(107, 75, 31)), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, r * .043f, fill)
        fill.shader = null
        fill.color = if (dark) Color.rgb(8, 45, 35) else Color.rgb(243, 244, 229)
        canvas.drawCircle(0f, 0f, r * .018f, fill)
    }

    private fun normalize(value: Float): Float = ((value % 360f) + 360f) % 360f
}
