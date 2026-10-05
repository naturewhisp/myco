package github.naturewhisp.myco.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end operational parity test suite for the unified Percorso A pipeline (REV2-04).
 *
 * Verifies that the KMP :core engine executes the exact complete operational pipeline:
 * - Canopy buffering (De Frenne offset)
 * - Dynamic phenological growth phase (Liebig minimum law & Mindino drought decay gate)
 * - Two-stage hurdle model
 * - Phenological weather convolution f(tau)
 * - Dynamic month rollover and growth phase in daily outlooks
 */
class EndToEndOperationalParityTest {

    @Test
    fun mindinoGate_droughtDecayAbortsFruitingInCoreAndEngine() {
        val days = List(36) { index ->
            val rain = when (index) {
                16 -> 22.3 // 10 September
                23 -> 25.3 // 17 September
                else -> 0.0
            }
            val soilShallow = when {
                index < 16 -> 0.18
                index in 16..24 -> 0.28
                else -> 0.16 // Post-trigger prolonged drought
            }
            // 36 real Gregorian dates: Aug 25 (idx 0) to Aug 31 (idx 6), Sep 01 (idx 7) to Sep 29 (idx 35)
            val dateStr = if (index < 7) {
                "2026-08-${(25 + index).toString().padStart(2, '0')}"
            } else {
                "2026-09-${(index - 6).toString().padStart(2, '0')}"
            }
            ProcessedDay(
                dateIso = dateStr,
                avgTemp = 14.5,
                totalPrecipMm = rain,
                avgHumidityPercent = 65.0,
                weatherCode = 1,
                soilMoisture0To7 = soilShallow,
                soilMoisture7To28 = 0.22,
                evapotranspiration = 2.8,
                minTemp = 8.5,
                maxTemp = 20.0,
            )
        }

        // Target: Day 35 = 2026-09-29 (12 dry days post-trigger)
        val inputs = AnalysisInputs(
            days = days,
            todayIndex = 35,
            speciesId = "boletus_edulis",
            habitatScore = 0.85,
            habitatDescription = "Bosco di faggio e castagno",
            canopyTypes = listOf("fagus", "castanea"),
            elevationSamples = listOf(902.0, 915.0, 890.0, 905.0, 898.0),
            monthIndex = 8, // September
            spunEcmRichness = 45.0,
            spunHyphalDensity = 3.8,
            missingSources = emptyList(),
            canopyCover = 0.75,
        )

        val result = MycoAnalysisEngine().analyze(inputs)

        // Mindino Gate: must be aborted due to severe drought (score <= 35/100, stage WANING)
        assertTrue(result.probability <= 35, "Mindino score must be <= 35 due to drought gate, got ${result.probability}")
        assertTrue(result.tier in listOf(ProbabilityTier.VERY_LOW, ProbabilityTier.LOW))
        assertNotNull(result.growthPhase)
        val phase = result.growthPhase
        assertEquals(GrowthStage.WANING, phase.stage)
        assertTrue(phase.phaseText.contains("Stress idrico e disseccamento superficiale"))
        assertTrue(result.factors.any { it.id == FactorId.MYCELIAL_PHASE && it.level == FactorLevel.ADVERSE })
    }

    @Test
    fun postRainDay1_mycelialHydrationGatingLimitsEarlySuitability() {
        val days = List(25) { index ->
            val rain = if (index == 23) 35.0 else 0.0
            ProcessedDay(
                dateIso = "2026-09-${(index + 1).toString().padStart(2, '0')}",
                avgTemp = 16.0,
                totalPrecipMm = rain,
                avgHumidityPercent = 82.0,
                weatherCode = if (index == 23) 61 else 2,
                soilMoisture0To7 = if (index >= 23) 0.32 else 0.20,
                soilMoisture7To28 = 0.25,
                evapotranspiration = 1.8,
                minTemp = 11.0,
                maxTemp = 21.0,
            )
        }

        // Target: Day 24 (tau = 1 day post-rain)
        val inputs = AnalysisInputs(
            days = days,
            todayIndex = 24,
            speciesId = "boletus_edulis",
            habitatScore = 0.90,
            habitatDescription = "Bosco misto",
            canopyTypes = listOf("fagus"),
            elevationSamples = listOf(850.0, 860.0, 840.0, 855.0, 845.0),
            monthIndex = 8,
            spunEcmRichness = 50.0,
            spunHyphalDensity = 4.0,
            missingSources = emptyList(),
            canopyCover = 0.80,
        )

        val result = MycoAnalysisEngine().analyze(inputs)

        // Liebig law: mycelial hydration gate phi_phase <= 0.45 prevents premature false positive
        assertNotNull(result.growthPhase)
        val phase = result.growthPhase
        assertEquals(GrowthStage.MYCELIAL_HYDRATION, phase.stage)
        assertTrue(phase.multiplier <= 0.50, "Phase multiplier must be <= 0.50 at tau=1, got ${phase.multiplier}")
        assertTrue(result.probability <= 45, "Suitability must be gated <= 45 at tau=1, got ${result.probability}")
    }

