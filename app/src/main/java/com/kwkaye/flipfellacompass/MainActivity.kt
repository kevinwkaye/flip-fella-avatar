package com.kwkaye.flipfellacompass

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager

/**
 * Flip Fella Compass. Reads the heading from the rotation vector sensor and hands it to
 * CompassView. Sensors only run while the app is on screen.
 */
class MainActivity : Activity(), SensorEventListener {

    private lateinit var view: CompassView
    private lateinit var sensors: SensorManager
    private var rotationSensor: Sensor? = null
    private var magnetSensor: Sensor? = null

    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)

    private val handler = Handler(Looper.getMainLooper())
    private val releaseScreen = Runnable { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensors.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        magnetSensor = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val prefs = getSharedPreferences("compass", Context.MODE_PRIVATE)
        view = CompassView(this)
        view.bearing = prefs.getFloat("bearing", -1f).takeIf { it >= 0f }
        view.onBearingChanged = { b ->
            prefs.edit().putFloat("bearing", b ?: -1f).apply()
            keepScreenOnForAWhile()
        }
        view.onCourse = { buzz() }
        setContentView(view)
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magnetSensor?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        view.sensorMissing = rotationSensor == null
        keepScreenOnForAWhile()
    }

    override fun onPause() {
        sensors.unregisterListener(this)
        handler.removeCallbacks(releaseScreen)
        super.onPause()
    }

    /** Stay awake while you're using it, then let the watch time out as normal. */
    private fun keepScreenOnForAWhile() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.removeCallbacks(releaseScreen)
        handler.postDelayed(releaseScreen, 3 * 60 * 1000L)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) return
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        var deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (deg < 0f) deg += 360f
        view.setHeading(deg)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) view.setAccuracy(accuracy)
    }

    private fun buzz() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
