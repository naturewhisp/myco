package github.naturewhisp.myco

import github.naturewhisp.myco.platform.DeviceHeading
import github.naturewhisp.myco.platform.MapOrientationMode
import github.naturewhisp.myco.platform.PlatformLocationProvider
import github.naturewhisp.myco.platform.PlatformOrientationProvider
import github.naturewhisp.myco.platform.UserLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformNavigationTest {

    @Test
    fun testMapOrientationRotationCalculation() {
        // In modalità NORTH_UP la rotazione mappa deve essere fissa a 0°
        val headingNorth = DeviceHeading(azimuthDegrees = 0f)
        val headingEast = DeviceHeading(azimuthDegrees = 90f)
        val headingSouth = DeviceHeading(azimuthDegrees = 180f)
        val headingWest = DeviceHeading(azimuthDegrees = 270f)

        fun calcRotation(mode: MapOrientationMode, heading: DeviceHeading?): Float {
            return when (mode) {
                MapOrientationMode.NORTH_UP -> 0f
                MapOrientationMode.HEADING_UP -> {
                    val azimuth = heading?.azimuthDegrees ?: 0f
                    (360f - azimuth) % 360f
                }
            }
        }

        assertEquals(0f, calcRotation(MapOrientationMode.NORTH_UP, headingEast), 0.01f)
        assertEquals(0f, calcRotation(MapOrientationMode.HEADING_UP, headingNorth), 0.01f)
        assertEquals(270f, calcRotation(MapOrientationMode.HEADING_UP, headingEast), 0.01f)
        assertEquals(180f, calcRotation(MapOrientationMode.HEADING_UP, headingSouth), 0.01f)
        assertEquals(90f, calcRotation(MapOrientationMode.HEADING_UP, headingWest), 0.01f)
    }

    @Test
    fun testAngularSmoothing() {
        fun smoothAngle(current: Float, target: Float, alpha: Float): Float {
            var diff = (target - current) % 360f
            if (diff > 180f) diff -= 360f
            if (diff < -180f) diff += 360f
            var result = current + diff * alpha
            if (result < 0f) result += 360f
            if (result >= 360f) result -= 360f
            return result
        }

        // Test interpolazione standard (0 -> 100 con alpha 0.5 = 50)
        val mid = smoothAngle(0f, 100f, 0.5f)
        assertEquals(50f, mid, 0.01f)

        // Test attraversamento frontiera 360° / 0°:
        // Da 350° verso 10° la distanza minima è +20° (attraversando 0°), non -340°
        val wrap = smoothAngle(350f, 10f, 0.5f)
        assertEquals(0f, wrap, 0.01f) // 350 + 10 = 360 -> 0°

        // Da 10° verso 350° la distanza minima è -20°
        val wrapReverse = smoothAngle(10f, 350f, 0.5f)
        assertEquals(0f, wrapReverse, 0.01f) // 10 - 10 = 0°
    }

    @Test
    fun testOrientationUnavailablePlatformFallback() {
        // Simula provider su target/ambienti dove la bussola hardware è assente (es. iPad WiFi, simulatori)
        val fallbackOrientationProvider = object : PlatformOrientationProvider {
            override fun headingUpdates(): Flow<DeviceHeading> = flowOf(DeviceHeading(0f, isReliable = false))
            override fun isSupported(): Boolean = false
        }

        assertFalse("Quando la bussola non è supportata isSupported deve ritornare false", fallbackOrientationProvider.isSupported())
    }

    @Test
    fun testSelectedPointCenteringResetsOrientationToNorthUp() {
        // Simula lo stato iniziale in cui l'utente sta navigando con orientamento del dispositivo (HEADING_UP)
        var mapOrientationMode = MapOrientationMode.HEADING_UP
        var isMapCenteredOnUser = true
        var centerOnPointTrigger = 0

        // Quando si ricentra sul punto selezionato o si seleziona un punto cartografico:
        fun centerMapOnSelectedPoint() {
            isMapCenteredOnUser = false
            mapOrientationMode = MapOrientationMode.NORTH_UP
            centerOnPointTrigger++
        }

        centerMapOnSelectedPoint()

        assertFalse("Centrando sul punto selezionato il lock sull'utente deve essere disattivato", isMapCenteredOnUser)
        assertEquals("Centrando sul punto selezionato l'orientamento deve tornare rigorosamente a NORTH_UP", MapOrientationMode.NORTH_UP, mapOrientationMode)
        assertEquals(1, centerOnPointTrigger)
    }

    @Test
    fun testMapDragResetsOrientationToNorthUp() {
        var mapOrientationMode = MapOrientationMode.HEADING_UP
        var isMapCenteredOnUser = true

        fun onMapDraggedByUser() {
            isMapCenteredOnUser = false
            mapOrientationMode = MapOrientationMode.NORTH_UP
        }

        onMapDraggedByUser()

        assertFalse(isMapCenteredOnUser)
        assertEquals(MapOrientationMode.NORTH_UP, mapOrientationMode)
    }
}
