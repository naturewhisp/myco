package github.naturewhisp.myco

import github.naturewhisp.myco.core.AnalysisInputs
import github.naturewhisp.myco.core.EnvironmentalWindows
import github.naturewhisp.myco.core.MycoAnalysisEngine
import github.naturewhisp.myco.core.ProcessedDay as CoreDay
import github.naturewhisp.myco.model.FactorId
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.utils.MushroomAlgorithms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end parity fixtures for the Android facade and the KMP analysis engine.
 *
 * The mapper deliberately lives in this test: it documents the contract without
 * introducing an Android-to-KMP production dependency. Textual factor details and
 * localized prose is intentionally excluded while every shared scientific value
 * and factor level is compared.
 */
class FullAnalysisResultParityTest {

    @Test fun baselineForest() = assertParity(fixture())
    @Test fun boletusIdealAutumn() = assertParity(fixture(speciesId = "boletus_edulis", month = 8, elevation = 900.0))
    @Test fun dryWeather() = assertParity(fixture(rain = 0.0, humidity = 40.0, soil = null))
    @Test fun lowAltitude() = assertParity(fixture(elevation = 150.0))
    @Test fun highAltitude() = assertParity(fixture(speciesId = "boletus_edulis", elevation = 1900.0))
    @Test fun northSlopeSummer() = assertParity(fixture(month = 6, temperature = 22.0, elevations = northSlope()))
    @Test fun southSlopeColdSeason() = assertParity(fixture(month = 10, temperature = 10.0, elevations = southSlope()))
    @Test fun spunStrong() = assertParity(fixture(spunEcm = 52.0, spunHyphal = 6.0))
    @Test fun spunWeak() = assertParity(fixture(spunEcm = 5.0, spunHyphal = 1.0))
    @Test fun spunAbsent() = assertParity(fixture(spunEcm = null, spunHyphal = null))
    @Test fun saprotrophLowHabitat() = assertParity(fixture(
        speciesId = "macrolepiota_procera", habitat = 0.10, canopy = emptyList(),
    ))
    @Test fun saprotrophPositiveGrassland() = assertParity(fixture(
        speciesId = "macrolepiota_procera", habitat = 0.90, canopy = listOf("prati"),
    ))
    @Test fun partialSources() = assertParity(fixture(missing = listOf("SPUN", "DEM"), soil = null))
    @Test fun sevenDayOutlook() = assertParity(fixture(days = 21))

    private fun assertParity(case: Fixture) {
        val shared = MycoAnalysisEngine().analyze(case.input)
        val legacySpecies = legacySpecies(case.input.speciesId)
        val legacyDays = case.input.days.map(::legacyDay)
        val terrain = MushroomAlgorithms.calculateTerrainAspect(case.input.elevationSamples.map(Double::toFloat))
        val terrainEvaluation = MushroomAlgorithms.evaluateTerrainAspect(
            terrain,
            case.input.monthIndex,
            EnvironmentalWindows.derive(case.input.days, case.input.todayIndex).averageTempWindowC,
            MushroomAlgorithms.calculateSpeciesSeasonalityScore(case.input.monthIndex, legacySpecies).score,
            legacySpecies,
        )
        val altitude = MushroomAlgorithms.calculateSpeciesAltitudeScore(terrain.centerElevation, legacySpecies).score
        val seasonality = MushroomAlgorithms.calculateSpeciesSeasonalityScore(case.input.monthIndex, legacySpecies).score
        val weather = MushroomAlgorithms.calculateWeatherScore(
            case.input.todayIndex,
            legacyDays,
            case.input.spunHyphalDensity?.toFloat(),
            legacySpecies,
        )
        val effectiveHabitat = probabilityHabitat(case.input)
        val legacyProbability = MushroomAlgorithms.dailyGrowthProbability(
            weather,
            effectiveHabitat,
            altitude,
            seasonality,
            terrainEvaluation.modifier,
        )

        assertEquals("probability", legacyProbability, shared.probability)
        assertEquals("tier", shared.tier.tierIndex, legacyTier(legacyProbability))
        assertEquals("weather", weather, shared.weatherScore)
        assertEquals(effectiveHabitat, shared.habitatScore, CONTINUOUS_TOLERANCE)
        assertEquals(altitude, shared.altitudeScore, CONTINUOUS_TOLERANCE)
        assertEquals(seasonality, shared.seasonalityScore, CONTINUOUS_TOLERANCE)
        assertEquals(terrain.centerElevation.toDouble(), shared.terrain.elevation, CONTINUOUS_TOLERANCE)
        assertEquals(terrain.slopeDegrees.toDouble(), shared.terrain.slopeDegrees, CONTINUOUS_TOLERANCE)
        assertEquals(terrain.aspectDegrees.toDouble(), shared.terrain.aspectDegrees, CONTINUOUS_TOLERANCE)
        assertEquals(terrain.cardinalDirection, shared.terrain.cardinalDirection)
        assertEquals(terrainEvaluation.modifier, shared.terrain.modifier, CONTINUOUS_TOLERANCE)

        val legacyFactors = legacyFactors(case, legacySpecies, terrain)
        val sharedIds = shared.factors.map { it.id.name }
        val commonLegacyFactors = legacyFactors.filter { it.id.name in sharedIds }
        assertEquals(sharedIds, commonLegacyFactors.map { it.id.name })
        val sharedById = shared.factors.associateBy { it.id.name }
        commonLegacyFactors.forEach { legacy ->
            assertEquals(legacy.level.name, sharedById.getValue(legacy.id.name).level.name)
        }
        // Legacy additionally exposes lunar/mycelial phase rows; AnalysisResult has
        // no corresponding fields yet, so their absence is a documented API gap.
        assertTrue(legacyFactors.any { it.id == FactorId.MYCELIAL_PHASE })
        assertTrue(legacyFactors.any { it.id == FactorId.LUNAR_PHASE })

        assertEquals(7, shared.dailyOutlooks.size)
        shared.dailyOutlooks.forEachIndexed { offset, kmp ->
            val index = case.input.todayIndex + offset
            val android = legacyDays[index]
            val dailyWeather = MushroomAlgorithms.calculateWeatherScore(
                index,
                legacyDays,
                case.input.spunHyphalDensity?.toFloat(),
                legacySpecies,
            )
            val dailyProbability = MushroomAlgorithms.dailyGrowthProbability(
                dailyWeather,
                effectiveHabitat,
                altitude,
                seasonality,
                terrainEvaluation.modifier,
            )
            assertEquals(android.date, kmp.dateIso)
            assertEquals(android.weatherCode, kmp.weatherCode)
            assertEquals(android.avgTemp.toDouble(), kmp.avgTemp, CONTINUOUS_TOLERANCE)
            assertEquals(android.totalPrecip.toDouble(), kmp.totalPrecipMm, CONTINUOUS_TOLERANCE)
            assertEquals(android.avgHumidity.toDouble(), kmp.avgHumidityPercent, CONTINUOUS_TOLERANCE)
            assertEquals(dailyProbability, kmp.probability)
            assertEquals(legacyTier(dailyProbability), kmp.tier.tierIndex)
        }
    }

