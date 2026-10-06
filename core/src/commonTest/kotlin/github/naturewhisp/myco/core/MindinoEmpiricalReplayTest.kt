package github.naturewhisp.myco.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Open reconstruction and sensitivity suite for the Mindino case study (D08). Raw payload and verified hash are archived in testFixtures/mindino; habitat, species and execution identity remain assumptions.
 *
 * Origin metadata:
 * - Coordinates: 44.2149°N, 7.9755°E (Garessio, Monte Mindino, 902 m a.s.l.)
 * - Target date: 2026-09-29
 * - Target species: Boletus edulis
 * - Provenance: Android SQLite cache record (key `weather_44.2149_7.9755`)
 * - Cached timestamp: 1791133963798
 * - SHA-256 hash: 6fee1178c9ce95e15e2b4828ff74ed72d5256dcb90e49f8bdd91fb9b6b0b5467
 *
 * Replay matrix per §D08:
 * 1. Dati originali (linea di base empirica: suolo 0.191–0.198 m³/m³, decelerazione idrologica)
 * 2. Stessi eventi con suolo secco (contro-fattuale: siccità severa <= 0.15 m³/m³, WANING, <= 35)
 * 3. Stessi eventi con suolo umido / ricarica persistente (contro-fattuale: suolo >= 0.28 m³/m³, >= 70)
 * 4. Pioggia debole senza ricarica (drizzle <= 2 mm, in attesa di precipitazioni)
 * 5. Suolo mancante (DEGRADED_MISSING_SOIL)
 * 6. Lacuna nel calendario (data target fuori serie -> non calcolabile)
 */
class MindinoEmpiricalReplayTest {

    private fun buildMindinoEmpiricalDays(): List<ProcessedDay> {
        return listOf(
            ProcessedDay("2026-09-06", 22.9, 0.0, 64.4, 1, 0.102, 0.145, 4.31, 20.3, 26.7),
            ProcessedDay("2026-09-07", 21.6, 8.8, 77.8, 61, 0.108, 0.145, 3.37, 18.2, 26.7),
            ProcessedDay("2026-09-08", 20.9, 2.8, 70.5, 61, 0.122, 0.145, 3.81, 16.9, 26.8),
            ProcessedDay("2026-09-09", 18.7, 0.8, 83.9, 3, 0.178, 0.145, 2.13, 14.5, 22.6),
            ProcessedDay("2026-09-10", 15.9, 22.3, 83.5, 61, 0.253, 0.149, 2.60, 13.1, 19.7), // 1st major rain
            ProcessedDay("2026-09-11", 15.1, 0.0, 81.1, 1, 0.277, 0.148, 2.49, 10.8, 19.8),
            ProcessedDay("2026-09-12", 17.1, 0.0, 68.4, 1, 0.258, 0.150, 3.47, 14.0, 21.1),
            ProcessedDay("2026-09-13", 17.1, 0.0, 77.5, 1, 0.245, 0.151, 2.63, 13.8, 21.9),
            ProcessedDay("2026-09-14", 19.2, 0.0, 69.8, 1, 0.232, 0.160, 3.74, 14.9, 24.9),
            ProcessedDay("2026-09-15", 19.0, 0.0, 69.9, 1, 0.215, 0.165, 2.90, 16.4, 22.5),
            ProcessedDay("2026-09-16", 18.3, 0.2, 82.5, 2, 0.219, 0.169, 2.15, 15.6, 21.8),
            ProcessedDay("2026-09-17", 17.0, 25.3, 86.7, 61, 0.236, 0.169, 1.50, 15.0, 19.6), // 2nd major rain (trigger)
            ProcessedDay("2026-09-18", 16.0, 0.0, 85.3, 1, 0.283, 0.171, 1.71, 13.5, 19.1), // tau = 1
            ProcessedDay("2026-09-19", 16.3, 0.0, 73.4, 1, 0.288, 0.184, 2.92, 12.2, 20.6), // tau = 2
            ProcessedDay("2026-09-20", 17.4, 0.0, 71.8, 1, 0.260, 0.189, 3.23, 13.9, 22.6), // tau = 3
            ProcessedDay("2026-09-21", 19.4, 0.0, 58.1, 1, 0.236, 0.189, 3.67, 16.6, 24.1), // tau = 4
            ProcessedDay("2026-09-22", 18.3, 0.0, 64.5, 1, 0.221, 0.192, 3.48, 14.9, 22.0), // tau = 5
            ProcessedDay("2026-09-23", 15.0, 0.0, 79.3, 2, 0.199, 0.179, 1.40, 13.3, 16.8), // tau = 6
            ProcessedDay("2026-09-24", 16.0, 0.0, 79.1, 1, 0.207, 0.194, 2.62, 12.9, 20.6), // tau = 7
            ProcessedDay("2026-09-25", 14.8, 0.0, 77.6, 1, 0.220, 0.206, 2.44, 11.6, 18.3), // tau = 8
            ProcessedDay("2026-09-26", 14.4, 0.0, 81.8, 1, 0.203, 0.199, 2.15, 10.9, 18.5), // tau = 9
            ProcessedDay("2026-09-27", 15.7, 0.0, 75.7, 1, 0.198, 0.201, 2.76, 12.0, 20.2), // tau = 10
            ProcessedDay("2026-09-28", 16.5, 0.0, 75.5, 1, 0.197, 0.206, 2.75, 12.9, 21.3), // tau = 11
            ProcessedDay("2026-09-29", 16.5, 0.0, 73.2, 1, 0.191, 0.206, 2.61, 14.0, 21.0), // tau = 12 (Target)
        )
    }

