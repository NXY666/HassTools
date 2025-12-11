package org.nxy.hasstools.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coordinates 坐标转换工具类的单元测试。
 */
class CoordinatesTest {

    private val DELTA = 1e-6 // 精度误差范围

    // 测试坐标数据
    private val wgs84Lng = 116.391349
    private val wgs84Lat = 39.907375

    private val gcj02Lng = 116.39759019123527
    private val gcj02Lat = 39.90877629414095

    private val bd09Lng = 116.40396302524593
    private val bd09Lat = 39.915119833724745

    @Test
    fun `convert WGS84 to GCJ02 correctly`() {
        val coords = Coordinates(
            CoordinateType.WGS84,
            LatLngAlt(wgs84Lng, wgs84Lat)
        )

        val result = coords.gcj02
        assertEquals(gcj02Lng, result.longitude, DELTA)
        assertEquals(gcj02Lat, result.latitude, DELTA)
    }

    @Test
    fun `convert WGS84 to BD09 correctly`() {
        val coords = Coordinates(
            CoordinateType.WGS84,
            LatLngAlt(wgs84Lng, wgs84Lat)
        )

        val result = coords.bd09
        assertEquals(bd09Lng, result.longitude, DELTA)
        assertEquals(bd09Lat, result.latitude, DELTA)
    }

    @Test
    fun `convert GCJ02 to WGS84 correctly`() {
        val coords = Coordinates(
            CoordinateType.GCJ02,
            LatLngAlt(gcj02Lng, gcj02Lat)
        )

        val result = coords.wgs84
        assertEquals(wgs84Lng, result.longitude, DELTA)
        assertEquals(wgs84Lat, result.latitude, DELTA)
    }

    @Test
    fun `convert GCJ02 to BD09 correctly`() {
        val coords = Coordinates(
            CoordinateType.GCJ02,
            LatLngAlt(gcj02Lng, gcj02Lat)
        )

        val result = coords.bd09
        assertEquals(bd09Lng, result.longitude, DELTA)
        assertEquals(bd09Lat, result.latitude, DELTA)
    }

    @Test
    fun `convert BD09 to WGS84 correctly`() {
        val coords = Coordinates(
            CoordinateType.BD09,
            LatLngAlt(bd09Lng, bd09Lat)
        )

        val result = coords.wgs84
        assertEquals(wgs84Lng, result.longitude, DELTA)
        assertEquals(wgs84Lat, result.latitude, DELTA)
    }

    @Test
    fun `convert BD09 to GCJ02 correctly`() {
        val coords = Coordinates(
            CoordinateType.BD09,
            LatLngAlt(bd09Lng, bd09Lat)
        )

        val result = coords.gcj02
        assertEquals(gcj02Lng, result.longitude, DELTA)
        assertEquals(gcj02Lat, result.latitude, DELTA)
    }

    @Test
    fun `preserve altitude through conversions`() {
        val altitude = 100.0
        val coords = Coordinates(
            CoordinateType.WGS84,
            LatLngAlt(wgs84Lng, wgs84Lat, altitude)
        )

        assertEquals(altitude, coords.wgs84.altitude)
        assertEquals(altitude, coords.gcj02.altitude)
        assertEquals(altitude, coords.bd09.altitude)
    }

    @Test
    fun `cache converted coordinates`() {
        val coords = Coordinates(
            CoordinateType.WGS84,
            LatLngAlt(wgs84Lng, wgs84Lat)
        )

        // 第一次访问会进行转换
        val gcj02First = coords.gcj02
        // 第二次访问应该返回缓存的结果（同一对象引用）
        val gcj02Second = coords.gcj02

        assert(gcj02First === gcj02Second)
    }
}
