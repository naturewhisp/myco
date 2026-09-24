package github.naturewhisp.myco.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScientificParityTest {
    @Test
    fun speciesCatalogMatchesAndroidBaseline() {
        assertEquals(12, SpeciesCatalog.all.size)
        assertEquals(
            listOf("general", "boletus_edulis", "boletus_aereus", "boletus_pinophilus", "boletus_reticulatus", "cantharellus_cibarius", "amanita_caesarea", "hydnum_repandum", "macrolepiota_procera", "armillaria_mellea", "lactarius_deliciosus", "morchella_esculenta"),
            SpeciesCatalog.all.map { it.id },
        )
        assertTrue(SpeciesCatalog.byId("amanita_caesarea").toxicLookAlikes.any { "phalloides" in it })
    }

    @Test
    fun canonicalProbabilityFormulaMatchesGoldenMaster() {
        assertEquals(0, MycoAlgorithms.growthProbability(0, 1.0, 1.0, 1.0, 1.0))
        assertEquals(89, MycoAlgorithms.growthProbability(100, 1.0, 1.0, 1.0, 1.0))
        assertEquals(58, MycoAlgorithms.growthProbability(80, 0.9, 0.9, 0.9, 1.05))
        assertEquals(8, MycoAlgorithms.growthProbability(85, 0.1, 1.0, 1.0, 1.0))
    }

    @Test
    fun analysisIsDeterministicAndUsesAllScientificFactors() {
        val days = (0 until 21).map { index ->
            ProcessedDay("2026-09-${(index + 1).toString().padStart(2, '0')}", 17.0, if (index in 4..11) 5.0 else 0.0, 86.0, 2, 0.28, 0.26, 2.0)
        }
        val input = AnalysisInputs(days, 14, "boletus_edulis", 0.95, "Bosco", listOf("fagus"), listOf(1_000.0, 990.0, 1_010.0, 995.0, 1_005.0), 8, 55.0, 5.5, emptyList())
        val first = MycoAnalysisEngine().analyze(input)
        val second = MycoAnalysisEngine().analyze(input)
        assertEquals(first, second)
        assertTrue(first.probability in 1..100)
        assertEquals(7, first.dailyOutlooks.size)
        assertTrue(first.factors.any { it.id == FactorId.SPUN_ECM })
        assertTrue(first.factors.any { it.id == FactorId.SLOPE })
    }

    @Test
    fun spunParserAndSamplingMatchBinaryContract() {
        val header = header(width = 3, height = 3)
        val payload = byteArrayOf(10, 20, 30, 40, 50, 60, 20, 30, 40, 20, 40, 60, 80, 100, 120, 20, 40, 60)
        val grid = assertNotNull(SpunParser.parseInflated(header, payload))
        val sample = assertNotNull(SpunParser.sample(grid, 45.0, 10.0, 100_000))
        assertEquals("TST", sample.regionCode)
        assertTrue(sample.ecmRichness > 0)
        assertTrue(sample.hyphalDensity > 0)
    }

    @Test
    fun fullHeatmapPixelBufferHasStableHash() {
        val grid = assertNotNull(SpunParser.parseInflated(header(8, 8), ByteArray(128) { ((it % 64) + 1).toByte() }))
        val first = assertNotNull(HeatmapEngine().generate(45.0, 10.0, grid, 82.0, 1.0, 1.0, "boletus_edulis", false, 16, 25.0))
        val second = assertNotNull(HeatmapEngine().generate(45.0, 10.0, grid, 82.0, 1.0, 1.0, "boletus_edulis", false, 16, 25.0))
        assertContentEquals(first.argbPixels, second.argbPixels)
        assertEquals(-4_568_036_496_626_547_972L, fnv1a(first.argbPixels))
    }

    private fun fnv1a(values: IntArray): Long {
        var hash = -3_750_763_034_362_895_579L
        for (value in values) {
            hash = (hash xor value.toLong()) * 1_099_511_628_211L
        }
        return hash
    }

    private fun header(width: Int, height: Int): ByteArray = ByteArray(32).also { bytes ->
        "SPUN".encodeToByteArray().copyInto(bytes, 0)
        bytes.putShort(4, 1)
        "TST\u0000".encodeToByteArray().copyInto(bytes, 6)
        bytes.putInt(10, 44.0f.toBits())
        bytes.putInt(14, 46.0f.toBits())
        bytes.putInt(18, 9.0f.toBits())
        bytes.putInt(22, 11.0f.toBits())
        bytes.putShort(26, width)
        bytes.putShort(28, height)
        bytes.putShort(30, 30)
    }

    private fun ByteArray.putShort(offset: Int, value: Int) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }

    private fun ByteArray.putInt(offset: Int, value: Int) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
    }
}
