package com.kwkaye.flipfellaavatar

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Puts the avatar in a slot on any watch face. Full colour where the slot takes an image,
 * a white outline (tinted by the face) plus one word where it only takes an icon.
 * Tapping him opens the full-screen app.
 */
class AvatarComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        readStepsBriefly()
        val mood = Avatar.mood(this, tapWindowMs = 10 * 60 * 1000L)
        return build(request.complicationType, Avatar.character(this), mood)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, Avatar.character(this), Mood.IDLE)

    private fun build(type: ComplicationType, character: Character, mood: Mood): ComplicationData? {
        val description = PlainComplicationText.Builder("${character.name}, ${Avatar.label(mood, this).lowercase()}").build()
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return when (type) {
            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                SmallImage.Builder(Icon.createWithBitmap(render(character, mood, outline = false, px = 160)), SmallImageType.ICON).build(),
                description
            ).setTapAction(tap).build()

            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
                MonochromaticImage.Builder(Icon.createWithBitmap(render(character, mood, outline = true, px = 96))).build(),
                description
            ).setTapAction(tap).build()

            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder(Avatar.word(mood, this)).build(),
                description
            ).setMonochromaticImage(
                MonochromaticImage.Builder(Icon.createWithBitmap(render(character, mood, outline = true, px = 96))).build()
            ).setTapAction(tap).build()

            else -> null
        }
    }

    private fun render(character: Character, mood: Mood, outline: Boolean, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val scale = px * 0.88f / character.height
        c.translate(px / 2f, px / 2f)
        c.scale(scale, scale)
        c.translate(-character.centreX, -character.centreY)
        character.draw(c, mood, outline = outline)
        return bmp
    }

    /** Grab the latest step count, giving up quickly so the face never waits on us. */
    private suspend fun readStepsBriefly() {
        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return
        val sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        val value = withTimeoutOrNull(1500L) {
            suspendCancellableCoroutine<Float> { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sensors.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values[0])
                    }
                    override fun onAccuracyChanged(s: Sensor, a: Int) = Unit
                }
                sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sensors.unregisterListener(listener) }
            }
        }
        if (value != null) Avatar.recordStepCounter(this, value)
    }
}
