package com.racingwheelpro.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.asin

/**
 * Steering from the phone's tilt, the same physical gesture Touch Racer uses:
 * hold the phone upright in landscape (screen facing you) and roll it like a wheel.
 * Game-rotation / rotation-vector is preferred so ranges past 180° can unwrap.
 * Accelerometer Y is the fallback. Zero is wherever [recenter] last captured,
 * on top of this sensor angle.
 */
class SteeringSensor(
    context: Context,
    private val onAngleRad: (Float) -> Unit,
    private val config: () -> Config,
) : SensorEventListener {
    class Config(
        val mode: String,
        val rangeDeg: Float,
        val zeroAtTop: Boolean,
        val fastest: Boolean,
        val gravityScale: Float,
    )

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val unwrapper = AngleUnwrapper()

    var missing: Boolean = true
        private set
    var activeName: String = "none"
        private set

    fun start() {
        stop()
        unwrapper.reset()
        val cfg = config()
        if (cfg.mode == "touch") {
            missing = true
            activeName = "touch"
            return
        }
        val sensor = choose(cfg.mode)
        if (sensor == null) {
            missing = true
            activeName = "unavailable"
            return
        }
        missing = false
        activeName = when (sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR -> "game rotation"
            Sensor.TYPE_ROTATION_VECTOR -> "rotation vector"
            else -> "accelerometer"
        }
        val delay = if (cfg.fastest) SensorManager.SENSOR_DELAY_FASTEST else SensorManager.SENSOR_DELAY_GAME
        sensors.registerListener(this, sensor, delay)
    }

    fun stop() {
        sensors.unregisterListener(this)
    }

    fun resetUnwrap() {
        unwrapper.reset()
    }

    private fun choose(mode: String): Sensor? {
        fun get(type: Int) = sensors.getDefaultSensor(type)
        return when (mode) {
            "accelerometer" -> get(Sensor.TYPE_ACCELEROMETER)
            "game_rotation" -> get(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: get(Sensor.TYPE_ROTATION_VECTOR)
            else -> get(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: get(Sensor.TYPE_ROTATION_VECTOR)
                ?: get(Sensor.TYPE_ACCELEROMETER)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val cfg = config()
        val rad = if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            fromAccelerometer(event.values, cfg)
        } else {
            fromRotationVector(event.values, cfg.zeroAtTop)
        }
        onAngleRad(unwrapper.feed(rad, cfg.rangeDeg > 180f))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun fromAccelerometer(v: FloatArray, cfg: Config): Float {
        val y = v[1]
        val g = 9.80665f * cfg.gravityScale.coerceIn(0.5f, 1.5f)
        var angle = asin((y / g).coerceIn(-1f, 1f))
        val reference = v[if (cfg.zeroAtTop) 2 else 0]
        if (reference < 0f) {
            angle = if (angle > 0f) (Math.PI.toFloat() - angle) else (-Math.PI.toFloat() - angle)
        }
        return angle
    }

    private fun fromRotationVector(values: FloatArray, zeroAtTop: Boolean): Float {
        val matrix = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(matrix, values)
        SensorManager.getOrientation(matrix, orientation)
        var angle = -orientation[1]
        val roll = orientation[2] + if (zeroAtTop) 0f else (Math.PI / 2.0).toFloat()
        if (roll > Math.PI / 2.0 || roll < -Math.PI / 2.0) {
            angle = if (angle > 0f) (Math.PI - angle).toFloat() else (-Math.PI - angle).toFloat()
        }
        return angle
    }
}