    private fun makeMindinoInput(
        days: List<ProcessedDay>,
        todayIndex: Int = 23,
    ): AnalysisInputs {
        return AnalysisInputs(
            days = days,
            todayIndex = todayIndex,
            speciesId = "boletus_edulis",
            habitatScore = 0.85,
            habitatDescription = "Faggeta e castagneto",
            canopyTypes = listOf("fagus", "castanea"),
            elevationSamples = listOf(902.0, 915.0, 890.0, 905.0, 898.0),
            monthIndex = 8, // September
            spunEcmRichness = 45.0,
            spunHyphalDensity = 3.8,
            missingSources = emptyList(),
            canopyCover = 0.75,
        )
    }

    @Test
    fun replayCondition1_empiricalBaselineShowsMildDeceleration() {
        val days = buildMindinoEmpiricalDays()
        val input = makeMindinoInput(days, todayIndex = 23) // 2026-09-29

        val result = MycoAnalysisEngine().analyze(input)

        assertTrue(result.isCalculable, "Empirical data must be fully calculable")
        assertEquals(DataQualityStatus.OPTIMAL, result.dataQuality)
        // Empirical soil moisture 0.191 m³/m³ (3-day avg ~0.195) produces mild hydrological deceleration: phiSoil ~ 0.82
        assertTrue(result.probability < MycoAnalysisEngine().analyze(input.copy(days = days.map { it.copy(soilMoisture0To7 = 0.29) })).probability, "Soil stress must lower suitability for the same reconstructed context")
        assertEquals(ProbabilityTier.fromProbability(result.probability), result.tier)

        val phase = result.growthPhase
        assertNotNull(phase)
        assertTrue(phase.phiSoil in 0.80..0.85, "Soil factor phiSoil must be in mild deceleration range, got ${phase.phiSoil}")
        assertTrue(phase.phaseText.contains("Rallentamento per deficit idrico superficiale"))
        assertFalse(result.deterministicFieldNote.contains("compromesso"), "Must not assert unobserved biological damage")
    }

    @Test
    fun replayCondition1b_unknownHabitatProducesNeutralScore() {
        val days = buildMindinoEmpiricalDays()
        val input = AnalysisInputs(
            days = days,
            todayIndex = 23,
            speciesId = "boletus_edulis",
            habitatScore = 0.50,
            habitatDescription = "Dati geografici non disponibili",
            canopyTypes = emptyList(),
            elevationSamples = listOf(902.0, 915.0, 890.0, 905.0, 898.0),
            monthIndex = 8,
            spunEcmRichness = null,
            spunHyphalDensity = null,
            missingSources = listOf("SPUN"),
            canopyCover = 0.0,
            forestProximityIndex = 0.0,
        )

        val result = MycoAnalysisEngine().analyze(input)
        assertTrue(result.isCalculable)
        assertTrue(result.probability in 20..45, "UNKNOWN habitat must produce neutral/moderate suitability, got ${result.probability}")
    }

