package github.naturewhisp.myco

import github.naturewhisp.myco.model.SavedLocation
import github.naturewhisp.myco.platform.InMemoryCacheStore
import github.naturewhisp.myco.platform.KeyValueStorage
import github.naturewhisp.myco.repository.CacheManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CacheManagerTest {

    private class TestKeyValueStorage : KeyValueStorage {
        private val map = mutableMapOf<String, Any?>()

        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
        override fun putString(key: String, value: String?) {
            if (value != null) map[key] = value else map.remove(key)
        }
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun putInt(key: String, value: Int) { map[key] = value }
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun putBoolean(key: String, value: Boolean) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun clear() { map.clear() }
        override fun getAll(): Map<String, *> = map
    }

    private lateinit var storage: TestKeyValueStorage
    private lateinit var cacheStore: InMemoryCacheStore
    private lateinit var cacheManager: CacheManager

    @Before
    fun setUp() {
        storage = TestKeyValueStorage()
        cacheStore = InMemoryCacheStore()
        cacheManager = CacheManager(storage, cacheStore)
    }

    @Test
    fun testCachePutAndGet() {
        data class SampleData(val temp: Double, val status: String)

        val sample = SampleData(18.5, "Optimal")
        cacheManager.saveCachedData("sample_key", sample)

        val retrieved = cacheManager.getCachedData("sample_key", SampleData::class.java, 10_000L)
        assertNotNull(retrieved)
        assertEquals(18.5, retrieved?.temp ?: 0.0, 0.01)
        assertEquals("Optimal", retrieved?.status)
    }

    @Test
    fun testCacheExpiry() {
        data class TempData(val v: Int)
        cacheManager.saveCachedData("expiring_key", TempData(42))

        // Con expiryMs = 0 (scadenza immediata), deve restituire null
        val expired = cacheManager.getCachedData("expiring_key", TempData::class.java, 0L)
        assertNull(expired)
    }

    @Test
    fun testClearCachePreservesPreferencesAndFavorites() {
        // 1. Configurazione preferenze utente e sicurezza
        cacheManager.mapStyle = "satellite"
        cacheManager.radius = 2500
        cacheManager.threshold = 75
        cacheManager.cacheEnabled = true
        cacheManager.useLocalAi = true
        cacheManager.isSafetyDisclaimerAccepted = true

        val favorite = SavedLocation(
            lat = 44.2149,
            lon = 7.9755,
            displayName = "Garessio Bosco",
            shortName = "Garessio",
            savedAt = 1000L,
            isFavorite = true
        )
        cacheManager.addFavorite(favorite)
        cacheManager.saveRecentLocation(favorite)

        // 2. Aggiunta dati effimeri di rete nella cache
        cacheManager.saveCachedData("weather_44.2149_7.9755", "heavy_weather_json_payload")
        cacheManager.saveGeocodeCache("garessio", "{\"lat\":44.2149}")

        assertEquals(1, cacheManager.getFavoriteLocations().size)
        assertEquals(1, cacheManager.getRecentLocations().size)
        assertNotNull(cacheManager.getGeocodeCache("garessio"))

        // 3. Esecuzione clearCache() (Risoluzione definitiva TD-18)
        cacheManager.clearCache()

        // 4. Verifica: la cache di rete è azzerata...
        assertNull(cacheManager.getGeocodeCache("garessio"))
        assertNull(cacheManager.getCachedData("weather_44.2149_7.9755", String::class.java, 60_000L))
        assertEquals("Vuota", cacheManager.getCacheSizeString())

        // ...MA tutte le preferenze utente, i preferiti e il disclaimer di sicurezza sono intatti!
        assertEquals("satellite", cacheManager.mapStyle)
        assertEquals(2500, cacheManager.radius)
        assertEquals(75, cacheManager.threshold)
        assertTrue(cacheManager.cacheEnabled)
        assertTrue(cacheManager.useLocalAi)
        assertTrue(cacheManager.isSafetyDisclaimerAccepted)
        assertEquals(1, cacheManager.getFavoriteLocations().size)
        assertEquals(1, cacheManager.getRecentLocations().size)
    }

    @Test
    fun testCacheStatsReporting() {
        assertEquals("Vuota", cacheManager.getCacheSizeString())

        cacheManager.saveCachedData("entry1", "abcdefghij")
        val sizeStr = cacheManager.getCacheSizeString()
        assertTrue(sizeStr.contains("1 elementi"))
    }

    @Test
    fun testWeatherCacheAge() {
        val lat = 45.0
        val lon = 7.0
        cacheManager.saveCachedData("weather_45.0000_7.0000", "some_data")

        val age = cacheManager.getWeatherCacheAge(lat, lon)
        assertNotNull(age)
        assertTrue(age!! >= 0)
    }
}
