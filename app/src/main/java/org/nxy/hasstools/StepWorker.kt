package org.nxy.hasstools

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.withLock
import org.nxy.hasstools.data.StepPushDataStore
import org.nxy.hasstools.objects.StepPushViewModel
import org.nxy.hasstools.utils.StepCounter
import org.nxy.hasstools.utils.checkGroupPermissions
import org.nxy.hasstools.utils.stepPushPermissionGroup
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.concurrent.locks.ReentrantLock

private object StepWorker {
    var serviceIntent: Intent? = null

    lateinit var stepCounter: StepCounter

    val lock = ReentrantLock()
}

fun startStepWork(context: Context): Boolean {
    println("开始工作 - StepWorker")
    StepWorker.lock.withLock {
        if (StepWorker.serviceIntent != null) {
            println("工作已经在运行中")
            return true
        }

        // 权限检查
        if (!checkGroupPermissions(context, stepPushPermissionGroup)) {
            println("权限检查失败")
            return false
        }

        // 创建StepCounter实例
        StepWorker.stepCounter = StepCounter(context)

        StepAlarmScheduler.scheduleExactAlarm(context)

        println("工作已开始 - StepWorker")

        return true
    }
}

fun stopStepWork(context: Context) {
    println("停止工作 - StepWorker")
    StepWorker.lock.withLock {
        if (StepWorker.serviceIntent == null) {
            println("工作已经停止")
            return
        }

        // 停止StepCounter
        StepWorker.stepCounter.close()

        StepAlarmScheduler.cancelAlarm(context)

        StepWorker.serviceIntent = null

        println("工作已停止 - StepWorker")
    }
}

class StepBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        println("StepBootReceiver received: $intent")

        // 检查广播类型
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                println("启动 - StepWorker")

                val stepPushConfig = StepPushDataStore().readData()

                if (!stepPushConfig.enabled) {
                    println("步数推送未启用，跳过")
                    return
                }

                startStepWork(context)
            }
        }
    }
}

private object StepAlarmScheduler {
    private lateinit var nextAlarmPendingIntent: PendingIntent

    fun scheduleExactAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, StepAlarmReceiver::class.java)
        nextAlarmPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 计算下一个整分钟的触发时间
        val currentTimeMillis = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentTimeMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 1)
        }
        val triggerAtMillis = calendar.timeInMillis

        // 设置精确闹钟
        if (alarmManager.canScheduleExactAlarms()) {
            StepWorker.serviceIntent = intent
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                nextAlarmPendingIntent
            )
        }
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(nextAlarmPendingIntent)
    }
}

class StepAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        println("StepAlarmReceiver received: $intent")

        StepAlarmScheduler.scheduleExactAlarm(context)

        val healthConnectClient = HealthConnectClient.getOrCreate(context)

        val stepPushViewModel = StepPushViewModel(App.instance)

        // 进入协程
        CoroutineScope(Dispatchers.Main).launch {
            val stepCount = StepWorker.stepCounter.steps()

            // 没有上次步数
            if (lastStepCount == -1L) {
                // 在配置中获取上次步数
                stepPushViewModel.stepPushConfig.value.let {
                    // 如果上次步数为-1，说明没有记录，直接使用当前步数
                    if (it.lastStepCount == -1L) {
                        lastStepCount = stepCount
                        lastRecordTime = Instant.now()
                        lastRecordZoneId = ZoneId.systemDefault()
                    } else {
                        lastStepCount = it.lastStepCount
                        lastRecordTime = Instant.ofEpochMilli(it.lastRecordTimestamp)
                        lastRecordZoneId = ZoneId.of(it.lastRecordZoneId)
                    }
                }

                println("从配置中获取上次步数：$lastStepCount")
            }

            val startTime = lastRecordTime
            val startZoneId = lastRecordZoneId
            val startZoneOffset = ZonedDateTime.now(startZoneId).offset
            val endTime = Instant.now()
            val endZoneId = ZoneId.systemDefault()
            val endZoneOffset = ZonedDateTime.now(endZoneId).offset

            val stepDelta = stepCount - lastStepCount

            // 设备经历过重启，步数传感器清零，那么计数也要初始化
            if (stepDelta < 0) {
                lastStepCount = stepCount
                lastRecordTime = endTime
                lastRecordZoneId = endZoneId

                return@launch
            } else if (stepDelta == 0L) {
                // 没有步数变化，直接返回
                lastRecordTime = endTime
                lastRecordZoneId = endZoneId

                println("没有步数变化，跳过")
                return@launch
            }

            // 写入Health Connect
            try {
                val stepsRecord = StepsRecord(
                    count = stepDelta,
                    startTime = startTime,
                    endTime = endTime,
                    startZoneOffset = startZoneOffset,
                    endZoneOffset = endZoneOffset,
                    metadata = Metadata.autoRecorded(
                        device = Device(
                            type = Device.TYPE_PHONE,
                            manufacturer = StepWorker.stepCounter.vendor(),
                            model = StepWorker.stepCounter.name(),
                        )
                    )
                )
                healthConnectClient.insertRecords(listOf(stepsRecord))

                println("写入步数：$stepsRecord")

                lastStepCount = stepCount
                lastRecordTime = endTime
                lastRecordZoneId = endZoneId

                // 更新配置
                stepPushViewModel.setLastStepRecord(
                    lastCount = stepCount,
                    lastTime = endTime,
                    lastZoneId = endZoneId
                )
            } catch (e: Exception) {
                // Run error handling here
                println("写入步数失败：${e.message}")
            }
        }
    }

    companion object {
        var lastStepCount: Long = -1

        var lastRecordTime: Instant = Instant.now()

        var lastRecordZoneId: ZoneId = ZoneId.systemDefault()
    }
}
