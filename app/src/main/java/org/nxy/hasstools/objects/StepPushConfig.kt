package org.nxy.hasstools.objects

import kotlinx.serialization.Serializable
import java.time.ZoneId

@Serializable
data class StepPushConfig(
    val enabled: Boolean = false,

    val lastStepCount: Long = -1L,

    val lastRecordTimestamp: Long = -1L,

    val lastRecordZoneId: String = ZoneId.systemDefault().id
)
