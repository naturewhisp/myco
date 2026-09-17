package github.naturewhisp.myco

import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.model.DailyData
import github.naturewhisp.myco.model.FactorId
import github.naturewhisp.myco.model.FactorLevel
import github.naturewhisp.myco.model.HourlyData
import github.naturewhisp.myco.model.WeatherResponse
import github.naturewhisp.myco.network.WeatherService
import github.naturewhisp.myco.platform.AssetProvider
import github.naturewhisp.myco.platform.InMemoryCacheStore
import github.naturewhisp.myco.platform.KeyValueStorage
import github.naturewhisp.myco.repository.CacheManager
import github.naturewhisp.myco.repository.MushroomRepository
import github.naturewhisp.myco.repository.SpunDataManager
import github.naturewhisp.myco.utils.HeatmapGenerator
import github.naturewhisp.myco.utils.MushroomAlgorithms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.util.Date

/**
 * Golden master deterministico per il nucleo scientifico che sara estratto in KMP.
 *
 * I valori attesi bloccano le attuali formule canoniche; le tolleranze si applicano
 * solo ai coefficienti continui, prima della quantizzazione della probabilita.
 */
class ScientificGoldenMasterTest {

    @Test
    fun p1Scenario01_alpineFavorableHabitatWithBoletusEdulis() {
        val edulis = species("boletus_edulis")
        assertEquals(1.0, MushroomAlgorithms.calculateSpeciesAltitudeScore(1_200f, edulis).score, CONTINUOUS_TOLERANCE)
        assertEquals(1.0, MushroomAlgorithms.calculateSpeciesSeasonalityScore(8, edulis).score, CONTINUOUS_TOLERANCE)
    }

    @Test
    fun p1Scenario02_piedmontHillyHabitatContract() {
        val factors = factorsFor(habitatScore = 0.9, elevation = 350f)
        val habitat = factors.first { it.id == FactorId.HABITAT }
        assertEquals("90%", habitat.formattedValue)
        assertEquals(FactorLevel.FAVORABLE, habitat.level)
    }

    @Test
    fun p1Scenario03_urbanHabitatReducesProbability() {
        assertEquals(8, MushroomAlgorithms.dailyGrowthProbability(85, 0.1, 1.0, 1.0))
    }

    @Test
    fun p1Scenario04_pointOutsideSpunCoverageHasNoRegion() {
        val manager = SpunDataManager(AssetProvider { _: String -> throw IllegalStateException("not read") })
        assertNull(manager.findRegionFor(51.5072, -0.1276))
    }

    @Test
    fun p1Scenario05_saprotrophicHabitatUsesGrasslandFloor() {
        val factors = factorsFor(species = species("macrolepiota_procera"), habitatScore = 0.1)
        val habitat = factors.first { it.id == FactorId.HABITAT }
        assertEquals("85%", habitat.formattedValue)
        assertEquals(FactorLevel.FAVORABLE, habitat.level)
        assertTrue(habitat.detail.contains("praticolo"))
    }

    @Test
    fun p1Scenario06_speciesCanopyProfilesRemainDistinct() {
        val edulis = species("boletus_edulis")
        val aereus = species("boletus_aereus")
        assertTrue(edulis.preferredCanopyTypes.contains("picea"))
        assertTrue(aereus.preferredCanopyTypes.contains("quercus"))
        assertFalse(aereus.preferredCanopyTypes.contains("picea"))
    }

    @Test
    fun p1Scenario07_veryDryConditionsHaveWeatherGoldenScore() {
        assertEquals(30, MushroomAlgorithms.calculateWeatherScore(6, weatherDays(rain = 0f, humidity = 40f)))
    }

    @Test
    fun p1Scenario08_favorablePrecipitationHasWeatherGoldenScore() {
        assertEquals(85, MushroomAlgorithms.calculateWeatherScore(6, weatherDays(rain = 10f, humidity = 90f)))
    }

    @Test
    fun p1Scenario09_thermalShockAddsItsCanonicalBonus() {
        val days = weatherDays(rain = 10f, humidity = 90f).toMutableList()
        days[2] = days[2].copy(avgTemp = 22f)
        days[5] = days[5].copy(avgTemp = 17f)
        assertEquals(95, MushroomAlgorithms.calculateWeatherScore(6, days))
    }

