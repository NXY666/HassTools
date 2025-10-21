package org.nxy.hasstools.utils

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.nxy.hasstools.App
import org.nxy.hasstools.data.WifiGeofenceDataStore
import org.nxy.hasstools.objects.FusedProvider
import org.nxy.hasstools.utils.amap.AMap
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

suspend fun getLastLocation(
    context: Context,
    fusedProvider: FusedProvider = FusedProvider.NONE,
    tryHarder: Boolean = true
): Location? {
    if (
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return null
    }

    val locationManager = context.getSystemService(LocationManager::class.java)

    val providers = buildList {
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (fusedProvider != FusedProvider.NONE) add("fused")
        add(LocationManager.PASSIVE_PROVIDER)
    }.filter { provider ->
        runCatching {
            locationManager.isProviderEnabled(provider) || provider == "fused"
        }.getOrDefault(false)
    }.toMutableList()

    while (providers.isNotEmpty()) {
        val bestProvider = providers.removeAt(0)

        val location: Location? = try {
            withTimeout(5_000) {
                if (bestProvider == "fused" && fusedProvider == FusedProvider.AMAP) {
                    return@withTimeout AMap.getLastLocation()
                }

                return@withTimeout locationManager.getLastKnownLocation(bestProvider)
            }
        } catch (_: TimeoutCancellationException) {
            println("$bestProvider 获取最后位置超时")
            null
        }

        if (location != null) {
            return location
        }
    }

    if (tryHarder) {
        return getCurrentLocation(context, fusedProvider, false)
    }

    return null
}

