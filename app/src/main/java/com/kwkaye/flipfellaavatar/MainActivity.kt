package com.kwkaye.flipfellaavatar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator

/** Opens when you tap the avatar's complication, or from the app list. */
class MainActivity : Activity(), SensorEventListener {

    private lateinit var view: AvatarView
    private lateinit var sensors: SensorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        view = AvatarView(this)
        view.onPat = {
            Avatar.pat(this)
            buzz()
        }
        view.onSwap = {
            view.character = Avatar.nextCharacter(this)
        }
        setContentView(view)

        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startSteps()
    }

    override fun onResume() {
        super.onResume()
        view.character = Avatar.character(this)
        view.steps = Avatar.stepsToday(this)
        startSteps()
    }

    override fun onPause() {
        sensors.unregisterListener(this)
        // Let the watch face catch up with any taps or a new character.
        Avatar.refreshComplication(this)
        super.onPause()
    }

    private fun startSteps() {
        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return
        sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            view.steps = Avatar.recordStepCounter(this, event.values[0])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private fun buzz() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
