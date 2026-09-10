package github.naturewhisp.myco

import github.naturewhisp.myco.model.EcologicalWeightsConfig
import github.naturewhisp.myco.model.FactorLevel
import github.naturewhisp.myco.model.MushroomSpecies
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.model.TerrainAspectConfig
import github.naturewhisp.myco.utils.MushroomAlgorithms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Suite di test avversariali Gate 1 per la validazione empirica, matematica e orografica
 * dei modelli ecologici, curve continue e configurazioni tipizzate.
 */
class AdversarialGate1Test {

    // =========================================================================
    // 1. ORACLE TEST: Legacy vs EcologicalWeightsConfig.DEFAULT
    // =========================================================================

    /**
     * Oracolo matematico legacy con costanti hardcoded come nella versione precedente ad M1.
     */
    private fun legacyCalculateWeatherScore(
        dayIndex: Int,
        allData: List<ProcessedDay>,
        spunHyphalDensity: Float? = null,
        species: MushroomSpecies = SPECIES_CATALOG[0]
    ): Int {
        if (dayIndex < 0 || dayIndex >= allData.size) return 0

        val rainStart = max(0, dayIndex - 10)
        val rainEnd = max(0, dayIndex - 2)
        val rainWindow = if (rainStart < rainEnd && rainEnd <= allData.size) {
            allData.subList(rainStart, rainEnd)
        } else {
            emptyList()
        }
        val totalRainLast10Days = rainWindow.sumOf { it.totalPrecip.toDouble() }
        var rainScore = MushroomAlgorithms.rainScoreSmooth(totalRainLast10Days, species) * 40.0

        if (spunHyphalDensity != null && spunHyphalDensity >= 5.0f && totalRainLast10Days >= 12.0) {
            rainScore = min(40.0, rainScore + 6.0)
        } else if (spunHyphalDensity != null && spunHyphalDensity < 2.5f) {
            rainScore = max(0.0, rainScore - 4.0)
        }

        val tempStart = max(0, dayIndex - 5)
        val tempWindow = if (tempStart < dayIndex && dayIndex <= allData.size) {
            allData.subList(tempStart, dayIndex)
        } else {
            emptyList()
        }
        val avgTempLast5Days = if (tempWindow.isNotEmpty()) {
            tempWindow.sumOf { it.avgTemp.toDouble() } / tempWindow.size
        } else {
            0.0
        }
        val tempScore = MushroomAlgorithms.tempScoreSmooth(avgTempLast5Days, species) * 30.0

        val humStart = max(0, dayIndex - 3)
        val humEnd = min(allData.size, dayIndex + 1)
        val humWindow = if (humStart < humEnd) {
            allData.subList(humStart, humEnd)
        } else {
            emptyList()
        }
        val avgHumidityRecent = if (humWindow.isNotEmpty()) {
            humWindow.sumOf { it.avgHumidity.toDouble() } / humWindow.size
        } else {
            0.0
        }
        val humScore = MushroomAlgorithms.humidityScoreSmooth(avgHumidityRecent) * 15.0

        var shockScore = 0.0
        if (dayIndex > 4 && totalRainLast10Days >= 12.0) {
            val tempBefore = allData[dayIndex - 4].avgTemp
            val tempAfter = allData[dayIndex - 1].avgTemp
            val drop = (tempBefore - tempAfter).toDouble()
            val minDrop = if (spunHyphalDensity != null && spunHyphalDensity >= 5.0f) 2.0 else 3.0
            if (drop > minDrop) {
                val dropFactor = ((drop - minDrop) / 3.0).coerceIn(0.0, 1.0)
                val rainFactor = (totalRainLast10Days / 25.0).coerceIn(0.0, 1.0)
                shockScore = 15.0 * dropFactor * rainFactor
            }
        }

        return (rainScore + tempScore + humScore + shockScore).coerceIn(0.0, 100.0).roundToInt()
    }

    private fun legacyDailyGrowthProbability(
        weatherScore: Int,
        habitatScore: Double,
        altitudeScore: Double,
        seasonalityScore: Double,
        terrainModifier: Double = 1.0
    ): Int {
        val weightedWeatherScore = 100.0 * Math.pow(weatherScore / 100.0, 1.2)
        val combined = weightedWeatherScore * habitatScore * altitudeScore * seasonalityScore * terrainModifier
        return combined.toInt().coerceIn(0, 100)
    }

