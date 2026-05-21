package github.naturewhisp.myco.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SavedLocation
import github.naturewhisp.myco.network.LocalAiService
import github.naturewhisp.myco.repository.CacheManager
import github.naturewhisp.myco.repository.MushroomRepository
import github.naturewhisp.myco.utils.MushroomAlgorithms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class MushroomViewModel(
    private val repository: MushroomRepository,
    val cacheManager: CacheManager,
    val localAiService: LocalAiService
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
    var forecastDays by mutableStateOf<List<ProcessedDay>>(emptyList())
        private set

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
                    LocalAiService.Status.NOT_SUPPORTED -> "Non supportato da questo dispositivo"
                    LocalAiService.Status.INITIALIZING -> "Configurazione in corso..."
                    LocalAiService.Status.DOWNLOADING -> "Download modello in corso..."
                    LocalAiService.Status.DOWNLOAD_FAILED -> "Download modello fallito"
                    LocalAiService.Status.READY -> "Supportato e pronto"
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

    /** Flusso B: geolocalizzazione GPS — fa reverse geocoding e poi chiama selectLocation */
    fun selectLocationFromGps(lat: Double, lon: Double) {
        aiJob?.cancel()
        viewModelScope.launch {
            isLoading = true
            loadingText = "Rilevamento posizione GPS..."
            errorMessage = null
            showMap = false

            try {
                // Reverse geocoding per ottenere un nome significativo
                val geocoded = repository.reverseGeocode(lat, lon)
                val resolvedName = geocoded?.displayName ?: "Posizione GPS (${
                    String.format(Locale.US, "%.3f", lat)}, ${
                    String.format(Locale.US, "%.3f", lon)})"

                locationName = resolvedName
                selectedLatLng = Pair(lat, lon)
                showMap = true
                searchQuery = resolvedName.split(",").firstOrNull()?.trim() ?: resolvedName

                selectLocation(lat, lon, resolvedName, isGps = true)
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback senza reverse geocoding
                val fallbackName = "Posizione GPS"
                locationName = fallbackName
                selectedLatLng = Pair(lat, lon)
                showMap = true
                selectLocation(lat, lon, fallbackName, isGps = true)
            }
        }
    }

    /** Flusso A: tap sulla mappa — fa reverse geocoding per ottenere un nome reale, poi selectLocation */
    fun selectLocationFromMap(lat: Double, lon: Double) {
        aiJob?.cancel()
        viewModelScope.launch {
            isLoading = true
            loadingText = "Determinazione località..."
            errorMessage = null

            try {
                val geocoded = repository.reverseGeocode(lat, lon)
                val resolvedName = geocoded?.displayName ?: "Punto selezionato (${
                    String.format(Locale.US, "%.3f", lat)}, ${
                    String.format(Locale.US, "%.3f", lon)})"

                locationName = resolvedName
                selectedLatLng = Pair(lat, lon)
                showMap = true
                searchQuery = resolvedName.split(",").firstOrNull()?.trim() ?: resolvedName

                selectLocation(lat, lon, resolvedName, isGps = false)
            } catch (e: Exception) {
                e.printStackTrace()
                val fallbackName = "Punto selezionato"
                locationName = fallbackName
                selectedLatLng = Pair(lat, lon)
                showMap = true
                selectLocation(lat, lon, fallbackName, isGps = false)
            }
        }
    }

    /** Carica una località salvata dalla cronologia/preferiti */

    fun selectSavedLocation(loc: SavedLocation) {
        searchQuery = loc.shortName
        selectLocation(loc.lat, loc.lon, loc.displayName, isGps = loc.isGpsLocation)
    }

    /** Aggiunge/rimuove il punto corrente dai preferiti (solo se non è GPS) */
    fun toggleCurrentFavorite() {
        val latLng = selectedLatLng ?: return
        if (currentLocationIsGps) return
        val loc = SavedLocation(
            lat = latLng.first,
            lon = latLng.second,
            displayName = locationName,
            shortName = locationName.split(",").firstOrNull()?.trim() ?: locationName,
            savedAt = System.currentTimeMillis(),
            isFavorite = !currentLocationIsFavorite,
            isGpsLocation = false
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
        viewModelScope.launch {
            isLoading = true
            loadingText = "Analisi del punto selezionato in corso..."
            errorMessage = null
            selectedLatLng = Pair(lat, lon)
            showMap = true
            currentLocationIsGps = isGps

            try {
                // Check if weather is cached
                val roundedLat = String.format(Locale.US, "%.4f", lat)
                val roundedLon = String.format(Locale.US, "%.4f", lon)
                val weatherCacheKey = "weather_${roundedLat}_${roundedLon}"
                isFromCache = cacheManager.getCachedData(weatherCacheKey, github.naturewhisp.myco.model.WeatherResponse::class.java, 60 * 60 * 1000) != null

                // Cache age text
                val ageMs = cacheManager.getWeatherCacheAge(lat, lon)
                cacheAgeText = ageMs?.let { formatCacheAge(it) }

                // Check favorite status
                currentLocationIsFavorite = if (isGps) false else cacheManager.isFavorite(lat, lon)

                // Fetch weather and habitat details
                val weatherDeferred = async { repository.fetchWeather(lat, lon) }
                val habitatDeferred = async { repository.fetchHabitat(lat, lon) }
                val habitatBonusDeferred = async { repository.fetchSpecificHabitatBonus(lat, lon) }

                val weather = weatherDeferred.await()
                val habitat = habitatDeferred.await()
                val habitatBonus = habitatBonusDeferred.await()

                // Calculate Habitat Score
                val forestCount = habitat?.elements?.size ?: 0
                val habitatScore: Double
                val habitatBaseText: String
                when {
                    forestCount > 15 -> {
                        habitatScore = 1.0
                        habitatBaseText = "🌳 Habitat: Ideale (punto immerso in area boschiva)."
                    }
                    forestCount > 4 -> {
                        habitatScore = 0.95
                        habitatBaseText = "🌳 Habitat: Promettente (vicinanza a boschi e foreste)."
                    }
                    forestCount > 0 -> {
                        habitatScore = 0.6
                        habitatBaseText = "🌳 Habitat: Misto (presenza di aree verdi sparse)."
                    }
                    else -> {
                        habitatScore = 0.1
                        habitatBaseText = "🌳 Habitat: Non ideale (assenza di boschi nelle vicinanze)."
                    }
                }

                var finalHabitatScore = habitatScore
                var habitatBonusTextVal = "ℹ️ Bonus: Nessun dato vegetativo aggiuntivo rilevato."
                val specificForestCount = habitatBonus?.elements?.size ?: 0
                if (specificForestCount > 0) {
                    finalHabitatScore = min(1.0, habitatScore * 1.15)
                    habitatBonusTextVal = "✅ Bonus: Rilevati alberi ottimali! Punteggio habitat potenziato."
                }

                // Process weather
                val processedDays = MushroomAlgorithms.processWeatherData(weather)
                val todayIndex = 14
                
                // Forecast grid: next 5 days starting today (todayIndex to todayIndex+4)
                forecastDays = processedDays.subList(todayIndex, min(processedDays.size, todayIndex + 5))

                val elevation = weather.elevation
                val altitudeScore = MushroomAlgorithms.calculateAltitudeScore(elevation)
                val calendar = Calendar.getInstance()
                val currentMonth = calendar.get(Calendar.MONTH) // 0-indexed
                val seasonalityScore = MushroomAlgorithms.calculateSeasonalityScore(currentMonth)
                val growthPhaseVal = MushroomAlgorithms.calculateGrowthPhase(processedDays)
                val moonPhase = MushroomAlgorithms.getMoonPhase()

                // Rain window calculation (last 10 days to 2 days ago)
                val rainStart = max(0, todayIndex - 10)
                val rainEnd = max(0, todayIndex - 2)
                val rainWindow = processedDays.subList(rainStart, rainEnd)
                val totalRainLast10Days = rainWindow.sumOf { it.totalPrecip.toDouble() }
                val rainStatus = MushroomAlgorithms.getRainStatus(totalRainLast10Days)
                val rainTextVal = "🌧️ Pioggia: ${totalRainLast10Days.toInt()}mm (${rainStatus.label})"

                // Temp window calculation (last 5 days)
                val tempStart = max(0, todayIndex - 5)
                val tempWindow = processedDays.subList(tempStart, todayIndex)
                val avgTempLast5Days = if (tempWindow.isNotEmpty()) {
                    tempWindow.sumOf { it.avgTemp.toDouble() } / tempWindow.size
                } else {
                    0.0
                }
                val tempStatus = MushroomAlgorithms.getTempStatus(avgTempLast5Days)
                val tempTextVal = String.format(Locale.ITALIAN, "🌡️ Temp. media: %.1f°C (%s)", avgTempLast5Days, tempStatus.label)

                val slopeTextVal = MushroomAlgorithms.getSlopeRecommendation(seasonalityScore.score, avgTempLast5Days, currentMonth)

                // Today's weather score
                val rawWeatherScore = MushroomAlgorithms.calculateWeatherScore(todayIndex, processedDays)
                val weightedWeatherScore = 100.0 * Math.pow(rawWeatherScore / 100.0, 1.2)
                
                // Probability calculation
                val probability = (weightedWeatherScore * finalHabitatScore * altitudeScore.score * seasonalityScore.score).toInt()
                todayProbability = max(0, min(100, probability))

                // Assign states
                locationName = displayName
                growthPhase = growthPhaseVal
                habitatText = habitatBaseText
                habitatBonusText = habitatBonusTextVal
                altitudeText = altitudeScore.text
                seasonText = seasonalityScore.text
                rainText = rainTextVal
                tempText = tempTextVal
                moonPhaseText = "🌙 Luna: ${moonPhase.text} (${if (moonPhase.favorable) "Favorevole" else "Ininfluente"})"
                moonPhaseEmoji = moonPhase.emoji
                slopeText = slopeTextVal

                val futureTrend = MushroomAlgorithms.analyzeFutureTrend(processedDays)

                // Salva nella cronologia recenti (solo se nome significativo)
                val isPlaceholder = displayName == "Punto selezionato" || 
                                    displayName == "Posizione GPS" || 
                                    displayName.startsWith("Punto selezionato") || 
                                    displayName.startsWith("Posizione GPS")
                if (!isPlaceholder) {
                    val shortName = displayName.split(",").firstOrNull()?.trim() ?: displayName
                    val savedLoc = SavedLocation(
                        lat = lat,
                        lon = lon,
                        displayName = displayName,
                        shortName = shortName,
                        savedAt = System.currentTimeMillis(),
                        isFavorite = currentLocationIsFavorite,
                        isGpsLocation = isGps
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
                            val prompt = """
                                Sei un esperto micologo. Genera un'analisi in parole semplici in lingua italiana basandoti su questi dati:
                                - Località: $displayName
                                - Habitat: $habitatBaseText (Punteggio: $finalHabitatScore/1.0)
                                - Altitudine: ${altitudeScore.text} (Punteggio: ${altitudeScore.score}/1.0)
                                - Stagione: ${seasonalityScore.text} (Punteggio: ${seasonalityScore.score}/1.0)
                                - Pioggia ultimi 10 giorni: $rainTextVal
                                - Temperatura media ultimi 5 giorni: $tempTextVal
                                - Luna: ${moonPhase.text} (${if (moonPhase.favorable) "Favorevole" else "Ininfluente"})
                                - Esposizione versante consigliata: $slopeTextVal
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
                                    futureTrend = futureTrend
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
                                futureTrend = futureTrend
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
                        futureTrend = futureTrend
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
}
