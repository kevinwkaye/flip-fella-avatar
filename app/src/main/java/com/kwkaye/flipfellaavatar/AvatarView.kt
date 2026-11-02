package com.kwkaye.flipfellaavatar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val SIZE = 480f
private const val C = 240f
private const val BOUNCE_MS = 750L
private const val TAP_HAPPY_MS = 3000L
private const val NAME_MS = 1800L

/**
 * The full-screen avatar, as big as the round screen allows: breathing, blinking,
 * looking around, bouncing when tapped, with three meters along the bottom.
 */
@SuppressLint("ViewConstructor")
class AvatarView(context: Context) : View(context) {

    var onPat: () -> Unit = {}
    var onSwap: () -> Unit = {}

    var character: Character = Avatar.character(context)
    var steps = Avatar.stepsToday(context)

    private var mood = Mood.IDLE
    private var moodCheckedAt = 0L
    private var battery = Avatar.battery(context)
    private var attention = Avatar.attention(context)

    private var tapAt = 0L
    private var nameUntil = 0L

    private var lookX = 0f
    private var lookY = 0f
    private var lookTargetX = 0f
    private var lookTargetY = 0f
    private var nextLook = 0L
    private var nextBlink = SystemClock.uptimeMillis() + 2000L

    private val confetti = List(16) { floatArrayOf(Random.nextFloat() * 480f, Random.nextFloat() * 480f, Random.nextFloat() * 360f, 0.6f + Random.nextFloat()) }
    private val confettiColours = intArrayOf(0xFFFF6A1A.toInt(), 0xFFFFD24A.toInt(), 0xFF7BD88F.toInt(), 0xFF4FB3FF.toInt(), 0xFFFF6B8A.toInt())

