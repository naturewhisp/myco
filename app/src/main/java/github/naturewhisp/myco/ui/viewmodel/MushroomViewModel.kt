package github.naturewhisp.myco.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import github.naturewhisp.myco.model.DailyOutlook
import github.naturewhisp.myco.model.Factor
import github.naturewhisp.myco.model.GeocodeResult
import github.naturewhisp.myco.platform.android.HeatmapData
import github.naturewhisp.myco.model.MushroomSpecies
import github.naturewhisp.myco.model.PlaceName
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.model.SavedLocation
import github.naturewhisp.myco.model.SpunData
import github.naturewhisp.myco.model.TerrainAspectData
import github.naturewhisp.myco.model.TerrainAspectEvaluation
import github.naturewhisp.myco.platform.AiEngineStatus
import github.naturewhisp.myco.platform.DeviceHeading
import github.naturewhisp.myco.platform.MapOrientationMode
import github.naturewhisp.myco.platform.PlatformAiEngine
import github.naturewhisp.myco.platform.PlatformLocationProvider
import github.naturewhisp.myco.platform.PlatformOrientationProvider
import github.naturewhisp.myco.platform.UserLocation
import github.naturewhisp.myco.repository.CacheManager
import github.naturewhisp.myco.repository.MushroomRepository
import github.naturewhisp.myco.repository.SpunDataManager
import github.naturewhisp.myco.ui.theme.ThemeMode
import github.naturewhisp.myco.ui.theme.ThemePreference
import github.naturewhisp.myco.utils.HeatmapGenerator
import github.naturewhisp.myco.utils.MoonPhaseResult
import github.naturewhisp.myco.utils.MushroomAlgorithms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ViewModel centrale dell'applicazione Myco secondo l'architettura MVVM.
 *
 * Coordina lo stato reattivo Compose per:
 * - Ricerca geocoding e cronologia posizioni salvate.
 * - Aggregazione dati meteorologici storici e previsionali tramite [MushroomRepository].
 * - Interrogazione dati pedologici e micorrizici globali SPUN ([SpunDataManager]).
 * - Calcolo probabilistico ecologico continuo ([MushroomAlgorithms]) e raster termico ([HeatmapGenerator]).
 * - Interazione con l'engine di intelligenza artificiale locale on-device ([PlatformAiEngine]).
 * - Ricezione live della posizione geografica ([PlatformLocationProvider]) e della bussola ([PlatformOrientationProvider]).
 *
 * @param repository Repository per l'accesso alle API remote (Open-Meteo, Overpass, Nominatim).
 * @param cacheManager Gestore dello stato di persistenza delle preferenze utente e cache HTTP.
 * @param localAiService Engine astratto per la generazione delle risposte AI locali.
 * @param spunDataManager Gestore del dataset micorrizico globale SPUN.
 * @param themePreference Gestore della preferenza del tema visivo (Chiaro, Scuro, Sistema).
 * @param locationProvider Provider astratto per la geolocalizzazione live.
 * @param orientationProvider Provider astratto per la bussola e l'orientamento del dispositivo.
 */
