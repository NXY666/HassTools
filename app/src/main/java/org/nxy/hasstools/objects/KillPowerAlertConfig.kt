package org.nxy.hasstools.objects

import kotlinx.serialization.Serializable

@Serializable
data class KillPowerAlertConfig(
    val enabled: Boolean = false
)