    @Test
    fun testEmpiricalOracleEquivalenceWeatherScore() {
        val rng = Random(42)
        val speciesList = SPECIES_CATALOG

        // Test over 1,000 synthetic weather scenarios
        for (scenario in 0 until 1000) {
            val daysCount = rng.nextInt(5, 35)
            val days = mutableListOf<ProcessedDay>()
            for (d in 0 until daysCount) {
                days.add(
                    ProcessedDay(
                        date = "2026-09-${(d + 1).toString().padStart(2, '0')}",
                        avgTemp = rng.nextDouble(-10.0, 40.0).toFloat(),
                        totalPrecip = rng.nextDouble(0.0, 80.0).toFloat(),
                        avgHumidity = rng.nextDouble(20.0, 100.0).toFloat(),
                        weatherCode = 0
                    )
                )
            }

            val targetDay = rng.nextInt(0, daysCount)
            val spunDensity: Float? = when (rng.nextInt(4)) {
                0 -> null
                1 -> rng.nextDouble(0.0, 2.4).toFloat()
                2 -> rng.nextDouble(2.5, 4.9).toFloat()
                else -> rng.nextDouble(5.0, 10.0).toFloat()
            }
            val species = speciesList[rng.nextInt(speciesList.size)]

            val legacyScore = legacyCalculateWeatherScore(targetDay, days, spunDensity, species)
            val refactoredScore = MushroomAlgorithms.calculateWeatherScore(
                targetDay,
                days,
                spunDensity,
                species,
                EcologicalWeightsConfig.DEFAULT
            )

            assertEquals(
                "Scenario $scenario failed: day $targetDay, spun $spunDensity, species ${species.id}",
                legacyScore,
                refactoredScore
            )
        }
    }

    @Test
    fun testEmpiricalOracleEquivalenceGrowthProbability() {
        val rng = Random(123)

        for (i in 0 until 5000) {
            val weatherScore = rng.nextInt(0, 101)
            val habitatScore = rng.nextDouble(0.0, 1.0)
            val altitudeScore = rng.nextDouble(0.4, 1.0)
            val seasonalityScore = rng.nextDouble(0.1, 1.0)
            val terrainModifier = rng.nextDouble(0.5, 1.1)

            val legacyProb = legacyDailyGrowthProbability(
                weatherScore,
                habitatScore,
                altitudeScore,
                seasonalityScore,
                terrainModifier
            )
            val refactoredProb = MushroomAlgorithms.dailyGrowthProbability(
                weatherScore,
                habitatScore,
                altitudeScore,
                seasonalityScore,
                terrainModifier,
                EcologicalWeightsConfig.DEFAULT
            )

            assertEquals(
                "Iteration $i mismatch for W=$weatherScore, H=$habitatScore, A=$altitudeScore, S=$seasonalityScore, T=$terrainModifier",
                legacyProb,
                refactoredProb
            )
        }
    }

    // =========================================================================
    // 2. MATHEMATICAL PROPERTIES & CONVEXITY
    // =========================================================================

    @Test
    fun testConvexPowerFunctionPenaltyProperty() {
        // P = 100 * (W/100)^1.2:
        // For 0 < W < 100, W_weighted must be strictly less than W (convex penalty on mediocre weather).
        for (w in 1 until 100) {
            val linear = w.toDouble()
            val weighted = 100.0 * Math.pow(w / 100.0, 1.2)
            assertTrue("Weather $w should suffer convex penalty: weighted ($weighted) < linear ($linear)", weighted < linear)
        }

        // At boundary 0 and 100, identity holds
        assertEquals(0.0, 100.0 * Math.pow(0.0 / 100.0, 1.2), 0.0001)
        assertEquals(100.0, 100.0 * Math.pow(100.0 / 100.0, 1.2), 0.0001)
    }

