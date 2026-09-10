package github.naturewhisp.myco

import github.naturewhisp.myco.model.HeatmapRenderConfig
import github.naturewhisp.myco.model.ProbabilityTier
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.model.TerrainAspectConfig
import github.naturewhisp.myco.model.TerrainAspectData
import github.naturewhisp.myco.utils.HeatmapGenerator
import github.naturewhisp.myco.utils.MushroomAlgorithms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

/**
 * Suite di test avversariali Gate 2 per la validazione empirica di robustezza,
 * condizioni al contorno e invarianti architetturali (AGENTS.md).
 */
class AdversarialGate2Test {

    // =========================================================================
    // 1. PROBABILITY TIER BOUNDARY ROBUSTNESS (-5, 0, 19, 20, 39, 40, 59, 60, 74, 75, 100, 105)
    // =========================================================================

    @Test
    fun testProbabilityTierSpecifiedBoundaries() {
        // -5 -> VERY_LOW
        val tMinus5 = ProbabilityTier.fromProbability(-5)
        assertEquals(ProbabilityTier.VERY_LOW, tMinus5)
        assertEquals("Inattivo", tMinus5.shortLabel)

        // 0 -> VERY_LOW
        val t0 = ProbabilityTier.fromProbability(0)
        assertEquals(ProbabilityTier.VERY_LOW, t0)
        assertEquals(0, t0.minProbability)
        assertEquals(19, t0.maxProbability)

        // 19 -> VERY_LOW
        val t19 = ProbabilityTier.fromProbability(19)
        assertEquals(ProbabilityTier.VERY_LOW, t19)

        // 20 -> LOW
        val t20 = ProbabilityTier.fromProbability(20)
        assertEquals(ProbabilityTier.LOW, t20)
        assertEquals(20, t20.minProbability)
        assertEquals(39, t20.maxProbability)
        assertEquals("Innesco", t20.shortLabel)

        // 39 -> LOW
        val t39 = ProbabilityTier.fromProbability(39)
        assertEquals(ProbabilityTier.LOW, t39)

        // 40 -> MODERATE
        val t40 = ProbabilityTier.fromProbability(40)
        assertEquals(ProbabilityTier.MODERATE, t40)
        assertEquals(40, t40.minProbability)
        assertEquals(59, t40.maxProbability)
        assertEquals("Discreto", t40.shortLabel)

        // 59 -> MODERATE
        val t59 = ProbabilityTier.fromProbability(59)
        assertEquals(ProbabilityTier.MODERATE, t59)

        // 60 -> HIGH
        val t60 = ProbabilityTier.fromProbability(60)
        assertEquals(ProbabilityTier.HIGH, t60)
        assertEquals(60, t60.minProbability)
        assertEquals(74, t60.maxProbability)
        assertEquals("Propizio", t60.shortLabel)

        // 74 -> HIGH
        val t74 = ProbabilityTier.fromProbability(74)
        assertEquals(ProbabilityTier.HIGH, t74)

        // 75 -> VERY_HIGH
        val t75 = ProbabilityTier.fromProbability(75)
        assertEquals(ProbabilityTier.VERY_HIGH, t75)
        assertEquals(75, t75.minProbability)
        assertEquals(100, t75.maxProbability)
        assertEquals("Culmine", t75.shortLabel)

        // 100 -> VERY_HIGH
        val t100 = ProbabilityTier.fromProbability(100)
        assertEquals(ProbabilityTier.VERY_HIGH, t100)

        // 105 -> VERY_HIGH (saturation fallback)
        val t105 = ProbabilityTier.fromProbability(105)
        assertEquals(ProbabilityTier.VERY_HIGH, t105)
    }

