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

/**
 * 步数计数器。
 *
 * 使用设备的步数传感器获取实时步数数据。实现了 Closeable 接口以便释放资源。
 *
 * @property context 应用上下文
 */
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

    /**
     * 获取当前步数。
     *
     * 挂起函数，等待传感器返回最新的步数数据。
     *
     * @return 当前步数
     */
    suspend fun steps(): Long = suspendCancellableCoroutine { continuation ->
        synchronized(waiters) {
            latestStep?.let {
                continuation.resume(it)
            } ?: run {
                waiters.add(continuation)
            }
        }
    }

    /**
     * 获取传感器名称。
     *
     * @return 传感器名称，如果传感器不可用则返回设备型号
     */
    fun name(): String = sensor?.name ?: Build.MODEL

    /**
     * 获取传感器厂商。
     *
     * @return 传感器厂商，如果传感器不可用则返回设备制造商
     */
    fun vendor(): String = sensor?.vendor ?: Build.MANUFACTURER

    /**
     * 释放传感器监听器资源。
     */
    override fun close() {
        sensorManager.unregisterListener(listener)
    }

    companion object {
        /**
         * 检查设备是否支持步数传感器。
         *
         * @param context 应用上下文
         * @return 是否支持步数传感器
         */
        fun isSupported(context: Context): Boolean {
            val sensorManager = context.getSystemService(SensorManager::class.java)
            return sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
        }
    }
}
