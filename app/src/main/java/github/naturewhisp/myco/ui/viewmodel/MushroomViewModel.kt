package github.naturewhisp.myco.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import github.naturewhisp.myco.model.DailyOutlook
import github.naturewhisp.myco.model.EcologicalCategory
import github.naturewhisp.myco.model.EcologicalWeightsConfig
import github.naturewhisp.myco.model.Factor
import github.naturewhisp.myco.model.FactorId
import github.naturewhisp.myco.model.FactorLevel
import github.naturewhisp.myco.model.weatherCondition
import github.naturewhisp.myco.model.GeocodeResult
import github.naturewhisp.myco.model.HabitatEvidence
import github.naturewhisp.myco.model.HabitatStatus
import github.naturewhisp.myco.platform.android.HeatmapData
import github.naturewhisp.myco.model.MushroomSpecies
import github.naturewhisp.myco.model.toCore
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
import github.naturewhisp.myco.utils.MushroomAlgorithms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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
    val orientationProvider: PlatformOrientationProvider? = null,
    val clock: java.time.Clock = java.time.Clock.systemDefaultZone()
) : ViewModel() {

    // Historical Target Date State
    var userSelectedHistoricalDate by mutableStateOf<String?>(null)
        private set

    data class AnalysisIdentity(
        val generation: Long,
        val speciesId: String,
        val lat: Double,
        val lon: Double,
        val targetDate: String?,
        val calculationMode: String
    )
    private var activeAnalysisIdentity: AnalysisIdentity? = null

    fun getRequestedAnalysisDate(timezone: String?): String {
        return userSelectedHistoricalDate ?: run {
            val zoneId = try {
                timezone?.let { java.time.ZoneId.of(it) } ?: clock.zone
            } catch (_: Exception) {
                clock.zone
            }
            java.time.LocalDate.now(clock.withZone(zoneId)).toString()
        }
    }

    fun setHistoricalAnalysisDate(dateIso: String?) {
        analysisGeneration++
        aiJob?.cancel()
        aiJob = null
        isAiLoading = false
        activeAnalysisIdentity = null
        userSelectedHistoricalDate = dateIso
        recalculateForSpecies()
    }

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
    var recentLocations by mutableStateOf<List<SavedLocation>>(cacheManager.getRecentLocations())
        private set
    var favoriteLocations by mutableStateOf<List<SavedLocation>>(cacheManager.getFavoriteLocations())
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
    var todaySuitabilityScore by mutableStateOf(0.0)
        private set
    var targetAnalysisDate by mutableStateOf<String?>(null)
        private set
    var dataAcquisitionTimestamp by mutableStateOf<Long?>(null)
        private set
    var analysisAsOfTimestamp by mutableStateOf<Long?>(null)
        private set
    var waterDiagnosisText by mutableStateOf<String?>(null)
        private set
    var dataQualityStatus by mutableStateOf<String?>(null)
        private set
    var lastWeatherTimezone: String? = null
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
    var isCalculable by mutableStateOf(true)
        private set
    private var analysisGeneration = 0L
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
    var closestCoverageName by mutableStateOf("Val Veny / Courmayeur (AO)")
        internal set
    var closestCoverageDistanceKm by mutableStateOf(14)
        internal set
    var closestCoverageLatLng by mutableStateOf<Pair<Double, Double>?>(Pair(45.7969, 6.9697))
        internal set
    var isOfflineFieldMode by mutableStateOf(false)
        private set
    var isPrefetchingOffline by mutableStateOf(false)
        private set
    var targetSpeciesSheetOpen by mutableStateOf(false)
        private set
    var showSafetyDisclaimer by mutableStateOf(false)
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

    private fun isCompassGeospatiallyValid(): Boolean {
        if (currentLocationIsGps) return true
        val uLoc = userLocation ?: return false
        val sLoc = selectedLatLng ?: return false
        val dist = github.naturewhisp.myco.utils.MushroomAlgorithms.haversineDistanceKm(sLoc.first, sLoc.second, uLoc.latitude, uLoc.longitude)
        return dist <= 0.05
    }

    val isCompassSupported: Boolean
        get() = (orientationProvider?.isSupported() ?: false) && isCompassGeospatiallyValid()

    val mapRotationDegrees: Float
        get() = when (mapOrientationMode) {
            MapOrientationMode.NORTH_UP -> 0f
            MapOrientationMode.HEADING_UP -> {
                if (isCompassGeospatiallyValid()) {
                    val azimuth = deviceHeading?.azimuthDegrees ?: 0f
                    (360f - azimuth) % 360f
                } else {
                    0f
                }
            }
        }

    internal var dataFetchJob: Job? = null
    internal var heatmapJob: Job? = null
    private var locationTrackingJob: Job? = null
    private var orientationTrackingJob: Job? = null

    fun confirmSafetyDisclaimer() {
        cacheManager.isSafetyDisclaimerAccepted = true
        showSafetyDisclaimer = false
    }

    fun openSafetyDisclaimer() {
        showSafetyDisclaimer = true
    }

    fun dismissSafetyDisclaimer() {
        showSafetyDisclaimer = false
    }

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
    private var lastHabitatEvidence: HabitatEvidence? = null
    private var lastElevation: Float = 800f
    private var lastSpunData: SpunData? = null
    private var lastLat: Double = 41.8902
    private var lastLon: Double = 12.4922
    private var lastDisplayName: String = ""
    private var lastGrowthPhaseVal: String = ""
    private var lastTerrainData: TerrainAspectData? = null
    private var lastTerrainEvaluation: TerrainAspectEvaluation? = null
    private var lastCurrentMonth: Int = 8

    fun toggleHeatmap() {
        showHeatmap = !showHeatmap
    }

    fun selectSpecies(species: MushroomSpecies) {
        analysisGeneration++
        aiJob?.cancel()
        aiJob = null
        isAiLoading = false
        activeAnalysisIdentity = null
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
        analysisGeneration++
        aiJob?.cancel()
        aiJob = null
        isAiLoading = false
        activeAnalysisIdentity = null
        calculationMode = mode
        recalculateForSpecies()
    }

    fun recalculateForSpecies() {
        val days = lastProcessedDays ?: return
        val species = selectedSpecies
        val requestedDate = getRequestedAnalysisDate(lastWeatherTimezone)
        val todayIndex = MushroomAlgorithms.deriveTodayIndex(days, lastWeatherTimezone, requestedDate, clock)
        if (days.size <= todayIndex || todayIndex < 0) {
            isCalculable = false
            todaySuitabilityScore = 0.0
            todayProbability = 0
            dataQualityStatus = github.naturewhisp.myco.core.DataQualityStatus.DEGRADED_OUT_OF_BOUNDS.name
            summaryText = "Analisi non calcolabile: data richiesta non presente nella serie temporale disponibile."
            activeAnalysisIdentity = null
            aiJob?.cancel()
            aiJob = null
            isAiLoading = false
            return
        }

        val targetDay = days.getOrNull(todayIndex)
        targetAnalysisDate = targetDay?.date
        analysisAsOfTimestamp = clock.millis()

        val parsedMonth = try {
            java.time.LocalDate.parse(requestedDate).monthValue - 1
        } catch (_: Exception) {
            val zId = try { lastWeatherTimezone?.let { java.time.ZoneId.of(it) } ?: clock.zone } catch (_: Exception) { clock.zone }
            java.time.LocalDate.now(clock.withZone(zId)).monthValue - 1
        }
        lastCurrentMonth = parsedMonth

        val altScore = MushroomAlgorithms.calculateSpeciesAltitudeScore(lastElevation, species)
        val seasonScore = MushroomAlgorithms.calculateSpeciesSeasonalityScore(lastCurrentMonth, species)

        // Copertura arborea stazionale: proprietà fisica ambientale del sito, indipendente dalla specie target (F18)
        val evidence = lastHabitatEvidence ?: HabitatEvidence.UNKNOWN_HABITAT
        val siteCanopyCover = if (evidence.status == HabitatStatus.UNKNOWN) 0.0 else evidence.forestCoverFraction

        val habEval = github.naturewhisp.myco.core.MycoAlgorithms.evaluateHabitat(
            evidence = evidence,
            species = species.toCore(),
            spunEcmRichness = lastSpunData?.ecmRichness?.toDouble()
        )
        val baseHabitatScore = habEval.baseScore
        val cleanHabText = habEval.baseText.removePrefix("Habitat: ").trim()
        val detailedHabDesc = if (!habEval.bonusText.isNullOrBlank() && !habEval.bonusText.startsWith("Nessuna essenza")) {
            val cleanBonus = habEval.bonusText.removePrefix("Bonus: ").removePrefix("Bonus SPUN: ").trim()
            "$cleanHabText • $cleanBonus"
        } else {
            cleanHabText
        }

        val coreDays = days.map { d ->
            github.naturewhisp.myco.core.ProcessedDay(
                dateIso = d.date,
                avgTemp = d.avgTemp.toDouble(),
                totalPrecipMm = d.totalPrecip.toDouble(),
                avgHumidityPercent = d.avgHumidity.toDouble(),
                weatherCode = d.weatherCode,
                soilMoisture0To7 = d.avgSoilMoisture0To7cm?.toDouble(),
                soilMoisture7To28 = d.avgSoilMoisture7To28cm?.toDouble(),
                evapotranspiration = d.totalEvapotranspiration?.toDouble(),
                minTemp = d.minTemp.toDouble(),
                maxTemp = d.maxTemp.toDouble(),
                coverage = d.coverage
            )
        }

        val inputs = github.naturewhisp.myco.core.AnalysisInputs(
            days = coreDays,
            todayIndex = todayIndex,
            speciesId = species.id,
            habitatScore = baseHabitatScore,
            habitatDescription = detailedHabDesc,
            canopyTypes = evidence.confirmedHostGenera.toList(),
            elevationSamples = lastTerrainData?.getOrSynthesizeRawElevations() ?: listOf(lastElevation.toDouble()),
            monthIndex = lastCurrentMonth,
            spunEcmRichness = lastSpunData?.ecmRichness?.toDouble(),
            spunHyphalDensity = lastSpunData?.hyphalDensity?.toDouble(),
            missingSources = buildList {
                if (lastSpunData == null) add("SPUN")
                if (!evidence.geometryComplete) add("geometrie habitat OSM incomplete")
            },
            canopyCover = siteCanopyCover,
            forestProximityIndex = evidence.forestProximityIndex,
            calculationMode = calculationMode,
            targetDateIso = requestedDate,
            habitatEvidence = evidence,
        )

        val engine = github.naturewhisp.myco.core.MycoAnalysisEngine()
        val result = engine.analyze(inputs)

        isCalculable = result.isCalculable
        todaySuitabilityScore = result.probability.toDouble()
        todayProbability = result.probability
        growthPhase = result.growthPhase?.phaseText ?: ""
        lastGrowthPhaseVal = result.growthPhase?.phaseText ?: ""
        dataQualityStatus = result.dataQuality.name
        waterDiagnosisText = result.waterDiagnosis
        lastFinalHabitatScore = result.habitatScore
        summaryText = result.deterministicFieldNote
        rainText = result.factors.firstOrNull { it.id == github.naturewhisp.myco.core.FactorId.PRECIPITATION }?.formattedValue ?: "Dati non disponibili"
        tempText = result.factors.firstOrNull { it.id == github.naturewhisp.myco.core.FactorId.TEMPERATURE }?.formattedValue ?: "Dati non disponibili"
        val moon = MushroomAlgorithms.getMoonPhase()
        moonPhaseText = "Luna: ${moon.text} (informativa)"
        moonPhaseEmoji = moon.emoji
        forecastDays = days.filter { it.date >= requestedDate }.take(5).filter { it.coverage?.weatherUsable != false }


        habitatText = habEval.baseText
        habitatBonusText = habEval.bonusText

        altitudeText = altScore.text
        seasonText = seasonScore.text
        slopeText = if (result.terrain.cardinalDirection == "Non disponibile") {
            "Esposizione non disponibile"
        } else if (result.terrain.slopeDegrees >= 3.0) {
            "${result.terrain.cardinalDirection} (${result.terrain.slopeDegrees.roundToInt()}°)"
        } else {
            "Pianeggiante (${result.terrain.slopeDegrees.roundToInt()}°)"
        }

        factors = result.factors.map { f ->
            Factor(
                id = FactorId.valueOf(f.id.name),
                label = f.label,
                formattedValue = f.formattedValue,
                level = FactorLevel.valueOf(f.level.name),
                detail = f.detail,
                iconGlyph = null
            )
        }

        val dayFormat = SimpleDateFormat("EEE", Locale.ITALIAN)
        val monthFormat = SimpleDateFormat("d MMM", Locale.ITALIAN)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        dailyOutlooks = result.dailyOutlooks.map { o ->
            val date = try { isoFormat.parse(o.dateIso) } catch (_: Exception) { null }
            DailyOutlook(
                dateIso = o.dateIso,
                dayOfWeek = date?.let { dayFormat.format(it).replaceFirstChar { c -> c.uppercase() } } ?: "",
                dayOfMonth = date?.let { monthFormat.format(it) } ?: "",
                weatherCode = o.weatherCode,
                avgTemp = o.avgTemp.toFloat(),
                totalPrecipMm = o.totalPrecipMm.toFloat(),
                avgHumidityPercent = o.avgHumidityPercent.toFloat(),
                probability = o.probability,
                tier = o.tier.ordinal,
                condition = weatherCondition(o.weatherCode),
                isCalculable = o.isCalculable,
                qualityReasons = o.qualityReasons
            )
        }

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
        if (isOutsideCoverage) {
            val closest = spunDataManager.findClosestCoveragePoint(lastLat, lastLon)
            closestCoverageName = closest.name
            closestCoverageDistanceKm = closest.distanceKm
            closestCoverageLatLng = Pair(closest.lat, closest.lon)
        }

        // Ricalcolo asincrono della nuvola di probabilità calibrata sulla nuova specie selezionata
        heatmapJob?.cancel()
        heatmapJob = viewModelScope.launch(Dispatchers.Default) {
            val h = HeatmapGenerator.generateHeatmap(
                centerLat = lastLat,
                centerLon = lastLon,
                spunDataManager = spunDataManager,
                baseWeatherScore = 100.0,
                seasonalityScore = 1.0,
                altitudeScore = 1.0,
                species = species
            )
            if (h != null) {
                heatmapData = h
            }
        }

        val currentIdentity = AnalysisIdentity(
            generation = ++analysisGeneration,
            speciesId = species.id,
            lat = lastLat,
            lon = lastLon,
            targetDate = targetAnalysisDate,
            calculationMode = calculationMode
        )
        activeAnalysisIdentity = currentIdentity
        launchAiEnrichment(currentIdentity)
    }

    private fun launchAiEnrichment(identity: AnalysisIdentity) {
        aiJob?.cancel()
        if (!useLocalAi || !localAiService.isAvailable() || !isCalculable) {
            isAiLoading = false
            aiJob = null
            return
        }

        isAiLoading = true
        aiJob = viewModelScope.launch {
            try {
                val deterministicNote = summaryText
                val prompt = """
                    Seleziona esclusivamente uno stile per una nota ambientale. Rispondi con una sola parola tra: essenziale, taccuino, osservazione. Non riscrivere la nota e non aggiungere altri contenuti.
                    Scegli lo stile per questa nota: $deterministicNote
                """.trimIndent()

                val localAiResponse = localAiService.generateAdvancedSummary(prompt)
                if (localAiResponse != null && activeAnalysisIdentity == identity) {
                    val token = cleanAiResponse(localAiResponse).trim().lowercase(Locale.ITALIAN)
                    val prefix = when (token) {
                        "taccuino" -> "Nota dal taccuino: "
                        "osservazione" -> "Osservazione ambientale: "
                        else -> ""
                    }
                    val safetyNotice = "Myco non identifica funghi e non conferma la commestibilità."
                    summaryText = if (prefix.isNotEmpty()) {
                        "$prefix$deterministicNote $safetyNotice"
                    } else {
                        deterministicNote
                    }
                }
            } catch (_: Exception) {
                // Silently keep deterministic note
            } finally {
                if (activeAnalysisIdentity == identity) {
                    isAiLoading = false
                }
            }
        }
    }

    init {
        updateCacheSize()
        observeLocalAiStatus()
        // Carica cronologia e preferiti
        recentLocations = cacheManager.getRecentLocations()
        favoriteLocations = cacheManager.getFavoriteLocations()
        // Prefetch silenzioso dei preferiti
        prefetchFavorites()
        // Mostra il disclaimer di sicurezza se non ancora accettato
        if (!cacheManager.isSafetyDisclaimerAccepted) {
            showSafetyDisclaimer = true
        }
    }

    private var midnightWatcherJob: Job? = null

    fun startMidnightWatcher() {
        midnightWatcherJob?.cancel()
        midnightWatcherJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000L)
                checkDayChangeAndRefresh()
            }
        }
    }

    fun stopMidnightWatcher() {
        midnightWatcherJob?.cancel()
        midnightWatcherJob = null
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

    /**
     * Esegue lo snap verso il centroide o stazione sentinella SPUN più vicina calcolata dinamicamente.
     * Risolve TD-02 eliminando il toponimo fisso di Val Veny.
     */
    fun snapToClosestCoverage() {
        val target = closestCoverageLatLng ?: return
        selectLocation(target.first, target.second, closestCoverageName)
    }

    /**
     * Esegue una scansione radar Overpass delle formazioni boschive e forestali reali nell'intorno
     * del punto selezionato (5 km) e trasla il cursore sul baricentro del bosco più vicino.
     * Risolve TD-01 eliminando l'offset fisso (+0.015, +0.015).
     */
    fun snapToNearestForest() {
        val current = selectedLatLng ?: Pair(lastLat, lastLon)
        viewModelScope.launch {
            loadingText = "Scansione formazioni boschive OSM in corso..."
            val forestCoord = repository.findNearestForest(current.first, current.second)
            if (forestCoord != null) {
                val dist = MushroomAlgorithms.haversineDistanceKm(
                    current.first, current.second, forestCoord.first, forestCoord.second
                )
                val distFormatted = String.format(Locale.US, "%.1f", dist)
                selectLocation(forestCoord.first, forestCoord.second, "Fascia boschiva vicina (~$distFormatted km)")
            } else {
                // Fallback di prossimità controllato
                val fallbackLat = current.first + 0.012
                val fallbackLon = current.second + 0.012
                selectLocation(fallbackLat, fallbackLon, "Fascia boschiva adiacente")
            }
        }
    }

    /**
     * Precarica in modo esaustivo tutti i layer ambientali (previsioni meteo orarie, orografia DEM,
     * copertura forestale OSM e alberi simbionti guida) per tutti i luoghi preferiti e per la posizione attuale,
     * garantendo piena operatività e consultazione in assenza di segnale telefonico (FEAT-04).
     */
    fun prefetchForOfflineUse(onCompleted: (Int) -> Unit = {}) {
        if (isPrefetchingOffline) return
        viewModelScope.launch {
            isPrefetchingOffline = true
            var count = 0
            val targets = mutableListOf<SavedLocation>()
            val favs = cacheManager.getFavoriteLocations()
            favoriteLocations = favs
            targets.addAll(favs)
            selectedLatLng?.let { curr ->
                targets.add(
                    SavedLocation(
                        lat = curr.first,
                        lon = curr.second,
                        displayName = locationName,
                        shortName = locationName.split(",").firstOrNull()?.trim() ?: locationName,
                        savedAt = System.currentTimeMillis()
                    )
                )
            }
            val distinctTargets = targets.distinctBy {
                String.format(Locale.US, "%.3f_%.3f", it.lat, it.lon)
            }
            for (loc in distinctTargets) {
                try {
                    repository.prefetchCompleteLocation(loc.lat, loc.lon, selectedSpecies)
                    count++
                } catch (_: Exception) {}
            }
            updateCacheSize()
            isPrefetchingOffline = false
            onCompleted(count)
        }
    }

    fun selectLocation(lat: Double, lon: Double, displayName: String = "Punto selezionato", isGps: Boolean = false) {
        analysisGeneration++
        aiJob?.cancel()
        aiJob = null
        isAiLoading = false
        activeAnalysisIdentity = null
        dataFetchJob?.cancel()
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

        dataFetchJob = viewModelScope.launch {
            isLoading = true
            loadingText = "Analisi micologica e ambientale in corso..."
            errorMessage = null
            isFromCache = false
            cacheAgeText = null
            isOfflineFieldMode = false

            // Generazione istantanea della nuvola locale in background (<10ms)
            // Sfrutta i dati SPUN residenti in memoria senza attendere 3-5 secondi di chiamate di rete
            launch(Dispatchers.Default) {
                val instant = HeatmapGenerator.generateHeatmap(
                    centerLat = lat,
                    centerLon = lon,
                    spunDataManager = spunDataManager,
                    baseWeatherScore = 100.0,
                    seasonalityScore = 1.0,
                    altitudeScore = 1.0,
                    species = selectedSpecies
                )
                if (instant != null) {
                    heatmapData = instant
                }
            }

            try {
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
                val spunDeferred = async { repository.fetchSpunData(lat, lon, searchRadius) }
                val terrainDeferred = async { repository.fetchTerrainAspect(lat, lon) }

                val weather = weatherDeferred.await()
                val habitat = habitatDeferred.await()
                val spunData = spunDeferred.await()
                val terrainData = terrainDeferred.await()
                val resolvedGeo = geocodeDeferred?.await() ?: cachedGeo

                val finalAgeMs = cacheManager.getWeatherCacheAge(lat, lon)
                val now = System.currentTimeMillis()
                dataAcquisitionTimestamp = if (finalAgeMs != null) now - finalAgeMs else now
                if (finalAgeMs != null && finalAgeMs > 60_000L) {
                    isFromCache = true
                    isOfflineFieldMode = finalAgeMs > 60 * 60 * 1000L
                    cacheAgeText = formatCacheAge(finalAgeMs)
                } else {
                    isFromCache = false
                    cacheAgeText = null
                    isOfflineFieldMode = false
                }

                val resolvedDisplayName = when {
                    resolvedGeo != null -> resolvedGeo.displayName
                    !isPlaceholder -> displayName
                    else -> displayName
                }

                // Calculate Habitat Evidence & Score using Species-Aware Ecological Evaluation (F08, F09)

                val evidence = repository.extractHabitatEvidence(habitat, lat, lon, searchRadius)
                lastHabitatEvidence = evidence


                // Acquisition stores evidence; the shared engine owns every scientific calculation.
                lastWeatherTimezone = weather.timezone
                val processedDays = MushroomAlgorithms.processWeatherData(weather)
                lastProcessedDays = processedDays
                lastTerrainData = terrainData
                lastElevation = weather.elevation
                lastSpunData = spunData
                lastLat = lat
                lastLon = lon
                lastDisplayName = resolvedDisplayName
                locationName = resolvedDisplayName
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
                recalculateForSpecies()

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

                updateCacheSize()

            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = e.message ?: "Errore durante il caricamento e l'analisi dei dati."
                isFromCache = false
                cacheAgeText = null
                isOfflineFieldMode = false
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

    fun checkDayChangeAndRefresh() {
        val lat = lastLat
        val lon = lastLon
        if (lat == 0.0 && lon == 0.0) return

        val zoneId = try {
            lastWeatherTimezone?.let { java.time.ZoneId.of(it) } ?: clock.zone
        } catch (_: Exception) {
            clock.zone
        }

        val todayDate = java.time.LocalDate.now(clock.withZone(zoneId)).toString()
        val isDayChanged = userSelectedHistoricalDate == null && targetAnalysisDate != null && targetAnalysisDate != todayDate
        val cacheAgeMs = cacheManager.getWeatherCacheAge(lat, lon)
        val isCacheExpired = cacheAgeMs != null && cacheAgeMs > 60 * 60 * 1000L // 1 hour TTL

        if (isDayChanged || isCacheExpired) {
            selectLocation(lat, lon, locationName, isGps = currentLocationIsGps)
        }
    }

    fun formatTimestampInLocationTz(timestampMs: Long?): String {
        if (timestampMs == null) return "Non disponibile"
        val zoneId = try {
            lastWeatherTimezone?.let { java.time.ZoneId.of(it) } ?: clock.zone
        } catch (_: Exception) {
            clock.zone
        }
        val instant = java.time.Instant.ofEpochMilli(timestampMs)
        val zonedDateTime = instant.atZone(zoneId)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm z", Locale.ITALIAN)
        return zonedDateTime.format(formatter)
    }

    fun formatTargetAnalysisDate(): String {
        val target = targetAnalysisDate ?: return "Data odierna"
        return try {
            val date = java.time.LocalDate.parse(target)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)
            date.format(formatter)
        } catch (_: Exception) {
            target
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationAndOrientationTracking()
        midnightWatcherJob?.cancel()
        dataFetchJob?.cancel()
        heatmapJob?.cancel()
        aiJob?.cancel()
    }
}
