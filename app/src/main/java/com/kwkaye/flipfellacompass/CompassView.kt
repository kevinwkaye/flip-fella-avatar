package com.kwkaye.flipfellacompass

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SIZE = 480f
private const val C = 240f

private const val BG = 0xFF070708.toInt()
private const val DISC = 0xFF0F0F11.toInt()
private const val CARD = 0xFF1B1B1B.toInt()
private const val INK = 0xFFF3F0E8.toInt()
private const val MUTED = 0xFFA0A0A6.toInt()
private const val DIM = 0xFF6E6E76.toInt()
private const val ACCENT = 0xFFFF6A1A.toInt()
private const val BLUE = 0xFF4FB3FF.toInt()
private const val BLUE_BG = 0xFF10283A.toInt()
private const val PEBBLE = 0xFF4B57FF.toInt()
private const val PEBBLE_SHADE = 0xFF2329A8.toInt()
private const val PUPIL = 0xFF0B0B12.toInt()

private const val FLIP_MS = 180L
private const val ON_COURSE_DEG = 3f

/**
 * Draws the whole compass on a 480 x 480 grid scaled to the screen, matching
 * mockups F (live), G (bearing set) and H (calibrate).
 */
@SuppressLint("ViewConstructor")
class CompassView(context: Context) : View(context) {

    var bearing: Float? = null
    var onBearingChanged: (Float?) -> Unit = {}
    var onCourse: () -> Unit = {}
    var sensorMissing = false

    private var targetHeading = 0f
    private var shownHeading = 0f
    private var hasHeading = false

    private var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    private var poorSince = 0L
    private var calibrateDismissed = false

    private var wasOnCourse = false

    // Flip cards for the three heading digits.
    private val cardShown = charArrayOf('0', '0', '0')
    private val cardFrom = charArrayOf('0', '0', '0')
    private val cardStart = LongArray(3)

    // Pebble's blink timer.
    private var nextBlink = SystemClock.uptimeMillis() + 2500L

    // Fonts
    private val cardFont: Typeface = font(R.font.rc_extrabold)
    private val labelFont: Typeface = font(R.font.rc_bold)
    private val compassFont: Typeface = font(R.font.saira_medium)

    // Paints
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val pebbleBody = Path().apply {
        moveTo(100f, 22f)
        cubicTo(146f, 20f, 176f, 54f, 174f, 100f)
        cubicTo(172f, 146f, 140f, 176f, 98f, 174f)
        cubicTo(54f, 172f, 24f, 142f, 26f, 98f)
        cubicTo(28f, 56f, 58f, 24f, 100f, 22f)
        close()
    }
    private val lubber = Path().apply { moveTo(234f, 3f); lineTo(246f, 3f); lineTo(240f, 14f); close() }
    private val figureEight = Path().apply {
        moveTo(90f, 40f)
        cubicTo(60f, 0f, 14f, 4f, 14f, 40f)
        cubicTo(14f, 76f, 60f, 80f, 90f, 40f)
        cubicTo(120f, 0f, 166f, 4f, 166f, 40f)
        cubicTo(166f, 76f, 120f, 80f, 90f, 40f)
        close()
    }

    init {
        setBackgroundColor(Color.BLACK)
    }

    private fun font(id: Int): Typeface =
        try { resources.getFont(id) } catch (e: Exception) { Typeface.create("sans-serif-condensed", Typeface.BOLD) }

    // ---------------------------------------------------------------- input

    fun setHeading(deg: Float) {
        targetHeading = deg
        if (!hasHeading) {
            shownHeading = deg
            hasHeading = true
        }
        postInvalidateOnAnimation()
    }

    fun setAccuracy(value: Int) {
        accuracy = value
        val poor = value <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
        poorSince = if (poor) (if (poorSince == 0L) SystemClock.uptimeMillis() else poorSince) else 0L
        if (!poor) calibrateDismissed = false
        postInvalidateOnAnimation()
    }

    private fun showCalibrate(): Boolean =
        !calibrateDismissed && poorSince != 0L && SystemClock.uptimeMillis() - poorSince > 2000L

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        if (showCalibrate()) {
            calibrateDismissed = true
        } else if (bearing == null) {
            bearing = (shownHeading.roundToInt() % 360).toFloat()
            wasOnCourse = true
            onBearingChanged(bearing)
        } else {
            bearing = null
            onBearingChanged(null)
        }
        performClick()
        invalidate()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        val side = min(width, height).toFloat()
        canvas.save()
        canvas.translate((width - side) / 2f, (height - side) / 2f)
        canvas.scale(side / SIZE, side / SIZE)

        // Ease the shown heading towards the sensor so the ring glides.
        val delta = wrap(targetHeading - shownHeading)
        shownHeading = norm(shownHeading + delta * 0.18f)

