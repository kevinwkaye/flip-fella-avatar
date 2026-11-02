package com.kwkaye.flipfellaavatar

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import java.time.LocalDate
import java.time.LocalTime

/**
 * Everything the avatar knows about your day, shared by the app and the complication:
 * which character you picked, how much attention he's had, steps today and the battery.
 */
object Avatar {
    const val STEP_GOAL = 10_000
    private const val ATTENTION_DRAIN_MS = 12 * 60 * 60 * 1000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("avatar", Context.MODE_PRIVATE)

    fun character(ctx: Context): Character =
        Character.ALL[prefs(ctx).getInt("character", 0).coerceIn(0, Character.ALL.size - 1)]

    fun nextCharacter(ctx: Context): Character {
        val next = (prefs(ctx).getInt("character", 0) + 1) % Character.ALL.size
        prefs(ctx).edit().putInt("character", next).apply()
        return Character.ALL[next]
    }

    // ---------------------------------------------------------------- attention

    /** 0..1. Taps fill it, and it drains to empty over 12 hours. */
    fun attention(ctx: Context, now: Long = System.currentTimeMillis()): Float {
        val p = prefs(ctx)
        val stored = p.getFloat("attention", 0.5f)
        val at = p.getLong("attentionAt", now)
        return (stored - (now - at).toFloat() / ATTENTION_DRAIN_MS).coerceIn(0f, 1f)
    }

    fun pat(ctx: Context, now: Long = System.currentTimeMillis()) {
        val value = (attention(ctx, now) + 0.15f).coerceAtMost(1f)
        prefs(ctx).edit()
            .putFloat("attention", value)
            .putLong("attentionAt", now)
            .putLong("lastTap", now)
            .apply()
    }

    fun lastTap(ctx: Context): Long = prefs(ctx).getLong("lastTap", 0L)

    // ---------------------------------------------------------------- steps

    /** Feed it the raw step counter (steps since the watch booted). Returns steps today. */
    fun recordStepCounter(ctx: Context, counter: Float): Int {
        val p = prefs(ctx)
        val today = LocalDate.now().toString()
        var base = p.getFloat("stepBase", counter)
        val last = p.getFloat("stepLast", counter)
        if (p.getString("stepDay", null) != today) {
            base = counter
        } else if (counter < last) {
            // The watch restarted, so the counter began again from zero. Keep today's total.
            base = counter - (last - base)
        }
        p.edit().putString("stepDay", today).putFloat("stepBase", base).putFloat("stepLast", counter).apply()
        return (counter - base).toInt().coerceAtLeast(0)
    }

    fun stepsToday(ctx: Context): Int {
        val p = prefs(ctx)
        if (p.getString("stepDay", null) != LocalDate.now().toString()) return 0
        return (p.getFloat("stepLast", 0f) - p.getFloat("stepBase", 0f)).toInt().coerceAtLeast(0)
    }

    // ---------------------------------------------------------------- battery

    data class Battery(val percent: Int, val charging: Boolean)

    fun battery(ctx: Context): Battery {
        val i: Intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return Battery(100, false)
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return Battery(level * 100 / scale, charging)
    }

    // ---------------------------------------------------------------- mood

    fun isNight(now: LocalTime = LocalTime.now()): Boolean = now.hour >= 22 || now.hour < 7

    /** tapWindowMs: how long a tap keeps him happy. Short in the app, longer on the face. */
    fun mood(ctx: Context, tapWindowMs: Long, now: Long = System.currentTimeMillis()): Mood {
        val battery = battery(ctx)
        return when {
            now - lastTap(ctx) < tapWindowMs -> Mood.HAPPY
            isNight() -> Mood.SLEEPY
            battery.percent < 20 && !battery.charging -> Mood.WORRIED
            stepsToday(ctx) >= STEP_GOAL -> Mood.CHEER
            else -> Mood.IDLE
        }
    }

    fun label(mood: Mood, ctx: Context): String = when (mood) {
        Mood.IDLE -> "Content"
        Mood.HAPPY -> "Hello!"
        Mood.CHEER -> "%,d steps!".format(STEP_GOAL)
        Mood.WORRIED -> "Running low"
        Mood.SLEEPY -> "Sleeping"
    }

    /** One word for small complication slots. */
    fun word(mood: Mood, ctx: Context): String = when (mood) {
        Mood.IDLE -> "Hi"
        Mood.HAPPY -> "Yay"
        Mood.CHEER -> "${STEP_GOAL / 1000}k"
        Mood.WORRIED -> "${battery(ctx).percent}%"
        Mood.SLEEPY -> "Zzz"
    }

    /** Ask any watch face showing the complication to refresh it now. */
    fun refreshComplication(ctx: Context) {
        ComplicationDataSourceUpdateRequester
            .create(ctx, ComponentName(ctx, AvatarComplicationService::class.java))
            .requestUpdateAll()
    }
}