class MushroomViewModel(
    private val repository: MushroomRepository,
    val cacheManager: CacheManager,
    val localAiService: PlatformAiEngine,
    val spunDataManager: SpunDataManager,
    val themePreference: ThemePreference? = null,
    val locationProvider: PlatformLocationProvider? = null,
    val orientationProvider: PlatformOrientationProvider? = null
) : ViewModel() {

    // Settings State
    var mapStyle by mutableStateOf(cacheManager.mapStyle)
        private set
    var searchRadius by mutableStateOf(cacheManager.radius)
        private set
    var highlightThreshold by mutableStateOf(cacheManager.threshold)
        private set
    var cacheEnabled by mutableStateOf(cacheManager.cacheEnabled)
        private set
    var useLocalAi by mutableStateOf(cacheManager.useLocalAi)
        private set
    var aiStatusText by mutableStateOf("Verifica in corso...")
        private set
    var cacheSize by mutableStateOf("Vuota")
        private set

    // UI States
    var searchQuery by mutableStateOf("")
    var isLoading by mutableStateOf(false)
        private set
    var isAiLoading by mutableStateOf(false)
        private set
    var isFromCache by mutableStateOf(false)
        private set
    var loadingText by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var showMap by mutableStateOf(false)
        private set
    var showSettings by mutableStateOf(false)

    // Saved locations state
    var recentLocations by mutableStateOf<List<SavedLocation>>(emptyList())
        private set
    var favoriteLocations by mutableStateOf<List<SavedLocation>>(emptyList())
        private set
    var currentLocationIsFavorite by mutableStateOf(false)
        private set
    var currentLocationIsGps by mutableStateOf(false)
        private set
    var editingFavoriteLocation by mutableStateOf<SavedLocation?>(null)
        private set
    var cacheAgeText by mutableStateOf<String?>(null)
        private set

    private var aiJob: Job? = null

    // Results States
    var locationName by mutableStateOf("")
        private set
    var selectedLatLng by mutableStateOf<Pair<Double, Double>?>(null)
        private set
    var todayProbability by mutableStateOf(0)
        private set
    var growthPhase by mutableStateOf("")
        private set
    var habitatText by mutableStateOf("")
        private set
    var habitatBonusText by mutableStateOf("")
        private set
    var altitudeText by mutableStateOf("")
        private set
    var seasonText by mutableStateOf("")
        private set
    var rainText by mutableStateOf("")
        private set
    var tempText by mutableStateOf("")
        private set
    var moonPhaseText by mutableStateOf("")
        private set
    var moonPhaseEmoji by mutableStateOf("")
        private set
    var slopeText by mutableStateOf("")
        private set
    var summaryText by mutableStateOf("")
        private set
    var spunEcmText by mutableStateOf("")
        private set
    var spunHyphalText by mutableStateOf("")
        private set
    var spunRegionName by mutableStateOf<String?>(null)
        private set
    var spunDataAvailable by mutableStateOf(false)
        private set
    var showHeatmap by mutableStateOf(true)
        private set
    var heatmapData by mutableStateOf<HeatmapData?>(null)
        private set
    var forecastDays by mutableStateOf<List<ProcessedDay>>(emptyList())
        private set

    // Nuovi stati Herbarium (Specie bersaglio, fattori tipizzati, previsioni unificate)
    var selectedSpecies by mutableStateOf<MushroomSpecies>(SPECIES_CATALOG[0])
        private set
    var factors by mutableStateOf<List<Factor>>(emptyList())
        private set
    var dailyOutlooks by mutableStateOf<List<DailyOutlook>>(emptyList())
        private set
    var placeName by mutableStateOf<PlaceName?>(null)
        private set
    var isOutsideHabitat by mutableStateOf(false)
        private set
    var isOutsideCoverage by mutableStateOf(false)
        private set
    var targetSpeciesSheetOpen by mutableStateOf(false)
        private set
    var currentScreenTab by mutableStateOf(0)
        private set
    var calculationMode by mutableStateOf("UNIFIED")
        private set

    // Navigazione da campo e orientamento mappa (100% puro Kotlin, agnostico da piattaforma)
    var userLocation by mutableStateOf<UserLocation?>(null)
        private set
    var deviceHeading by mutableStateOf<DeviceHeading?>(null)
        private set
    var mapOrientationMode by mutableStateOf(MapOrientationMode.NORTH_UP)
        private set
    var isMapCenteredOnUser by mutableStateOf(false)
        private set
    var centerOnPointTrigger by mutableStateOf(0)
        private set

    val isCompassSupported: Boolean
        get() = orientationProvider?.isSupported() ?: false

    val mapRotationDegrees: Float
        get() = when (mapOrientationMode) {
            MapOrientationMode.NORTH_UP -> 0f
            MapOrientationMode.HEADING_UP -> {
                val azimuth = deviceHeading?.azimuthDegrees ?: 0f
                (360f - azimuth) % 360f
            }
        }

    private var locationTrackingJob: Job? = null
    private var orientationTrackingJob: Job? = null

    fun startLocationAndOrientationTracking() {
        if (locationTrackingJob == null && locationProvider != null) {
            locationTrackingJob = viewModelScope.launch {
                locationProvider.locationUpdates().collect { loc ->
                    userLocation = loc
                }
            }
        }
        if (orientationTrackingJob == null && orientationProvider != null && orientationProvider.isSupported()) {
            orientationTrackingJob = viewModelScope.launch {
                orientationProvider.headingUpdates().collect { heading ->
                    deviceHeading = heading
                }
            }
        }
    }

    fun stopLocationAndOrientationTracking() {
        locationTrackingJob?.cancel()
        locationTrackingJob = null
        orientationTrackingJob?.cancel()
        orientationTrackingJob = null
    }

    fun centerMapOnUser() {
        if (userLocation != null) {
            isMapCenteredOnUser = true
            mapOrientationMode = MapOrientationMode.NORTH_UP
        }
    }

    fun centerMapOnSelectedPoint() {
        if (selectedLatLng != null) {
            isMapCenteredOnUser = false
            mapOrientationMode = MapOrientationMode.NORTH_UP
            centerOnPointTrigger++
        }
    }

    fun onMapDraggedByUser() {
        isMapCenteredOnUser = false
        mapOrientationMode = MapOrientationMode.NORTH_UP
    }

    fun resetToNorthUp() {
        mapOrientationMode = MapOrientationMode.NORTH_UP
    }

    fun toggleMapOrientationMode() {
        if (!isCompassSupported) {
            mapOrientationMode = MapOrientationMode.NORTH_UP
            return
        }
        mapOrientationMode = when (mapOrientationMode) {
            MapOrientationMode.NORTH_UP -> MapOrientationMode.HEADING_UP
            MapOrientationMode.HEADING_UP -> MapOrientationMode.NORTH_UP
        }
    }

    // Cache parametri correnti per ricalibrazione dinamica istantanea
    private var lastProcessedDays: List<ProcessedDay>? = null
    private var lastFinalHabitatScore: Double = 1.0
    private var lastHabitatBaseText: String = ""
    private var lastElevation: Float = 800f
    private var lastSpunData: SpunData? = null
    private var lastLat: Double = 41.8902
    private var lastLon: Double = 12.4922
    private var lastDisplayName: String = ""
    private var lastGrowthPhaseVal: String = ""
    private var lastMoonPhase: MoonPhaseResult? = null
    private var lastSlopeTextVal: String = ""
    private var lastTerrainData: TerrainAspectData? = null
    private var lastTerrainEvaluation: TerrainAspectEvaluation? = null
    private var lastCurrentMonth: Int = 8

    fun toggleHeatmap() {
        showHeatmap = !showHeatmap
    }

    fun selectSpecies(species: MushroomSpecies) {
        selectedSpecies = species
        recalculateForSpecies()
    }

    fun setTargetSpeciesSheetVisibility(open: Boolean) {
        targetSpeciesSheetOpen = open
    }

    fun setScreenTab(tab: Int) {
        currentScreenTab = tab
    }

    fun updateCalculationMode(mode: String) {
        calculationMode = mode
        recalculateForSpecies()
    }

    fun recalculateForSpecies() {
        val days = lastProcessedDays ?: return
        val species = selectedSpecies
        val todayIndex = 14
        if (days.size <= todayIndex) return

        val altScore = MushroomAlgorithms.calculateSpeciesAltitudeScore(lastElevation, species)
        val seasonScore = MushroomAlgorithms.calculateSpeciesSeasonalityScore(lastCurrentMonth, species)
        val rawWeatherScore = MushroomAlgorithms.calculateWeatherScore(
            todayIndex,
            days,
            lastSpunData?.hyphalDensity,
            species
        )

        val habScore = if (calculationMode == "WEATHER_ONLY") 1.0 else lastFinalHabitatScore
        val altMult = if (calculationMode == "WEATHER_ONLY") 1.0 else altScore.score
        val seasonMult = if (calculationMode == "WEATHER_ONLY") 1.0 else seasonScore.score

        val moon = lastMoonPhase ?: MushroomAlgorithms.getMoonPhase()
        val tempWindow = if (todayIndex >= 5) days.subList(todayIndex - 5, todayIndex) else emptyList()
        val avgTemp = if (tempWindow.isNotEmpty()) tempWindow.sumOf { it.avgTemp.toDouble() } / tempWindow.size else 0.0
        val rainWindow = if (todayIndex >= 10) days.subList(todayIndex - 10, todayIndex - 2) else emptyList()
        val totalRain = rainWindow.sumOf { it.totalPrecip.toDouble() }
        val humWindow = days.subList(todayIndex - 3, min(days.size, todayIndex + 1))
        val avgHum = if (humWindow.isNotEmpty()) humWindow.sumOf { it.avgHumidity.toDouble() } / humWindow.size else 0.0

        val terrainEval = MushroomAlgorithms.evaluateTerrainAspect(
            terrain = lastTerrainData,
            month = lastCurrentMonth,
            avgTemp = avgTemp,
            seasonalityScore = seasonScore.score,
            species = species
        )
        lastTerrainEvaluation = terrainEval

        val prob = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = rawWeatherScore,
            habitatScore = habScore,
            altitudeScore = altMult,
            seasonalityScore = seasonMult,
            terrainModifier = if (calculationMode == "WEATHER_ONLY") 1.0 else terrainEval.modifier
        )
        todayProbability = prob

        factors = MushroomAlgorithms.calculateFactors(
            avgTemp = avgTemp,
            totalRain = totalRain,
            avgHumidity = avgHum,
            habitatScore = lastFinalHabitatScore,
            habitatText = lastHabitatBaseText,
            elevation = lastElevation,
            month = lastCurrentMonth,
            growthPhaseText = lastGrowthPhaseVal,
            moon = moon,
            slopeText = lastSlopeTextVal,
            species = species,
            spunEcmText = lastSpunData?.ecmText,
            spunHyphalText = lastSpunData?.hyphalText,
            terrainEvaluation = terrainEval
        )

        dailyOutlooks = MushroomAlgorithms.calculateDailyOutlooks(
            processedDays = days,
            startIndex = todayIndex,
            species = species,
            habitatScore = habScore,
            elevation = lastElevation,
            month = lastCurrentMonth,
            spunHyphalDensity = lastSpunData?.hyphalDensity
        )

        val fav = favoriteLocations.firstOrNull {
            String.format(Locale.US, "%.3f", it.lat) == String.format(Locale.US, "%.3f", lastLat) &&
            String.format(Locale.US, "%.3f", it.lon) == String.format(Locale.US, "%.3f", lastLon)
        }
        val customPrimary = fav?.customName?.takeIf { it.isNotBlank() }

        placeName = PlaceName.fromNominatimOrCoordinates(
            rawName = lastDisplayName,
            latitude = lastLat,
            longitude = lastLon,
            elevationMeters = lastElevation
        ).let { base ->
            if (customPrimary != null) base.copy(primary = customPrimary) else base
        }

        isOutsideHabitat = lastFinalHabitatScore < 0.1
        isOutsideCoverage = lastSpunData == null && spunDataManager.findRegionFor(lastLat, lastLon) == null
    }

    init {
        updateCacheSize()
        observeLocalAiStatus()
        // Carica cronologia e preferiti
        recentLocations = cacheManager.getRecentLocations()
        favoriteLocations = cacheManager.getFavoriteLocations()
        // Prefetch silenzioso dei preferiti
        prefetchFavorites()
    }

    private fun observeLocalAiStatus() {
        viewModelScope.launch {
            localAiService.status.collect { status ->
                aiStatusText = when (status) {
                    AiEngineStatus.NOT_SUPPORTED -> "Non supportato da questo dispositivo"
                    AiEngineStatus.INITIALIZING -> "Configurazione in corso..."
                    AiEngineStatus.DOWNLOADING -> "Download modello in corso..."
                    AiEngineStatus.DOWNLOAD_FAILED -> "Download modello fallito"
                    AiEngineStatus.READY -> "Supportato e pronto"
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun setShowSettingsDialog(show: Boolean) {
        showSettings = show
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreference?.setThemeMode(mode)
        }
    }

    fun updateCacheSize() {
        cacheSize = cacheManager.getCacheSizeString()
    }

    fun clearCache() {
        cacheManager.clearCache()
        updateCacheSize()
    }

    fun clearRecentLocations() {
        cacheManager.clearRecentLocations()
        recentLocations = emptyList()
    }

    fun saveSettings(style: String, radius: Int, threshold: Int, cacheActive: Boolean, useLocalAiActive: Boolean) {
        cacheManager.mapStyle = style
        cacheManager.radius = radius
        cacheManager.threshold = threshold
        cacheManager.cacheEnabled = cacheActive
        cacheManager.useLocalAi = useLocalAiActive

        mapStyle = style
        searchRadius = radius
        highlightThreshold = threshold
        cacheEnabled = cacheActive
        useLocalAi = useLocalAiActive

        updateCacheSize()
        showSettings = false

        // Re-run analysis with new parameters if a location is selected
        selectedLatLng?.let { (lat, lon) ->
            selectLocation(lat, lon, locationName)
        }
    }

    fun searchLocation() {
        val query = searchQuery.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            isLoading = true
            loadingText = "Ricerca della località in corso..."
            errorMessage = null
            showMap = false

            try {
                val result = repository.searchLocation(query)
                if (result != null) {
                    val lat = result.lat.toDoubleOrNull()
                    val lon = result.lon.toDoubleOrNull()
                    if (lat != null && lon != null) {
                        locationName = result.displayName
                        selectedLatLng = Pair(lat, lon)
                        showMap = true
                        isMapCenteredOnUser = false
                        mapOrientationMode = MapOrientationMode.NORTH_UP
                        centerOnPointTrigger++
                        selectLocation(lat, lon, result.displayName, isGps = false)
                    } else {
                        errorMessage = "Coordinate non valide per la località trovata."
                    }
                } else {
                    errorMessage = "Località non trovata. Prova un nome più specifico."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = e.message ?: "Errore nella ricerca della località."
            } finally {
                isLoading = false
            }
        }
    }

    /** Flusso B: geolocalizzazione GPS — avvia la selezione con geocodifica inversa e calcolo parallelo */
    fun selectLocationFromGps(lat: Double, lon: Double) {
        userLocation = UserLocation(latitude = lat, longitude = lon)
        isMapCenteredOnUser = true
        aiJob?.cancel()
        viewModelScope.launch { spunDataManager.ensureRegionLoadedFor(lat, lon) }
        selectLocation(lat, lon, displayName = "Posizione GPS", isGps = true)
    }

    /** Flusso A: tap sulla mappa — imposta subito il punto e avvia geocodifica e calcoli in parallelo */
    fun selectLocationFromMap(lat: Double, lon: Double) {
        aiJob?.cancel()
        isMapCenteredOnUser = false
        mapOrientationMode = MapOrientationMode.NORTH_UP
        centerOnPointTrigger++
        selectLocation(lat, lon, displayName = "Localizzazione in corso...", isGps = false)
    }

    /** Carica una località salvata dalla cronologia/preferiti */
    fun selectSavedLocation(loc: SavedLocation) {
        searchQuery = loc.effectiveName
        isMapCenteredOnUser = false
        mapOrientationMode = MapOrientationMode.NORTH_UP
        centerOnPointTrigger++
        selectLocation(loc.lat, loc.lon, loc.displayName, isGps = loc.isGpsLocation)
    }

    /** Aggiunge/rimuove il punto corrente dai preferiti */
    fun toggleCurrentFavorite() {
        val latLng = selectedLatLng ?: return
        val rLat = String.format(Locale.US, "%.3f", latLng.first)
        val rLon = String.format(Locale.US, "%.3f", latLng.second)
        val existingFav = favoriteLocations.firstOrNull {
            String.format(Locale.US, "%.3f", it.lat) == rLat &&
            String.format(Locale.US, "%.3f", it.lon) == rLon
        }
        val loc = SavedLocation(
            lat = latLng.first,
            lon = latLng.second,
            displayName = locationName,
            shortName = locationName.split(",").firstOrNull()?.trim() ?: locationName,
            savedAt = System.currentTimeMillis(),
            isFavorite = !currentLocationIsFavorite,
            isGpsLocation = currentLocationIsGps,
            customName = existingFav?.customName
        )
        if (currentLocationIsFavorite) {
            cacheManager.removeFavorite(latLng.first, latLng.second)
        } else {
            cacheManager.addFavorite(loc)
        }
        currentLocationIsFavorite = !currentLocationIsFavorite
        favoriteLocations = cacheManager.getFavoriteLocations()
        recentLocations = cacheManager.getRecentLocations()
    }

    /** Alias per compatibilità con i componenti UI */
    fun toggleCurrentLocationFavorite() {
        toggleCurrentFavorite()
    }

    /** Avvia la modifica del nome per un preferito specifico */
    fun startEditingFavorite(loc: SavedLocation) {
        editingFavoriteLocation = loc
    }

    /** Avvia la modifica del nome per il preferito attualmente attivo a schermo */
    fun startEditingCurrentFavorite() {
        val latLng = selectedLatLng ?: return
        val rLat = String.format(Locale.US, "%.3f", latLng.first)
        val rLon = String.format(Locale.US, "%.3f", latLng.second)
        val currentFav = favoriteLocations.firstOrNull {
            String.format(Locale.US, "%.3f", it.lat) == rLat &&
            String.format(Locale.US, "%.3f", it.lon) == rLon
        }
        if (currentFav != null) {
            editingFavoriteLocation = currentFav
        } else if (currentLocationIsFavorite) {
            editingFavoriteLocation = SavedLocation(
                lat = latLng.first,
                lon = latLng.second,
                displayName = locationName,
                shortName = locationName.split(",").firstOrNull()?.trim() ?: locationName,
                savedAt = System.currentTimeMillis(),
                isFavorite = true,
                isGpsLocation = currentLocationIsGps
            )
        }
    }

    /** Chiude il dialogo di rinomina */
    fun dismissEditingFavorite() {
        editingFavoriteLocation = null
    }

    /** Salva il nome personalizzato per un preferito */
    fun saveFavoriteCustomName(loc: SavedLocation, newName: String) {
        val trimmed = newName.trim()
        val customName = if (trimmed == loc.shortName || trimmed.isEmpty()) null else trimmed
        cacheManager.renameFavorite(loc.lat, loc.lon, customName)
        favoriteLocations = cacheManager.getFavoriteLocations()
        recentLocations = cacheManager.getRecentLocations()

        // Sincronizza il placeName attivo se corrisponde alla località modificata
        selectedLatLng?.let { (curLat, curLon) ->
            val rCurLat = String.format(Locale.US, "%.3f", curLat)
            val rCurLon = String.format(Locale.US, "%.3f", curLon)
            val rLocLat = String.format(Locale.US, "%.3f", loc.lat)
            val rLocLon = String.format(Locale.US, "%.3f", loc.lon)
            if (rCurLat == rLocLat && rCurLon == rLocLon) {
                val primaryName = customName ?: loc.shortName
                placeName = placeName?.copy(primary = primaryName)
                searchQuery = primaryName
            }
        }
        editingFavoriteLocation = null
    }

    /** Rimuove il preferito direttamente dal dialogo di rinomina */
    fun removeFavoriteFromDialog(loc: SavedLocation) {
        removeFavoriteLocation(loc)
        editingFavoriteLocation = null
    }

    /** Rimuove una località specifica dalla cronologia recenti */
    fun removeRecentLocation(loc: SavedLocation) {
        cacheManager.removeRecentLocation(loc.lat, loc.lon)
        recentLocations = cacheManager.getRecentLocations()
    }

    /** Aggiorna lo stile del layer cartografico */
    fun updateMapStyle(style: String) {
        mapStyle = style
        cacheManager.mapStyle = style
    }

    /** Commuta rapidamente tra stile topografico (OpenTopoMap) e toponomastico (OpenStreetMap standard) */
    fun toggleMapStyle() {
        val nextStyle = if (mapStyle == "standard") "topo" else "standard"
        updateMapStyle(nextStyle)
    }

    /** Rimuove una località specifica dai preferiti */
    fun removeFavoriteLocation(loc: SavedLocation) {
        cacheManager.removeFavorite(loc.lat, loc.lon)
        favoriteLocations = cacheManager.getFavoriteLocations()
        recentLocations = cacheManager.getRecentLocations()
        
        // Sincronizza lo stato preferito corrente se corrisponde a quella rimossa
        selectedLatLng?.let { (lat, lon) ->
            val rLat = String.format(Locale.US, "%.3f", lat)
            val rLon = String.format(Locale.US, "%.3f", lon)
            val targetLat = String.format(Locale.US, "%.3f", loc.lat)
            val targetLon = String.format(Locale.US, "%.3f", loc.lon)
            if (rLat == targetLat && rLon == targetLon) {
                currentLocationIsFavorite = false
            }
        }
    }

    /** Prefetch silenzioso meteo per tutti i preferiti con cache scaduta/assente */
    private fun prefetchFavorites() {
        val favs = favoriteLocations
        if (favs.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            favs.forEach { loc ->
                val age = cacheManager.getWeatherCacheAge(loc.lat, loc.lon)
                if (age == null || age > 45 * 60 * 1000L) {
                    try {
                        repository.fetchWeather(loc.lat, loc.lon)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun selectLocation(lat: Double, lon: Double, displayName: String = "Punto selezionato", isGps: Boolean = false) {
        aiJob?.cancel()
        if (!isGps) {
            isMapCenteredOnUser = false
            mapOrientationMode = MapOrientationMode.NORTH_UP
            centerOnPointTrigger++
        }

        // Aggiornamento immediato sincrono a 0ms per marker e scheda
        selectedLatLng = Pair(lat, lon)
        showMap = true
        currentLocationIsGps = isGps

        val rLat = String.format(Locale.US, "%.3f", lat)
        val rLon = String.format(Locale.US, "%.3f", lon)
        val fav = favoriteLocations.firstOrNull {
            String.format(Locale.US, "%.3f", it.lat) == rLat &&
            String.format(Locale.US, "%.3f", it.lon) == rLon
        }
        currentLocationIsFavorite = (fav != null)

        val isPlaceholder = SavedLocation.isPlaceholderName(displayName)
        val cachedGeo = if (isPlaceholder) {
            cacheManager.getCachedData("reverse_${rLat}_${rLon}", GeocodeResult::class.java, 7 * 24 * 60 * 60 * 1000L)
        } else null

        val initialTitle = fav?.effectiveName
            ?: cachedGeo?.let { PlaceName.fromGeocodeResult(it).primary }
            ?: if (isPlaceholder) "Localizzazione in corso..." else (displayName.split(",").firstOrNull()?.trim() ?: displayName)

        locationName = initialTitle
        placeName = if (cachedGeo != null && fav == null) {
            PlaceName.fromGeocodeResult(cachedGeo, lastElevation)
        } else {
            PlaceName.fromNominatimOrCoordinates(
                rawName = initialTitle,
                latitude = lat,
                longitude = lon,
                elevationMeters = lastElevation
            )
        }

        viewModelScope.launch {
            isLoading = true
            loadingText = "Analisi micologica e ambientale in corso..."
            errorMessage = null

            // Generazione istantanea della nuvola locale in background (<10ms)
            // Sfrutta i dati SPUN residenti in memoria senza attendere 3-5 secondi di chiamate di rete
            launch(Dispatchers.Default) {
                val calendar = Calendar.getInstance()
                val month = calendar.get(Calendar.MONTH)
                val season = MushroomAlgorithms.calculateSeasonalityScore(month).score
                val instant = HeatmapGenerator.generateHeatmap(
                    centerLat = lat,
                    centerLon = lon,
                    spunDataManager = spunDataManager,
                    baseWeatherScore = 40.0,
                    seasonalityScore = season,
                    altitudeScore = 0.8
                )
                if (instant != null) {
                    heatmapData = instant
                }
            }

            try {
                // Check if weather is cached
                val roundedLat = String.format(Locale.US, "%.4f", lat)
                val roundedLon = String.format(Locale.US, "%.4f", lon)
                val weatherCacheKey = "weather_${roundedLat}_${roundedLon}"
                isFromCache = cacheManager.getCachedData(weatherCacheKey, github.naturewhisp.myco.model.WeatherResponse::class.java, 60 * 60 * 1000) != null

                // Cache age text
                val ageMs = cacheManager.getWeatherCacheAge(lat, lon)
                cacheAgeText = ageMs?.let { formatCacheAge(it) }

                // Se il nome è generico e non abbiamo una voce in cache, avvia il reverse geocoding in parallelo
                val geocodeDeferred = if (isPlaceholder && cachedGeo == null) {
                    async { repository.reverseGeocode(lat, lon) }
                } else null

                // Aggiorna subito il toponimo non appena il reverse geocoding risponde (~200ms)
                if (geocodeDeferred != null) {
                    launch {
                        val geocoded = geocodeDeferred.await()
                        if (geocoded != null && selectedLatLng == Pair(lat, lon)) {
                            val resolvedName = geocoded.displayName
                            locationName = resolvedName
                            searchQuery = resolvedName.split(",").firstOrNull()?.trim() ?: resolvedName
                            val customPrimary = fav?.customName?.takeIf { it.isNotBlank() }
                            val base = PlaceName.fromGeocodeResult(geocoded, lastElevation)
                            placeName = if (customPrimary != null) base.copy(primary = customPrimary) else base
                        }
                    }
                }

                // Fetch weather, habitat, SPUN micorrize, and terrain aspect in parallel
                val weatherDeferred = async { repository.fetchWeather(lat, lon) }
                val habitatDeferred = async { repository.fetchHabitat(lat, lon) }
                val habitatBonusDeferred = async { repository.fetchSpecificHabitatBonus(lat, lon) }
                val spunDeferred = async { repository.fetchSpunData(lat, lon, searchRadius) }
                val terrainDeferred = async { repository.fetchTerrainAspect(lat, lon) }

                val weather = weatherDeferred.await()
                val habitat = habitatDeferred.await()
                val habitatBonus = habitatBonusDeferred.await()
                val spunData = spunDeferred.await()
                val terrainData = terrainDeferred.await()
                val resolvedGeo = geocodeDeferred?.await() ?: cachedGeo

                val resolvedDisplayName = when {
                    resolvedGeo != null -> resolvedGeo.displayName
                    !isPlaceholder -> displayName
                    else -> displayName
                }

                // Calculate Habitat Score
                val forestCount = habitat?.elements?.size ?: 0
                val habitatScore: Double
                val habitatBaseText: String
                when {
                    forestCount > 15 -> {
                        habitatScore = 1.0
                        habitatBaseText = "Habitat: Ideale (punto immerso in area boschiva)."
                    }
                    forestCount > 4 -> {
                        habitatScore = 0.95
                        habitatBaseText = "Habitat: Promettente (vicinanza a boschi e foreste)."
                    }
                    forestCount > 0 -> {
                        habitatScore = 0.6
                        habitatBaseText = "Habitat: Misto (presenza di aree verdi sparse)."
                    }
                    else -> {
                        habitatScore = 0.1
                        habitatBaseText = "Habitat: Non ideale (assenza di boschi nelle vicinanze)."
                    }
                }

                var finalHabitatScore = habitatScore
                var habitatBonusTextVal = "Bonus: Nessun dato vegetativo aggiuntivo rilevato."
                val specificForestCount = habitatBonus?.elements?.size ?: 0
                if (specificForestCount > 0) {
                    finalHabitatScore = min(1.0, habitatScore * 1.15)
                    habitatBonusTextVal = "Bonus: Rilevati alberi ottimali! Punteggio habitat potenziato."
                }

                // Modulazione scientifica SPUN: certifica se il sottosuolo ospita la comunità ectomicorrizica adatta
                if (spunData != null) {
                    if (spunData.ecmRichness >= 50.0f) {
                        finalHabitatScore = min(1.0, finalHabitatScore * 1.15)
                        habitatBonusTextVal = if (specificForestCount > 0) {
                            "Bonus: Alberi e simbiosi EcM SPUN ottimali (${spunData.ecmRichness.toInt()} specie)!"
                        } else {
                            "Bonus SPUN: Rete ectomicorrizica eccellente (${spunData.ecmRichness.toInt()} specie)!"
                        }
                    } else if (spunData.ecmRichness < 15.0f && forestCount > 0) {
                        // Penalizza boschi con microflora micorrizica scarsa
                        finalHabitatScore = max(0.2, finalHabitatScore * 0.8)
                    }
                }

                // Process weather
                val processedDays = MushroomAlgorithms.processWeatherData(weather)
                val todayIndex = 14
                
                // Forecast grid: next 5 days starting today (todayIndex to todayIndex+4)
                forecastDays = processedDays.subList(todayIndex, min(processedDays.size, todayIndex + 5))

                val elevation = weather.elevation
                val altitudeScore = MushroomAlgorithms.calculateSpeciesAltitudeScore(elevation, selectedSpecies)
                val calendar = Calendar.getInstance()
                val currentMonth = calendar.get(Calendar.MONTH) // 0-indexed
                val seasonalityScore = MushroomAlgorithms.calculateSpeciesSeasonalityScore(currentMonth, selectedSpecies)
                val growthPhaseVal = MushroomAlgorithms.calculateGrowthPhase(processedDays)
                val moonPhase = MushroomAlgorithms.getMoonPhase()

                // Rain window calculation (last 10 days to 2 days ago)
                val rainStart = max(0, todayIndex - 10)
                val rainEnd = max(0, todayIndex - 2)
                val rainWindow = processedDays.subList(rainStart, rainEnd)
                val totalRainLast10Days = rainWindow.sumOf { it.totalPrecip.toDouble() }
                val rainStatus = MushroomAlgorithms.getRainStatus(totalRainLast10Days)
                val rainTextVal = "Pioggia: ${totalRainLast10Days.toInt()}mm (${rainStatus.label})"

                // Temp window calculation (last 5 days)
                val tempStart = max(0, todayIndex - 5)
                val tempWindow = processedDays.subList(tempStart, todayIndex)
                val avgTempLast5Days = if (tempWindow.isNotEmpty()) {
                    tempWindow.sumOf { it.avgTemp.toDouble() } / tempWindow.size
                } else {
                    0.0
                }
                val tempStatus = MushroomAlgorithms.getTempStatus(avgTempLast5Days)
                val tempTextVal = String.format(Locale.ITALIAN, "Temp. media: %.1f°C (%s)", avgTempLast5Days, tempStatus.label)

                val slopeTextVal = MushroomAlgorithms.getSlopeRecommendation(seasonalityScore.score, avgTempLast5Days, currentMonth)

                // Today's weather score ponderato con la densità ifale SPUN (volano per la pioggia e lo shock termico)
                val rawWeatherScore = MushroomAlgorithms.calculateWeatherScore(
                    todayIndex,
                    processedDays,
                    spunHyphalDensity = spunData?.hyphalDensity,
                    species = selectedSpecies
                )

                // Assign states
                locationName = resolvedDisplayName
                growthPhase = growthPhaseVal
                habitatText = habitatBaseText
                habitatBonusText = habitatBonusTextVal
                altitudeText = altitudeScore.text
                seasonText = seasonalityScore.text
                rainText = rainTextVal
                tempText = tempTextVal
                moonPhaseText = "Luna: ${moonPhase.text} (${if (moonPhase.favorable) "Favorevole" else "Ininfluente"})"
                moonPhaseEmoji = moonPhase.emoji
                slopeText = if (terrainData != null && !terrainData.isFlat) {
                    "${terrainData.cardinalDirection} (${terrainData.slopeDegrees.roundToInt()}°)"
                } else if (terrainData != null && terrainData.isFlat) {
                    "Pianeggiante (${terrainData.slopeDegrees.roundToInt()}°)"
                } else {
                    slopeTextVal
                }

                if (spunData != null) {
                    spunEcmText = spunData.ecmText
                    spunHyphalText = spunData.hyphalText
                    spunRegionName = spunData.regionName
                    spunDataAvailable = true
                } else {
                    spunEcmText = ""
                    spunHyphalText = ""
                    spunRegionName = null
                    spunDataAvailable = false
                }

                lastTerrainData = terrainData
                // Memorizza i dati per ricalibrazioni istantanee con specie bersaglio differenti
                lastProcessedDays = processedDays
                lastFinalHabitatScore = finalHabitatScore
                lastHabitatBaseText = habitatBaseText
                lastElevation = elevation
                lastSpunData = spunData
                lastLat = lat
                lastLon = lon
                lastDisplayName = resolvedDisplayName
                lastGrowthPhaseVal = growthPhaseVal
                lastMoonPhase = moonPhase
                lastSlopeTextVal = slopeTextVal
                lastCurrentMonth = currentMonth

                recalculateForSpecies()

                val futureTrend = MushroomAlgorithms.analyzeFutureTrend(processedDays)

                // Generazione asincrona della nuvola termica di probabilità (Heatmap)
                heatmapData = HeatmapGenerator.generateHeatmap(
                    centerLat = lat,
                    centerLon = lon,
                    spunDataManager = spunDataManager,
                    baseWeatherScore = rawWeatherScore.toDouble(),
                    seasonalityScore = seasonalityScore.score,
                    altitudeScore = altitudeScore.score
                )

                // Salva nella cronologia recenti (solo se toponimo reale e non segnaposto)
                if (!SavedLocation.isPlaceholderName(resolvedDisplayName)) {
                    val shortName = resolvedDisplayName.split(",").firstOrNull()?.trim() ?: resolvedDisplayName
                    val existingFav = favoriteLocations.firstOrNull {
                        String.format(Locale.US, "%.3f", it.lat) == rLat &&
                        String.format(Locale.US, "%.3f", it.lon) == rLon
                    }
                    val savedLoc = SavedLocation(
                        lat = lat,
                        lon = lon,
                        displayName = resolvedDisplayName,
                        shortName = shortName,
                        savedAt = System.currentTimeMillis(),
                        isFavorite = currentLocationIsFavorite,
                        isGpsLocation = isGps,
                        customName = existingFav?.customName
                    )
                    cacheManager.saveRecentLocation(savedLoc)
                    recentLocations = cacheManager.getRecentLocations()
                }

                // Dismiss main full-screen loader immediately
                isLoading = false

                // Process AI summary in background coroutine
                summaryText = ""
                if (useLocalAi && localAiService.isAvailable()) {
                    isAiLoading = true
                    aiJob = viewModelScope.launch {
                        try {
                            val spunPromptInfo = if (spunData != null) {
                                "- Rete micorrizica sotterranea (dati scientifici SPUN): Densità ifale ${String.format(Locale.ITALIAN, "%.1f", spunData.hyphalDensity)} m/cm³ (${spunData.hyphalText.substringAfter("(").substringBefore(")")}), Ricchezza specie ectomicorriziche ${spunData.ecmRichness.toInt()} specie (${spunData.ecmText.substringAfter("(").substringBefore(")")})"
                            } else {
                                "- Rete micorrizica: Nessun dato regionale SPUN registrato per questa coordinata"
                            }

                            val terrainPromptInfo = if (lastTerrainData != null) {
                                val t = lastTerrainData!!
                                if (t.isFlat) {
                                    "- Orografia e versante: Terreno pianeggiante o altopiano (pendenza ${String.format(Locale.ITALIAN, "%.0f°", t.slopeDegrees)})"
                                } else {
                                    "- Orografia e versante reale: Pendenza ${t.slopeDegrees.roundToInt()}°, Esposizione versante a ${t.cardinalDirection} (${lastTerrainEvaluation?.detail ?: ""})"
                                }
                            } else {
                                "- Esposizione versante consigliata: $slopeTextVal"
                            }

                            val prompt = """
                                Sei un esperto micologo. Genera un'analisi in parole semplici in lingua italiana basandoti su questi dati:
                                - Località: $displayName
                                - Habitat: $habitatBaseText (Punteggio: $finalHabitatScore/1.0)
                                $spunPromptInfo
                                - Altitudine: ${altitudeScore.text} (Punteggio: ${altitudeScore.score}/1.0)
                                - Stagione: ${seasonalityScore.text} (Punteggio: ${seasonalityScore.score}/1.0)
                                - Pioggia ultimi 10 giorni: $rainTextVal
                                - Temperatura media ultimi 5 giorni: $tempTextVal
                                - Luna: ${moonPhase.text} (${if (moonPhase.favorable) "Favorevole" else "Ininfluente"})
                                $terrainPromptInfo
                                - Tendenza futura: $futureTrend

                                ISTRUZIONI CRITICHE DI FORMATTAZIONE:
                                1. NON usare NESSUNA formattazione markdown. NON usare asterischi (* o **), trattini (-), hashtag (#), o elenchi puntati. Genera solo testo normale continuo.
                                2. NON includere NESSUN preambolo, saluto o commento meta-testuale (come "Ecco l'analisi...", "Di seguito l'analisi completa", ecc.).
                                3. Inizia DIRETTAMENTE con la prima frase dell'analisi micologica (es. "La località presenta condizioni...").
                                4. Genera al massimo 4 frasi chiare, professionali e precise.
                            """.trimIndent()

                            val localAiSummary = localAiService.generateAdvancedSummary(prompt)
                            summaryText = if (localAiSummary != null) {
                                cleanAiResponse(localAiSummary)
                            } else {
                                MushroomAlgorithms.generateSummaryText(
                                    weatherScore = rawWeatherScore.toDouble(),
                                    habitatScore = finalHabitatScore,
                                    habitatText = habitatBaseText,
                                    altitudeScore = altitudeScore.score,
                                    altitudeText = altitudeScore.text,
                                    seasonalityScore = seasonalityScore.score,
                                    seasonalityText = seasonalityScore.text,
                                    totalRain = totalRainLast10Days,
                                    futureTrend = futureTrend,
                                    spunEcmText = spunData?.ecmText,
                                    spunHyphalText = spunData?.hyphalText
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            summaryText = MushroomAlgorithms.generateSummaryText(
                                weatherScore = rawWeatherScore.toDouble(),
                                habitatScore = finalHabitatScore,
                                habitatText = habitatBaseText,
                                altitudeScore = altitudeScore.score,
                                altitudeText = altitudeScore.text,
                                seasonalityScore = seasonalityScore.score,
                                seasonalityText = seasonalityScore.text,
                                totalRain = totalRainLast10Days,
                                futureTrend = futureTrend,
                                spunEcmText = spunData?.ecmText,
                                spunHyphalText = spunData?.hyphalText
                            )
                        } finally {
                            isAiLoading = false
                        }
                    }
                } else {
                    isAiLoading = false
                    summaryText = MushroomAlgorithms.generateSummaryText(
                        weatherScore = rawWeatherScore.toDouble(),
                        habitatScore = finalHabitatScore,
                        habitatText = habitatBaseText,
                        altitudeScore = altitudeScore.score,
                        altitudeText = altitudeScore.text,
                        seasonalityScore = seasonalityScore.score,
                        seasonalityText = seasonalityScore.text,
                        totalRain = totalRainLast10Days,
                        futureTrend = futureTrend,
                        spunEcmText = spunData?.ecmText,
                        spunHyphalText = spunData?.hyphalText
                    )
                }

                updateCacheSize()

            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = e.message ?: "Errore durante il caricamento e l'analisi dei dati."
                isLoading = false
            }
        }
    }

    private fun cleanAiResponse(text: String): String {
        var cleaned = text.trim()
        
        // Remove markdown formatting
        cleaned = cleaned.replace(Regex("\\*\\*"), "")
        cleaned = cleaned.replace(Regex("\\*"), "")
        cleaned = cleaned.replace(Regex("`"), "")
        
        // Remove markdown headers or list markers at start of lines
        cleaned = cleaned.replace(Regex("(?m)^#+\\s+"), "")
        cleaned = cleaned.replace(Regex("(?m)^[-\\s*+]+\\s+"), "")
        
        // Remove common preambles
        val preambles = listOf(
            "l'analisi completa è la seguente:",
            "l'analisi completa è la seguente",
            "ecco l'analisi completa:",
            "ecco l'analisi completa",
            "ecco l'analisi dei dati:",
            "ecco l'analisi:",
            "di seguito l'analisi dei dati:",
            "di seguito l'analisi:",
            "di seguito l'analisi completa:",
            "ecco il riassunto dell'analisi:",
            "riassunto dell'analisi:",
            "basandomi sui dati forniti, ecco l'analisi:",
            "basandosi sui dati forniti, ecco l'analisi:",
            "in base ai dati forniti, ecco l'analisi:",
            "in base ai dati forniti, l'analisi è la seguente:",
            "ecco la tua analisi:",
            "ecco la mia analisi:",
            "analisi completa:",
            "riassunto:"
        )
        
        var foundPreamble = true
        while (foundPreamble) {
            foundPreamble = false
            val lowerCleaned = cleaned.lowercase(Locale.ROOT)
            for (preamble in preambles) {
                if (lowerCleaned.startsWith(preamble)) {
                    cleaned = cleaned.substring(preamble.length).trim()
                    cleaned = cleaned.replace(Regex("^[:\\-\\s]+"), "").trim()
                    foundPreamble = true
                    break
                }
            }
        }
        
        return cleaned
    }

    private fun formatCacheAge(ageMs: Long): String {
        val minutes = ageMs / 60_000
        return when {
            minutes < 1 -> "< 1 min fa"
            minutes < 60 -> "$minutes min fa"
            else -> "${minutes / 60}h fa"
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationAndOrientationTracking()
    }
}
