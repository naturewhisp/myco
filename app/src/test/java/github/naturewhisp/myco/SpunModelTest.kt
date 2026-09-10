package github.naturewhisp.myco

import github.naturewhisp.myco.model.SpunRegionDescriptor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpunModelTest {

    @Test
    fun testRegionDescriptorContains() {
        val italy = SpunRegionDescriptor(
            regionCode = "ITA",
            displayName = "Italia",
            assetFileName = "spun/spun_italy.bin",
            minLat = 35.0f,
            maxLat = 47.5f,
            minLon = 6.0f,
            maxLon = 19.0f
        )

        // Cuneo (Alpi Marittime / Cozie)
        assertTrue(italy.contains(44.39, 7.55))
        // Val Tanaro (Ormea)
        assertTrue(italy.contains(44.15, 7.91))
        // Val Pesio (Certosa)
        assertTrue(italy.contains(44.24, 7.68))
        // Roma
        assertTrue(italy.contains(41.90, 12.50))
        // Sicilia (Madonie)
        assertTrue(italy.contains(37.88, 14.03))
        // Sardegna (Gennargentu)
        assertTrue(italy.contains(39.99, 9.32))

        // Paris, France (Out of Italy bounding box)
        assertFalse(italy.contains(48.85, 2.35))
        // Madrid, Spain (Out of Italy bounding box)
        assertFalse(italy.contains(40.41, -3.70))
        // London, UK (Out of Italy bounding box)
        assertFalse(italy.contains(51.50, -0.12))
    }

    @Test
    fun testFindClosestCoveragePoint_CalculatesAccurateNearestStationAndDistance() {
        val spunManager = github.naturewhisp.myco.repository.SpunDataManager {
            java.io.ByteArrayInputStream(ByteArray(0))
        }

        // Ginevra, Svizzera (~46.20, 6.14) -> Val Veny / Courmayeur (AO) o Gran San Bernardo (~70-90 km)
        val genevaClosest = spunManager.findClosestCoveragePoint(46.20, 6.14)
        org.junit.Assert.assertNotNull(genevaClosest)
        org.junit.Assert.assertTrue(
            genevaClosest.name.contains("Courmayeur") || genevaClosest.name.contains("San Bernardo")
        )
        org.junit.Assert.assertTrue("Distanza da Ginevra a Courmayeur ~80 km", genevaClosest.distanceKm in 50..120)

        // Monaco di Baviera, Germania (48.13, 11.58) -> Passo del Brennero (~120-150 km)
        val munichClosest = spunManager.findClosestCoveragePoint(48.13, 11.58)
        org.junit.Assert.assertNotNull(munichClosest)
        org.junit.Assert.assertTrue(munichClosest.name.contains("Brennero"))
        org.junit.Assert.assertTrue("Distanza da Monaco a Brennero ~125 km", munichClosest.distanceKm in 100..160)

        // New York, USA (40.71, -74.00) -> Distanza transatlantica > 6000 km
        val nyClosest = spunManager.findClosestCoveragePoint(40.71, -74.00)
        org.junit.Assert.assertTrue(nyClosest.distanceKm > 6000)
    }
}