    private fun legacyFactors(case: Fixture, species: github.naturewhisp.myco.model.MushroomSpecies, terrain: github.naturewhisp.myco.model.TerrainAspectData): List<github.naturewhisp.myco.model.Factor> {
        val windows = EnvironmentalWindows.derive(case.input.days, case.input.todayIndex)
        return MushroomAlgorithms.calculateFactors(
            avgTemp = windows.averageTempWindowC,
            totalRain = windows.rainWindowTotalMm,
            avgHumidity = windows.averageHumidityWindowPercent,
            habitatScore = probabilityHabitat(case.input),
            habitatText = case.input.habitatDescription,
            elevation = terrain.centerElevation,
            month = case.input.monthIndex,
            growthPhaseText = "",
            moon = MushroomAlgorithms.getMoonPhase(java.util.Date(0)),
            slopeText = terrain.cardinalDirection,
            species = species,
            spunEcmText = case.input.spunEcmRichness?.toString(),
            spunHyphalText = case.input.spunHyphalDensity?.toString(),
            terrainEvaluation = MushroomAlgorithms.evaluateTerrainAspect(
                terrain,
                case.input.monthIndex,
                windows.averageTempWindowC,
                MushroomAlgorithms.calculateSpeciesSeasonalityScore(case.input.monthIndex, species).score,
                species,
            ),
            avgSoilMoisture0To7 = windows.averageSoil0To7?.toFloat(),
            avgSoilMoisture7To28 = windows.averageSoil7To28?.toFloat(),
            totalEvapotranspiration = windows.averageEt0?.toFloat(),
        )
    }

    private fun fixture(
        speciesId: String = "boletus_edulis",
        habitat: Double = 0.90,
        canopy: List<String> = listOf("fagus"),
        elevation: Double = 900.0,
        month: Int = 8,
        temperature: Double = 18.0,
        rain: Double = 5.0,
        humidity: Double = 86.0,
        soil: Double? = 0.28,
        spunEcm: Double? = 30.0,
        spunHyphal: Double? = 5.5,
        elevations: List<Double> = listOf(elevation, elevation, elevation, elevation, elevation),
        missing: List<String> = emptyList(),
        days: Int = 21,
    ): Fixture {
        val samples = List(days) { index ->
            CoreDay(
                dateIso = "2026-09-${(index + 1).toString().padStart(2, '0')}",
                avgTemp = temperature,
                totalPrecipMm = if (index in 4..11) rain else 0.0,
                avgHumidityPercent = humidity,
                weatherCode = 2,
                soilMoisture0To7 = soil,
                soilMoisture7To28 = soil?.minus(0.02),
                evapotranspiration = if (soil == null) null else 2.0,
            )
        }
        return Fixture(AnalysisInputs(samples, 14.coerceAtMost(days - 1), speciesId, habitat, "Bosco misto", canopy, elevations, month, spunEcm, spunHyphal, missing))
    }

    private data class Fixture(val input: AnalysisInputs)

    private fun legacySpecies(id: String) = SPECIES_CATALOG.first { it.id == id }
    private fun legacyDay(day: CoreDay) = ProcessedDay(day.dateIso, day.avgTemp.toFloat(), day.totalPrecipMm.toFloat(), day.avgHumidityPercent.toFloat(), day.weatherCode, day.soilMoisture0To7?.toFloat(), day.soilMoisture7To28?.toFloat(), day.evapotranspiration?.toFloat())
    private fun probabilityHabitat(input: AnalysisInputs): Double {
        var score = input.habitatScore
        if (input.canopyTypes.isNotEmpty()) score = minOf(1.0, score * 1.15)
        input.spunEcmRichness?.let { score = when { it >= 50.0 -> minOf(1.0, score * 1.15); it < 15.0 && input.habitatScore > 0.1 -> maxOf(0.2, score * 0.8); else -> score } }
        return score
    }
    private fun legacyTier(probability: Int) = when { probability < 20 -> 0; probability < 40 -> 1; probability < 60 -> 2; probability < 75 -> 3; else -> 4 }
    private fun northSlope() = listOf(1000.0, 950.0, 1050.0, 1000.0, 1000.0)
    private fun southSlope() = listOf(1000.0, 1050.0, 950.0, 1000.0, 1000.0)

    private companion object { const val CONTINUOUS_TOLERANCE = 1.0e-6 }
}
