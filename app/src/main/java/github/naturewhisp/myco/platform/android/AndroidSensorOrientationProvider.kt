package github.naturewhisp.myco.platform.android

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import github.naturewhisp.myco.platform.DeviceHeading
import github.naturewhisp.myco.platform.PlatformOrientationProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Adapter Android per PlatformOrientationProvider basato su SensorManager e ROTATION_VECTOR.
 * Include filtro passa-basso EMA (Exponential Moving Average) sull'angolo per smorzare jitter.
 */
class AndroidSensorOrientationProvider(
    context: Context
) : PlatformOrientationProvider {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    override fun isSupported(): Boolean = rotationSensor != null

    override fun headingUpdates(): Flow<DeviceHeading> = callbackFlow {
        val manager = sensorManager
        val sensor = rotationSensor
        if (manager == null || sensor == null) {
            close()
            return@callbackFlow
        }

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var smoothedAzimuth: Float? = null
        var lastEmittedAzimuth: Float? = null
        var lastEmitTime = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.sensor.type != sensor.type) return

                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)

                var azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (azimuthDeg < 0f) {
                    azimuthDeg += 360f
                }

                val current = smoothedAzimuth
                val targetAzimuth = if (current == null) {
                    azimuthDeg
                } else {
                    smoothAngle(current, azimuthDeg, alpha = 0.20f)
                }
                smoothedAzimuth = targetAzimuth

                val lastEmitted = lastEmittedAzimuth
                val diff = if (lastEmitted == null) 360f else {
                    var d = kotlin.math.abs(targetAzimuth - lastEmitted) % 360f
                    if (d > 180f) d = 360f - d
                    d
                }

                val now = System.currentTimeMillis()
                // Emetti solo in caso di rotazione tangibile (> 0.75°) o cadenza periodica controllata
                if (lastEmitted == null || diff >= 0.75f || (diff >= 0.25f && now - lastEmitTime >= 120L)) {
                    lastEmittedAzimuth = targetAzimuth
                    lastEmitTime = now
                    trySend(DeviceHeading(azimuthDegrees = targetAzimuth, isReliable = true))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Non rilevante per il vettore di rotazione fuso
            }
        }

        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)

        awaitClose {
            manager.unregisterListener(listener)
        }
    }

    internal fun smoothAngle(current: Float, target: Float, alpha: Float): Float {
        var diff = (target - current) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        var result = current + diff * alpha
        if (result < 0f) result += 360f
        if (result >= 360f) result -= 360f
        return result
    }
}