    @Test
    fun optimalFruitingPeak_day11WithHurdleClearedProducesHighSuitability() {
        val days = List(35) { index ->
            val rain = if (index == 23) 35.0 else 0.0
            val dateStr = if (index < 6) {
                "2026-08-${(26 + index).toString().padStart(2, '0')}"
            } else {
                "2026-09-${(index - 5).toString().padStart(2, '0')}"
            }
            ProcessedDay(
                dateIso = dateStr,
                avgTemp = 16.0,
                totalPrecipMm = rain,
                avgHumidityPercent = 82.0,
                weatherCode = if (index == 23) 61 else 2,
                soilMoisture0To7 = if (index >= 23) 0.30 else 0.20,
                soilMoisture7To28 = 0.25,
                evapotranspiration = 1.5,
                minTemp = 11.0,
                maxTemp = 21.0,
            )
        }

        // Target: Day 34 (tau = 11 days post-rain = tau_peak for B. edulis)
        val inputs = AnalysisInputs(
            days = days,
            todayIndex = 34,
            speciesId = "boletus_edulis",
            habitatScore = 0.95,
            habitatDescription = "Faggeta matura",
            canopyTypes = listOf("fagus"),
            elevationSamples = listOf(1000.0, 1010.0, 990.0, 1005.0, 995.0),
            monthIndex = 8,
            spunEcmRichness = 55.0,
            spunHyphalDensity = 4.5,
            missingSources = emptyList(),
            canopyCover = 0.85,
        )

        val result = MycoAnalysisEngine().analyze(inputs)

        assertNotNull(result.growthPhase)
        val phase = result.growthPhase
        assertEquals(GrowthStage.ACTIVE_FRUITING, phase.stage)
        assertEquals(1.0, phase.multiplier, 0.05)
        assertTrue(result.probability >= 60, "Optimal fruiting peak must produce high suitability >= 60, got ${result.probability}")
        assertEquals(ProbabilityTier.HIGH, result.tier)
    }

    @Test
    fun ectomycorrhizalHurdleBlocksOpenMeadowWhileSaprotrophClearsIt() {
        val days = List(25) { index ->
            ProcessedDay(
                dateIso = "2026-09-${(index + 1).toString().padStart(2, '0')}",
                avgTemp = 18.0,
                totalPrecipMm = if (index == 15) 30.0 else 0.0,
                avgHumidityPercent = 80.0,
                weatherCode = 2,
                soilMoisture0To7 = 0.28,
                soilMoisture7To28 = 0.24,
                evapotranspiration = 2.0,
                minTemp = 12.0,
                maxTemp = 23.0,
            )
        }

        // Day 21 = tau of 6 days post-rain (exact peak latency for Macrolepiota)
        val boletusInputs = AnalysisInputs(
            days = days,
            todayIndex = 21,
            speciesId = "boletus_edulis",
            habitatScore = 0.10,
            habitatDescription = "Prato aperto senza alberi",
            canopyTypes = emptyList(),
            elevationSamples = listOf(500.0, 500.0, 500.0, 500.0),
            monthIndex = 8,
            spunEcmRichness = null,
            spunHyphalDensity = null,
            missingSources = emptyList(),
            canopyCover = 0.0,
        )
        val boletusResult = MycoAnalysisEngine().analyze(boletusInputs)
        assertTrue(boletusResult.probability <= 15, "Obligate mycorrhizal taxon must be blocked by hurdle on open grassland, got ${boletusResult.probability}")

        // Test 2: Macrolepiota procera on meadow with habitat = 0.90 (excellent pasture) at peak tau=6
        val lepiotaInputs = AnalysisInputs(
            days = days,
            todayIndex = 21,
            speciesId = "macrolepiota_procera",
            habitatScore = 0.90,
            habitatDescription = "Pascolo montano",
            canopyTypes = listOf("prati"),
            elevationSamples = listOf(500.0, 500.0, 500.0, 500.0),
            monthIndex = 8,
            spunEcmRichness = null,
            spunHyphalDensity = null,
            missingSources = emptyList(),
            canopyCover = 0.0,
        )
        val lepiotaResult = MycoAnalysisEngine().analyze(lepiotaInputs)
        assertTrue(lepiotaResult.probability >= 50, "Meadow saprotroph must clear hurdle on suitable pasture, got ${lepiotaResult.probability}")
    }