    @Test
    fun testProbabilityTierExhaustiveAndExtremeValues() {
        // Test extreme integer values
        assertEquals(ProbabilityTier.VERY_LOW, ProbabilityTier.fromProbability(Int.MIN_VALUE))
        assertEquals(ProbabilityTier.VERY_LOW, ProbabilityTier.fromProbability(-1000000))
        assertEquals(ProbabilityTier.VERY_LOW, ProbabilityTier.fromProbability(-1))
        assertEquals(ProbabilityTier.VERY_HIGH, ProbabilityTier.fromProbability(101))
        assertEquals(ProbabilityTier.VERY_HIGH, ProbabilityTier.fromProbability(1000000))
        assertEquals(ProbabilityTier.VERY_HIGH, ProbabilityTier.fromProbability(Int.MAX_VALUE))

        // Test monotonicity across full 0..100 domain
        var prevTierIndex = -1
        for (p in 0..100) {
            val tier = ProbabilityTier.fromProbability(p)
            assertTrue("Tier index must be non-decreasing for $p", tier.tierIndex >= prevTierIndex)
            assertTrue("Probability $p must be within declared bounds [${tier.minProbability}, ${tier.maxProbability}]",
                p >= tier.minProbability && p <= tier.maxProbability)
            prevTierIndex = tier.tierIndex
        }

        // Test fromTierIndex bounds & fallback
        assertEquals(ProbabilityTier.VERY_LOW, ProbabilityTier.fromTierIndex(0))
        assertEquals(ProbabilityTier.LOW, ProbabilityTier.fromTierIndex(1))
        assertEquals(ProbabilityTier.MODERATE, ProbabilityTier.fromTierIndex(2))
        assertEquals(ProbabilityTier.HIGH, ProbabilityTier.fromTierIndex(3))
        assertEquals(ProbabilityTier.VERY_HIGH, ProbabilityTier.fromTierIndex(4))
        assertEquals(ProbabilityTier.VERY_HIGH, ProbabilityTier.fromTierIndex(5))
        assertEquals(ProbabilityTier.VERY_HIGH, ProbabilityTier.fromTierIndex(100))
        assertEquals(ProbabilityTier.VERY_HIGH, ProbabilityTier.fromTierIndex(-1))

        // Contiguity check: each tier's min must equal previous tier's max + 1
        val tiers = ProbabilityTier.entries
        for (i in 1 until tiers.size) {
            assertEquals(
                "Tier $i min must follow tier ${i - 1} max",
                tiers[i - 1].maxProbability + 1,
                tiers[i].minProbability
            )
        }
    }

    // =========================================================================
    // 2. HEATMAP RENDER CONFIG AND BOUNDS ROBUSTNESS
    // =========================================================================

    @Test
    fun testHeatmapRenderConfigInvariants() {
        val config = HeatmapRenderConfig.DEFAULT

        // Invariants from AGENTS.md section 4.6
        assertEquals("Grid size must be 96", 96, config.gridSize)
        assertEquals("Radius km must be 35.0", 35.0, config.radiusKm, 0.001)
        assertEquals("Cutoff threshold must be 16", 16, config.cutoffThreshold)
        assertEquals("Feather threshold must be 20", 20, config.featherThreshold)
        assertTrue("Feather threshold must be >= cutoff threshold", config.featherThreshold >= config.cutoffThreshold)

        // Alpha bounds window: 115..180 (light) and 125..195 (dark)
        assertEquals("alphaMinLight must be 115", 115, config.alphaMinLight)
        assertEquals("alphaMaxLight must be 180", 180, config.alphaMaxLight)
        assertEquals("alphaMinDark must be 125", 125, config.alphaMinDark)
        assertEquals("alphaMaxDark must be 195", 195, config.alphaMaxDark)

        // Plateaus must be strictly ordered
        assertTrue("plateauTier1 < plateauTier2", config.plateauTier1 < config.plateauTier2)
        assertTrue("plateauTier2 < plateauTier3", config.plateauTier2 < config.plateauTier3)
        assertTrue("plateauTier3 < plateauTier4", config.plateauTier3 < config.plateauTier4)

        // Color palettes must contain exactly 4 anchor pigments
        assertEquals(4, config.lightColors.size)
        assertEquals(4, config.darkColors.size)
    }