    @Test
    fun testWeightConfigurationSensitivity() {
        val days = listOf(
            ProcessedDay("2026-09-01", 18f, 30f, 80f, 0),
            ProcessedDay("2026-09-02", 18f, 20f, 80f, 0),
            ProcessedDay("2026-09-03", 18f, 10f, 80f, 0),
            ProcessedDay("2026-09-04", 18f, 0f, 80f, 0),
            ProcessedDay("2026-09-05", 18f, 0f, 80f, 0),
            ProcessedDay("2026-09-06", 18f, 0f, 80f, 0),
            ProcessedDay("2026-09-07", 18f, 0f, 80f, 0)
        )

        // Configuration with 100% rain weight and 0 for others
        val rainHeavy = EcologicalWeightsConfig(rainWeight = 100.0, tempWeight = 0.0, humidityWeight = 0.0, thermalShockWeight = 0.0)
        val scoreRainHeavy = MushroomAlgorithms.calculateWeatherScore(6, days, config = rainHeavy)

        // Configuration with 0% rain weight and 100% temp weight
        val tempHeavy = EcologicalWeightsConfig(rainWeight = 0.0, tempWeight = 100.0, humidityWeight = 0.0, thermalShockWeight = 0.0)
        val scoreTempHeavy = MushroomAlgorithms.calculateWeatherScore(6, days, config = tempHeavy)

        // 18°C is 100% ideal for Boletus edulis (tempScoreSmooth = 1.0), so tempHeavy should yield 100
        assertEquals(100, scoreTempHeavy)
        // Rain is ~60mm (target 40mm) -> rainScoreSmooth = 1.0 -> 100
        assertEquals(100, scoreRainHeavy)
    }

    // =========================================================================
    // 3. CONTINUOUS BIOLOGICAL CURVES & DEGENERATE SPECIES
    // =========================================================================

    @Test
    fun testTempScoreSmoothContinuityAndBounds() {
        for (species in SPECIES_CATALOG) {
            var prevScore = MushroomAlgorithms.tempScoreSmooth(-30.0, species)
            assertEquals("Extreme cold must produce 0.0", 0.0, prevScore, 0.0001)

            var t = -30.0
            while (t <= 60.0) {
                val score = MushroomAlgorithms.tempScoreSmooth(t, species)
                assertTrue("Score at $t for ${species.id} must be in [0.0, 1.0]: was $score", score in 0.0..1.0)
                assertFalse("Score at $t for ${species.id} must not be NaN", score.isNaN())

                // Check Lipschitz continuity (no abrupt step jumps greater than 0.2 across 0.5°C)
                val delta = abs(score - prevScore)
                assertTrue("Abrupt discontinuity ($delta) at $t for ${species.id}", delta <= 0.25)

                prevScore = score
                t += 0.5
            }

            val extremeHeat = MushroomAlgorithms.tempScoreSmooth(60.0, species)
            assertEquals("Extreme heat must produce 0.0", 0.0, extremeHeat, 0.0001)
        }
    }

    @Test
    fun testDegenerateSpeciesProfilesNoDivisionByZero() {
        // Construct a degenerate species where ideal span = 0 or tolerated span = 0
        val degenerateSpecies = SPECIES_CATALOG[0].copy(
            id = "degenerate",
            idealTempMin = 20f,
            idealTempMax = 20f,
            toleratedTempMin = 20f, // span = 0
            toleratedTempMax = 20f, // span = 0
            minRainAccumulation = 0f, // target = 0
            minElevation = 500,
            maxElevation = 500, // span = 0
            idealElevationMin = 500,
            idealElevationMax = 500
        )

        // tempScoreSmooth must not produce NaN or crash
        val s1 = MushroomAlgorithms.tempScoreSmooth(19.0, degenerateSpecies)
        val s2 = MushroomAlgorithms.tempScoreSmooth(20.0, degenerateSpecies)
        val s3 = MushroomAlgorithms.tempScoreSmooth(21.0, degenerateSpecies)
        assertFalse("s1 is NaN", s1.isNaN())
        assertFalse("s2 is NaN", s2.isNaN())
        assertFalse("s3 is NaN", s3.isNaN())
        assertEquals(0.0, s1, 0.0001)
        assertEquals(1.0, s2, 0.0001)
        assertEquals(0.0, s3, 0.0001)

        // rainScoreSmooth with target = 0 must return 1.0 or 0.0 for negative, not NaN
        val r1 = MushroomAlgorithms.rainScoreSmooth(-5.0, degenerateSpecies)
        val r2 = MushroomAlgorithms.rainScoreSmooth(0.0, degenerateSpecies)
        val r3 = MushroomAlgorithms.rainScoreSmooth(10.0, degenerateSpecies)
        assertEquals(0.0, r1, 0.0001)
        assertEquals(0.0, r2, 0.0001)
        assertEquals(1.0, r3, 0.0001)

        // calculateSpeciesAltitudeScore with span = 0 must not crash
        val a1 = MushroomAlgorithms.calculateSpeciesAltitudeScore(400f, degenerateSpecies)
        val a2 = MushroomAlgorithms.calculateSpeciesAltitudeScore(500f, degenerateSpecies)
        val a3 = MushroomAlgorithms.calculateSpeciesAltitudeScore(600f, degenerateSpecies)
        assertEquals(0.4, a1.score, 0.0001)
        assertEquals(1.0, a2.score, 0.0001)
        assertEquals(0.4, a3.score, 0.0001)
    }