    @Test
    fun replayCondition2_counterfactualSevereDroughtAbortsFruiting() {
        // Same rain events and temperatures, but hypothetical severe superficial soil drought (0.14 m³/m³)
        val dryDays = buildMindinoEmpiricalDays().map { day ->
            if (day.dateIso >= "2026-09-26") {
                day.copy(soilMoisture0To7 = 0.14)
            } else {
                day
            }
        }
        val input = makeMindinoInput(dryDays, todayIndex = 23) // 2026-09-29

        val result = MycoAnalysisEngine().analyze(input)

        assertTrue(result.isCalculable)
        val phase = result.growthPhase
        assertNotNull(phase)
        assertEquals(GrowthStage.WANING, phase.stage)
        assertTrue(phase.phiSoil <= 0.50, "Dry soil must yield phiSoil <= 0.50, got ${phase.phiSoil}")
        assertTrue(result.probability <= 35, "Severe dry soil post-trigger must drop suitability <= 35, got ${result.probability}")
        assertEquals(ProbabilityTier.VERY_LOW, result.tier)
        assertTrue(result.deterministicFieldNote.contains("deficit idrico superficiale modellato"))
        assertFalse(result.deterministicFieldNote.contains("compromesso"), "Must not assert unobserved biological damage")
    }

    @Test
    fun replayCondition3_counterfactualSustainedMoistureEnablesOptimalPeak() {
        // Same rain events and temperatures, but hypothetical sustained soil moisture (0.29 m³/m³)
        val moistDays = buildMindinoEmpiricalDays().map { day ->
            if (day.dateIso >= "2026-09-18") {
                day.copy(soilMoisture0To7 = 0.29)
            } else {
                day
            }
        }
        val input = makeMindinoInput(moistDays, todayIndex = 23) // 2026-09-29

        val result = MycoAnalysisEngine().analyze(input)

        assertTrue(result.isCalculable)
        val phase = result.growthPhase
        assertNotNull(phase)
        assertEquals(GrowthStage.ACTIVE_FRUITING, phase.stage)
        assertTrue(phase.phiSoil >= 0.95, "Moist soil must yield phiSoil >= 0.95")
        assertTrue(result.probability > MycoAnalysisEngine().analyze(makeMindinoInput(buildMindinoEmpiricalDays(), 23)).probability, "Counterfactual moist soil must improve the same scenario")
    }

    @Test
    fun replayCondition4_weakRainWithoutRechargeDoesNotTrigger() {
        // Only drizzle (1.0 mm), below the 10 mm candidate trigger threshold
        val drizzleDays = buildMindinoEmpiricalDays().map { day ->
            day.copy(totalPrecipMm = if (day.totalPrecipMm > 0.0) 1.0 else 0.0)
        }
        val input = makeMindinoInput(drizzleDays, todayIndex = 23)

        val result = MycoAnalysisEngine().analyze(input)

        assertTrue(result.isCalculable)
        val phase = result.growthPhase
        assertNotNull(phase)
        assertEquals(GrowthStage.WAITING_FOR_RAIN, phase.stage)
        assertTrue(result.probability <= 25, "Weak rain without trigger must keep suitability low <= 25, got ${result.probability}")
    }

    @Test
    fun replayCondition5_missingSoilDegradesQualityGracefully() {
        // Soil moisture sensors absent
        val noSoilDays = buildMindinoEmpiricalDays().map { it.copy(soilMoisture0To7 = null, soilMoisture7To28 = null) }
        val input = makeMindinoInput(noSoilDays, todayIndex = 23)

        val result = MycoAnalysisEngine().analyze(input)

        assertTrue(result.isCalculable)
        assertEquals(DataQualityStatus.DEGRADED_MISSING_SOIL, result.dataQuality)
        // With missing soil, note must report missing soil and not claim all sources available
        assertTrue(result.deterministicFieldNote.contains("suolo"))
        assertFalse(result.deterministicFieldNote.contains("Tutte le fonti ambientali sono disponibili"))
    }

    @Test
    fun replayCondition6_missingTargetDateReportsUncalculable() {
        val days = buildMindinoEmpiricalDays()
        // Request an index outside the available range
        val input = makeMindinoInput(days, todayIndex = 50)

        val result = MycoAnalysisEngine().analyze(input)

        assertFalse(result.isCalculable)
        assertEquals(0, result.probability)
        assertEquals(DataQualityStatus.DEGRADED_OUT_OF_BOUNDS, result.dataQuality)
        assertTrue(result.deterministicFieldNote.contains("Analisi non calcolabile"))
    }
}
