package github.naturewhisp.myco

import github.naturewhisp.myco.model.EcologicalWeightsConfig
import github.naturewhisp.myco.model.HeatmapRenderConfig
import github.naturewhisp.myco.model.ProbabilityTier
import github.naturewhisp.myco.model.TerrainAspectConfig
import github.naturewhisp.myco.utils.HeatmapGenerator
import github.naturewhisp.myco.utils.MushroomAlgorithms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurationAndModelTest {

    @Test
    fun testEcologicalWeightsConfigDefaults() {
        val config = EcologicalWeightsConfig.DEFAULT

        assertEquals(40.0, config.rainWeight, 0.001)
        assertEquals(30.0, config.tempWeight, 0.001)
        assertEquals(15.0, config.humidityWeight, 0.001)
        assertEquals(15.0, config.thermalShockWeight, 0.001)
        assertEquals(1.2, config.weatherExponent, 0.001)

        val totalWeight = config.rainWeight + config.tempWeight + config.humidityWeight + config.thermalShockWeight
        assertEquals(100.0, totalWeight, 0.001)
        assertEquals(10, config.rainWindowDays)
        assertEquals(2, config.rainLagDays)
        assertEquals(5, config.tempWindowDays)
        assertEquals(3, config.humidityWindowDays)
    }

    @Test
    fun testCustomEcologicalWeightsInAlgorithms() {
        val weatherScore = 80
        val habitatScore = 0.9
        val altitudeScore = 0.9
        val seasonalityScore = 0.9

        // Default exponent 1.2
        val probDefault = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = weatherScore,
            habitatScore = habitatScore,
            altitudeScore = altitudeScore,
            seasonalityScore = seasonalityScore,
            config = EcologicalWeightsConfig.DEFAULT
        )

        // Linear exponent 1.0
        val linearConfig = EcologicalWeightsConfig(weatherExponent = 1.0)
        val probLinear = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = weatherScore,
            habitatScore = habitatScore,
            altitudeScore = altitudeScore,
            seasonalityScore = seasonalityScore,
            config = linearConfig
        )

        // Since weatherScore is 80 (0.8), 0.8^1.0 = 0.80 > 0.8^1.2 (~0.765), so probLinear should be >= probDefault
        assertTrue("Linear probability ($probLinear) should be >= non-linear probability ($probDefault)", probLinear >= probDefault)
    }

    @Test
    fun testProbabilityTierBoundaries() {
        assertEquals(ProbabilityTier.VERY_LOW, ProbabilityTier.fromProbability(0))
        assertEquals(ProbabilityTier.VERY_LOW, ProbabilityTier.fromProbability(19))

        assertEquals(ProbabilityTier.LOW, ProbabilityTier.fromProbability(20))
        assertEquals(ProbabilityTier.LOW, ProbabilityTier.fromProbability(39))

        assertEquals(ProbabilityTier.MODERATE, ProbabilityTier.fromProbability(40))
        assertEquals(ProbabilityTier.MODERATE, ProbabilityTier.fromProbability(59))

        assertEquals(ProbabilityTier.HIGH, ProbabilityTier.fromProbability(60))
        assertEquals(ProbabilityTier.HIGH, ProbabilityTier.fromProbability(74))

        assertEquals(ProbabilityTier.VERY_HIGH, ProbabilityTier.fromProbability(75))
        assertEquals(ProbabilityTier.VERY_HIGH, ProbabilityTier.fromProbability(100))

        // Out-of-bounds fallback
        assertEquals(ProbabilityTier.VERY_LOW, ProbabilityTier.fromProbability(-10))
        assertEquals(ProbabilityTier.VERY_HIGH, ProbabilityTier.fromProbability(150))
    }

    @Test
    fun testProbabilityTierProperties() {
        assertEquals("Inattivo", ProbabilityTier.VERY_LOW.shortLabel)
        assertEquals(0, ProbabilityTier.VERY_LOW.minProbability)
        assertEquals(19, ProbabilityTier.VERY_LOW.maxProbability)

        assertEquals("Innesco", ProbabilityTier.LOW.shortLabel)
        assertEquals(20, ProbabilityTier.LOW.minProbability)
        assertEquals(39, ProbabilityTier.LOW.maxProbability)

        assertEquals("Discreto", ProbabilityTier.MODERATE.shortLabel)
        assertEquals(40, ProbabilityTier.MODERATE.minProbability)
        assertEquals(59, ProbabilityTier.MODERATE.maxProbability)

        assertEquals("Propizio", ProbabilityTier.HIGH.shortLabel)
        assertEquals(60, ProbabilityTier.HIGH.minProbability)
        assertEquals(74, ProbabilityTier.HIGH.maxProbability)

        assertEquals("Culmine", ProbabilityTier.VERY_HIGH.shortLabel)
        assertEquals(75, ProbabilityTier.VERY_HIGH.minProbability)
        assertEquals(100, ProbabilityTier.VERY_HIGH.maxProbability)
    }

    @Test
    fun testHeatmapRenderConfigDefaults() {
        val config = HeatmapRenderConfig.DEFAULT

        assertEquals(96, config.gridSize)
        assertEquals(35.0, config.radiusKm, 0.001)
        assertEquals(16, config.cutoffThreshold)
        assertEquals(20, config.featherThreshold)
        assertEquals(115, config.alphaMinLight)
        assertEquals(180, config.alphaMaxLight)
        assertEquals(125, config.alphaMinDark)
        assertEquals(195, config.alphaMaxDark)
        assertEquals(4, config.lightColors.size)
        assertEquals(4, config.darkColors.size)
    }

    @Test
    fun testCustomHeatmapRenderConfigCutoff() {
        val standardCutoffColor = HeatmapGenerator.getHeatmapColor(18, isDark = false, config = HeatmapRenderConfig.DEFAULT)
        assertNotEquals("Probability 18 with default cutoff 16 should produce color", 0, standardCutoffColor)

        // Config with cutoff at 25
        val highCutoffConfig = HeatmapRenderConfig(cutoffThreshold = 25, featherThreshold = 30)
        val highCutoffColor = HeatmapGenerator.getHeatmapColor(18, isDark = false, config = highCutoffConfig)
        assertEquals("Probability 18 with cutoff 25 must be 0 (transparent)", 0, highCutoffColor)
    }

    @Test
    fun testTerrainAspectConfigDefaults() {
        val config = TerrainAspectConfig.DEFAULT

        assertEquals(75.0, config.deltaMeters, 0.001)
        assertEquals(3.0f, config.flatSlopeThresholdDegrees, 0.001f)
        assertEquals(38.0f, config.steepSlopeThresholdDegrees, 0.001f)
        assertEquals(0.92, config.steepSlopePenaltyMax, 0.001)
        assertEquals(1.05, config.favorableMultiplier, 0.001)
        assertEquals(0.90, config.adverseMultiplier, 0.001)
        assertEquals(1.03, config.morningSunMultiplier, 0.001)
        assertEquals(1.02, config.moderateSunMultiplier, 0.001)
    }

    @Test
    fun testCustomTerrainAspectConfigInCalculation() {
        // Flat terrain: elevations all 500m
        val flatElevations = listOf(500f, 500f, 500f, 500f, 500f)
        val flatAspect = MushroomAlgorithms.calculateTerrainAspect(flatElevations)
        assertTrue("All equal elevations must result in isFlat = true", flatAspect.isFlat)

        val flatEval = MushroomAlgorithms.evaluateTerrainAspect(
            terrain = flatAspect,
            month = 9,
            avgTemp = 18.0,
            seasonalityScore = 1.0
        )
        assertTrue("Flat evaluation must contain Pian", flatEval.formattedValue.contains("Pian"))
        assertEquals(1.0, flatEval.modifier, 0.001)

        // Custom config where flat slope threshold is 0.0 (anything > 0 is not flat)
        val zeroThresholdConfig = TerrainAspectConfig(flatSlopeThresholdDegrees = 0.0f)
        // With slightly inclined terrain (delta ~ 4m across 150m = ~1.5°)
        val slightSlope = listOf(500f, 502f, 498f, 500f, 500f)
        val defaultAspect = MushroomAlgorithms.calculateTerrainAspect(slightSlope, config = TerrainAspectConfig.DEFAULT)
        val strictAspect = MushroomAlgorithms.calculateTerrainAspect(slightSlope, config = zeroThresholdConfig)

        // Slight slope (~1.5°) is flat with default threshold (3.0°), but not flat with 0.0° threshold
        assertTrue("Default threshold should treat ~1.5° slope as flat", defaultAspect.isFlat)
        assertFalse("Zero threshold should treat ~1.5° slope as non-flat", strictAspect.isFlat)

        val defaultEval = MushroomAlgorithms.evaluateTerrainAspect(
            terrain = defaultAspect,
            month = 9,
            avgTemp = 18.0,
            seasonalityScore = 1.0,
            config = TerrainAspectConfig.DEFAULT
        )
        val strictEval = MushroomAlgorithms.evaluateTerrainAspect(
            terrain = strictAspect,
            month = 9,
            avgTemp = 18.0,
            seasonalityScore = 1.0,
            config = zeroThresholdConfig
        )

        assertTrue("Default evaluation should contain Pian", defaultEval.formattedValue.contains("Pian"))
        assertFalse("Strict evaluation should not contain Pian", strictEval.formattedValue.contains("Pian"))
    }
}
