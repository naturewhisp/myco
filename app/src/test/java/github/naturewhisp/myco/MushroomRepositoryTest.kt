package github.naturewhisp.myco

import github.naturewhisp.myco.model.DailyData
import github.naturewhisp.myco.model.GeocodeResult
import github.naturewhisp.myco.model.HourlyData
import github.naturewhisp.myco.model.OverpassResponse
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.model.SpunData
import github.naturewhisp.myco.model.TerrainAspectData
import github.naturewhisp.myco.model.WeatherResponse
import github.naturewhisp.myco.platform.AssetProvider
import github.naturewhisp.myco.platform.InMemoryCacheStore
import github.naturewhisp.myco.platform.KeyValueStorage
import github.naturewhisp.myco.repository.CacheManager
import github.naturewhisp.myco.repository.MushroomRepository
import github.naturewhisp.myco.repository.SpunDataManager
import github.naturewhisp.myco.model.OverpassCenter
import github.naturewhisp.myco.model.OverpassElement
import github.naturewhisp.myco.network.OverpassService
import github.naturewhisp.myco.network.WeatherService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class MushroomRepositoryTest {

    private class TestKeyValueStorage : KeyValueStorage {
        private val map = mutableMapOf<String, Any?>()
        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
        override fun putString(key: String, value: String?) { if (value != null) map[key] = value else map.remove(key) }
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun putInt(key: String, value: Int) { map[key] = value }
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun putBoolean(key: String, value: Boolean) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun clear() { map.clear() }
        override fun getAll(): Map<String, *> = map
    }

    private lateinit var cacheManager: CacheManager
    private lateinit var spunDataManager: SpunDataManager
    private lateinit var repository: MushroomRepository

    @Before
    fun setUp() {
        val storage = TestKeyValueStorage()
        val cacheStore = InMemoryCacheStore()
        cacheManager = CacheManager(storage, cacheStore)

        val assetProvider = mockk<AssetProvider>()
        every { assetProvider.open(any()) } throws Exception("Asset not found in test")
        spunDataManager = SpunDataManager(assetProvider)

        repository = MushroomRepository(cacheManager, spunDataManager)
    }

    @Test
    fun testSearchLocationCacheHit() = runBlocking {
        val cachedResult = GeocodeResult(
            lat = "45.123",
            lon = "7.456",
            displayName = "Torino, Piemonte, Italia"
        )
        cacheManager.saveGeocodeCache("torino", com.google.gson.Gson().toJson(cachedResult))

        val result = repository.searchLocation("Torino")
        assertNotNull(result)
        assertEquals("45.123", result?.lat)
        assertEquals("7.456", result?.lon)
        assertEquals("Torino, Piemonte, Italia", result?.displayName)
    }

    @Test
    fun testFetchWeatherCacheHit() = runBlocking {
        val dummyResponse = WeatherResponse(
            elevation = 750f,
            timezone = "Europe/Rome",
            hourly = HourlyData(emptyList(), emptyList(), emptyList(), emptyList()),
            daily = DailyData(emptyList(), emptyList())
        )

        // Pre-popola la cache
        val cacheKey = "weather_44.5000_8.0000"
        cacheManager.saveCachedData(cacheKey, dummyResponse)

        val result = repository.fetchWeather(44.5, 8.0)
        assertNotNull(result)
        assertEquals(750f, result.elevation, 0.01f)
        assertEquals("Europe/Rome", result.timezone)
    }

    @Test
    fun testFetchTerrainAspectCacheHit() = runBlocking {
        val dummyTerrain = TerrainAspectData(
            centerElevation = 850f,
            slopeDegrees = 10f,
            slopePercent = 12.5f,
            aspectDegrees = 180f,
            cardinalDirection = "Sud",
            cardinalAbbreviation = "S",
            isFlat = false
        )
        val cacheKey = "terrain_45.1000_7.2000"
        cacheManager.saveCachedData(cacheKey, dummyTerrain)

        val result = repository.fetchTerrainAspect(45.1, 7.2)
        assertNotNull(result)
        assertEquals(12.5f, result?.slopePercent ?: 0f, 0.01f)
        assertEquals(180f, result?.aspectDegrees ?: 0f, 0.01f)
        assertEquals(850f, result?.centerElevation ?: 0f, 0.01f)
    }

    @Test
    fun testFetchSpecificHabitatBonus_CacheIsolationBySpecies() = runBlocking {
        val boletusEdulis = SPECIES_CATALOG.first { it.id == "boletus_edulis" }
        val macrolepiota = SPECIES_CATALOG.first { it.id == "macrolepiota_procera" }

        val dummyResponse = OverpassResponse(elements = emptyList())
        val boletusKey = "habitat_bonus_boletus_edulis_45.1000_7.2000"
        cacheManager.saveCachedData(boletusKey, dummyResponse)

        // Cache hit per Boletus edulis
        val boletusHit = repository.fetchSpecificHabitatBonus(45.1, 7.2, boletusEdulis)
        assertNotNull("Boletus edulis deve leggere la cache isolata", boletusHit)

        // Cache miss per Macrolepiota procera (chiave diversa, isolamento rigoroso della cache per specie)
        val macrolepiotaKey = "habitat_bonus_macrolepiota_procera_45.1000_7.2000"
        val cachedMacrolepiota = cacheManager.getCachedData(macrolepiotaKey, OverpassResponse::class.java, 24 * 60 * 60 * 1000)
        org.junit.Assert.assertNull("Macrolepiota non deve condividere la cache di Boletus edulis", cachedMacrolepiota)
    }

    @Test
    fun testFetchWeatherOfflineFallback_ReturnsExpiredCacheWhenServiceFails() = runBlocking {
        val weatherService = mockk<WeatherService>()
        coEvery { weatherService.getForecast(any(), any(), any(), any(), any(), any(), any()) } throws java.io.IOException("Network unavailable")

        val offlineRepo = MushroomRepository(
            cacheManager = cacheManager,
            spunDataManager = spunDataManager,
            weatherService = weatherService
        )

        val dummyResponse = WeatherResponse(
            elevation = 750f,
            timezone = "Europe/Rome",
            hourly = HourlyData(emptyList(), emptyList(), emptyList(), emptyList()),
            daily = DailyData(emptyList(), emptyList())
        )
        val cacheKey = "weather_44.5000_8.0000"
        cacheManager.saveCachedData(cacheKey, dummyResponse)

        val result = offlineRepo.fetchWeather(44.5, 8.0)
        assertNotNull("Deve restituire i dati meteo in cache anche se la rete fallisce", result)
        assertEquals(750f, result.elevation, 0.01f)
    }

    @Test
    fun testFindNearestForest_ReturnsClosestElementCoordinate() = runBlocking {
        val overpassService = mockk<OverpassService>()
        val elements = listOf(
            OverpassElement(
                type = "way",
                id = 1L,
                center = OverpassCenter(lat = 44.55, lon = 8.05)
            ),
            OverpassElement(
                type = "way",
                id = 2L,
                center = OverpassCenter(lat = 44.51, lon = 8.01)
            )
        )
        coEvery { overpassService.queryOverpass(any()) } returns OverpassResponse(elements = elements)

        val repo = MushroomRepository(
            cacheManager = cacheManager,
            spunDataManager = spunDataManager,
            overpassServices = listOf(overpassService)
        )

        val closest = repo.findNearestForest(44.50, 8.00)
        assertNotNull(closest)
        assertEquals(44.51, closest!!.first, 0.001)
        assertEquals(8.01, closest.second, 0.001)
    }
}
