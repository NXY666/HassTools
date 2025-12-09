package org.nxy.hasstools.utils.amap

import android.location.Location
import com.amap.api.location.AMapLocation
import org.nxy.hasstools.utils.CoordinateType
import org.nxy.hasstools.utils.Coordinates
import org.nxy.hasstools.utils.LatLngAlt

/**
 * 高德地图坐标转换工具。
 *
 * 提供 GCJ-02（火星坐标系）与 WGS-84（GPS 坐标系）之间的转换功能。
 */
object AMapLocationConverter {
    /**
     * 将 AMapLocation 转换为 WGS-84 坐标的 Location 对象。
     *
     * @param amapLocation 高德定位对象
     * @return 转换后的 Location 对象（WGS-84 坐标系）
     */
    fun toWgs84Location(amapLocation: AMapLocation): Location {
        return Location("amap").apply {
            val source = LatLngAlt(
                longitude = amapLocation.longitude,
                latitude = amapLocation.latitude
            )
            val coordType = when (amapLocation.coordType) {
                AMapLocation.COORD_TYPE_GCJ02 -> CoordinateType.GCJ02
                else -> CoordinateType.WGS84
            }
            val coordinates = Coordinates(coordType, source)
            val realLatLng = coordinates.wgs84

            latitude = realLatLng.latitude
            longitude = realLatLng.longitude

            accuracy = amapLocation.accuracy

            if (amapLocation.hasAltitude() && amapLocation.altitude != 0.0) {
                altitude = amapLocation.altitude
            }
            if (amapLocation.hasVerticalAccuracy() && amapLocation.verticalAccuracyMeters != 0.0f) {
                verticalAccuracyMeters = amapLocation.verticalAccuracyMeters
            }

            if (amapLocation.hasBearing() && amapLocation.bearing != 0.0f) {
                bearing = amapLocation.bearing
            }
            if (amapLocation.hasBearingAccuracy() && amapLocation.bearingAccuracyDegrees != 0.0f) {
                bearingAccuracyDegrees = amapLocation.bearingAccuracyDegrees
            }

            if (amapLocation.hasSpeed() && amapLocation.speed != 0.0f) {
                speed = amapLocation.speed
            }
            if (amapLocation.hasSpeedAccuracy() && amapLocation.speedAccuracyMetersPerSecond != 0.0f) {
                speedAccuracyMetersPerSecond = amapLocation.speedAccuracyMetersPerSecond
            }
        }
    }
}