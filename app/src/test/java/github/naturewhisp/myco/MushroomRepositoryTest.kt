package github.naturewhisp.myco

import github.naturewhisp.myco.model.DailyData
import github.naturewhisp.myco.model.GeocodeResult
import github.naturewhisp.myco.model.HourlyData
import github.naturewhisp.myco.model.SpunData
import github.naturewhisp.myco.model.TerrainAspectData
import github.naturewhisp.myco.model.WeatherResponse
import github.naturewhisp.myco.platform.AssetProvider
import github.naturewhisp.myco.platform.InMemoryCacheStore
import github.naturewhisp.myco.platform.KeyValueStorage
import github.naturewhisp.myco.repository.CacheManager
import github.naturewhisp.myco.repository.MushroomRepository
import github.naturewhisp.myco.repository.SpunDataManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.InputStream

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
}
