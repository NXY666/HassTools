package org.nxy.hasstools.utils.amap

import android.location.Location
import com.amap.api.location.AMapLocation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 高德地图坐标转换工具。
 *
 * 提供 GCJ-02（火星坐标系）与 WGS-84（GPS 坐标系）之间的转换功能。
 */
object AMapLocationConverter {
    private const val A = 6378245.0
    private const val EE = 0.006693421622965943
    private const val DEFAULT_TOL_METERS = 0.5

    /**
     * 经纬度坐标数据类。
     *
     * @property lat 纬度
     * @property lon 经度
     */
    data class LatLng(val lat: Double, val lon: Double)

    /**
     * 纬度变换函数。
     */
    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    /**
     * 经度变换函数。
     */
    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }

    /**
     * 计算坐标偏移量。
     */
    private fun delta(lat: Double, lon: Double): LatLng {
        val dLat = transformLat(lon - 105.0, lat - 35.0)
        val dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        val magic = 1 - EE * sin(radLat) * sin(radLat)
        val sqrtMagic = sqrt(magic)
        val dLat2 = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        val dLon2 = (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return LatLng(dLat2, dLon2)
    }

    /**
     * WGS-84 坐标转 GCJ-02 坐标。
     */
    private fun wgs84ToGcj02(lat: Double, lon: Double): LatLng {
        val d = delta(lat, lon)
        return LatLng(lat + d.lat, lon + d.lon)
    }

    /**
     * 计算两点之间的距离（Haversine 公式）。
     *
     * @return 距离（单位：米）
     */
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6378137.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }

    /**
     * GCJ-02 坐标转 WGS-84 坐标（高精度迭代算法）。
     *
     * @param lat GCJ-02 纬度
     * @param lon GCJ-02 经度
     * @param tolMeters 误差容限（单位：米），默认为 0.5 米
     * @param maxIter 最大迭代次数，默认为 15 次
     * @return WGS-84 坐标
     */
    private fun gcj02ToWgs84Acc(
        lat: Double,
        lon: Double,
        tolMeters: Double = DEFAULT_TOL_METERS,
        maxIter: Int = 15
    ): LatLng {
        var wLat = lat
        var wLon = lon
        repeat(maxIter) {
            val g = wgs84ToGcj02(wLat, wLon)
            val err = haversineMeters(g.lat, g.lon, lat, lon)
            if (err <= tolMeters) return LatLng(wLat, wLon)
            val dLat = g.lat - lat
            val dLon = g.lon - lon
            wLat -= dLat
            wLon -= dLon
        }
        return LatLng(wLat, wLon)
    }

    /**
     * 将 WGS-84 坐标的 AMapLocation 转换为 GCJ-02 坐标。
     *
     * @param amapLocation 高德定位对象（WGS-84 坐标系）
     * @return 转换后的 Location 对象（GCJ-02 坐标系）
     * @throws IllegalArgumentException 如果坐标类型不是 WGS-84
     */
    fun wgs84ToGcj02(amapLocation: AMapLocation): Location {
        val newLocation = Location(amapLocation)

        if (amapLocation.coordType == AMapLocation.COORD_TYPE_GCJ02) {
            return newLocation
        }

        if (amapLocation.coordType != AMapLocation.COORD_TYPE_WGS84) {
            throw IllegalArgumentException(
                "AMapLocation.coordType must be AMapLocation.COORD_TYPE_WGS84, got ${amapLocation.coordType} instead"
            )
        }

        val lat = amapLocation.latitude
        val lon = amapLocation.longitude

        val result = wgs84ToGcj02(lat, lon)

        newLocation.latitude = result.lat
        newLocation.longitude = result.lon
        return newLocation
    }

    /**
     * 将 GCJ-02 坐标的 AMapLocation 转换为 WGS-84 坐标。
     *
     * @param amapLocation 高德定位对象（GCJ-02 坐标系）
     * @return 转换后的 Location 对象（WGS-84 坐标系）
     * @throws IllegalArgumentException 如果坐标类型不是 GCJ-02
     */
    fun gcj02ToWgs84(amapLocation: AMapLocation): Location {
        val newLocation = Location(amapLocation)

        if (amapLocation.coordType == AMapLocation.COORD_TYPE_WGS84) {
            return newLocation
        }

        if (amapLocation.coordType != AMapLocation.COORD_TYPE_GCJ02) {
            throw IllegalArgumentException(
                "AMapLocation.coordType must be AMapLocation.COORD_TYPE_GCJ02, got ${amapLocation.coordType} instead"
            )
        }

        val lat = amapLocation.latitude
        val lon = amapLocation.longitude

        val result = gcj02ToWgs84Acc(lat, lon)

        newLocation.latitude = result.lat
        newLocation.longitude = result.lon
        return newLocation
    }
}