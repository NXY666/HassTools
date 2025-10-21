package org.nxy.hasstools.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import kotlin.coroutines.resume

class StepCounter(context: Context) : Closeable {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private var latestStep: Long? = null
    private val waiters = mutableListOf<CancellableContinuation<Long>>()

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            val value = event.values[0].toLong()
            latestStep = value
            synchronized(waiters) {
                waiters.forEach { it.resume(value) }
                waiters.clear()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    init {
        sensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    suspend fun steps(): Long = suspendCancellableCoroutine { continuation ->
        synchronized(waiters) {
            latestStep?.let {
                continuation.resume(it)
            } ?: run {
                waiters.add(continuation)
            }
        }
    }

    fun name(): String = sensor?.name ?: Build.MODEL

    fun vendor(): String = sensor?.vendor ?: Build.MANUFACTURER

    override fun close() {
        sensorManager.unregisterListener(listener)
    }

    companion object {
        fun isSupported(context: Context): Boolean {
            val sensorManager = context.getSystemService(SensorManager::class.java)
            return sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
        }
    }
}
