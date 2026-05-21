package github.naturewhisp.myco.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.network.LocalAiService
import github.naturewhisp.myco.repository.CacheManager
import github.naturewhisp.myco.repository.MushroomRepository
import github.naturewhisp.myco.utils.MushroomAlgorithms
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
    var loadingText by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var showMap by mutableStateOf(false)
        private set
    var showSettings by mutableStateOf(false)

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
                        selectLocation(lat, lon, result.displayName)
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

    fun selectLocation(lat: Double, lon: Double, displayName: String = "Punto selezionato") {
        viewModelScope.launch {
            isLoading = true
            loadingText = "Analisi del punto selezionato in corso..."
            errorMessage = null
            selectedLatLng = Pair(lat, lon)
            showMap = true

            try {
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

                var localAiSummary: String? = null
                if (useLocalAi && localAiService.isAvailable()) {
                    loadingText = "Ottimizzazione con IA on-device..."

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

                        Genera un riassunto di massimo 4 frasi, in tono professionale da micologo, spiegando le probabilità e i fattori favorevoli o sfavorevoli per la crescita dei funghi porcini. Non aggiungere preamboli o saluti.
                    """.trimIndent()

                    localAiSummary = localAiService.generateAdvancedSummary(prompt)
                }

                summaryText = localAiSummary ?: MushroomAlgorithms.generateSummaryText(
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

                updateCacheSize()

            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = e.message ?: "Errore durante il caricamento e l'analisi dei dati."
            } finally {
                isLoading = false
            }
        }
    }
}
