package org.nxy.hasstools.utils

import android.content.Context
import android.location.Location
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class HassClient(
    private val baseUrl: String,
    private var token: String? = null,
    private var deviceId: String? = null,
    private var deviceName: String? = null,
    private var webhookId: String? = null
) {
    private var cachedZones: List<Zone>? = null

    // {
    //  "device_id": "ABCDEFGH",
    //  "app_id": "awesome_home",
    //  "app_name": "Awesome Home",
    //  "app_version": "1.2.0",
    //  "device_name": "Robbies iPhone",
    //  "manufacturer": "Apple, Inc.",
    //  "model": "iPhone X",
    //  "os_name": "iOS",
    //  "os_version": "iOS 10.12",
    //  "supports_encryption": true,
    //  "app_data": {
    //    "push_notification_key": "abcdef"
    //  }
    //}
    fun registerDevice(context: Context): String? {
        val appId = context.packageName
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
        val appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName

        val reqJson = """
            {
                "device_id": "$deviceId",
                "app_id": "$appId",
                "app_name": "$appName",
                "app_version": "$appVersion",
                "device_name": "$deviceName",
                "manufacturer": "${android.os.Build.MANUFACTURER}",
                "model": "${android.os.Build.MODEL}",
                "os_name": "Android",
                "os_version": "${android.os.Build.VERSION.SDK_INT}",
                "supports_encryption": false
            }
        """.trimIndent()

        println("设备注册请求：$reqJson")

        val requestBody = reqJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        try {
            val request = Request.Builder()
                .url("$baseUrl/api/mobile_app/registrations")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)

            NetworkMonitor.getHttpClient().newCall(request.build()).execute().use { response ->
                if (response.isSuccessful) {
                    // {
                    //  "cloudhook_url": "https://hooks.nabu.casa/randomlongstring123",
                    //  "remote_ui_url": "https://randomlongstring123.ui.nabu.casa",
                    //  "secret": "qwerty",
                    //  "webhook_id": "abcdefgh"
                    //}
                    val resJson = JSONObject(response.body.string())

                    // 只看webhook_id
                    val webhookId = resJson.getString("webhook_id")

                    println("设备注册成功，webhook_id：$webhookId")

                    return webhookId
                } else {
                    println("设备注册失败，状态码：${response.code}")

                    return null
                }
            }
        } catch (e: Exception) {
            println("设备注册失败，状态码：${e.message}")
            return null
        }
    }

    /**
     * 发送Webhook请求
     * @param body 请求体
     * @return 响应体
     * @throws IOException 网络请求失败
     */
    private fun postWebhook(body: String): String {
        val requestBody = body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("$baseUrl/api/webhook/$webhookId")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        NetworkMonitor.getHttpClient().newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val resBody = response.body.string()

                println("Webhook发送成功，返回：$resBody")

                return resBody
            } else {
                throw IOException("Webhook发送失败，状态码：${response.code}")
            }
        }
    }

    /**
     * 获取配置
     * @return 配置对象
     * @throws IOException 网络请求失败
     */
    fun getConfig(): JSONObject {
        val getConfigBody = postWebhook(
            """
            {
                "type": "get_config"
            }
        """.trimIndent()
        )
        println("获取配置：$getConfigBody")
        return JSONObject(getConfigBody)
    }

    /**
     * 获取区域列表
     * @param allowCache 是否允许使用缓存。如果未显式设置，则默认允许使用缓存。
     * @return 区域列表
     * @throws IOException 网络请求失败
     */
    fun getZones(allowCache: Boolean = true): List<Zone> {
        // [{
        //	"entity_id": "zone.bu_shi_shan",
        //	"state": "0",
        //	"attributes": {
        //		"latitude": 0.84650876806598,
        //		"longitude": 0.23445455170825,
        //		"radius": 114.0,
        //		"passive": false,
        //		"persons": [],
        //		"editable": true,
        //		"icon": "mdi:map-marker",
        //		"friendly_name": "不是山"
        //	},
        //	"last_changed": "2025-01-26T07:49:56.473112+00:00",
        //	"last_reported": "2025-01-26T07:49:56.473112+00:00",
        //	"last_updated": "2025-01-26T07:49:56.473112+00:00",
        //	"context": {
        //		"id": "KFC0CRAZY0THURSDAY0V0ME050",
        //		"parent_id": null,
        //		"user_id": null
        //	}
        //}]
        try {
            val getZonesBody = postWebhook(
                """
                    {
                        "type": "get_zones"
                    }
                """.trimIndent()
            )
            val getZonesJson = JSONArray(getZonesBody)

            val zones: MutableList<Zone> = ArrayList()

            for (i in 0 until getZonesJson.length()) {
                val zone = getZonesJson.getJSONObject(i)

                val id = zone.getString("entity_id")
                val name = zone.getJSONObject("attributes").getString("friendly_name")
                val latitude = zone.getJSONObject("attributes").getDouble("latitude")
                val longitude = zone.getJSONObject("attributes").getDouble("longitude")
                val radius = zone.getJSONObject("attributes").getDouble("radius")

                zones.add(Zone(id, name, latitude, longitude, radius))
            }

            cachedZones = zones

            return zones
        } catch (e: Exception) {
            if (allowCache) {
                cachedZones?.let { return it }
            }

            // 如果没有缓存或还没有获取到缓存，则抛出异常
            throw e
        }
    }

    /**
     * 发送位置信息
     * @param locationName 位置名称
     * @param location 位置
     * @throws IOException 网络请求失败
     */
    fun postLocation(
        locationName: String,
        location: Location,
    ) {
        val jsonObject = JSONObject()
        jsonObject.put("type", "update_location")

        val dataObject = JSONObject()
        dataObject.put("location_name", locationName)
        dataObject.put("gps", JSONArray().put(location.latitude).put(location.longitude))
        dataObject.put("gps_accuracy", location.accuracy)

        if (location.hasAltitude() && location.verticalAccuracyMeters > 0) {
            dataObject.put("altitude", location.altitude)
            dataObject.put("vertical_accuracy", location.verticalAccuracyMeters)
        }

        if (location.hasSpeed()) {
            dataObject.put("speed", location.speed)
        }

        if (location.hasBearing() && location.bearingAccuracyDegrees > 0) {
            dataObject.put("course", location.bearing)
        }

        jsonObject.put("data", dataObject)

        println("发送位置信息：$jsonObject")

        postWebhook(jsonObject.toString())
    }
}

data class Zone(
    /**
     * ID
     */
    val id: String,
    /**
     * 名称
     */
    val name: String,
    /**
     * 中心点
     */
    private val location: Location,
    /**
     * 半径（单位：米）
     */
    val radius: Double
) {
    init {
        location.provider = "zone"
    }

    constructor(id: String, name: String, latitude: Double, longitude: Double, radius: Double) : this(
        id, name, Location("zone").apply {
            this.latitude = latitude
            this.longitude = longitude
        }, radius
    )

    fun distance(location: Location): Float {
        return this.location.distanceTo(location)
    }

    fun distance(lat: Double, lon: Double): Float {
        return distance(Location("zone").apply {
            this.latitude = lat
            this.longitude = lon
        })
    }

    fun contains(location: Location): Boolean {
        return distance(location) <= radius + location.accuracy
    }

    fun contains(lat: Double, lon: Double, accuracy: Double): Boolean {
        return distance(lat, lon) <= radius + accuracy
    }

    val latitude: Double get() = location.latitude
    val longitude: Double get() = location.longitude
}