suspend fun getCurrentLocation(
    context: Context,
    fusedProvider: FusedProvider = FusedProvider.NONE,
    tryHarder: Boolean = true
): Location? {
    if (ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return null
    }

    val locationManager = context.getSystemService(LocationManager::class.java)

    // 获取可用的定位提供者
    val providers = buildList {
        if (fusedProvider != FusedProvider.NONE) add("fused")
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.PASSIVE_PROVIDER)
    }.filter { provider ->
        runCatching {
            locationManager.isProviderEnabled(provider) || provider == "fused"
        }.getOrDefault(false)
    }.toMutableList()

    println("可用的定位提供者：$providers")
    while (providers.isNotEmpty()) {
        val bestProvider = providers.removeAt(0)
        println("使用定位提供者：$bestProvider")
        val location: Location? = try {
            withTimeout(5_000L) {
                if (bestProvider == "fused") {
                    if (fusedProvider == FusedProvider.AMAP) {
                        return@withTimeout AMap.getCurrentLocation()
                    }
                }

                suspendCoroutine { continuation ->
                    locationManager.getCurrentLocation(
                        bestProvider,
                        null,
                        ContextCompat.getMainExecutor(context)
                    ) { location ->
                        println("$bestProvider 定位结果：$location")
                        continuation.resume(location)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            println("$bestProvider 定位超时")
            null
        }

        if (location != null) {
            return location
        }
    }

    if (tryHarder) {
        return getLastLocation(context, fusedProvider, false)
    }

    return null
}

fun checkProviderEnabled(provider: String): Boolean {
    val locationManager = App.context.getSystemService(LocationManager::class.java)

    return runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
}

class LocationInfo(
    val location: Location
) {
    val createElapsedTime = SystemClock.elapsedRealtime()

    var ignoreTimes = 0
}

object LocationHandler {
    private var lastLocationInfo: LocationInfo? = null

    private var currLocationInfo: LocationInfo? = null

    fun getZones(hassClient: HassClient): List<Zone> {
        return try {
            hassClient.getZones()
        } catch (_: Exception) {
            return emptyList()
        }
    }

    fun getLocationName(oldLocationName: String?, newLocation: Location, zones: List<Zone>): String {
        val rawAccuracy = newLocation.accuracy.toDouble()
        val sigmaMeters = if (rawAccuracy.isFinite() && rawAccuracy > 0.0) rawAccuracy else 1.0
        val gaussianRadiusMeters = sigmaMeters
        val gaussianTotalMass = gaussianDiskMassMeters(gaussianRadiusMeters, sigmaMeters)

        data class ScoredZone(val zone: Zone, val score: Double, val distanceMeters: Double)

        val scoredZones = zones.mapNotNull { zone ->
            val zoneRadius = zone.radius

            val intersectionMass = intersectWeightedArea(
                zone.latitude,
                zone.longitude,
                zoneRadius,
                newLocation.latitude,
                newLocation.longitude,
                gaussianRadiusMeters,
                sigmaMeters
            )
            val distanceMeters = zone.distance(newLocation).toDouble()
            val intersectionArea = circleIntersectionArea(distanceMeters, zoneRadius, gaussianRadiusMeters)

            if (intersectionMass <= 0.0 && intersectionArea <= 0.0) {
                return@mapNotNull null
            }

            val uniformArea = uniformDiskAreaMeters(zoneRadius)
            val gaussianRatio = if (gaussianTotalMass > 0.0) intersectionMass / gaussianTotalMass else 0.0
            val uniformRatio = if (uniformArea > 0.0) intersectionArea / uniformArea else 0.0
            val score = maxOf(gaussianRatio, uniformRatio).coerceIn(0.0, 1.0)

            ScoredZone(zone, score, distanceMeters)
        }

        val nearestZone = scoredZones.minWithOrNull(
            compareByDescending<ScoredZone> { it.score }
                .thenBy { it.distanceMeters - it.zone.radius }
        )?.zone

        val nearestLocationName = if (nearestZone == null) {
            "not_home"
        } else if (nearestZone.id == "zone.home") {
            "home"
        } else {
            nearestZone.name
        }

        // 如果没有上次位置，则直接返回当前zone
        // 如果名称一样，说明没动，直接返回
        // 如果名称不一样，说明离开了旧的zone
        if (oldLocationName == null || nearestLocationName == oldLocationName) {
            return nearestLocationName
        }

        // 如果精确度过低，则不主动进入任何zone
        if (newLocation.accuracy > 200) {
            return "not_home"
        }

        return nearestLocationName
    }

    fun getLastLocation(): Location? {
        return lastLocationInfo?.location
    }

    fun getCurrLocation(): Location? {
        return currLocationInfo?.location
    }

    fun getCurrIgnoreTimes(): Int {
        return currLocationInfo?.ignoreTimes ?: 0
    }

    fun hasLocationChange(location: Location): Boolean {
        currLocationInfo.let {
            if (it == null) {
                return true
            }

            val currLocation = it.location

            val currLatStr = String.format(Locale.CHINA, "%.4f", currLocation.latitude)
            val currLonStr = String.format(Locale.CHINA, "%.4f", currLocation.longitude)

            val newLatStr = String.format(Locale.CHINA, "%.4f", location.latitude)
            val newLonStr = String.format(Locale.CHINA, "%.4f", location.longitude)

            println(
                "currLatStr: $currLatStr, currLonStr: $currLonStr, newLatStr: $newLatStr, newLonStr: $newLonStr, same: ${currLatStr == newLatStr && currLonStr == newLonStr}"
            )

            return currLatStr != newLatStr || currLonStr != newLonStr
        }
    }

    fun postLocation(hassClient: HassClient, location: Location, locationName: String?): Boolean {
        try {
            hassClient.postLocation(locationName ?: "unknown", location)

            return true
        } catch (e: Exception) {
            e.printStackTrace()

            return false
        }
    }

    fun updateLocation(location: Location): LocationInfo {
        val newLocationInfo = LocationInfo(location)

        // 如果没有速度，补充速度
        newLocationInfo.location.apply {
            val currLocationInfo = currLocationInfo

            println("rawSpeed: ${speed}, rawSpeedAccuracy: $speedAccuracyMetersPerSecond")

            if ((!hasSpeed() || speed == 0.0f) && currLocationInfo != null) {
                val lastLocation = currLocationInfo.location

                val distance = lastLocation.distanceTo(this).toDouble()
                val deltaSeconds = (newLocationInfo.createElapsedTime - currLocationInfo.createElapsedTime) / 1000

                println("distance: $distance, deltaSeconds: $deltaSeconds")

                if (deltaSeconds > 0) {
                    speed = (distance / deltaSeconds).toFloat()
                    speedAccuracyMetersPerSecond = accuracy / deltaSeconds
                }
            }

            println("hasSpeed: ${hasSpeed()}, hasSpeedAccuracy: ${hasSpeedAccuracy()}")
            println("currLocation.speed: $speed")
            println("currLocation.speedAccuracyMetersPerSecond: $speedAccuracyMetersPerSecond")
        }

        lastLocationInfo = currLocationInfo

        currLocationInfo = newLocationInfo

        return newLocationInfo
    }

    fun ignoreLocation() {
        currLocationInfo?.let { it.ignoreTimes++ }
    }

    /**
     * 获取下次定位的时间间隔
     *
     * @return 下次定位的时间间隔（单位：分钟）
     */
    private fun getScheduledIntervalMinute(): Int {
        val lastLocationInfo = lastLocationInfo
        val currLocationInfo = currLocationInfo

        // 信息不全，2min
        if (lastLocationInfo == null || currLocationInfo == null) {
            return 2
        }

        // ignoreTimes大于1则代表上次位置没变
        // 忽略次数1-5，每次+10min
        // 忽略次数大于5，前5次+10min，之后每次+15min
        when (currLocationInfo.ignoreTimes) {
            in 1..5 -> return currLocationInfo.ignoreTimes * 10
            in 6..Int.MAX_VALUE -> return 50 + (currLocationInfo.ignoreTimes - 5) * 15
        }

        val currLocation = currLocationInfo.location

        val speed = currLocation.speed

        println("speed: $speed")

        // 1min移动小于30米，5
        // 1min移动大于30米且小于30km/h，3
        // 大于30km/h且小于50km/h，2
        // 大于50km/h，1
        return when {
            speed < 0.5 -> 5
            speed < 8.33 -> 3
            speed < 13.89 -> 2
            else -> 1
        }
    }

    fun getNextScheduledElapsedTime(): Long {
        // currTime+interval*60*1000，如果离现在的时间小于1min，就等1min
        val elapsedTime = SystemClock.elapsedRealtime()
        val currTime = currLocationInfo?.createElapsedTime ?: elapsedTime

        val wishedIntervalMinute = getScheduledIntervalMinute()

        println("currTime: $currTime, wishedIntervalMinute: $wishedIntervalMinute, elapsedTime: $elapsedTime")

        return (currTime + wishedIntervalMinute * 60 * 1000).coerceAtLeast(elapsedTime + 30 * 1000)
    }

    fun isSticky(
        application: Application,
        userId: String,
        zones: List<Zone>,
        currLocationName: String?,
        newLocationName: String
    ): Boolean {
        val geofenceConfig = WifiGeofenceDataStore().readData()

        if (!geofenceConfig.enabled || currLocationName in listOf(
                newLocationName,
                "not_home",
                "unknown",
                null
            )
        ) {
            println(
                "无需离开保护 enabled:${geofenceConfig.enabled} currLocationName:$currLocationName newLocationName:$newLocationName"
            )
            return false
        }

        val targetSsidList = mutableSetOf<String>()
        val targetBssidList = mutableSetOf<String>()

        geofenceConfig.items.forEach { geofenceItem ->
            if (!geofenceItem.enabled) {
                println("地理围栏 ${geofenceItem.geofenceName} 已禁用，跳过离开保护")
                return@forEach
            }

            if (!geofenceItem.functions.leaveProtection) {
                println("地理围栏 ${geofenceItem.geofenceName} 未启用离开保护，跳过")
                return@forEach
            }

            if (geofenceItem.effectUserIds.isNotEmpty() && !geofenceItem.effectUserIds.contains(userId)) {
                println("用户不受 ${geofenceItem.geofenceName} 的离开保护影响")
                return@forEach
            }

            val zone = zones.find { it.id == geofenceItem.zoneId } ?: return@forEach

            if (zone.name == currLocationName || (zone.id == "zone.home" && currLocationName == "home")) {
                geofenceItem.ssidList.forEach { targetSsidList.add(it.ssid) }
                geofenceItem.bssidList.forEach { targetBssidList.add(it.bssid) }
            }
        }

        println("离开保护目标 SSID: $targetSsidList")
        println("离开保护目标 BSSID: $targetBssidList")

        if (targetSsidList.isEmpty() && targetBssidList.isEmpty()) {
            println("未找到离开保护的目标 Wi-Fi")
            return false
        }

        val wifiManager = application.getSystemService(WifiManager::class.java)

        if (ActivityCompat.checkSelfPermission(
                application, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            println("无 Wi-Fi 权限，无法执行离开保护")
            return false
        }

        val scanResults = wifiManager.scanResults

        val leaveProtectionWifiExists = scanResults.any {
            val ssid = it.wifiSsid.toString().removeSurrounding("\"")
            val bssid = it.BSSID
            println("扫描到的 Wi-Fi：$ssid $bssid, ${targetSsidList.contains(ssid)} ${targetBssidList.contains(bssid)}")

            targetSsidList.contains(ssid) || targetBssidList.contains(bssid)
        }

        if (leaveProtectionWifiExists) {
            println("黏住了")
            return true
        } else {
            println("未黏住")
            return false
        }
    }

    private fun circleIntersectionArea(distanceMeters: Double, radiusA: Double, radiusB: Double): Double {
        if (distanceMeters >= radiusA + radiusB) return 0.0

        val minRadius = minOf(radiusA, radiusB)
        val maxRadius = maxOf(radiusA, radiusB)
        if (distanceMeters + minRadius <= maxRadius) {
            return Math.PI * minRadius * minRadius
        }

        val partA = radiusA * radiusA * acos(
            ((distanceMeters * distanceMeters) + (radiusA * radiusA) - (radiusB * radiusB)) / (2 * distanceMeters * radiusA)
        )
        val partB = radiusB * radiusB * acos(
            ((distanceMeters * distanceMeters) + (radiusB * radiusB) - (radiusA * radiusA)) / (2 * distanceMeters * radiusB)
        )
        val partC = 0.5 * sqrt(
            abs((-distanceMeters + radiusA + radiusB) * (distanceMeters + radiusA - radiusB) * (distanceMeters - radiusA + radiusB) * (distanceMeters + radiusA + radiusB))
        )

        return partA + partB - partC
    }
}
