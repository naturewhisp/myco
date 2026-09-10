package github.naturewhisp.myco

import github.naturewhisp.myco.model.DailyData
import github.naturewhisp.myco.model.FactorId
import github.naturewhisp.myco.model.HourlyData
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.model.WeatherResponse
import github.naturewhisp.myco.platform.AiEngineStatus
import github.naturewhisp.myco.platform.AssetProvider
import github.naturewhisp.myco.model.SavedLocation
import github.naturewhisp.myco.platform.InMemoryCacheStore
import github.naturewhisp.myco.platform.KeyValueStorage
import github.naturewhisp.myco.platform.PlatformAiEngine
import github.naturewhisp.myco.repository.CacheManager
import github.naturewhisp.myco.repository.MushroomRepository
import github.naturewhisp.myco.repository.SpunDataManager
import github.naturewhisp.myco.ui.viewmodel.MushroomViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class MushroomViewModelTest {

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

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var cacheManager: CacheManager
    private lateinit var spunDataManager: SpunDataManager
    private lateinit var repository: MushroomRepository
    private lateinit var localAiService: PlatformAiEngine
    private lateinit var viewModel: MushroomViewModel

    private fun createRealisticWeatherResponse(): WeatherResponse {
        val hourlyTimes = mutableListOf<String>()
        val temps = mutableListOf<Float>()
        val hums = mutableListOf<Float>()
        val precips = mutableListOf<Float>()
        val dailyTimes = mutableListOf<String>()
        val codes = mutableListOf<Int?>()

        for (d in 1..21) {
            val dayStr = String.format(Locale.US, "2026-09-%02d", d)
            dailyTimes.add(dayStr)
            codes.add(1)
            for (h in 0..23) {
                hourlyTimes.add(String.format(Locale.US, "%sT%02d:00", dayStr, h))
                temps.add(18.0f)
                hums.add(75.0f)
                precips.add(2.0f)
            }
        }

        return WeatherResponse(
            elevation = 750f,
            timezone = "Europe/Rome",
            hourly = HourlyData(hourlyTimes, temps, hums, precips),
            daily = DailyData(dailyTimes, codes)
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val storage = TestKeyValueStorage()
        val cacheStore = InMemoryCacheStore()
        cacheManager = CacheManager(storage, cacheStore)

        val assetProvider = mockk<AssetProvider>()
        every { assetProvider.open(any()) } throws Exception("No asset")
        spunDataManager = SpunDataManager(assetProvider)

        repository = mockk(relaxed = true)
        localAiService = mockk(relaxed = true)
        val aiStatusFlow = MutableStateFlow(AiEngineStatus.READY)
        every { localAiService.status } returns aiStatusFlow
        every { localAiService.isAvailable() } returns false

        coEvery { repository.fetchWeather(any(), any()) } returns createRealisticWeatherResponse()
        coEvery { repository.fetchHabitat(any(), any()) } returns null
        coEvery { repository.fetchSpecificHabitatBonus(any(), any(), any()) } returns null
        coEvery { repository.fetchSpunData(any(), any(), any()) } returns null
        coEvery { repository.fetchTerrainAspect(any(), any()) } returns null
        coEvery { repository.reverseGeocode(any(), any()) } returns null

        viewModel = MushroomViewModel(
            repository = repository,
            cacheManager = cacheManager,
            localAiService = localAiService,
            spunDataManager = spunDataManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() {
        assertEquals(SPECIES_CATALOG.first().id, viewModel.selectedSpecies.id)
        assertFalse(viewModel.isLoading)
        assertEquals("standard", viewModel.mapStyle)
        assertEquals(1500, viewModel.searchRadius)
        assertEquals(65, viewModel.highlightThreshold)
    }

    @Test
    fun testSelectSpeciesUpdatesSelection() {
        val targetSpecies = SPECIES_CATALOG[1] // "boletus_edulis"
        viewModel.selectSpecies(targetSpecies)

        assertEquals("boletus_edulis", viewModel.selectedSpecies.id)
    }

    @Test
    fun testToggleCurrentFavorite() = runTest(testDispatcher) {
        assertFalse(viewModel.currentLocationIsFavorite)

        // Seleziona località
        viewModel.selectLocation(45.123, 7.456, "Valle di Susa")
        advanceUntilIdle()

        viewModel.toggleCurrentFavorite()
        assertTrue(viewModel.currentLocationIsFavorite)
        assertEquals(1, cacheManager.getFavoriteLocations().size)

        // Toggle di rimozione
        viewModel.toggleCurrentFavorite()
        assertFalse(viewModel.currentLocationIsFavorite)
        assertEquals(0, cacheManager.getFavoriteLocations().size)
    }

    @Test
    fun testSelectLocationConcurrencyCancellation() = runTest(testDispatcher) {
        // Tap rapido su Garessio, poi subito dopo su Ormea
        viewModel.selectLocation(44.0, 7.0, "Garessio")
        viewModel.selectLocation(45.0, 8.0, "Ormea")

        // La località corrente deve riflettere immediatamente Ormea
        assertEquals(Pair(45.0, 8.0), viewModel.selectedLatLng)
        assertEquals("Ormea", viewModel.locationName)

        viewModel.dataFetchJob?.join()
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun testClearCacheInViewModelPreservesSettings() {
        viewModel.saveSettings(
            style = "satellite",
            radius = 3000,
            threshold = 80,
            cacheActive = true,
            useLocalAiActive = true
        )

        cacheManager.saveCachedData("some_network_call", "payload")
        viewModel.clearCache()

        assertEquals("satellite", viewModel.mapStyle)
        assertEquals(3000, viewModel.searchRadius)
        assertEquals(80, viewModel.highlightThreshold)
        assertEquals("Vuota", viewModel.cacheSize)
    }

    @Test
    fun testSelectSpecies_RecalculatesFactorsAndLaunchesHeatmapJob() = runTest(testDispatcher) {
        viewModel.selectLocation(44.2, 7.9, "Garessio")
        viewModel.dataFetchJob?.join()
        assertFalse(viewModel.isLoading)

        val edulis = SPECIES_CATALOG.first { it.id == "boletus_edulis" }
        viewModel.selectSpecies(edulis)
        assertEquals("boletus_edulis", viewModel.selectedSpecies.id)
        viewModel.heatmapJob?.join()

        // Switch a Macrolepiota procera (saprofita da prato)
        val macrolepiota = SPECIES_CATALOG.first { it.id == "macrolepiota_procera" }
        viewModel.selectSpecies(macrolepiota)
        assertEquals("macrolepiota_procera", viewModel.selectedSpecies.id)
        viewModel.heatmapJob?.join()

        // Verifica che i fattori e le descrizioni siano aggiornati per la specie saprofita
        val habFactor = viewModel.factors.firstOrNull { it.id == FactorId.HABITAT }
        assertNotNull("Fattore HABITAT deve essere presente", habFactor)
        assertEquals("Idoneità suolo/margine", habFactor!!.label)
    }

    @Test
    fun testSnapToClosestCoverage_SelectsCalculatedStation() = runTest(testDispatcher) {
        viewModel.closestCoverageLatLng = Pair(45.7969, 6.9678)
        viewModel.closestCoverageName = "Courmayeur / Val Veny"
        viewModel.snapToClosestCoverage()
        advanceUntilIdle()

        assertEquals(Pair(45.7969, 6.9678), viewModel.selectedLatLng)
        assertEquals("Courmayeur / Val Veny", viewModel.locationName)
    }

    @Test
    fun testSnapToNearestForest_UpdatesSelectedLocationWhenForestFound() = runTest(testDispatcher) {
        coEvery { repository.findNearestForest(44.2, 7.9) } returns Pair(44.21, 7.91)
        viewModel.selectLocation(44.2, 7.9, "Pianura")
        advanceUntilIdle()

        viewModel.snapToNearestForest()
        advanceUntilIdle()

        assertEquals(Pair(44.21, 7.91), viewModel.selectedLatLng)
    }

    @Test
    fun testPrefetchForOfflineUse_PreloadsFavorites() = runTest(testDispatcher) {
        cacheManager.addFavorite(SavedLocation(lat = 44.5, lon = 8.0, displayName = "Bosco 1", shortName = "Bosco 1"))
        cacheManager.addFavorite(SavedLocation(lat = 45.0, lon = 7.5, displayName = "Bosco 2", shortName = "Bosco 2"))

        var prefetchResultCount = 0
        viewModel.prefetchForOfflineUse { count ->
            prefetchResultCount = count
        }
        advanceUntilIdle()

        assertEquals(2, prefetchResultCount)
        assertFalse(viewModel.isPrefetchingOffline)
        coVerify(exactly = 1) { repository.prefetchCompleteLocation(44.5, 8.0, any()) }
        coVerify(exactly = 1) { repository.prefetchCompleteLocation(45.0, 7.5, any()) }
    }
}
