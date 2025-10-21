package org.nxy.hasstools.objects

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class WifiGeofenceConfig(
    val enabled: Boolean = false,
    val items: List<WifiGeofence> = emptyList(),
)

@Serializable
data class GeofenceFunction(
    val fastEnter: Boolean = true,
    val leaveProtection: Boolean = true,
)

@Serializable
data class WifiGeofence(
    val geofenceId: String = "${System.nanoTime()}-${UUID.randomUUID()}",
    val geofenceName: String = "",
    val effectUserIds: Set<String> = emptySet(),
    val zoneId: String = "",
    val enabled: Boolean = true,
    val functions: GeofenceFunction = GeofenceFunction(),
    val ssidList: Set<WifiGeofenceWifiInfo> = emptySet(),
    val bssidList: Set<WifiGeofenceWifiInfo> = emptySet(),
)

@Serializable
data class WifiGeofenceWifiInfo(
    val ssid: String,
    val bssid: String,
)
