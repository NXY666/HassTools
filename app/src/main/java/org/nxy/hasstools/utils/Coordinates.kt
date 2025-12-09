package org.nxy.hasstools.utils

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 坐标系类型枚举。
 */
enum class CoordinateType {
    /** WGS-84 坐标系（GPS 原始坐标） */
    WGS84,
    /** GCJ-02 坐标系（火星坐标系） */
    GCJ02,
    /** BD-09 坐标系（百度坐标系） */
    BD09
}

/**
 * 经纬度坐标数据类。
 *
 * @property longitude 经度
 * @property latitude 纬度
 * @property altitude 海拔（可选）
 */
data class LatLngAlt(
    val longitude: Double,
    val latitude: Double,
    val altitude: Double? = null
)

/**
 * 坐标转换工具类。
 *
 * 提供 WGS-84、GCJ-02、BD-09 三种坐标系之间的相互转换。
 *
 * @property sourceType 源坐标系类型
 * @property source 源坐标
 */
class Coordinates(
    private val sourceType: CoordinateType,
    private val source: LatLngAlt
) {
    companion object {
        private const val A = 6378245.0
        private const val EE = 0.006693421622965943
        private const val X_PI = PI * 3000.0 / 180.0
        private const val EPS = 1e-7

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
        private fun transformLng(x: Double, y: Double): Double {
            var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
            ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
            ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
            ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
            return ret
        }

        /**
         * 计算 WGS84 -> GCJ02 的偏移量（度）。
         */
        private fun delta(lng: Double, lat: Double): Pair<Double, Double> {
            val radLat = lat / 180.0 * PI
            val magic = 1 - EE * sin(radLat) * sin(radLat)
            val sqrtMagic = sqrt(magic)
            val dLat = (transformLat(lng - 105.0, lat - 35.0) * 180.0) /
                    (((A * (1 - EE)) / (magic * sqrtMagic)) * PI)
            val dLng = (transformLng(lng - 105.0, lat - 35.0) * 180.0) /
                    ((A / sqrtMagic) * cos(radLat) * PI)
            return Pair(dLng, dLat)
        }

        /**
         * WGS-84 -> GCJ-02
         */
        private fun wgs84ToGcj02(lng: Double, lat: Double): Pair<Double, Double> {
            val (dLng, dLat) = delta(lng, lat)
            return Pair(lng + dLng, lat + dLat)
        }

        /**
         * GCJ-02 -> WGS-84（厘米级精确反解，迭代收敛）
         */
        private fun gcj02ToWgs84(lng: Double, lat: Double, eps: Double = EPS): Pair<Double, Double> {
            var wgsLng = lng
            var wgsLat = lat
            repeat(15) {
                val (dLng, dLat) = delta(wgsLng, wgsLat)
                val gcjLng = wgsLng + dLng
                val gcjLat = wgsLat + dLat
                val offLng = gcjLng - lng
                val offLat = gcjLat - lat
                wgsLng -= offLng
                wgsLat -= offLat
                if (maxOf(abs(offLng), abs(offLat)) < eps) {
                    return@repeat
                }
            }
            return Pair(wgsLng, wgsLat)
        }

        /**
         * GCJ-02 -> BD-09
         */
        private fun gcj02ToBd09(lng: Double, lat: Double): Pair<Double, Double> {
            val z = sqrt(lng * lng + lat * lat) + 0.00002 * sin(lat * X_PI)
            val theta = kotlin.math.atan2(lat, lng) + 0.000003 * cos(lng * X_PI)
            return Pair(z * cos(theta) + 0.0065, z * sin(theta) + 0.006)
        }

        /**
         * BD-09 -> GCJ-02
         */
        private fun bd09ToGcj02(lng: Double, lat: Double): Pair<Double, Double> {
            val x = lng - 0.0065
            val y = lat - 0.006
            val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
            val theta = kotlin.math.atan2(y, x) - 0.000003 * cos(x * X_PI)
            return Pair(z * cos(theta), z * sin(theta))
        }
    }

    // 缓存转换结果
    private var _wgs84: LatLngAlt? = null
    private var _gcj02: LatLngAlt? = null
    private var _bd09: LatLngAlt? = null

    init {
        // 根据源坐标系类型初始化对应的缓存
        when (sourceType) {
            CoordinateType.WGS84 -> _wgs84 = source
            CoordinateType.GCJ02 -> _gcj02 = source
            CoordinateType.BD09 -> _bd09 = source
        }
    }

    /**
     * 获取 WGS-84 坐标。
     */
    val wgs84: LatLngAlt
        get() {
            _wgs84?.let { return it }

            val gcj = gcj02
            val (lng, lat) = gcj02ToWgs84(gcj.longitude, gcj.latitude)
            return LatLngAlt(lng, lat, gcj.altitude).also { _wgs84 = it }
        }

    /**
     * 获取 GCJ-02 坐标。
     */
    val gcj02: LatLngAlt
        get() {
            _gcj02?.let { return it }

            // 从 WGS84 转换
            _wgs84?.let {
                val (lng, lat) = wgs84ToGcj02(it.longitude, it.latitude)
                return LatLngAlt(lng, lat, it.altitude).also { converted -> _gcj02 = converted }
            }

            // 从 BD09 转换
            _bd09?.let {
                val (lng, lat) = bd09ToGcj02(it.longitude, it.latitude)
                return LatLngAlt(lng, lat, it.altitude).also { converted -> _gcj02 = converted }
            }

            error("No valid source coordinates available")
        }

    /**
     * 获取 BD-09 坐标。
     */
    val bd09: LatLngAlt
        get() {
            _bd09?.let { return it }

            val gcj = gcj02
            val (lng, lat) = gcj02ToBd09(gcj.longitude, gcj.latitude)
            return LatLngAlt(lng, lat, gcj.altitude).also { _bd09 = it }
        }
}