    @Test
    fun testRainScoreSmoothMonotonicity() {
        for (species in SPECIES_CATALOG) {
            var prev = -1.0
            var r = -10.0
            while (r <= 200.0) {
                val score = MushroomAlgorithms.rainScoreSmooth(r, species)
                assertTrue("Rain score must be in [0, 1]: was $score at $r", score in 0.0..1.0)
                assertTrue("Rain score must be monotonically non-decreasing: $score < $prev at $r", score >= prev)
                prev = score
                r += 2.0
            }
        }
    }

    @Test
    fun testHumidityScoreSmoothMonotonicity() {
        var prev = -1.0
        var h = -10.0
        while (h <= 120.0) {
            val score = MushroomAlgorithms.humidityScoreSmooth(h)
            assertTrue("Humidity score must be in [0, 1]: was $score at $h", score in 0.0..1.0)
            assertTrue("Humidity score must be monotonically non-decreasing: $score < $prev at $h", score >= prev)
            prev = score
            h += 1.0
        }
    }

    @Test
    fun testSeasonalityCircularAdjacency() {
        // Construct a species active only in December (month 11)
        val winterSpecies = SPECIES_CATALOG[0].copy(activeMonths = listOf(11))

        // December (11) must be peak (1.0)
        val dec = MushroomAlgorithms.calculateSpeciesSeasonalityScore(11, winterSpecies)
        assertEquals(1.0, dec.score, 0.001)

        // November (10) is adjacent (0.6)
        val nov = MushroomAlgorithms.calculateSpeciesSeasonalityScore(10, winterSpecies)
        assertEquals(0.6, nov.score, 0.001)

        // January (0) is circularly adjacent across new year (0.6)
        val jan = MushroomAlgorithms.calculateSpeciesSeasonalityScore(0, winterSpecies)
        assertEquals("January must be circularly adjacent to December", 0.6, jan.score, 0.001)

        // February (1) is off-season (0.1)
        val feb = MushroomAlgorithms.calculateSpeciesSeasonalityScore(1, winterSpecies)
        assertEquals(0.1, feb.score, 0.001)
    }

    // =========================================================================
    // 4. DEM TERRAIN ASPECT & CARDINAL ACCURACY
    // =========================================================================

    @Test
    fun testTerrainAspect360DegreeAzimuthAndQuadrants() {
        val delta = 75.0
        val center = 500f
        val slopeMag = 50f // ~25° slope

        // Test every 15 degrees around the compass
        val angles = (0 until 360 step 15)
        for (targetDeg in angles) {
            // Downhill direction is targetDeg:
            // vx = sin(rad), vy = cos(rad)
            // dzdx = -vx, dzdy = -vy
            // zEast - zWest = dzdx * 2 * delta
            // zNorth - zSouth = dzdy * 2 * delta
            val rad = Math.toRadians(targetDeg.toDouble())
            val vx = sin(rad)
            val vy = cos(rad)

            val dzdx = -vx * (slopeMag / (2.0 * delta))
            val dzdy = -vy * (slopeMag / (2.0 * delta))

            val zEast = (center + dzdx * delta).toFloat()
            val zWest = (center - dzdx * delta).toFloat()
            val zNorth = (center + dzdy * delta).toFloat()
            val zSouth = (center - dzdy * delta).toFloat()

            val elevations = listOf(center, zNorth, zSouth, zEast, zWest)
            val aspect = MushroomAlgorithms.calculateTerrainAspect(elevations, delta)

            assertFalse("Slope should not be flat for slopeMag $slopeMag", aspect.isFlat)
            val diff = abs(aspect.aspectDegrees - targetDeg.toFloat())
            val circularDiff = min(diff, 360f - diff)
            assertTrue("Aspect degree $circularDiff too far from target $targetDeg (computed: ${aspect.aspectDegrees})", circularDiff < 2.0f)
        }
    }

