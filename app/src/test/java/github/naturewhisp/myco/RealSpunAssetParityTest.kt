package github.naturewhisp.myco

import github.naturewhisp.myco.core.SpunParser
import github.naturewhisp.myco.platform.AssetProvider
import github.naturewhisp.myco.repository.SpunDataManager
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.InflaterInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealSpunAssetParityTest {
    private val asset = File("src/main/assets/spun/spun_italy.bin")

    @Test
    fun bundledAtlasProducesEquivalentAndroidAndSharedSamples() = runBlocking {
        assertTrue("Bundled SPUN atlas is missing", asset.isFile)
        val sharedGrid = parseSharedGrid(asset.readBytes())
        val android = SpunDataManager(AssetProvider { asset.inputStream() })
        android.ensureRegionLoadedFor(44.39, 7.55)
        val legacyGrid = checkNotNull(android.getCurrentRegionData(44.39, 7.55))
        assertEquals(legacyGrid.header.width, sharedGrid.header.width)
        assertEquals(legacyGrid.header.height, sharedGrid.header.height)
        assertArrayEquals(legacyGrid.ecmData, sharedGrid.ecmData)
        assertArrayEquals(legacyGrid.hyphalData, sharedGrid.hyphalData)
        val scenarios = listOf(
            Triple(44.39, 7.55, 1_500), // Piemonte
            Triple(41.90, 12.50, 1_500), // Lazio
            Triple(37.88, 14.03, 1_500), // Sicilia
            Triple(44.39, 7.55, 10_000), // radius semantics
        )

        scenarios.forEach { (latitude, longitude, radius) ->
            val legacy = android.getSpunData(latitude, longitude, radius)
            val shared = SpunParser.sample(sharedGrid, latitude, longitude, radius)

            assertEquals("Coverage mismatch at $latitude,$longitude r=$radius", legacy == null, shared == null)
            if (legacy != null && shared != null) {
                val point = "$latitude,$longitude r=$radius"
                assertEquals("EcM mismatch at $point", legacy.ecmRichness.toDouble(), shared.ecmRichness, 0.0001)
                assertEquals("Hyphal mismatch at $point", legacy.hyphalDensity.toDouble(), shared.hyphalDensity, 0.0001)
                assertEquals(legacy.ecmScore, shared.ecmScore, 0.0001)
                assertEquals(legacy.hyphalScore, shared.hyphalScore, 0.0001)
                assertEquals(legacy.regionCode, shared.regionCode)
            }
        }
    }

    @Test
    fun outsideCoverageIsAbsentInsteadOfBeingReportedAsZero() = runBlocking {
        val sharedGrid = parseSharedGrid(asset.readBytes())
        val android = SpunDataManager(AssetProvider { asset.inputStream() })

        assertNull(android.getSpunData(50.0, 2.0, 1_500))
        assertNull(SpunParser.sample(sharedGrid, 50.0, 2.0, 1_500))
    }

    @Test
    fun corruptOrMissingAssetFailsSafely() = runBlocking {
        val corrupt = ByteArray(40)
        assertNull(SpunParser.parseInflated(corrupt.copyOfRange(0, 32), corrupt.copyOfRange(32, 40)))

        val android = SpunDataManager(AssetProvider { ByteArrayInputStream(corrupt) })
        assertNull(android.getSpunData(44.39, 7.55, 1_500))
    }

    private fun parseSharedGrid(bytes: ByteArray) = SpunParser.parseInflated(
        headerBytes = bytes.copyOfRange(0, SpunParser.headerSize),
        payloadBytes = InflaterInputStream(ByteArrayInputStream(bytes, SpunParser.headerSize, bytes.size - SpunParser.headerSize)).readBytes(),
    ) ?: error("Bundled SPUN atlas could not be parsed")
}