    @Test
    fun testHeatmapGeneratorColorAdversarialBounds() {
        val config = HeatmapRenderConfig.DEFAULT

        // Values below cutoff (16) must be transparent (0)
        for (p in listOf(-50, -5, 0, 1, 10, 15)) {
            assertEquals("Probability $p must be transparent for light", 0, HeatmapGenerator.getHeatmapColor(p, isDark = false, config = config))
            assertEquals("Probability $p must be transparent for dark", 0, HeatmapGenerator.getHeatmapColor(p, isDark = true, config = config))
        }

        // Values in feather zone [16..19] must have non-zero alpha scaling up
        var prevAlphaLight = -1
        var prevAlphaDark = -1
        for (p in 16..19) {
            val colorLight = HeatmapGenerator.getHeatmapColor(p, isDark = false, config = config)
            val colorDark = HeatmapGenerator.getHeatmapColor(p, isDark = true, config = config)
            val alphaLight = (colorLight ushr 24) and 0xFF
            val alphaDark = (colorDark ushr 24) and 0xFF

            assertTrue("Alpha for $p light must be monotonically non-decreasing", alphaLight >= prevAlphaLight)
            assertTrue("Alpha for $p dark must be monotonically non-decreasing", alphaDark >= prevAlphaDark)
            assertTrue("Alpha for $p light must be <= alphaMinLight", alphaLight <= config.alphaMinLight)
            assertTrue("Alpha for $p dark must be <= alphaMinDark", alphaDark <= config.alphaMinDark)

            prevAlphaLight = alphaLight
            prevAlphaDark = alphaDark
        }

        // At feather threshold (20), alpha must equal alphaMin
        val color20Light = HeatmapGenerator.getHeatmapColor(20, isDark = false, config = config)
        val color20Dark = HeatmapGenerator.getHeatmapColor(20, isDark = true, config = config)
        assertEquals(config.alphaMinLight, (color20Light ushr 24) and 0xFF)
        assertEquals(config.alphaMinDark, (color20Dark ushr 24) and 0xFF)

        // At 100%, alpha must equal alphaMax
        val color100Light = HeatmapGenerator.getHeatmapColor(100, isDark = false, config = config)
        val color100Dark = HeatmapGenerator.getHeatmapColor(100, isDark = true, config = config)
        assertEquals(config.alphaMaxLight, (color100Light ushr 24) and 0xFF)
        assertEquals(config.alphaMaxDark, (color100Dark ushr 24) and 0xFF)

        // Saturated values > 100 must remain stable at alphaMax and top color without exception
        val color150Light = HeatmapGenerator.getHeatmapColor(150, isDark = false, config = config)
        assertEquals(color100Light, color150Light)
        val colorMaxLight = HeatmapGenerator.getHeatmapColor(Int.MAX_VALUE, isDark = false, config = config)
        assertEquals(color100Light, colorMaxLight)

        // Custom config: degenerate / edge configs should not crash
        val degenerateConfig = HeatmapRenderConfig(
            cutoffThreshold = 20,
            featherThreshold = 20, // zero feather range
            alphaMinLight = 100,
            alphaMaxLight = 100 // flat alpha
        )
        val degenColor19 = HeatmapGenerator.getHeatmapColor(19, config = degenerateConfig)
        assertEquals(0, degenColor19)
        val degenColor20 = HeatmapGenerator.getHeatmapColor(20, config = degenerateConfig)
        assertEquals(100, (degenColor20 ushr 24) and 0xFF)
    }

    // =========================================================================
    // 3. TERRAIN ASPECT CONFIG AND BOUNDS ROBUSTNESS
    // =========================================================================