        fill.color = BG
        canvas.drawCircle(C, C, 240f, fill)

        if (showCalibrate()) drawCalibrate(canvas) else drawCompass(canvas)

        canvas.restore()
        if (abs(delta) > 0.05f || isAnimating()) postInvalidateOnAnimation()
        else postInvalidateDelayed(250L)
    }

    private fun isAnimating(): Boolean {
        val now = SystemClock.uptimeMillis()
        return cardStart.any { now - it < FLIP_MS } || now >= nextBlink - 50
    }

    private fun drawCompass(c: Canvas) {
        fill.color = DISC
        c.drawCircle(C, C, 186f, fill)
        stroke.color = 0xFF1F1F23.toInt(); stroke.strokeWidth = 2f
        c.drawCircle(C, C, 186f, stroke)

        drawBezel(c, shownHeading, bearing)

        val b = bearing
        val diff = if (b != null) wrap(b - shownHeading) else 0f
        val onCourseNow = b != null && abs(diff) <= ON_COURSE_DEG
        if (onCourseNow && !wasOnCourse) onCourse()
        wasOnCourse = onCourseNow

        // Label above the cards
        if (b == null) {
            drawText(c, "HEADING", C, 120f, 16f, labelFont, MUTED, spacing = 0.19f)
        } else {
            val bi = b.roundToInt() % 360
            drawText(c, "BEARING %03d° %s".format(bi, direction(bi)), C, 120f, 16f, labelFont, BLUE, spacing = 0.12f)
        }

        // Heading flip cards
        val heading = if (hasHeading) shownHeading.roundToInt() % 360 else 0
        val digits = "%03d".format(heading)
        val now = SystemClock.uptimeMillis()
        val lefts = floatArrayOf(131f, 201f, 271f)
        for (i in 0 until 3) {
            if (digits[i] != cardShown[i] && now - cardStart[i] >= FLIP_MS) {
                cardFrom[i] = cardShown[i]
                cardShown[i] = digits[i]
                cardStart[i] = now
            }
            val p = ((now - cardStart[i]).toFloat() / FLIP_MS).coerceIn(0f, 1f)
            val label = if (hasHeading) cardShown[i].toString() else "-"
            val from = if (hasHeading) cardFrom[i].toString() else "-"
            drawFlipCard(c, RectF(lefts[i], 142f, lefts[i] + 64f, 238f), from, label, p)
        }
        drawText(c, "°", 352f, 160f, 30f, cardFont, ACCENT)

        // Direction card, or the turn pill when a bearing is set
        if (b == null) {
            drawCard(c, RectF(201f, 250f, 279f, 290f), 7f, direction(heading), 28f)
        } else {
            fill.color = BLUE_BG
            c.drawRoundRect(RectF(150f, 250f, 330f, 290f), 20f, 20f, fill)
            val turn = when {
                onCourseNow -> "On course"
                diff > 0 -> "%d° right".format(abs(diff).roundToInt())
                else -> "%d° left".format(abs(diff).roundToInt())
            }
            drawText(c, turn, C, 270f, 22f, labelFont, BLUE)
        }

        // Pebble looks at the bearing marker, or at north when there isn't one.
        val lookAt = wrap((b ?: 0f) - shownHeading)
        drawPebble(c, 190f, 312f, lookAt, happy = onCourseNow)

        val hint = when {
            sensorMissing -> "No compass sensor found"
            b == null -> "Tap to set a bearing"
            else -> "Tap again to clear"
        }
        drawText(c, hint, C, 432f, 15f, labelFont, DIM)
    }

    private fun drawBezel(c: Canvas, heading: Float, bearing: Float?) {
        stroke.color = 0xFF141417.toInt(); stroke.strokeWidth = 38f
        c.drawCircle(C, C, 219f, stroke)
        stroke.color = 0xFF2A2A2E.toInt(); stroke.strokeWidth = 1.5f
        c.drawCircle(C, C, 200f, stroke)

        c.save()
        c.rotate(-heading, C, C)
        stroke.strokeCap = Paint.Cap.ROUND
        for (d in 0 until 360 step 5) {
            val major = d % 45 == 0
            val mid = d % 15 == 0
            stroke.color = if (major || mid) 0xFF8A8A90.toInt() else 0xFF3E3E44.toInt()
            stroke.strokeWidth = if (major) 2f else 1.4f
            radial(c, d.toFloat(), 237f, if (major) 228f else if (mid) 231f else 233f)
        }
        val points = listOf(0 to "N", 45 to "NE", 90 to "E", 135 to "SE", 180 to "S", 225 to "SW", 270 to "W", 315 to "NW")
        for ((d, name) in points) {
            val cardinal = name.length == 1
            c.save()
            c.rotate(d.toFloat(), C, C)
            drawText(c, name, C, C - 214f, if (cardinal) 25f else 15f, compassFont,
                when { name == "N" -> ACCENT; cardinal -> INK; else -> MUTED })
            c.restore()
        }
        for (d in 30 until 360 step 30) {
            if (d % 90 == 0) continue
            c.save()
            c.rotate(d.toFloat(), C, C)
            drawText(c, d.toString(), C, C - 216f, 13f, compassFont, DIM)
            c.restore()
        }
        if (bearing != null) {
            c.save()
            c.rotate(bearing, C, C)
            stroke.color = BLUE; stroke.strokeWidth = 3f
            c.drawCircle(C, C - 219f, 9f, stroke)
            c.restore()
        }
        c.restore()

        fill.color = ACCENT
        c.drawPath(lubber, fill)
    }

    private fun drawCalibrate(c: Canvas) {
        stroke.color = 0xFF141417.toInt(); stroke.strokeWidth = 38f
        c.drawCircle(C, C, 219f, stroke)
        fill.color = DISC
        c.drawCircle(C, C, 186f, fill)
        fill.color = ACCENT
        c.drawPath(lubber, fill)

        drawPebble(c, 190f, 96f, 0f, happy = false, dizzy = true)

        c.save()
        c.translate(150f, 216f)
        stroke.color = ACCENT; stroke.strokeWidth = 5f; stroke.strokeCap = Paint.Cap.ROUND
        stroke.pathEffect = DashPathEffect(floatArrayOf(1f, 12f), 0f)
        c.drawPath(figureEight, stroke)
        stroke.pathEffect = null
        // A dot that travels round the figure of eight.
        val t = (SystemClock.uptimeMillis() % 2400L) / 2400f
        val pos = FloatArray(2)
        android.graphics.PathMeasure(figureEight, false).let { it.getPosTan(it.length * t, pos, null) }
        fill.color = ACCENT
        c.drawCircle(pos[0], pos[1], 8f, fill)
        c.restore()

        drawText(c, "Feeling dizzy", C, 325f, 30f, labelFont, INK)
        drawText(c, "Wave your wrist in a figure", C, 366f, 18f, labelFont, MUTED)
        drawText(c, "of eight to calibrate", C, 390f, 18f, labelFont, MUTED)
        postInvalidateOnAnimation()
    }

    /** Pebble in his round window. lookDeg: where on screen to look, 0 = straight up, clockwise. */
    private fun drawPebble(c: Canvas, x: Float, y: Float, lookDeg: Float, happy: Boolean, dizzy: Boolean = false) {
        c.save()
        c.translate(x, y)
        c.scale(100f / 88f, 100f / 88f)
        fill.color = 0xFF151518.toInt()
        c.drawCircle(44f, 44f, 43f, fill)
        stroke.color = 0xFF2A2A2E.toInt(); stroke.strokeWidth = 2f
        c.drawCircle(44f, 44f, 43f, stroke)
        c.clipPath(Path().apply { addCircle(44f, 44f, 42f, Path.Direction.CW) })
        c.translate(-6f, 30f)
        c.scale(0.5f, 0.5f)

        c.save(); c.translate(7f, 9f); fill.color = PEBBLE_SHADE; c.drawPath(pebbleBody, fill); c.restore()
        fill.color = PEBBLE
        c.drawPath(pebbleBody, fill)

        stroke.color = PUPIL; stroke.strokeWidth = 6f; stroke.strokeCap = Paint.Cap.ROUND
        val eyes = arrayOf(floatArrayOf(78f, 80f), floatArrayOf(122f, 80f))
        val now = SystemClock.uptimeMillis()
        val blinking = now in nextBlink..(nextBlink + 130L)
        if (now > nextBlink + 130L) nextBlink = now + 2500L + (Math.random() * 3500).toLong()

        when {
            happy -> {
                for (e in eyes) c.drawPath(Path().apply { moveTo(e[0] - 16, e[1] + 6); quadTo(e[0], e[1] - 10, e[0] + 16, e[1] + 6) }, stroke)
            }
            blinking && !dizzy -> {
                for (e in eyes) c.drawPath(Path().apply { moveTo(e[0] - 16, e[1]); quadTo(e[0], e[1] + 8, e[0] + 16, e[1]) }, stroke)
            }
            else -> {
                fill.color = Color.WHITE
                for (e in eyes) c.drawCircle(e[0], e[1], 18f, fill)
                if (dizzy) {
                    stroke.strokeWidth = 3.5f
                    for (e in eyes) {
                        val spiral = Path()
                        var a = 0.0
                        spiral.moveTo(e[0], e[1])
                        while (a < Math.PI * 5) {
                            val r = (a / (Math.PI * 5) * 12).toFloat()
                            spiral.lineTo(e[0] + cos(a).toFloat() * r, e[1] + sin(a).toFloat() * r)
                            a += 0.2
                        }
                        c.drawPath(spiral, stroke)
                    }
                    stroke.strokeWidth = 6f
                } else {
                    // Pupils point the way he's looking.
                    val r = Math.toRadians(lookDeg.toDouble())
                    val dx = (sin(r) * 8).toFloat()
                    val dy = (-cos(r) * 8).toFloat()
                    fill.color = PUPIL
                    for (e in eyes) c.drawCircle(e[0] + dx, e[1] + dy, 9f, fill)
                }
            }
        }

        if (happy) {
            fill.color = PUPIL
            c.drawPath(Path().apply { moveTo(84f, 104f); lineTo(116f, 104f); quadTo(114f, 132f, 100f, 132f); quadTo(86f, 132f, 84f, 104f); close() }, fill)
        } else if (dizzy) {
            c.drawPath(Path().apply { moveTo(86f, 122f); quadTo(93f, 114f, 100f, 122f); quadTo(107f, 130f, 114f, 122f) }, stroke)
        } else {
            c.drawPath(Path().apply { moveTo(88f, 116f); quadTo(100f, 126f, 112f, 116f) }, stroke)
        }
        c.restore()
    }

    private fun drawFlipCard(c: Canvas, rect: RectF, old: String, new: String, p: Float) {
        if (p >= 1f || old == new) {
            drawCard(c, rect, 9f, new, 88f)
            return
        }
        val hinge = rect.centerY()
        drawHalf(c, rect, new, top = true, shade = 0)
        drawHalf(c, rect, old, top = false, shade = 0)
        c.save()
        if (p < 0.5f) {
            val k = 1f - p * 2f
            c.scale(1f, k.coerceAtLeast(0.001f), rect.centerX(), hinge)
            drawHalf(c, rect, old, top = true, shade = ((1f - k) * 140).toInt())
        } else {
            val k = (p - 0.5f) * 2f
            c.scale(1f, k.coerceAtLeast(0.001f), rect.centerX(), hinge)
            drawHalf(c, rect, new, top = false, shade = ((1f - k) * 140).toInt())
        }
        c.restore()
        fill.color = Color.BLACK
        c.drawRect(rect.left, hinge - 1f, rect.right, hinge + 1f, fill)
    }

    private fun drawHalf(c: Canvas, rect: RectF, value: String, top: Boolean, shade: Int) {
        val hinge = rect.centerY()
        c.save()
        if (top) c.clipRect(rect.left, rect.top, rect.right, hinge) else c.clipRect(rect.left, hinge, rect.right, rect.bottom)
        fill.color = CARD
        c.drawRoundRect(rect, 9f, 9f, fill)
        drawText(c, value, rect.centerX(), rect.centerY(), 88f, cardFont, INK)
        if (shade > 0) {
            fill.color = Color.argb(shade.coerceIn(0, 255), 0, 0, 0)
            c.drawRoundRect(rect, 9f, 9f, fill)
        }
        c.restore()
    }

    private fun drawCard(c: Canvas, rect: RectF, radius: Float, value: String, size: Float) {
        fill.color = CARD
        c.drawRoundRect(rect, radius, radius, fill)
        drawText(c, value, rect.centerX(), rect.centerY(), size, if (size > 40f) cardFont else labelFont, INK)
        fill.color = Color.BLACK
        c.drawRect(rect.left, rect.centerY() - 1f, rect.right, rect.centerY() + 1f, fill)
    }

    private fun drawText(
        c: Canvas, value: String, x: Float, centreY: Float, size: Float, font: Typeface, colour: Int,
        spacing: Float = 0f
    ) {
        text.typeface = font
        text.textSize = size
        text.color = colour
        text.letterSpacing = spacing
        val fm = text.fontMetrics
        c.drawText(value, x, centreY - (fm.ascent + fm.descent) / 2f, text)
    }

    private fun radial(c: Canvas, deg: Float, r1: Float, r2: Float) {
        val a = Math.toRadians((deg - 90f).toDouble())
        val cs = cos(a).toFloat()
        val sn = sin(a).toFloat()
        c.drawLine(C + cs * r1, C + sn * r1, C + cs * r2, C + sn * r2, stroke)
    }

    private fun wrap(d: Float): Float = ((d % 360f + 540f) % 360f) - 180f
    private fun norm(d: Float): Float = (d % 360f + 360f) % 360f

    private fun direction(h: Int): String {
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names[((h + 22.5f) / 45f).toInt() % 8]
    }
}
