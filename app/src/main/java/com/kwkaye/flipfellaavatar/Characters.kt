package com.kwkaye.flipfellaavatar

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

enum class Mood { IDLE, HAPPY, CHEER, WORRIED, SLEEPY }

private enum class EyeStyle { SCLERA, DOT }
private enum class MouthStyle { SMILE, O }

/**
 * Pebble, Bean and Drip. Each is drawn in its own 200 x 200 box, then scaled by whoever
 * draws it (the full-screen app or the complication bitmap).
 */
class Character private constructor(
    val name: String,
    private val bodyColour: Int,
    private val shade: Shade,
    private val body: Path,
    private val eyes: List<FloatArray>,
    private val eyeStyle: EyeStyle,
    private val eyeSize: Float,
    private val mouth: FloatArray,
    private val mouthStyle: MouthStyle,
    private val sweat: FloatArray,
    /** Centre and height of the drawn character, for fitting it into a space. */
    val centreX: Float,
    val centreY: Float,
    val height: Float,
) {
    private enum class ShadeKind { DROP, OUTLINE, NONE }
    private class Shade(val kind: ShadeKind, val colour: Int, val dx: Float, val dy: Float)

    companion object {
        private const val INK = 0xFF0B0B12.toInt()
        private const val CREAM = 0xFFF3F0E8.toInt()

        val ALL: List<Character> by lazy { listOf(pebble(), bean(), drip()) }

        private fun pebble(): Character {
            val p = Path().apply {
                moveTo(100f, 22f)
                cubicTo(146f, 20f, 176f, 54f, 174f, 100f)
                cubicTo(172f, 146f, 140f, 176f, 98f, 174f)
                cubicTo(54f, 172f, 24f, 142f, 26f, 98f)
                cubicTo(28f, 56f, 58f, 24f, 100f, 22f)
                close()
            }
            return Character(
                "Pebble", 0xFF4B57FF.toInt(), Shade(ShadeKind.DROP, 0xFF2329A8.toInt(), 7f, 9f), p,
                listOf(floatArrayOf(78f, 80f), floatArrayOf(122f, 80f)), EyeStyle.SCLERA, 18f,
                floatArrayOf(100f, 120f), MouthStyle.SMILE, floatArrayOf(158f, 50f),
                103f, 101f, 163f
            )
        }

        private fun bean(): Character {
            val p = Path().apply { addOval(RectF(42f, 20f, 158f, 164f), Path.Direction.CW) }
            p.transform(Matrix().apply { setRotate(-10f, 100f, 92f) })
            return Character(
                "Bean", 0xFFFF4F9A.toInt(), Shade(ShadeKind.OUTLINE, CREAM, 8f, 8f), p,
                listOf(floatArrayOf(82f, 84f), floatArrayOf(118f, 80f)), EyeStyle.DOT, 11f,
                floatArrayOf(102f, 118f), MouthStyle.SMILE, floatArrayOf(146f, 40f),
                104f, 96f, 156f
            )
        }

        private fun drip(): Character {
            val p = Path().apply {
                moveTo(100f, 14f)
                cubicTo(100f, 14f, 160f, 86f, 160f, 128f)
                arcTo(RectF(40f, 68f, 160f, 188f), 0f, 180f, false)
                cubicTo(40f, 86f, 100f, 14f, 100f, 14f)
                close()
            }
            return Character(
                "Drip", 0xFF38C6F4.toInt(), Shade(ShadeKind.NONE, 0, 0f, 0f), p,
                listOf(floatArrayOf(100f, 116f)), EyeStyle.SCLERA, 27f,
                floatArrayOf(100f, 162f), MouthStyle.O, floatArrayOf(138f, 64f),
                100f, 101f, 174f
            )
        }
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * Draws the character in its own coordinates.
     * lookX, lookY: -1..1, where the eyes point. outline: white line art for single-colour icons.
     */
    fun draw(c: Canvas, mood: Mood, lookX: Float = 0f, lookY: Float = 0f, blink: Boolean = false, outline: Boolean = false) {
        val ink = if (outline) Color.WHITE else INK

        // Body
        if (outline) {
            stroke.color = Color.WHITE; stroke.strokeWidth = 8f
            c.drawPath(body, stroke)
        } else {
            when (shade.kind) {
                ShadeKind.DROP -> {
                    c.save(); c.translate(shade.dx, shade.dy)
                    fill.color = shade.colour; c.drawPath(body, fill)
                    c.restore()
                }
                ShadeKind.OUTLINE -> {
                    c.save(); c.translate(shade.dx, shade.dy)
                    stroke.color = shade.colour; stroke.strokeWidth = 4f; c.drawPath(body, stroke)
                    c.restore()
                }
                ShadeKind.NONE -> Unit
            }
            fill.color = bodyColour
            c.drawPath(body, fill)
        }

        stroke.color = ink
        stroke.strokeWidth = if (eyes.size == 1) 7f else 6f

        // Eyes
        val closed = blink && mood != Mood.HAPPY && mood != Mood.CHEER
        for (e in eyes) {
            val x = e[0]
            val y = e[1]
            val w = if (eyeStyle == EyeStyle.SCLERA) eyeSize * 0.9f else 13f
            when {
                mood == Mood.HAPPY || mood == Mood.CHEER ->
                    c.drawPath(Path().apply { moveTo(x - w, y + 6); quadTo(x, y - w * 0.9f, x + w, y + 6) }, stroke)
                mood == Mood.SLEEPY ->
                    c.drawPath(Path().apply { moveTo(x - w, y + 4); quadTo(x, y + 12, x + w, y + 4) }, stroke)
                closed ->
                    c.drawPath(Path().apply { moveTo(x - w, y); quadTo(x, y + 8, x + w, y) }, stroke)
                else -> drawOpenEye(c, x, y, mood, lookX, lookY, outline, ink)
            }
        }

        // Brows when worried
        if (mood == Mood.WORRIED) {
            for (e in eyes) {
                val x = e[0]
                val top = e[1] - (if (eyeStyle == EyeStyle.SCLERA) eyeSize else 14f) - 10f
                val inward = if (eyes.size == 1) 0f else if (x < 100f) 1f else -1f
                if (eyes.size == 1) {
                    c.drawPath(Path().apply { moveTo(x - 24, top + 6); quadTo(x, top - 8, x + 24, top + 6) }, stroke)
                } else {
                    c.drawLine(x - 16 * inward, top + 8, x + 16 * inward, top - 4, stroke)
                }
            }
        }

        // Mouth
        val mx = mouth[0]
        val my = mouth[1]
        fill.color = ink
        when (mouthStyle) {
            MouthStyle.SMILE -> when (mood) {
                Mood.IDLE -> c.drawPath(Path().apply { moveTo(mx - 12, my - 4); quadTo(mx, my + 6, mx + 12, my - 4) }, stroke)
                Mood.HAPPY -> openMouth(c, mx, my - 14, 32f, 28f, outline)
                Mood.CHEER -> openMouth(c, mx, my - 16, 48f, 42f, outline)
                Mood.WORRIED -> c.drawPath(Path().apply {
                    moveTo(mx - 16, my + 6); quadTo(mx - 8, my - 2, mx, my + 6); quadTo(mx + 8, my + 14, mx + 16, my + 6)
                }, stroke)
                Mood.SLEEPY -> c.drawLine(mx - 6, my, mx + 6, my, stroke)
            }
            MouthStyle.O -> when (mood) {
                Mood.IDLE -> { stroke.strokeWidth = 5f; c.drawCircle(mx, my, 6f, stroke) }
                Mood.HAPPY -> c.drawCircle(mx, my, 10f, fill)
                Mood.CHEER -> c.drawOval(RectF(mx - 13, my - 13, mx + 13, my + 15), fill)
                Mood.WORRIED -> c.drawPath(Path().apply {
                    moveTo(mx - 12, my + 2); quadTo(mx - 6, my - 4, mx, my + 2); quadTo(mx + 6, my + 8, mx + 12, my + 2)
                }, stroke)
                Mood.SLEEPY -> c.drawLine(mx - 5, my, mx + 5, my, stroke)
            }
        }

        // Sweat drop when worried
        if (mood == Mood.WORRIED && !outline) {
            val sx = sweat[0]
            val sy = sweat[1]
            fill.color = 0xFF7FD3FF.toInt()
            c.drawPath(Path().apply { moveTo(sx, sy); quadTo(sx + 9, sy + 15, sx, sy + 21); quadTo(sx - 9, sy + 15, sx, sy); close() }, fill)
        }
    }

    private fun drawOpenEye(c: Canvas, x: Float, y: Float, mood: Mood, lookX: Float, lookY: Float, outline: Boolean, ink: Int) {
        val worried = mood == Mood.WORRIED
        if (eyeStyle == EyeStyle.SCLERA) {
            val r = if (worried) eyeSize * 0.9f else eyeSize
            val pr = if (worried) eyeSize * 0.28f else eyeSize * 0.5f
            val reach = r - pr - 2f
            val px = x + lookX * reach
            val py = y + (if (worried) reach * 0.5f else lookY * reach)
            if (outline) {
                stroke.strokeWidth = 5f; c.drawCircle(x, y, r - 2f, stroke)
                fill.color = Color.WHITE; c.drawCircle(px, py, pr * 0.75f, fill)
                stroke.strokeWidth = if (eyes.size == 1) 7f else 6f
            } else {
                fill.color = Color.WHITE; c.drawCircle(x, y, r, fill)
                fill.color = ink; c.drawCircle(px, py, pr, fill)
                if (eyes.size == 1) { fill.color = Color.WHITE; c.drawCircle(px + pr * 0.35f, py - pr * 0.4f, pr * 0.3f, fill) }
            }
        } else {
            val s = if (worried) 0.7f else 1f
            val ox = lookX * 4f
            val oy = lookY * 4f
            fill.color = if (outline) Color.WHITE else ink
            c.drawOval(RectF(x - 8 * s + ox, y - 11 * s + oy, x + 8 * s + ox, y + 11 * s + oy), fill)
            if (!outline) {
                fill.color = Color.WHITE
                c.drawCircle(x + 3 * s + ox, y - 4 * s + oy, 3f * s, fill)
            }
        }
    }

    private fun openMouth(c: Canvas, cx: Float, top: Float, w: Float, h: Float, outline: Boolean) {
        val p = Path().apply {
            moveTo(cx - w / 2, top)
            lineTo(cx + w / 2, top)
            quadTo(cx + w / 2 - 2, top + h, cx, top + h)
            quadTo(cx - w / 2 + 2, top + h, cx - w / 2, top)
            close()
        }
        fill.color = if (outline) Color.WHITE else INK
        c.drawPath(p, fill)
        if (!outline) {
            fill.color = 0xFFFF6B8A.toInt()
            c.save()
            c.clipPath(p)
            c.drawOval(RectF(cx - w * 0.28f, top + h * 0.62f, cx + w * 0.28f, top + h * 1.15f), fill)
            c.restore()
        }
    }
}