    @Test
    fun testTerrainAspectConfigInvariants() {
        val config = TerrainAspectConfig.DEFAULT

        assertEquals(75.0, config.deltaMeters, 0.001)
        assertEquals(3.0f, config.flatSlopeThresholdDegrees, 0.001f)
        assertEquals(38.0f, config.steepSlopeThresholdDegrees, 0.001f)
        assertEquals(0.92, config.steepSlopePenaltyMax, 0.001)
        assertEquals(1.05, config.favorableMultiplier, 0.001)
        assertEquals(0.90, config.adverseMultiplier, 0.001)
        assertEquals(1.03, config.morningSunMultiplier, 0.001)
        assertEquals(1.02, config.moderateSunMultiplier, 0.001)

        assertTrue(config.flatSlopeThresholdDegrees > 0.0f)
        assertTrue(config.steepSlopeThresholdDegrees > config.flatSlopeThresholdDegrees)
        assertTrue(config.favorableMultiplier > 1.0)
        assertTrue(config.adverseMultiplier < 1.0)
        assertTrue(config.steepSlopePenaltyMax < 1.0)
    }

    @Test
    fun testTerrainAspectBoundaryAndStressCalculations() {
        val config = TerrainAspectConfig.DEFAULT

        // Incomplete elevation lists
        val emptyDem = MushroomAlgorithms.calculateTerrainAspect(emptyList(), config = config)
        assertTrue(emptyDem.isFlat)
        assertEquals("Pian", emptyDem.cardinalAbbreviation)
        assertEquals(0f, emptyDem.slopeDegrees, 0.001f)

        val fourPointDem = MushroomAlgorithms.calculateTerrainAspect(listOf(100f, 100f, 100f, 100f), config = config)
        assertTrue(fourPointDem.isFlat)

        // Sub-zero / negative elevations (e.g. Dead Sea -400m)
        val belowSeaLevelElevations = listOf(-400f, -400f, -400f, -400f, -400f)
        val belowSeaFlat = MushroomAlgorithms.calculateTerrainAspect(belowSeaLevelElevations, config = config)
        assertTrue(belowSeaFlat.isFlat)
        assertEquals(-400f, belowSeaFlat.centerElevation, 0.001f)

        // Vertical cliff / extreme steep slope (delta = 5000m)
        val extremeCliff = listOf(1000f, 6000f, 1000f, 1000f, 1000f)
        val cliffAspect = MushroomAlgorithms.calculateTerrainAspect(extremeCliff, config = config)
        assertFalse(cliffAspect.isFlat)
        assertTrue("Slope must be steep (> 38°)", cliffAspect.slopeDegrees > config.steepSlopeThresholdDegrees)
        assertFalse("Slope degrees must not be NaN", cliffAspect.slopeDegrees.isNaN())
        assertFalse("Slope degrees must not be Infinite", cliffAspect.slopeDegrees.isInfinite())

        // Steep slope penalty enforcement
        val evalSteep = MushroomAlgorithms.evaluateTerrainAspect(
            terrain = cliffAspect,
            month = 9,
            avgTemp = 18.0,
            seasonalityScore = 1.0,
            config = config
        )
        assertTrue("Steep slope modifier must be <= steepSlopePenaltyMax (0.92)", evalSteep.modifier <= config.steepSlopePenaltyMax)

        // Multiplier safety bounds check: across all species and seasonal conditions, modifier must stay in [0.50, 1.10]
        for (species in SPECIES_CATALOG) {
            for (month in 0..11) {
                for (temp in listOf(-5.0, 5.0, 15.0, 25.0, 38.0)) {
                    val eval = MushroomAlgorithms.evaluateTerrainAspect(
                        terrain = cliffAspect,
                        month = month,
                        avgTemp = temp,
                        seasonalityScore = 0.8,
                        species = species,
                        config = config
                    )
                    assertTrue("Modifier ${eval.modifier} must be >= 0.50", eval.modifier >= 0.50)
                    assertTrue("Modifier ${eval.modifier} must be <= 1.10", eval.modifier <= 1.10)
                }
            }
        }
    }
}
