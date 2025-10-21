package org.nxy.hasstools.utils

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.nxy.hasstools.utils.LocationHandler.getLocationName

@RunWith(AndroidJUnit4::class)
class LocationHandlerInstrumentedTest {

    @Test
    fun `return not_home when no zone contains location`() {
        val location = Location("gps").apply {
            latitude = 0.0
            longitude = 0.0
            accuracy = 50f
        }

        val result = getLocationName(null, location, emptyList())

        assertEquals("not_home", result)
    }

    @Test
    fun `return home when location is in home zone`() {
        val location = Location("gps").apply {
            latitude = 31.2304
            longitude = 121.4737
            accuracy = 30f
        }
        val homeZone = Zone(
            id = "zone.home",
            name = "home_zone",
            latitude = location.latitude,
            longitude = location.longitude,
            radius = 100.0
        )

        val result = getLocationName(null, location, listOf(homeZone))

        assertEquals("home", result)
    }

    @Test
    fun `return oldLocationName if still in same zone`() {
        val location = Location("gps").apply {
            latitude = 48.8566
            longitude = 2.3522
            accuracy = 100f
        }
        val workZone = Zone(
            id = "zone.work",
            name = "work",
            latitude = location.latitude,
            longitude = location.longitude,
            radius = 150.0
        )

        val result = getLocationName("work", location, listOf(workZone))

        assertEquals("work", result)
    }

    @Test
    fun `return not_home if accuracy too low`() {
        val location = Location("gps").apply {
            latitude = 34.0522
            longitude = -118.2437
            accuracy = 300f
        }
        val parkZone = Zone(
            id = "zone.park",
            name = "park",
            latitude = location.latitude,
            longitude = location.longitude,
            radius = 200.0
        )

        val result = getLocationName("home", location, listOf(parkZone))

        assertEquals("not_home", result)
    }

    @Test
    fun `choose zone with largest intersectionArea`() {
        val location = Location("gps").apply {
            latitude = 37.4220
            longitude = -122.0841
            accuracy = 30f
        }

        val zoneA = Zone(
            id = "zone.a",
            name = "A",
            latitude = location.latitude + 0.00025,
            longitude = location.longitude,
            radius = 40.0
        )
        val zoneB = Zone(
            id = "zone.b",
            name = "B",
            latitude = location.latitude,
            longitude = location.longitude,
            radius = 80.0
        )

        // Location sits on zone B center, so scoring should favour zone B despite previous name.
        val result = getLocationName("A", location, listOf(zoneA, zoneB))

        assertEquals("B", result)
    }

    @Test
    fun `prefer closer larger zone when both fully contained`() {
        val location = Location("gps").apply {
            latitude = 0.0
            longitude = 0.0
            accuracy = 500f
        }

        val farSmallZone = Zone(
            id = "zone.small_far",
            name = "small_far",
            latitude = location.latitude + 0.0035,
            longitude = location.longitude,
            radius = 20.0
        )
        val nearLargeZone = Zone(
            id = "zone.large_near",
            name = "large_near",
            latitude = location.latitude - 0.0009,
            longitude = location.longitude,
            radius = 50.0
        )

        val result = getLocationName(null, location, listOf(farSmallZone, nearLargeZone))

        assertEquals("large_near", result)
    }

    @Test
    fun `prefer closer smaller zone when both fully contained`() {
        val location = Location("gps").apply {
            latitude = 0.0
            longitude = 0.0
            accuracy = 500f
        }

        val nearSmallZone = Zone(
            id = "zone.small_near",
            name = "small_near",
            latitude = location.latitude - 0.0009,
            longitude = location.longitude,
            radius = 20.0
        )
        val farLargeZone = Zone(
            id = "zone.large_far",
            name = "large_far",
            latitude = location.latitude + 0.0035,
            longitude = location.longitude,
            radius = 50.0
        )

        val result = getLocationName(null, location, listOf(nearSmallZone, farLargeZone))

        assertEquals("small_near", result)
    }

    // 两边圆心距相等时，优先选择半径更大的区域
    @Test
    fun `prefer larger zone when distance to center equal`(){
        val location = Location("gps").apply {
            latitude = 0.0
            longitude = 0.0
            accuracy = 500f
        }

        val zoneA = Zone(
            id = "zone.a",
            name = "A",
            latitude = location.latitude + 0.0018,
            longitude = location.longitude,
            radius = 50.0
        )
        val zoneB = Zone(
            id = "zone.b",
            name = "B",
            latitude = location.latitude - 0.0018,
            longitude = location.longitude,
            radius = 30.0
        )

        val result = getLocationName(null, location, listOf(zoneA, zoneB))

        assertEquals("B", result)
    }
}
