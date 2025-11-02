package org.nxy.hasstools.utils.amap

import android.location.Location
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import kotlinx.coroutines.suspendCancellableCoroutine
import org.nxy.hasstools.App
import org.nxy.hasstools.BuildConfig
import org.nxy.hasstools.data.AmapDataStore
import kotlin.coroutines.resume

/**
 * 高德地图定位工具类。
 *
 * 封装了高德地图 SDK 的定位功能，提供位置获取和坐标转换能力。
 */
object AMap {
    /** 静态 API Key（编译时配置） */
    const val STATIC_API_KEY = BuildConfig.AMAP_API_KEY

    /** 是否有静态 API Key */
    val hasStaticApiKey = STATIC_API_KEY.isNotBlank()

    /**
     * 获取当前使用的 API Key。
     *
     * 优先使用静态 API Key，否则从运行时配置读取。
     */
    val apiKey
        get() = if (hasStaticApiKey) {
            STATIC_API_KEY
        } else {
            AmapDataStore().readData().runtimeApiKey
        }

    /**
     * 初始化高德地图 SDK。
     *
     * 更新隐私合规设置并刷新 API Key。
     */
    fun init() {
        AMapLocationClient.updatePrivacyShow(App.context, true, true)
        AMapLocationClient.updatePrivacyAgree(App.context, true)

        refreshApiKey()
    }

    /**
     * 刷新高德地图 API Key。
     *
     * 从配置中读取并设置新的 API Key。
     */
    fun refreshApiKey() {
        val newApiKey = apiKey
        if (newApiKey.isBlank()) {
            println("高德Key未设置")
            return
        }

        println("高德Key设置为：$newApiKey")

        AMapLocationClient.setApiKey(newApiKey)
    }

    /**
     * 获取最后已知的位置。
     *
     * 从高德地图 SDK 获取最后已知位置，并转换为 WGS84 坐标系。
     *
     * @return 最后已知的位置，失败返回 null
     */
    suspend fun getLastLocation(): Location? {
        val applicationContext = App.context

        return suspendCancellableCoroutine { continuation ->
            try {
                val client = AMapLocationClient(applicationContext)

                val amapLocation = client.lastKnownLocation

                val location = AMapLocationConverter.gcj02ToWgs84(
                    amapLocation
                ).apply {
                    provider = "amap"
                }

                continuation.resume(location)
            } catch (e: Exception) {
                println("高德上次定位获取失败：${e.message}")
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }

    /**
     * 获取当前位置。
     *
     * 主动请求高德地图 SDK 进行定位，并转换为 WGS84 坐标系。
     * 使用高精度模式，单次定位，超时时间为 5 秒。
     *
     * @return 当前位置，失败返回 null
     */
    suspend fun getCurrentLocation(): Location? {
        val applicationContext = App.context

        return suspendCancellableCoroutine { continuation ->
            try {
                val client = AMapLocationClient(applicationContext)
                val option = AMapLocationClientOption().apply {
                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                    isNeedAddress = false
                    isOnceLocation = true
                    isSensorEnable = true
                    httpTimeOut = 5000
                }

                client.setLocationOption(option)
                client.setLocationListener { amapLocation ->
                    if (amapLocation == null || amapLocation.errorCode != 0) {
                        println("高德定位失败：${amapLocation?.errorCode} - ${amapLocation?.errorInfo}")
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                        client.stopLocation()
                        client.onDestroy()
                        return@setLocationListener
                    }

                    println("accuracy: ${amapLocation.accuracy} ${amapLocation.hasAccuracy()}")
                    println("altitude: ${amapLocation.altitude} ${amapLocation.hasAltitude()}")
                    println("bearing: ${amapLocation.bearing} ${amapLocation.hasBearing()}")
                    println("conScenario: ${amapLocation.conScenario}")
                    println("coordType: ${amapLocation.coordType}")
                    println("errorCode: ${amapLocation.errorCode}")
                    println("errorInfo: ${amapLocation.errorInfo}")
                    println("extras: ${amapLocation.extras}")
                    println("gpsAccuracyStatus: ${amapLocation.gpsAccuracyStatus}")
                    println("latitude: ${amapLocation.latitude}")
                    println(
                        "locationQualityReport: gpsStatus=${amapLocation.locationQualityReport.gpsStatus}, netUseTime=${amapLocation.locationQualityReport.netUseTime}, wifiAble=${amapLocation.locationQualityReport.isWifiAble}, adviseMessage=${amapLocation.locationQualityReport.adviseMessage}, isInstalledHighDangerMockApp=${amapLocation.locationQualityReport.isInstalledHighDangerMockApp}"
                    )
                    println("locationType: ${amapLocation.locationType}")
                    println("longitude: ${amapLocation.longitude}")
                    println("provider: ${amapLocation.provider}")
                    println("satellites: ${amapLocation.satellites}")
                    println("speed: ${amapLocation.speed} ${amapLocation.hasSpeed()}")
                    println("trustedLevel: ${amapLocation.trustedLevel}")

                    // 转换为WGS84坐标
                    val location = AMapLocationConverter.gcj02ToWgs84(
                        amapLocation
                    ).apply {
                        provider = "amap"
                    }

                    println("copy accuracy: ${location.accuracy} ${location.hasAccuracy()}")
                    println("copy altitude: ${location.altitude} ${location.hasAltitude()}")
                    println("copy bearing: ${location.bearing} ${location.hasBearing()}")
                    println("copy extras: ${location.extras}")
                    println("copy latitude: ${location.latitude}")
                    println("copy longitude: ${location.longitude}")
                    println("copy provider: ${location.provider}")
                    println("copy speed: ${location.speed} ${location.hasSpeed()}")

                    if (continuation.isActive) {
                        println(
                            "高德定位成功：${amapLocation.locationType} - ${location.latitude} , ${location.longitude}"
                        )
                        continuation.resume(location)
                    }

                    client.stopLocation()
                    client.onDestroy()
                }
                client.startLocation()

                continuation.invokeOnCancellation {
                    client.stopLocation()
                    client.onDestroy()
                }
            } catch (e: Exception) {
                println("高德定位失败：${e.message}")
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }
}