    @Test
    fun p1Scenario10_unfavorableAltitudeForEdulis() {
        assertEquals(0.4, MushroomAlgorithms.calculateSpeciesAltitudeScore(1_900f, species("boletus_edulis")).score, CONTINUOUS_TOLERANCE)
    }

    @Test
    fun p1Scenario11_favorableTerrainAspect() {
        val southTerrain = southTerrain()
        val evaluation = MushroomAlgorithms.evaluateTerrainAspect(southTerrain, 10, 9.0, 1.0)
        assertEquals(1.05, evaluation.modifier, CONTINUOUS_TOLERANCE)
        assertEquals(FactorLevel.FAVORABLE, evaluation.level)
    }

    @Test
    fun p1Scenario12_unfavorableTerrainAspect() {
        val evaluation = MushroomAlgorithms.evaluateTerrainAspect(southTerrain(), 6, 24.0, 0.5)
        assertEquals(0.9, evaluation.modifier, CONTINUOUS_TOLERANCE)
        assertEquals(FactorLevel.ADVERSE, evaluation.level)
    }

    @Test
    fun p1Scenario13_nearZeroProbability() {
        assertEquals(0, MushroomAlgorithms.dailyGrowthProbability(0, 1.0, 1.0, 1.0))
    }

    @Test
    fun p1Scenario14_intermediateProbability() {
        val northTerrain = MushroomAlgorithms.calculateTerrainAspect(
            elevations = listOf(1_000f, 950f, 1_050f, 1_000f, 1_000f),
        )
        val altitude = MushroomAlgorithms.calculateAltitudeScore(350f)
        val seasonality = MushroomAlgorithms.calculateSeasonalityScore(4)
        val terrain = MushroomAlgorithms.evaluateTerrainAspect(northTerrain, 4, 18.0, seasonality.score)
        assertEquals(0.9, altitude.score, CONTINUOUS_TOLERANCE)
        assertEquals(0.9, seasonality.score, CONTINUOUS_TOLERANCE)
        assertEquals(1.0, terrain.modifier, CONTINUOUS_TOLERANCE)
        assertEquals(55, MushroomAlgorithms.dailyGrowthProbability(80, 0.9, altitude.score, seasonality.score, terrain.modifier))
    }

    @Test
    fun p1Scenario15_highProbability() {
        val altitude = MushroomAlgorithms.calculateAltitudeScore(1_000f)
        val seasonality = MushroomAlgorithms.calculateSeasonalityScore(9)
        val terrain = MushroomAlgorithms.evaluateTerrainAspect(southTerrain(), 10, 9.0, seasonality.score)
        assertEquals(88, MushroomAlgorithms.dailyGrowthProbability(95, 1.0, altitude.score, seasonality.score, terrain.modifier))
    }

    @Test
    fun p1Scenario16_sevenDayForecastKeepsHistoricalWeatherWindowAndDailyContract() {
        val outlooks = MushroomAlgorithms.calculateDailyOutlooks(
            processedDays = weatherDays(rain = 10f, humidity = 90f, count = 13),
            startIndex = 6,
            species = species("general"),
            habitatScore = 1.0,
            elevation = 1_000f,
            month = 8,
        )
        assertEquals(7, outlooks.size)
        assertEquals((16..22).map { "2026-09-$it" }, outlooks.map { it.dateIso })
        assertEquals(List(7) { 81 }, outlooks.map { it.probability })
        assertEquals(List(7) { 4 }, outlooks.map { it.tier })
        assertTrue(outlooks.all { it.weatherCode == 61 && it.totalPrecipMm == 10f })
    }

    @Test
    fun p1Scenario17_heatmapIsDeterministicForStaticInput() {
        val probabilities = intArrayOf(0, 15, 16, 18, 20, 40, 45, 50, 60, 65, 70, 78, 82, 86, 100)
        val argbSamples = probabilities.map { HeatmapGenerator.getHeatmapColor(it) }.toIntArray()
        assertEquals(0, argbSamples[0])
        assertEquals(0x004E9648, argbSamples[2])
        assertEquals(0xB49E262C.toInt(), argbSamples.last())
        assertEquals(641_953_497, argbSamples.contentHashCode())
    }