    private val labelFont: Typeface = try { resources.getFont(R.font.rc_bold) } catch (e: Exception) { Typeface.DEFAULT_BOLD }
    private val zFont: Typeface = try { resources.getFont(R.font.saira_medium) } catch (e: Exception) { Typeface.DEFAULT }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            tapAt = SystemClock.uptimeMillis()
            onPat()
            attention = Avatar.attention(context)
            refreshMood(force = true)
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            onSwap()
            nameUntil = SystemClock.uptimeMillis() + NAME_MS
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event)

    private fun refreshMood(force: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - moodCheckedAt < 1000L) return
        moodCheckedAt = now
        battery = Avatar.battery(context)
        attention = Avatar.attention(context)
        mood = Avatar.mood(context, TAP_HAPPY_MS)
    }

    override fun onDraw(canvas: Canvas) {
        refreshMood()
        val now = SystemClock.uptimeMillis()
        val side = min(width, height).toFloat()
        canvas.save()
        canvas.translate((width - side) / 2f, (height - side) / 2f)
        canvas.scale(side / SIZE, side / SIZE)

        val night = mood == Mood.SLEEPY
        fill.color = if (night) 0xFF030306.toInt() else 0xFF070708.toInt()
        canvas.drawCircle(C, C, 240f, fill)

        if (mood == Mood.CHEER) drawConfetti(canvas, now)

        // Eyes wander now and then.
        if (now > nextLook) {
            val still = Random.nextFloat() < 0.35f
            lookTargetX = if (still) 0f else Random.nextFloat() * 2f - 1f
            lookTargetY = if (still) 0f else Random.nextFloat() * 1.6f - 0.8f
            nextLook = now + 1500L + Random.nextLong(3500L)
        }
        lookX += (lookTargetX - lookX) * 0.12f
        lookY += (lookTargetY - lookY) * 0.12f

        val blinking = now in nextBlink..(nextBlink + 130L)
        if (now > nextBlink + 130L) nextBlink = now + 2500L + Random.nextLong(4000L)

        // Bounce after a tap, gentle breathing the rest of the time.
        val sinceTap = now - tapAt
        val bounce = if (sinceTap < BOUNCE_MS) {
            val p = sinceTap.toFloat() / BOUNCE_MS
            if (p < 0.7f) -48f * sin(PI * p / 0.7f).toFloat() else -10f * sin(PI * (p - 0.7f) / 0.3f).toFloat()
        } else 0f
        val breathe = 1f + 0.018f * sin(now / 1600.0 * 2 * PI).toFloat() * (if (night) 1.6f else 1f)

        val scale = 340f / character.height
        canvas.save()
        canvas.translate(C, 242f + bounce)
        canvas.scale(scale, scale * breathe)
        canvas.translate(-character.centreX, -character.centreY)
        character.draw(canvas, mood, lookX, lookY, blink = blinking)
        canvas.restore()

        if (sinceTap < BOUNCE_MS + 200L) drawSparks(canvas, sinceTap)
        if (night) drawZs(canvas, now)

        // Label across the top: his name just after swapping, otherwise his mood.
        val label = if (now < nameUntil) character.name else Avatar.label(mood, context)
        val colour = when {
            now < nameUntil -> 0xFFF3F0E8.toInt()
            mood == Mood.HAPPY || mood == Mood.WORRIED -> 0xFFFFD24A.toInt()
            mood == Mood.CHEER -> 0xFF7BD88F.toInt()
            mood == Mood.SLEEPY -> 0xFF5A5A70.toInt()
            else -> 0xFFA0A0A6.toInt()
        }
        drawText(canvas, label.uppercase(), C, 42f, 18f, colour, 0.17f)

        if (!night) drawMeters(canvas)

        canvas.restore()
        postInvalidateOnAnimation()
    }

    private fun drawMeters(c: Canvas) {
        val values = floatArrayOf(attention, (steps.toFloat() / Avatar.STEP_GOAL).coerceIn(0f, 1f), battery.percent / 100f)
        val colours = intArrayOf(0xFFFF6A1A.toInt(), 0xFF7BD88F.toInt(), 0xFFFFD24A.toInt())
        val xs = floatArrayOf(170f, 240f, 310f)
        for (i in 0 until 3) {
            val x = xs[i]
            fill.color = colours[i]
            c.save()
            c.translate(x - 8f, 410f)
            c.scale(16f / 24f, 16f / 24f)
            when (i) {
                0 -> c.drawPath(Path().apply {
                    moveTo(12f, 21f); cubicTo(4f, 14f, 1f, 9.5f, 3.5f, 6f); cubicTo(6f, 2.5f, 10.5f, 3.5f, 12f, 7f)
                    cubicTo(13.5f, 3.5f, 18f, 2.5f, 20.5f, 6f); cubicTo(23f, 9.5f, 20f, 14f, 12f, 21f); close()
                }, fill)
                1 -> { c.drawOval(RectF(5.1f, 3f, 11.9f, 15f), fill); c.drawOval(RectF(12.1f, 7f, 18.9f, 19f), fill) }
                else -> c.drawPath(Path().apply { moveTo(13f, 2f); lineTo(4f, 14f); lineTo(11f, 14f); lineTo(10f, 22f); lineTo(19f, 10f); lineTo(12f, 10f); close() }, fill)
            }
            c.restore()
            fill.color = 0xFF2A2A2E.toInt()
            c.drawRoundRect(RectF(x - 28f, 431f, x + 28f, 437f), 3f, 3f, fill)
            fill.color = colours[i]
            c.drawRoundRect(RectF(x - 28f, 431f, x - 28f + 56f * values[i].coerceIn(0.04f, 1f), 437f), 3f, 3f, fill)
        }
    }

    private fun drawSparks(c: Canvas, since: Long) {
        val p = (since.toFloat() / (BOUNCE_MS + 200L)).coerceIn(0f, 1f)
        val alpha = ((1f - p) * 255).toInt()
        stroke.color = 0xFFFFD24A.toInt(); stroke.alpha = alpha; stroke.strokeWidth = 8f
        val out = 18f * p
        c.drawLine(70f - out, 120f - out, 100f - out, 145f - out, stroke)
        c.drawLine(410f + out, 120f - out, 380f + out, 145f - out, stroke)
        c.drawLine(110f - out, 70f - out, 128f - out, 96f - out, stroke)
        c.drawLine(370f + out, 70f - out, 352f + out, 96f - out, stroke)
        stroke.alpha = 255
    }

    private fun drawConfetti(c: Canvas, now: Long) {
        for ((i, bit) in confetti.withIndex()) {
            val y = (bit[1] + now / 12f * bit[3]) % 520f - 20f
            val x = bit[0] + 10f * sin(now / 500.0 + i).toFloat()
            fill.color = confettiColours[i % confettiColours.size]
            c.save()
            c.rotate(bit[2] + now / 8f * bit[3], x, y)
            c.drawRoundRect(RectF(x - 6f, y - 3.5f, x + 6f, y + 3.5f), 2f, 2f, fill)
            c.restore()
        }
    }

    private fun drawZs(c: Canvas, now: Long) {
        for (i in 0 until 3) {
            val p = ((now / 3000f) + i / 3f) % 1f
            val size = 22f + 22f * (1f - p)
            val x = 330f + 70f * p
            val y = 150f - 110f * p
            text.typeface = zFont
            text.textSize = size
            text.letterSpacing = 0f
            text.color = 0xFF8A8AA0.toInt()
            text.alpha = (255 * (1f - abs(p * 2f - 1f))).toInt()
            c.drawText("Z", x, y, text)
        }
        text.alpha = 255
    }

    private fun drawText(c: Canvas, value: String, x: Float, centreY: Float, size: Float, colour: Int, spacing: Float) {
        text.typeface = labelFont
        text.textSize = size
        text.color = colour
        text.letterSpacing = spacing
        val fm = text.fontMetrics
        c.drawText(value, x, centreY - (fm.ascent + fm.descent) / 2f, text)
    }
}