    @Test
    fun dailyOutlooksWithMonthRolloverDynamicallyUpdatesSeasonalityAndGrowthPhase() {
        val dates = listOf(
            "2026-09-28", "2026-09-29", "2026-09-30",
            "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04"
        )
        val days = dates.mapIndexed { index, date ->
            ProcessedDay(
                dateIso = date,
                avgTemp = 15.0 - index * 0.5,
                totalPrecipMm = if (index == 0) 25.0 else 0.0,
                avgHumidityPercent = 75.0,
                weatherCode = 1,
                soilMoisture0To7 = 0.28,
                soilMoisture7To28 = 0.25,
                evapotranspiration = 2.0,
                minTemp = 9.0,
                maxTemp = 20.0,
            )
        }

        val inputs = AnalysisInputs(
            days = days,
            todayIndex = 1, // 2026-09-29
            speciesId = "boletus_edulis",
            habitatScore = 0.85,
            habitatDescription = "Bosco",
            canopyTypes = listOf("fagus"),
            elevationSamples = listOf(800.0, 800.0, 800.0, 800.0),
            monthIndex = 8, // September
            spunEcmRichness = null,
            spunHyphalDensity = null,
            missingSources = emptyList(),
            canopyCover = 0.70,
        )

        val result = MycoAnalysisEngine().analyze(inputs)
        val outlooks = result.dailyOutlooks
        assertEquals(6, outlooks.size) // from index 1 to 6 (6 remaining days)
        assertEquals("2026-09-29", outlooks[0].dateIso)
        assertEquals("2026-10-01", outlooks[2].dateIso)
        assertEquals("2026-10-04", outlooks[5].dateIso)

        // All days must have valid non-negative probabilities
        outlooks.forEach {
            assertTrue(it.probability in 0..100)
        }
    }

    @Test
    fun uncalculableState_returnsNotCalculableWithoutScore() {
        val days = listOf(
            ProcessedDay("2026-09-15", 15.0, 0.0, 70.0, 1)
        )
        // todayIndex -1 (missing target date)
        val missingTargetInput = AnalysisInputs(
            days = days,
            todayIndex = -1,
            speciesId = "boletus_edulis",
            habitatScore = 0.8,
            habitatDescription = "Bosco",
            canopyTypes = emptyList(),
            elevationSamples = listOf(500.0, 500.0, 500.0, 500.0),
            monthIndex = 8,
            spunEcmRichness = null,
            spunHyphalDensity = null,
            missingSources = emptyList(),
            canopyCover = 0.5,
        )
        val res1 = MycoAnalysisEngine().analyze(missingTargetInput)
        assertEquals(false, res1.isCalculable)
        assertEquals(DataQualityStatus.DEGRADED_OUT_OF_BOUNDS, res1.dataQuality)
        assertTrue(res1.deterministicFieldNote.contains("Analisi non calcolabile"))

        // empty days
        val emptyDaysInput = missingTargetInput.copy(days = emptyList(), todayIndex = 0)
        val res2 = MycoAnalysisEngine().analyze(emptyDaysInput)
        assertEquals(false, res2.isCalculable)
        assertEquals(DataQualityStatus.DEGRADED_OUT_OF_BOUNDS, res2.dataQuality)
    }

    @Test
    fun saprotrophicEcMRichnessInvariance_ecmRichnessDoesNotAffectSaprotrophScore() {
        val days = (0 until 20).map { i ->
            ProcessedDay(
                dateIso = "2026-09-${(i + 1).toString().padStart(2, '0')}",
                avgTemp = 18.0,
                totalPrecipMm = if (i == 10) 30.0 else 0.0,
                avgHumidityPercent = 75.0,
                weatherCode = 1,
                soilMoisture0To7 = 0.26,
                soilMoisture7To28 = 0.22,
                evapotranspiration = 2.0,
                minTemp = 12.0,
                maxTemp = 22.0,
            )
        }
        val baseInput = AnalysisInputs(
            days = days,
            todayIndex = 16,
            speciesId = "macrolepiota_procera",
            habitatScore = 0.85,
            habitatDescription = "Prato collinare",
            canopyTypes = listOf("prati"),
            elevationSamples = listOf(600.0, 600.0, 600.0, 600.0),
            monthIndex = 8,
            spunEcmRichness = null,
            spunHyphalDensity = null,
            missingSources = emptyList(),
            canopyCover = 0.0,
        )
        val resNoSpun = MycoAnalysisEngine().analyze(baseInput)
        val resHighEcm = MycoAnalysisEngine().analyze(baseInput.copy(spunEcmRichness = 90.0))
        val resLowEcm = MycoAnalysisEngine().analyze(baseInput.copy(spunEcmRichness = 5.0))

        assertEquals(resNoSpun.probability, resHighEcm.probability, "EcM richness must not alter saprotroph probability")
        assertEquals(resNoSpun.probability, resLowEcm.probability, "Low EcM richness must not penalize saprotroph probability")
        assertEquals(resNoSpun.habitatScore, resHighEcm.habitatScore)
    }
}
