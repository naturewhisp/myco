package github.naturewhisp.myco

import github.naturewhisp.myco.core.HabitatEvidence
import github.naturewhisp.myco.core.HabitatStatus
import github.naturewhisp.myco.model.DailyData
import github.naturewhisp.myco.model.FactorId
import github.naturewhisp.myco.model.HourlyData
import github.naturewhisp.myco.model.OverpassResponse
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.model.SavedLocation
import github.naturewhisp.myco.model.SpunData
import github.naturewhisp.myco.model.TerrainAspectData
import github.naturewhisp.myco.model.WeatherResponse
import github.naturewhisp.myco.platform.AiEngineStatus
import github.naturewhisp.myco.platform.AssetProvider
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
import kotlinx.coroutines.test.runCurrent
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

    @Test
    fun originalMindinoPayloadPassesRealCacheRepositoryMapperAndViewModel() = runTest(testDispatcher) {
        val fixtureRoot = java.io.File(requireNotNull(System.getProperty("myco.fixture.root")))
        val raw = java.io.File(fixtureRoot, "mindino/weather-original.json").readBytes()
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(Locale.US, it) }
        assertEquals("6fee1178c9ce95e15e2b4828ff74ed72d5256dcb90e49f8bdd91fb9b6b0b5467", hash)
        cacheManager.cacheStore.put("weather_44.2149_7.9755", raw.toString(Charsets.UTF_8), 44.2149, 7.9755, 0, 1791133963798L)
        val weatherService = mockk<github.naturewhisp.myco.network.WeatherService>()
        coEvery { weatherService.getForecast(any(), any()) } throws java.io.IOException("Offline replay")
        coEvery { weatherService.getElevation(any(), any()) } throws java.io.IOException("No historical DEM")
        val realRepository = MushroomRepository(cacheManager, spunDataManager, mockk(relaxed = true), weatherService, emptyList())
        val recovered = realRepository.fetchWeather(44.2149, 7.9755)
        val mapped = github.naturewhisp.myco.utils.MushroomAlgorithms.processWeatherData(recovered)
        assertTrue(mapped.any { it.date == "2026-09-29" && it.coverage!!.weatherUsable })
        val vm = MushroomViewModel(realRepository, cacheManager, localAiService, spunDataManager)
        vm.selectSpecies(SPECIES_CATALOG.first { it.id == "boletus_edulis" })
        vm.setHistoricalAnalysisDate("2026-09-29")
        vm.selectLocation(44.2149, 7.9755, "Mindino — ricostruzione aperta")
        vm.dataFetchJob?.join()
        advanceUntilIdle()
        assertTrue(vm.isCalculable)
        assertEquals("2026-09-29", vm.targetAnalysisDate)
        assertTrue(vm.factors.any { it.id == FactorId.SOIL_MOISTURE })
        assertTrue(vm.summaryText.contains("Indice di idoneità"))
        val before = vm.factors
        vm.selectSpecies(SPECIES_CATALOG.first { it.category == github.naturewhisp.myco.model.EcologicalCategory.PARASITIC })
        advanceUntilIdle()
        assertTrue(vm.isCalculable)
        assertTrue(before != vm.factors)
        coVerify(exactly = 2) { weatherService.getForecast(any(), any()) }
        val output = java.io.File(fixtureRoot.parentFile, "build/audit/2026-10-06/replay-production.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(mapOf(
            "classification" to "ricostruzione aperta", "rawSha256" to hash,
            "cacheTimestamp" to 1791133963798L, "coordinate" to listOf(44.2149, 7.9755),
            "target" to vm.targetAnalysisDate, "habitatScenario" to "unknown; original evidence unavailable",
            "mappedDays" to mapped, "initialFactors" to before, "selectedSpecies" to vm.selectedSpecies.id,
            "mode" to vm.calculationMode, "score" to vm.todayProbability, "factors" to vm.factors,
            "quality" to vm.dataQualityStatus, "note" to vm.summaryText, "outlooks" to vm.dailyOutlooks,
        )))
    }

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

        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Rome"))
        cal.add(java.util.Calendar.DAY_OF_YEAR, -14)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("Europe/Rome")
        }

        for (d in 0 until 21) {
            val dayStr = sdf.format(cal.time)
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
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
        every { repository.extractHabitatEvidence(any(), any(), any(), any()) } answers {
            val resp = firstArg<OverpassResponse?>()
            val lat = secondArg<Double>()
            val lon = thirdArg<Double>()
            val radius = runCatching { arg<Int>(3) }.getOrDefault(1500)
            if (resp == null) {
                HabitatEvidence.UNKNOWN_HABITAT
            } else {
                val rawElements = resp.elements.map { el ->
                    github.naturewhisp.myco.core.OsmHabitatElement(
                        lat = el.coordinate?.first,
                        lon = el.coordinate?.second,
                        isWoodOrForest = el.isWoodOrForest,
                        isMeadowOrGrass = el.isMeadowOrGrass,
                        isUrbanOrBuilt = el.isUrbanOrBuilt,
                        genus = el.genus,
                        leafType = el.leafType,
                    )
                }
                github.naturewhisp.myco.core.MycoAlgorithms.extractHabitatEvidence(
                    elements = rawElements,
                    targetLat = lat,
                    targetLon = lon,
                    searchRadiusMeters = radius
                )
            }
        }

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

    @Test
    fun testSelectLocation_freshNetworkCleansCacheState() = runTest(testDispatcher) {
        val lat = 44.2
        val lon = 7.9
        val rLat = String.format(Locale.US, "%.4f", lat)
        val rLon = String.format(Locale.US, "%.4f", lon)
        val weatherCacheKey = "weather_${rLat}_${rLon}"

        // Simula risposta fresca salvata al momento della risposta di rete (timestamp = now)
        cacheManager.saveCachedData(weatherCacheKey, createRealisticWeatherResponse(), timestamp = System.currentTimeMillis())

        viewModel.selectLocation(lat, lon, "Garessio")
        viewModel.dataFetchJob?.join()

        assertFalse("Dato fresco da rete non deve risultare da cache obsoleta", viewModel.isFromCache)
        org.junit.Assert.assertNull("cacheAgeText deve essere null su risposta fresca", viewModel.cacheAgeText)
        assertFalse("isOfflineFieldMode deve essere false su risposta fresca", viewModel.isOfflineFieldMode)
    }

    @Test
    fun testSelectLocation_offlineFallbackSetsOfflineFieldModeAndCacheAge() = runTest(testDispatcher) {
        val lat = 44.5
        val lon = 8.0
        val rLat = String.format(Locale.US, "%.4f", lat)
        val rLon = String.format(Locale.US, "%.4f", lon)
        val weatherCacheKey = "weather_${rLat}_${rLon}"
        val expiredTimestamp = System.currentTimeMillis() - 2 * 3600 * 1000L // 2 ore fa
        val cachedResponse = createRealisticWeatherResponse()
        cacheManager.saveCachedData(weatherCacheKey, cachedResponse, timestamp = expiredTimestamp)

        coEvery { repository.fetchWeather(lat, lon) } returns cachedResponse

        viewModel.selectLocation(lat, lon, "Località Offline")
        viewModel.dataFetchJob?.join()

        assertTrue("Dato da fallback offline deve impostare isFromCache = true", viewModel.isFromCache)
        assertTrue("Cache > 1h deve attivare la modalità campo isOfflineFieldMode = true", viewModel.isOfflineFieldMode)
        assertEquals("2h fa", viewModel.cacheAgeText)
    }

    @Test
    fun testClockCrossingMidnightAndMonthRolloverUpdatesTargetDate() = runTest(testDispatcher) {
        val zone = java.time.ZoneId.of("Europe/Rome")
        val instantOct31 = java.time.ZonedDateTime.of(2026, 10, 31, 23, 59, 0, 0, zone).toInstant()
        val mutableClock = object : java.time.Clock() {
            var currentInstant: java.time.Instant = instantOct31
            override fun getZone(): java.time.ZoneId = zone
            override fun withZone(z: java.time.ZoneId): java.time.Clock = this
            override fun instant(): java.time.Instant = currentInstant
        }

        // Custom weather series spanning Oct 20 to Nov 8
        val hourlyTimes = mutableListOf<String>()
        val dailyTimes = mutableListOf<String>()
        val codes = mutableListOf<Int?>()
        val temps = mutableListOf<Float>()
        val hums = mutableListOf<Float>()
        val precips = mutableListOf<Float>()
        val startCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Rome")).apply {
            set(2026, java.util.Calendar.OCTOBER, 20, 0, 0, 0)
        }
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("Europe/Rome")
        }
        for (i in 0 until 20) {
            val dStr = sdf.format(startCal.time)
            dailyTimes.add(dStr)
            codes.add(1)
            for (h in 0..23) {
                hourlyTimes.add(String.format(Locale.US, "%sT%02d:00", dStr, h))
                temps.add(15.0f)
                hums.add(80.0f)
                precips.add(1.0f)
            }
            startCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val multiDayWeather = WeatherResponse(
            elevation = 500f,
            timezone = "Europe/Rome",
            hourly = HourlyData(hourlyTimes, temps, hums, precips),
            daily = DailyData(dailyTimes, codes)
        )
        coEvery { repository.fetchWeather(any(), any()) } returns multiDayWeather

        val vm = MushroomViewModel(
            repository = repository,
            cacheManager = cacheManager,
            localAiService = localAiService,
            spunDataManager = spunDataManager,
            clock = mutableClock
        )

        vm.selectLocation(44.2, 7.9, "Garessio")
        vm.dataFetchJob?.join()
        advanceUntilIdle()

        assertEquals("2026-10-31", vm.targetAnalysisDate)

        // Advance clock across midnight to 2026-11-01 00:01
        mutableClock.currentInstant = java.time.ZonedDateTime.of(2026, 11, 1, 0, 1, 0, 0, zone).toInstant()

        // Trigger midnight check
        vm.checkDayChangeAndRefresh()
        vm.dataFetchJob?.join()
        advanceUntilIdle()

        assertEquals("2026-11-01", vm.targetAnalysisDate)

        // Calling it again on the same day should NOT trigger another refresh
        val jobBefore = vm.dataFetchJob
        vm.checkDayChangeAndRefresh()
        assertEquals(jobBefore, vm.dataFetchJob)
    }

    @Test
    fun testSpeciesChangeCancelsPendingAiJobAndPreventsLateNoteOverwrite() = runTest(testDispatcher) {
        val delayedAi = mockk<PlatformAiEngine>(relaxed = true)
        val aiStatusFlow = MutableStateFlow(AiEngineStatus.READY)
        every { delayedAi.status } returns aiStatusFlow
        every { delayedAi.isAvailable() } returns true

        // Simulate a slow AI call
        val aiDeferred = kotlinx.coroutines.CompletableDeferred<String?>()
        coEvery { delayedAi.generateAdvancedSummary(any()) } coAnswers {
            aiDeferred.await()
        }

        val vm = MushroomViewModel(
            repository = repository,
            cacheManager = cacheManager,
            localAiService = delayedAi,
            spunDataManager = spunDataManager
        )

        // Enable AI in settings
        vm.saveSettings(vm.mapStyle, vm.searchRadius, vm.highlightThreshold, true, true)

        vm.selectLocation(44.2, 7.9, "Garessio")
        vm.dataFetchJob?.join()

        // First species
        val initialNote = vm.summaryText
        assertTrue(vm.isAiLoading)

        // User switches species before AI completes
        val species2 = SPECIES_CATALOG[1] // Boletus edulis
        vm.selectSpecies(species2)

        val edulisNote = vm.summaryText
        // Complete the old AI deferred now with a style token
        aiDeferred.complete("taccuino")
        advanceUntilIdle()

        // The summaryText must NOT be overwritten by the old job
        assertFalse("Old AI response must not overwrite new species note", vm.summaryText.contains(initialNote) && initialNote != edulisNote)
    }

    @Test
    fun lateNonCooperativeAiCannotOverwriteLocationDateOrModeChanges() = runTest(testDispatcher) {
        for (change in listOf("location", "date", "mode")) {
            val oldResponse = kotlinx.coroutines.CompletableDeferred<String?>()
            var calls = 0
            val ai = mockk<PlatformAiEngine>(relaxed = true)
            every { ai.status } returns MutableStateFlow(AiEngineStatus.READY)
            every { ai.isAvailable() } returns true
            coEvery { ai.generateAdvancedSummary(any()) } coAnswers {
                calls++
                if (calls == 1) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { oldResponse.await() }
                else "unsupported-style"
            }
            val vm = MushroomViewModel(repository, cacheManager, ai, spunDataManager)
            vm.saveSettings(vm.mapStyle, vm.searchRadius, vm.highlightThreshold, true, true)
            vm.selectLocation(44.2, 7.9, "First")
            vm.dataFetchJob?.join()
            runCurrent()
            assertTrue(vm.isAiLoading)
            when (change) {
                "location" -> { vm.selectLocation(45.0, 8.0, "Second"); vm.dataFetchJob?.join() }
                "date" -> vm.setHistoricalAnalysisDate(java.time.LocalDate.now(java.time.ZoneId.of("Europe/Rome")).minusDays(1).toString())
                else -> vm.updateCalculationMode("WEATHER_ONLY")
            }
            advanceUntilIdle()
            val currentNote = vm.summaryText
            oldResponse.complete("taccuino")
            advanceUntilIdle()
            assertEquals("Late AI overwritten $change analysis", currentNote, vm.summaryText)
            assertFalse(vm.isAiLoading)
        }
    }

    @Test
    fun testMindinoReplayEndToEndThroughViewModel() = runTest(testDispatcher) {
        val lat = 44.2149
        val lon = 7.9755

        data class EmpiricalMindinoDay(
            val date: String,
            val avgTemp: Float,
            val rain: Float,
            val hum: Float,
            val code: Int,
            val soil07: Float,
            val soil728: Float,
            val et0: Float,
            val minTemp: Float,
            val maxTemp: Float
        )

        val empiricalDays = listOf(
            EmpiricalMindinoDay("2026-09-06", 22.9f, 0.0f, 64.4f, 1, 0.102f, 0.145f, 4.31f, 20.3f, 26.7f),
            EmpiricalMindinoDay("2026-09-07", 21.6f, 8.8f, 77.8f, 61, 0.108f, 0.145f, 3.37f, 18.2f, 26.7f),
            EmpiricalMindinoDay("2026-09-08", 20.9f, 2.8f, 70.5f, 61, 0.122f, 0.145f, 3.81f, 16.9f, 26.8f),
            EmpiricalMindinoDay("2026-09-09", 18.7f, 0.8f, 83.9f, 3, 0.178f, 0.145f, 2.13f, 14.5f, 22.6f),
            EmpiricalMindinoDay("2026-09-10", 15.9f, 22.3f, 83.5f, 61, 0.253f, 0.149f, 2.60f, 13.1f, 19.7f),
            EmpiricalMindinoDay("2026-09-11", 15.1f, 0.0f, 81.1f, 1, 0.277f, 0.148f, 2.49f, 10.8f, 19.8f),
            EmpiricalMindinoDay("2026-09-12", 17.1f, 0.0f, 68.4f, 1, 0.258f, 0.150f, 3.47f, 14.0f, 21.1f),
            EmpiricalMindinoDay("2026-09-13", 17.1f, 0.0f, 77.5f, 1, 0.245f, 0.151f, 2.63f, 13.8f, 21.9f),
            EmpiricalMindinoDay("2026-09-14", 19.2f, 0.0f, 69.8f, 1, 0.232f, 0.160f, 3.74f, 14.9f, 24.9f),
            EmpiricalMindinoDay("2026-09-15", 19.0f, 0.0f, 69.9f, 1, 0.215f, 0.165f, 2.90f, 16.4f, 22.5f),
            EmpiricalMindinoDay("2026-09-16", 18.3f, 0.2f, 82.5f, 2, 0.219f, 0.169f, 2.15f, 15.6f, 21.8f),
            EmpiricalMindinoDay("2026-09-17", 17.0f, 25.3f, 86.7f, 61, 0.236f, 0.169f, 1.50f, 15.0f, 19.6f),
            EmpiricalMindinoDay("2026-09-18", 16.0f, 0.0f, 85.3f, 1, 0.283f, 0.171f, 1.71f, 13.5f, 19.1f),
            EmpiricalMindinoDay("2026-09-19", 16.3f, 0.0f, 73.4f, 1, 0.288f, 0.184f, 2.92f, 12.2f, 20.6f),
            EmpiricalMindinoDay("2026-09-20", 17.4f, 0.0f, 71.8f, 1, 0.260f, 0.189f, 3.23f, 13.9f, 22.6f),
            EmpiricalMindinoDay("2026-09-21", 19.4f, 0.0f, 58.1f, 1, 0.236f, 0.189f, 3.67f, 16.6f, 24.1f),
            EmpiricalMindinoDay("2026-09-22", 18.3f, 0.0f, 64.5f, 1, 0.221f, 0.192f, 3.48f, 14.9f, 22.0f),
            EmpiricalMindinoDay("2026-09-23", 15.0f, 0.0f, 79.3f, 2, 0.199f, 0.179f, 1.40f, 13.3f, 16.8f),
            EmpiricalMindinoDay("2026-09-24", 16.0f, 0.0f, 79.1f, 1, 0.207f, 0.194f, 2.62f, 12.9f, 20.6f),
            EmpiricalMindinoDay("2026-09-25", 14.8f, 0.0f, 77.6f, 1, 0.220f, 0.206f, 2.44f, 11.6f, 18.3f),
            EmpiricalMindinoDay("2026-09-26", 14.4f, 0.0f, 81.8f, 1, 0.203f, 0.199f, 2.15f, 10.9f, 18.5f),
            EmpiricalMindinoDay("2026-09-27", 15.7f, 0.0f, 75.7f, 1, 0.198f, 0.201f, 2.76f, 12.0f, 20.2f),
            EmpiricalMindinoDay("2026-09-28", 16.5f, 0.0f, 75.5f, 1, 0.197f, 0.206f, 2.75f, 12.9f, 21.3f),
            EmpiricalMindinoDay("2026-09-29", 16.5f, 0.0f, 73.2f, 1, 0.191f, 0.206f, 2.61f, 14.0f, 21.0f)
        )

        val hourlyTimes = mutableListOf<String>()
        val dailyTimes = mutableListOf<String>()
        val codes = mutableListOf<Int?>()
        val temps = mutableListOf<Float>()
        val hums = mutableListOf<Float>()
        val precips = mutableListOf<Float>()
        val soil07 = mutableListOf<Float>()
        val soil728 = mutableListOf<Float>()
        val et0 = mutableListOf<Float>()

        for (d in empiricalDays) {
            dailyTimes.add(d.date)
            codes.add(d.code)
            val intermediateTemp = (d.avgTemp * 24f - d.minTemp - d.maxTemp) / 22f
            for (h in 0..23) {
                hourlyTimes.add(String.format(Locale.US, "%sT%02d:00", d.date, h))
                val hourTemp = when (h) {
                    0 -> d.minTemp
                    13 -> d.maxTemp
                    else -> intermediateTemp
                }
                temps.add(hourTemp)
                hums.add(d.hum)
                precips.add(d.rain / 24f)
                soil07.add(d.soil07)
                soil728.add(d.soil728)
                et0.add(d.et0 / 24f)
            }
        }
        val mindinoWeather = WeatherResponse(
            elevation = 902f,
            timezone = "Europe/Rome",
            hourly = HourlyData(hourlyTimes, temps, hums, precips, soil07, soil728, et0),
            daily = DailyData(dailyTimes, codes)
        )
        coEvery { repository.fetchWeather(lat, lon) } returns mindinoWeather
        every { repository.extractHabitatEvidence(any(), any(), any(), any()) } returns HabitatEvidence(
            status = HabitatStatus.KNOWN_SUITABLE,
            forestCoverFraction = 0.85,
            meadowFraction = 0.05,
            distanceToNearestForestMeters = 0.0,
            confirmedHostGenera = setOf("fagus", "castanea")
        )
        coEvery { repository.fetchTerrainAspect(lat, lon) } returns TerrainAspectData(
            centerElevation = 902f,
            slopeDegrees = 12f,
            slopePercent = 21.2f,
            aspectDegrees = 180f,
            cardinalDirection = "Sud",
            cardinalAbbreviation = "S",
            isFlat = false,
            rawElevations = listOf(902f, 915f, 890f, 905f, 898f)
        )
        coEvery { repository.fetchSpunData(lat, lon, any()) } returns SpunData(
            ecmRichness = 45f,
            hyphalDensity = 3.8f,
            ecmScore = 0.9,
            hyphalScore = 0.85,
            ecmText = "45 specie EcM",
            hyphalText = "3.8 m/cm³",
            regionCode = "ALP",
            regionName = "Alpi Liguri"
        )

        val zone = java.time.ZoneId.of("Europe/Rome")
        val fixedClock = java.time.Clock.fixed(
            java.time.ZonedDateTime.of(2026, 9, 29, 12, 0, 0, 0, zone).toInstant(),
            zone
        )

        val vm = MushroomViewModel(
            repository = repository,
            cacheManager = cacheManager,
            localAiService = localAiService,
            spunDataManager = spunDataManager,
            clock = fixedClock
        )

        val edulis = SPECIES_CATALOG.first { it.id == "boletus_edulis" }
        vm.selectSpecies(edulis)
        vm.selectLocation(lat, lon, "Monte Mindino")
        vm.dataFetchJob?.join()
        advanceUntilIdle()

        assertTrue(vm.isCalculable)
        assertEquals("2026-09-29", vm.targetAnalysisDate)
        assertTrue(vm.todayProbability in 1..100)
        assertTrue("Suitability score must be positive: ${vm.todaySuitabilityScore}", vm.todaySuitabilityScore > 0.0)

        // Verify both FOREST_PROXIMITY and HABITAT factors exist
        val hasForestProx = vm.factors.any { it.id == FactorId.FOREST_PROXIMITY }
        val hasHabitat = vm.factors.any { it.id == FactorId.HABITAT }
        assertTrue("Must include FOREST_PROXIMITY factor", hasForestProx)
        assertTrue("Must include HABITAT factor", hasHabitat)
    }

    @Test
    fun testMindinoReplayUnknownHabitatEndToEndThroughViewModel() = runTest(testDispatcher) {
        val lat = 44.2149
        val lon = 7.9755

        data class EmpiricalMindinoDay(
            val date: String,
            val avgTemp: Float,
            val rain: Float,
            val hum: Float,
            val code: Int,
            val soil07: Float,
            val soil728: Float,
            val et0: Float,
            val minTemp: Float,
            val maxTemp: Float
        )

        val empiricalDays = listOf(
            EmpiricalMindinoDay("2026-09-06", 22.9f, 0.0f, 64.4f, 1, 0.102f, 0.145f, 4.31f, 20.3f, 26.7f),
            EmpiricalMindinoDay("2026-09-07", 21.6f, 8.8f, 77.8f, 61, 0.108f, 0.145f, 3.37f, 18.2f, 26.7f),
            EmpiricalMindinoDay("2026-09-08", 20.9f, 2.8f, 70.5f, 61, 0.122f, 0.145f, 3.81f, 16.9f, 26.8f),
            EmpiricalMindinoDay("2026-09-09", 18.7f, 0.8f, 83.9f, 3, 0.178f, 0.145f, 2.13f, 14.5f, 22.6f),
            EmpiricalMindinoDay("2026-09-10", 15.9f, 22.3f, 83.5f, 61, 0.253f, 0.149f, 2.60f, 13.1f, 19.7f),
            EmpiricalMindinoDay("2026-09-11", 15.1f, 0.0f, 81.1f, 1, 0.277f, 0.148f, 2.49f, 10.8f, 19.8f),
            EmpiricalMindinoDay("2026-09-12", 17.1f, 0.0f, 68.4f, 1, 0.258f, 0.150f, 3.47f, 14.0f, 21.1f),
            EmpiricalMindinoDay("2026-09-13", 17.1f, 0.0f, 77.5f, 1, 0.245f, 0.151f, 2.63f, 13.8f, 21.9f),
            EmpiricalMindinoDay("2026-09-14", 19.2f, 0.0f, 69.8f, 1, 0.232f, 0.160f, 3.74f, 14.9f, 24.9f),
            EmpiricalMindinoDay("2026-09-15", 19.0f, 0.0f, 69.9f, 1, 0.215f, 0.165f, 2.90f, 16.4f, 22.5f),
            EmpiricalMindinoDay("2026-09-16", 18.3f, 0.2f, 82.5f, 2, 0.219f, 0.169f, 2.15f, 15.6f, 21.8f),
            EmpiricalMindinoDay("2026-09-17", 17.0f, 25.3f, 86.7f, 61, 0.236f, 0.169f, 1.50f, 15.0f, 19.6f),
            EmpiricalMindinoDay("2026-09-18", 16.0f, 0.0f, 85.3f, 1, 0.283f, 0.171f, 1.71f, 13.5f, 19.1f),
            EmpiricalMindinoDay("2026-09-19", 16.3f, 0.0f, 73.4f, 1, 0.288f, 0.184f, 2.92f, 12.2f, 20.6f),
            EmpiricalMindinoDay("2026-09-20", 17.4f, 0.0f, 71.8f, 1, 0.260f, 0.189f, 3.23f, 13.9f, 22.6f),
            EmpiricalMindinoDay("2026-09-21", 19.4f, 0.0f, 58.1f, 1, 0.236f, 0.189f, 3.67f, 16.6f, 24.1f),
            EmpiricalMindinoDay("2026-09-22", 18.3f, 0.0f, 64.5f, 1, 0.221f, 0.192f, 3.48f, 14.9f, 22.0f),
            EmpiricalMindinoDay("2026-09-23", 15.0f, 0.0f, 79.3f, 2, 0.199f, 0.179f, 1.40f, 13.3f, 16.8f),
            EmpiricalMindinoDay("2026-09-24", 16.0f, 0.0f, 79.1f, 1, 0.207f, 0.194f, 2.62f, 12.9f, 20.6f),
            EmpiricalMindinoDay("2026-09-25", 14.8f, 0.0f, 77.6f, 1, 0.220f, 0.206f, 2.44f, 11.6f, 18.3f),
            EmpiricalMindinoDay("2026-09-26", 14.4f, 0.0f, 81.8f, 1, 0.203f, 0.199f, 2.15f, 10.9f, 18.5f),
            EmpiricalMindinoDay("2026-09-27", 15.7f, 0.0f, 75.7f, 1, 0.198f, 0.201f, 2.76f, 12.0f, 20.2f),
            EmpiricalMindinoDay("2026-09-28", 16.5f, 0.0f, 75.5f, 1, 0.197f, 0.206f, 2.75f, 12.9f, 21.3f),
            EmpiricalMindinoDay("2026-09-29", 16.5f, 0.0f, 73.2f, 1, 0.191f, 0.206f, 2.61f, 14.0f, 21.0f)
        )

        val hourlyTimes = mutableListOf<String>()
        val dailyTimes = mutableListOf<String>()
        val codes = mutableListOf<Int?>()
        val temps = mutableListOf<Float>()
        val hums = mutableListOf<Float>()
        val precips = mutableListOf<Float>()
        val soil07 = mutableListOf<Float>()
        val soil728 = mutableListOf<Float>()
        val et0 = mutableListOf<Float>()

        for (d in empiricalDays) {
            dailyTimes.add(d.date)
            codes.add(d.code)
            val intermediateTemp = (d.avgTemp * 24f - d.minTemp - d.maxTemp) / 22f
            for (h in 0..23) {
                hourlyTimes.add(String.format(Locale.US, "%sT%02d:00", d.date, h))
                val hourTemp = when (h) {
                    0 -> d.minTemp
                    13 -> d.maxTemp
                    else -> intermediateTemp
                }
                temps.add(hourTemp)
                hums.add(d.hum)
                precips.add(d.rain / 24f)
                soil07.add(d.soil07)
                soil728.add(d.soil728)
                et0.add(d.et0 / 24f)
            }
        }
        val mindinoWeather = WeatherResponse(
            elevation = 902f,
            timezone = "Europe/Rome",
            hourly = HourlyData(hourlyTimes, temps, hums, precips, soil07, soil728, et0),
            daily = DailyData(dailyTimes, codes)
        )
        coEvery { repository.fetchWeather(lat, lon) } returns mindinoWeather
        every { repository.extractHabitatEvidence(any(), any(), any(), any()) } returns HabitatEvidence.UNKNOWN_HABITAT
        coEvery { repository.fetchTerrainAspect(lat, lon) } returns TerrainAspectData(
            centerElevation = 902f,
            slopeDegrees = 12f,
            slopePercent = 21.2f,
            aspectDegrees = 180f,
            cardinalDirection = "Sud",
            cardinalAbbreviation = "S",
            isFlat = false,
            rawElevations = listOf(902f, 915f, 890f, 905f, 898f)
        )
        coEvery { repository.fetchSpunData(lat, lon, any()) } returns null

        val zone = java.time.ZoneId.of("Europe/Rome")
        val fixedClock = java.time.Clock.fixed(
            java.time.ZonedDateTime.of(2026, 9, 29, 12, 0, 0, 0, zone).toInstant(),
            zone
        )

        val vm = MushroomViewModel(
            repository = repository,
            cacheManager = cacheManager,
            localAiService = localAiService,
            spunDataManager = spunDataManager,
            clock = fixedClock
        )

        val edulis = SPECIES_CATALOG.first { it.id == "boletus_edulis" }
        vm.selectSpecies(edulis)
        vm.selectLocation(lat, lon, "Monte Mindino")
        vm.dataFetchJob?.join()
        advanceUntilIdle()

        assertTrue(vm.isCalculable)
        assertEquals("2026-09-29", vm.targetAnalysisDate)
        assertTrue("Unknown habitat must yield neutral suitability (20-45), got ${vm.todayProbability}", vm.todayProbability in 20..45)
    }
}