    @Test
    fun p1Scenario18_offlineFallbackReturnsCachedWeather() = runBlocking {
        val cache = CacheManager(TestKeyValueStorage(), InMemoryCacheStore())
        val cached = WeatherResponse(750f, "Europe/Rome", HourlyData(emptyList(), emptyList(), emptyList(), emptyList()), DailyData(emptyList(), emptyList()))
        cache.saveCachedData("weather_44.5000_8.0000", cached)
        val service = mockk<WeatherService>()
        coEvery { service.getForecast(any(), any(), any(), any(), any(), any(), any()) } throws java.io.IOException("offline")
        val repository = MushroomRepository(cache, SpunDataManager(AssetProvider { _: String -> throw IllegalStateException("not read") }), weatherService = service)
        val result = repository.fetchWeather(44.5, 8.0)
        assertNotNull(result)
        assertEquals(750f, result.elevation, FLOAT_TOLERANCE)
    }

    @Test
    fun p1Scenario19_expiredCacheDoesNotReadAsFresh() {
        val cache = CacheManager(TestKeyValueStorage(), InMemoryCacheStore())
        cache.saveCachedData("expired", CacheGoldenValue(42))
        assertNull(cache.getCachedData("expired", CacheGoldenValue::class.java, 0L))
    }

    @Test
    fun p1Scenario20_moonPhaseStaticInput() {
        val phase = MushroomAlgorithms.getMoonPhase(Date(947_182_440_000L))
        assertEquals("NEW_MOON", phase.code)
        assertTrue(phase.favorable)
    }

    @Test
    fun probabilityGoldenMaster_coversLowMediumAndHighTerrainProfiles() {
        val northTerrain = MushroomAlgorithms.calculateTerrainAspect(
            elevations = listOf(1_000f, 950f, 1_050f, 1_000f, 1_000f),
        )
        val southTerrain = MushroomAlgorithms.calculateTerrainAspect(
            elevations = listOf(1_000f, 1_050f, 950f, 1_000f, 1_000f),
        )

        val lowAltitude = MushroomAlgorithms.calculateAltitudeScore(150f)
        val lowSeason = MushroomAlgorithms.calculateSeasonalityScore(6)
        val lowAspect = MushroomAlgorithms.evaluateTerrainAspect(
            terrain = southTerrain,
            month = 6,
            avgTemp = 24.0,
            seasonalityScore = lowSeason.score,
        )
        assertEquals(0.7, lowAltitude.score, CONTINUOUS_TOLERANCE)
        assertEquals(0.5, lowSeason.score, CONTINUOUS_TOLERANCE)
        assertEquals(0.9, lowAspect.modifier, CONTINUOUS_TOLERANCE)
        assertEquals(
            13,
            MushroomAlgorithms.dailyGrowthProbability(
                weatherScore = 60,
                habitatScore = 0.8,
                altitudeScore = lowAltitude.score,
                seasonalityScore = lowSeason.score,
                terrainModifier = lowAspect.modifier,
            ),
        )

        val mediumAltitude = MushroomAlgorithms.calculateAltitudeScore(350f)
        val mediumSeason = MushroomAlgorithms.calculateSeasonalityScore(4)
        val mediumAspect = MushroomAlgorithms.evaluateTerrainAspect(
            terrain = northTerrain,
            month = 4,
            avgTemp = 18.0,
            seasonalityScore = mediumSeason.score,
        )
        assertEquals(0.9, mediumAltitude.score, CONTINUOUS_TOLERANCE)
        assertEquals(0.9, mediumSeason.score, CONTINUOUS_TOLERANCE)
        assertEquals(1.0, mediumAspect.modifier, CONTINUOUS_TOLERANCE)
        assertEquals(
            55,
            MushroomAlgorithms.dailyGrowthProbability(
                weatherScore = 80,
                habitatScore = 0.9,
                altitudeScore = mediumAltitude.score,
                seasonalityScore = mediumSeason.score,
                terrainModifier = mediumAspect.modifier,
            ),
        )

        val highAltitude = MushroomAlgorithms.calculateAltitudeScore(1_000f)
        val highSeason = MushroomAlgorithms.calculateSeasonalityScore(9)
        val highAspect = MushroomAlgorithms.evaluateTerrainAspect(
            terrain = southTerrain,
            month = 10,
            avgTemp = 9.0,
            seasonalityScore = highSeason.score,
        )
        assertEquals(1.0, highAltitude.score, CONTINUOUS_TOLERANCE)
        assertEquals(1.0, highSeason.score, CONTINUOUS_TOLERANCE)
        assertEquals(1.05, highAspect.modifier, CONTINUOUS_TOLERANCE)
        assertEquals(
            88,
            MushroomAlgorithms.dailyGrowthProbability(
                weatherScore = 95,
                habitatScore = 1.0,
                altitudeScore = highAltitude.score,
                seasonalityScore = highSeason.score,
                terrainModifier = highAspect.modifier,
            ),
        )
    }

