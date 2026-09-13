package github.naturewhisp.myco.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Regression coverage for the saprotrophic habitat/probability split (P2). */
class SaprotrophicProbabilityParityTest {
    @Test
    fun macrolepiotaLowHabitatUsesEnvironmentalScoreForProbability() {
        val result = analyze(habitatScore = 0.10)
        val expected = MycoAlgorithms.growthProbability(
            result.weatherScore,
            habitatScore = 0.10,
            altitudeScore = result.altitudeScore,
            seasonalityScore = result.seasonalityScore,
            terrainModifier = result.terrain.modifier,
        )

        assertEquals(expected, result.probability)
        assertTrue(result.probability < 20, "A 10% habitat must not be raised to the 85% display floor")
        assertEquals("85%", result.factors.first { it.id == FactorId.HABITAT }.formattedValue)
    }

    @Test
    fun macrolepiotaHighHabitatRetainsCanopyBonusWithoutArtificialFloor() {
        val result = analyze(habitatScore = 0.90, canopyTypes = listOf("prati"))
        val environmentalHabitat = (0.90 * 1.15).coerceAtMost(1.0)
        val expected = MycoAlgorithms.growthProbability(
            result.weatherScore,
            environmentalHabitat,
            result.altitudeScore,
            result.seasonalityScore,
            result.terrain.modifier,
        )

        assertEquals(expected, result.probability)
    }

    @Test
    fun macrolepiotaLowSpunRichnessAppliesPenaltyToRawGrasslandHabitat() {
        val result = analyze(habitatScore = 0.40, canopyTypes = listOf("prati"), spunEcmRichness = 5.0)
        val environmentalHabitat = 0.40 * 1.15 * 0.8
        val expected = MycoAlgorithms.growthProbability(
            result.weatherScore,
            environmentalHabitat,
            result.altitudeScore,
            result.seasonalityScore,
            result.terrain.modifier,
        )

        assertEquals(expected, result.probability)
        assertTrue(result.habitatScore < 0.85, "Low SPUN richness must not be masked by the display floor")
    }

    private fun analyze(
        habitatScore: Double,
        canopyTypes: List<String> = emptyList(),
        spunEcmRichness: Double? = null,
    ): AnalysisResult = MycoAnalysisEngine().analyze(
        AnalysisInputs(
            days = List(21) { index ->
                ProcessedDay(
                    dateIso = "2026-09-${(index + 1).toString().padStart(2, '0')}",
                    avgTemp = 19.0,
                    totalPrecipMm = if (index in 4..11) 5.0 else 0.0,
                    avgHumidityPercent = 86.0,
                    weatherCode = 2,
                    soilMoisture0To7 = 0.28,
                    soilMoisture7To28 = 0.26,
                    evapotranspiration = 2.0,
                )
            },
            todayIndex = 14,
            speciesId = "macrolepiota_procera",
            habitatScore = habitatScore,
            habitatDescription = "Prato e radura",
            canopyTypes = canopyTypes,
            elevationSamples = listOf(350.0, 345.0, 355.0, 348.0),
            monthIndex = 8,
            spunEcmRichness = spunEcmRichness,
            spunHyphalDensity = null,
            missingSources = emptyList(),
        ),
    )
}
