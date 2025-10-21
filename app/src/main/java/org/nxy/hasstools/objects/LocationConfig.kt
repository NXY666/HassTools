package org.nxy.hasstools.objects

import kotlinx.serialization.Serializable

@Serializable
enum class FusedProvider {
    NONE,
    SYSTEM,
    GOOGLE,
    AMAP
}

@Serializable
data class LocationConfig(
    val enabled: Boolean = false,
    val networkTriggerEnabled: Boolean = true,
    val fusedProvider: FusedProvider = FusedProvider.NONE
)