    @Test
    fun testTerrainAspectThresholdsAndSteepSlopePenalty() {
        val delta = 75.0
        val center = 500f

        // 1. Below 3.0° -> Flat
        // dz = 2.0m across 150m -> slope ~ 0.76°
        val elevationsGentle = listOf(center, center + 1f, center - 1f, center, center)
        val aspectGentle = MushroomAlgorithms.calculateTerrainAspect(elevationsGentle, delta)
        assertTrue("Slope < 3.0° must be flat", aspectGentle.isFlat)
        val evalGentle = MushroomAlgorithms.evaluateTerrainAspect(aspectGentle, 8, 20.0, 1.0)
        assertEquals(1.0, evalGentle.modifier, 0.001)

        // 2. Above 38.0° -> Steep slope penalty applied
        // tan(45°) = 1.0 -> dz across 150m is 150m (zNorth=575, zSouth=425)
        val elevationsSteep = listOf(center, center + 75f, center - 75f, center, center)
        val aspectSteep = MushroomAlgorithms.calculateTerrainAspect(elevationsSteep, delta)
        assertTrue("Slope ~45° must exceed 38.0°", aspectSteep.slopeDegrees > 38f)

        val evalSteep = MushroomAlgorithms.evaluateTerrainAspect(aspectSteep, 6, 24.0, 1.0)
        assertTrue("Steep slope modifier must be penalized to <= 0.92: was ${evalSteep.modifier}", evalSteep.modifier <= 0.92)
        assertTrue("Steep slope detail must report runoff", evalSteep.detail.contains("Ruscellamento"))
    }

    // =========================================================================
    // 5. NUMERICAL STABILITY, INSUFFICIENT DATA & ROBUSTNESS
    // =========================================================================

    @Test
    fun testEmptyAndBoundaryDataRobustness() {
        // Empty series
        assertEquals(0, MushroomAlgorithms.calculateWeatherScore(0, emptyList()))
        assertEquals(0, MushroomAlgorithms.calculateWeatherScore(-1, emptyList()))
        assertEquals(0, MushroomAlgorithms.calculateWeatherScore(10, emptyList()))

        // Insufficient DEM points
        val emptyDem = MushroomAlgorithms.calculateTerrainAspect(emptyList())
        assertTrue(emptyDem.isFlat)
        assertEquals(0f, emptyDem.slopeDegrees, 0.001f)

        val singlePointDem = MushroomAlgorithms.calculateTerrainAspect(listOf(1200f))
        assertTrue(singlePointDem.isFlat)
        assertEquals(1200f, singlePointDem.centerElevation, 0.001f)

        val threePointDem = MushroomAlgorithms.calculateTerrainAspect(listOf(1200f, 1250f, 1150f))
        assertTrue(threePointDem.isFlat)

        // Null terrain aspect evaluation
        val nullEval = MushroomAlgorithms.evaluateTerrainAspect(null, 8, 18.0, 1.0)
        assertEquals(1.0, nullEval.modifier, 0.001)
        assertEquals(FactorLevel.INFORMATIVE, nullEval.level)

        // Empty daily outlooks
        val outlooks = MushroomAlgorithms.calculateDailyOutlooks(emptyList())
        assertTrue(outlooks.isEmpty())

        // Negative elevation
        val belowSeaLevel = MushroomAlgorithms.calculateSpeciesAltitudeScore(-50f, SPECIES_CATALOG[0])
        assertEquals(0.4, belowSeaLevel.score, 0.001)

        // Extreme elevation
        val everest = MushroomAlgorithms.calculateSpeciesAltitudeScore(8848f, SPECIES_CATALOG[0])
        assertEquals(0.4, everest.score, 0.001)
    }
}