    @Test
    fun forecastGoldenMaster_keepsHistoricalWeatherWindowAndDailyContract() {
        val days = (0..7).map { index ->
            ProcessedDay(
                date = "2026-09-${10 + index}",
                avgTemp = 18f,
                totalPrecip = 10f,
                avgHumidity = 90f,
                weatherCode = 61,
            )
        }

        val outlooks = MushroomAlgorithms.calculateDailyOutlooks(
            processedDays = days,
            startIndex = 6,
            species = SPECIES_CATALOG.first { it.id == "general" },
            habitatScore = 1.0,
            elevation = 1_000f,
            month = 8,
        )

        assertEquals(2, outlooks.size)
        assertEquals(listOf("2026-09-16", "2026-09-17"), outlooks.map { it.dateIso })
        assertEquals(listOf(81, 81), outlooks.map { it.probability })
        assertEquals(listOf(4, 4), outlooks.map { it.tier })
        assertTrue(outlooks.all { it.weatherCode == 61 && it.totalPrecipMm == 10f })
    }

    @Test
    fun heatmapPaletteGoldenMaster_hashesCanonicalArgbSamples() {
        val probabilities = intArrayOf(0, 15, 16, 18, 20, 40, 45, 50, 60, 65, 70, 78, 82, 86, 100)
        val argbSamples = probabilities.map { HeatmapGenerator.getHeatmapColor(it) }.toIntArray()

        assertEquals(0, argbSamples[0])
        assertEquals(0, argbSamples[1])
        assertEquals(0x004E9648, argbSamples[2])
        assertEquals(0xB49E262C.toInt(), argbSamples.last())
        assertEquals(641_953_497, argbSamples.contentHashCode())
    }

    private fun species(id: String) = SPECIES_CATALOG.first { it.id == id }

    private fun southTerrain() = MushroomAlgorithms.calculateTerrainAspect(
        elevations = listOf(1_000f, 1_050f, 950f, 1_000f, 1_000f),
    )

    private fun weatherDays(
        rain: Float,
        humidity: Float,
        count: Int = 7,
    ): List<ProcessedDay> = List(count) { index ->
        ProcessedDay(
            date = "2026-09-${10 + index}",
            avgTemp = 18f,
            totalPrecip = rain,
            avgHumidity = humidity,
            weatherCode = 61,
        )
    }

    private fun factorsFor(
        species: github.naturewhisp.myco.model.MushroomSpecies = species("general"),
        habitatScore: Double,
        elevation: Float = 800f,
    ) = MushroomAlgorithms.calculateFactors(
        avgTemp = 18.0,
        totalRain = 25.0,
        avgHumidity = 80.0,
        habitatScore = habitatScore,
        habitatText = "Habitat: bosco misto",
        elevation = elevation,
        month = 8,
        growthPhaseText = "Fase deterministica",
        moon = MushroomAlgorithms.getMoonPhase(Date(947_182_440_000L)),
        slopeText = "Nord",
        species = species,
    )

    private data class CacheGoldenValue(val value: Int)

    private class TestKeyValueStorage : KeyValueStorage {
        private val values = mutableMapOf<String, Any?>()

        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
        override fun putString(key: String, value: String?) {
            if (value == null) values.remove(key) else values[key] = value
        }
        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
        override fun getAll(): Map<String, *> = values
    }

    private companion object {
        const val CONTINUOUS_TOLERANCE = 0.000_001
        const val FLOAT_TOLERANCE = 0.0001f
    }
}
