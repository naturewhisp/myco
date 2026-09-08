package github.naturewhisp.myco.utils

import github.naturewhisp.myco.model.DailyOutlook
import github.naturewhisp.myco.model.Factor
import github.naturewhisp.myco.model.FactorId
import github.naturewhisp.myco.model.FactorLevel
import github.naturewhisp.myco.model.MushroomSpecies
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.model.WeatherResponse
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ScoreResult(val score: Double, val text: String)
data class MoonPhaseResult(val text: String, val code: String, val favorable: Boolean) {
    val emoji: String get() = code
}
data class RainStatus(val score: Int, val label: String)
data class TempStatus(val score: Int, val label: String)

object MushroomAlgorithms {

    fun processWeatherData(data: WeatherResponse): List<ProcessedDay> {
        val dailyMap = mutableMapOf<String, TempDailyAccumulator>()
        val hourlyTimes = data.hourly.time
        for (i in hourlyTimes.indices) {
            val date = hourlyTimes[i].split("T")[0]
            val acc = dailyMap.getOrPut(date) { TempDailyAccumulator() }
            acc.temps.add(data.hourly.temperature2m.getOrElse(i) { 0.0f })
            acc.precips.add(data.hourly.precipitation.getOrElse(i) { 0.0f })
            acc.humidities.add(data.hourly.relativeHumidity2m.getOrElse(i) { 0.0f })
        }

        val dailyTimes = data.daily.time
        for (i in dailyTimes.indices) {
            val date = dailyTimes[i]
            val acc = dailyMap[date]
            if (acc != null) {
                acc.weatherCode = data.daily.weatherCode.getOrNull(i)
            }
        }

        return dailyMap.map { (date, acc) ->
            val avgTemp = if (acc.temps.isNotEmpty()) acc.temps.sum() / acc.temps.size else 0.0f
            val totalPrecip = acc.precips.sum()
            val avgHumidity = if (acc.humidities.isNotEmpty()) acc.humidities.sum() / acc.humidities.size else 0.0f
            ProcessedDay(
                date = date,
                avgTemp = avgTemp,
                totalPrecip = totalPrecip,
                avgHumidity = avgHumidity,
                weatherCode = acc.weatherCode
            )
        }.sortedBy { it.date }
    }

    private class TempDailyAccumulator {
        val temps = mutableListOf<Float>()
        val precips = mutableListOf<Float>()
        val humidities = mutableListOf<Float>()
        var weatherCode: Int? = null
    }

    // Weather thresholds mapping
    fun getRainStatus(totalRain: Double): RainStatus {
        return when {
            totalRain >= 40.0 -> RainStatus(40, "Ottimale")
            totalRain >= 25.0 -> RainStatus(35, "Molto buona")
            totalRain >= 15.0 -> RainStatus(25, "Buona")
            totalRain >= 5.0 -> RainStatus(10, "Sufficiente")
            else -> RainStatus(0, "Scarsa")
        }
    }

    fun getTempStatus(avgTemp: Double): TempStatus {
        return when {
            avgTemp >= 14.0 && avgTemp <= 22.0 -> TempStatus(30, "Ideale")
            avgTemp >= 10.0 && avgTemp <= 25.0 -> TempStatus(15, "Favorevole")
            avgTemp < 10.0 -> TempStatus(0, "Troppo freddo")
            else -> TempStatus(0, "Troppo caldo")
        }
    }

    fun getHumidityScore(avgHumidity: Double): Int {
        return when {
            avgHumidity >= 85.0 -> 15
            avgHumidity >= 75.0 -> 10
            else -> 0
        }
    }

    fun calculateAltitudeScore(elevation: Float): ScoreResult {
        val score: Double
        val desc: String
        when {
            elevation < 200 -> {
                score = 0.7
                desc = "Bassa, impatto moderato"
            }
            elevation < 400 -> {
                score = 0.9
                desc = "Collinare, favorevole"
            }
            elevation <= 1400 -> {
                score = 1.0
                desc = "Ideale"
            }
            elevation <= 1800 -> {
                score = 0.9
                desc = "Montana, buona ma con stagione breve"
            }
            else -> {
                score = 0.6
                desc = "Elevata, meno favorevole"
            }
        }
        return ScoreResult(score, "Altitudine: ${elevation.toInt()}m ($desc).")
    }

    fun calculateSeasonalityScore(month: Int): ScoreResult {
        // month is 0-indexed (0 = Gennaio, 11 = Dicembre)
        val monthsNames = listOf(
            "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
            "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
        )
        val monthName = monthsNames.getOrNull(month) ?: "Sconosciuto"

        val score: Double
        val desc: String
        when (month) {
            8, 9 -> { // September, October
                score = 1.0
                desc = "Picco della stagione"
            }
            4, 5 -> { // May, June
                score = 0.9
                desc = "Buona stagione primaverile"
            }
            10 -> { // November
                score = 0.7
                desc = "Fine stagione, possibile con clima mite"
            }
            6, 7 -> { // July, August
                score = 0.5
                desc = "Estivo, crescita legata a temporali"
            }
            3 -> { // April
                score = 0.4
                desc = "Inizio stagione, ancora presto"
            }
            else -> {
                score = 0.1
                desc = "Fuori stagione"
            }
        }
        return ScoreResult(score, "Stagione: $monthName ($desc).")
    }

    fun getMoonPhase(date: Date = Date()): MoonPhaseResult {
        // Known New Moon: 2000-01-06T18:14:00Z
        val knownNewMoonMs = 947182440000L
        val daysSinceKnownNewMoon = (date.time - knownNewMoonMs).toDouble() / (1000 * 60 * 60 * 24)
        val lunarCycleDays = 29.53058867
        var currentCyclePos = daysSinceKnownNewMoon % lunarCycleDays
        if (currentCyclePos < 0) {
            currentCyclePos += lunarCycleDays
        }

        val phaseText: String
        val code: String
        val favorable: Boolean

        when {
            currentCyclePos < 1.845 -> {
                phaseText = "Luna Nuova"
                code = "NEW_MOON"
                favorable = true
            }
            currentCyclePos < 5.535 -> {
                phaseText = "Crescente"
                code = "WAXING_CRESCENT"
                favorable = true
            }
            currentCyclePos < 9.225 -> {
                phaseText = "Primo Quarto"
                code = "FIRST_QUARTER"
                favorable = false
            }
            currentCyclePos < 12.915 -> {
                phaseText = "Gibbosa Crescente"
                code = "WAXING_GIBBOUS"
                favorable = false
            }
            currentCyclePos < 16.605 -> {
                phaseText = "Luna Piena"
                code = "FULL_MOON"
                favorable = false
            }
            currentCyclePos < 20.295 -> {
                phaseText = "Gibbosa Calante"
                code = "WANING_GIBBOUS"
                favorable = false
            }
            currentCyclePos < 23.985 -> {
                phaseText = "Ultimo Quarto"
                code = "LAST_QUARTER"
                favorable = false
            }
            else -> {
                phaseText = "Calante"
                code = "WANING_CRESCENT"
                favorable = false
            }
        }
        return MoonPhaseResult(phaseText, code, favorable)
    }

    fun calculateWeatherScore(
        dayIndex: Int,
        allData: List<ProcessedDay>,
        spunHyphalDensity: Float? = null,
        species: MushroomSpecies = SPECIES_CATALOG[0]
    ): Int {
        if (dayIndex < 0 || dayIndex >= allData.size) return 0

        // Calcolo continuo della pioggia cumulata (finestra da 10 a 2 giorni fa)
        val rainStart = max(0, dayIndex - 10)
        val rainEnd = max(0, dayIndex - 2)
        val rainWindow = if (rainStart < rainEnd && rainEnd <= allData.size) {
            allData.subList(rainStart, rainEnd)
        } else {
            emptyList()
        }
        val totalRainLast10Days = rainWindow.sumOf { it.totalPrecip.toDouble() }
        var rainScore = rainScoreSmooth(totalRainLast10Days, species) * 40.0

        // Modulatore biologico SPUN: rete ifale densa (>5.0 m/cm3) amplifica la risposta a piogge moderate
        if (spunHyphalDensity != null && spunHyphalDensity >= 5.0f && totalRainLast10Days >= 12.0) {
            rainScore = min(40.0, rainScore + 6.0)
        } else if (spunHyphalDensity != null && spunHyphalDensity < 2.5f) {
            rainScore = max(0.0, rainScore - 4.0)
        }

        // Calcolo continuo della temperatura media (finestra ultimi 5 giorni)
        val tempStart = max(0, dayIndex - 5)
        val tempWindow = if (tempStart < dayIndex && dayIndex <= allData.size) {
            allData.subList(tempStart, dayIndex)
        } else {
            emptyList()
        }
        val avgTempLast5Days = if (tempWindow.isNotEmpty()) {
            tempWindow.sumOf { it.avgTemp.toDouble() } / tempWindow.size
        } else {
            0.0
        }
        val tempScore = tempScoreSmooth(avgTempLast5Days, species) * 30.0

        // Calcolo continuo dell'umidità relativa (finestra 3 giorni fino a oggi)
        val humStart = max(0, dayIndex - 3)
        val humEnd = min(allData.size, dayIndex + 1)
        val humWindow = if (humStart < humEnd) {
            allData.subList(humStart, humEnd)
        } else {
            emptyList()
        }
        val avgHumidityRecent = if (humWindow.isNotEmpty()) {
            humWindow.sumOf { it.avgHumidity.toDouble() } / humWindow.size
        } else {
            0.0
        }
        val humScore = humidityScoreSmooth(avgHumidityRecent) * 15.0

        // Calcolo continuo dello shock termico induttivo dei primordi
        var shockScore = 0.0
        if (dayIndex > 4 && totalRainLast10Days >= 12.0) {
            val tempBefore = allData[dayIndex - 4].avgTemp
            val tempAfter = allData[dayIndex - 1].avgTemp
            val drop = (tempBefore - tempAfter).toDouble()
            val minDrop = if (spunHyphalDensity != null && spunHyphalDensity >= 5.0f) 2.0 else 3.0
            if (drop > minDrop) {
                val dropFactor = ((drop - minDrop) / 3.0).coerceIn(0.0, 1.0)
                val rainFactor = (totalRainLast10Days / 25.0).coerceIn(0.0, 1.0)
                shockScore = 15.0 * dropFactor * rainFactor
            }
        }

        return (rainScore + tempScore + humScore + shockScore).coerceIn(0.0, 100.0).roundToInt()
    }

    fun calculateGrowthPhase(processedData: List<ProcessedDay>): String {
        val todayIndex = 14
        if (processedData.size <= todayIndex) {
            return "Fase: Dati insufficienti per il calcolo fenologico."
        }

        var triggerDayIndex = -1
        for (i in todayIndex downTo 0) {
            if (i < processedData.size && processedData[i].totalPrecip >= 12.0f) {
                triggerDayIndex = i
                break
            }
            if (i >= 2 && i < processedData.size) {
                val threeDayRain = processedData[i].totalPrecip +
                        processedData[i - 1].totalPrecip +
                        processedData[i - 2].totalPrecip
                if (threeDayRain >= 18.0f) {
                    triggerDayIndex = i - 2
                    break
                }
            }
        }

        if (triggerDayIndex == -1) {
            return "Fase: Crescita assente (in attesa di precipitazioni)."
        }

        val daysSinceTrigger = todayIndex - triggerDayIndex
        return when {
            daysSinceTrigger <= 3 -> {
                "Fase: Idratazione miceliare (piogge recenti $daysSinceTrigger giorni fa)."
            }
            daysSinceTrigger <= 7 -> {
                val daysToFruiting = 8 - daysSinceTrigger
                "Fase: Incubazione primordi (differenziazione in $daysToFruiting-${daysToFruiting + 2} giorni)."
            }
            daysSinceTrigger <= 14 -> {
                "Fase: Buttata attiva (finestra ottimale di raccolta)."
            }
            else -> {
                "Fase: Flusso in esaurimento (in attesa di nuove piogge)."
            }
        }
    }

    fun getSlopeRecommendation(seasonalityScore: Double, avgTemp: Double, month: Int): String {
        return when {
            seasonalityScore < 0.2 -> "Indifferente (Fuori stagione fenologica)"
            month in 5..7 -> "Nord (Versanti più freschi e umidi)"
            month == 4 || month >= 8 -> "Sud (Versanti più caldi e soleggiati)"
            else -> "Est (Soleggiamento mattutino)"
        }
    }

    fun analyzeFutureTrend(processedData: List<ProcessedDay>): String {
        val todayIndex = 14
        if (processedData.size < todayIndex + 6) return ""

        val futureWindow = processedData.subList(todayIndex + 1, todayIndex + 6)
        val futureRain = futureWindow.sumOf { it.totalPrecip.toDouble() }

        return when {
            futureRain > 15 -> {
                "Inoltre, le piogge significative previste nei prossimi giorni potrebbero innescare una **nuova e promettente 'buttata'** tra circa 7-10 giorni."
            }
            futureRain < 2 -> {
                "Guardando al futuro, il tempo si manterrà stabile e asciutto. Questo significa che l'umidità del terreno calerà, **riducendo gradualmente il potenziale di crescita** se non arriveranno nuove piogge."
            }
            else -> {
                "Nei prossimi giorni il tempo si manterrà variabile ma senza piogge decisive, quindi la situazione di crescita dovrebbe rimanere simile a quella attuale."
            }
        }
    }

    fun generateSummaryText(
        weatherScore: Double,
        habitatScore: Double,
        habitatText: String,
        altitudeScore: Double,
        altitudeText: String,
        seasonalityScore: Double,
        seasonalityText: String,
        totalRain: Double,
        futureTrend: String,
        spunEcmText: String? = null,
        spunHyphalText: String? = null
    ): String {
        var summary = ""
        val scores = listOf(
            Triple("habitat", habitatScore, if (habitatText.contains("Ideale")) "l'habitat ideale" else "l'habitat promettente"),
            Triple("season", seasonalityScore, "la stagione, che è al suo picco"),
            Triple("altitude", altitudeScore, "l'altitudine"),
            Triple("weather", weatherScore / 100.0, "le condizioni meteo")
        )

        val overallPotential = (weatherScore / 100.0) * habitatScore * altitudeScore * seasonalityScore
        summary += when {
            overallPotential > 0.6 -> "Il potenziale generale è ottimo. "
            overallPotential > 0.3 -> "Le condizioni generali sono buone. "
            else -> "Il potenziale di crescita è moderato. "
        }

        val strongest = scores.filter { it.second >= 0.95 }.sortedByDescending { it.second }
        if (strongest.size > 1) {
            val strongPoints = strongest.map { it.third }.joinToString(" e ")
            summary += "I punti di forza sono $strongPoints, che creano una base eccellente. "
        } else if (strongest.size == 1) {
            summary += "Il punto di forza principale è ${strongest[0].third}. "
        }

        val limiting = scores.filter { it.second < 0.9 }.sortedBy { it.second }
        if (limiting.isNotEmpty()) {
            val mainLimiter = limiting[0]
            summary += "Tuttavia, "
            summary += when (mainLimiter.first) {
                "weather" -> "la pioggia solo sufficiente (${totalRain.toInt()}mm) limita il potenziale di una 'buttata' più abbondante, mantenendo le probabilità su questi livelli. "
                "altitude" -> "l'altitudine non perfettamente ideale sta frenando leggermente il risultato finale. "
                "habitat" -> "l'habitat non ottimale è il principale fattore limitante. "
                "season" -> "la stagione non è ancora al suo picco, e questo è il principale fattore limitante. "
                else -> ""
            }
        } else {
            summary += "Tutti i fattori sono allineati in modo ottimale per una buona crescita. "
        }

        // Biological SPUN insight
        if (!spunHyphalText.isNullOrEmpty() && !spunEcmText.isNullOrEmpty()) {
            summary += "A livello sotterraneo ($spunEcmText, $spunHyphalText), la rete micorrizica offre un supporto biologico scientificamente documentato. "
        }

        summary += futureTrend
        return summary
    }

    // Calcolo continuo della temperatura rispetto al profilo biologico della specie
    fun tempScoreSmooth(temp: Double, species: MushroomSpecies = SPECIES_CATALOG[0]): Double {
        return when {
            temp < species.toleratedTempMin || temp > species.toleratedTempMax -> 0.0
            temp in species.idealTempMin.toDouble()..species.idealTempMax.toDouble() -> 1.0
            temp < species.idealTempMin -> {
                val span = species.idealTempMin - species.toleratedTempMin
                if (span > 0) (temp - species.toleratedTempMin) / span else 0.0
            }
            else -> {
                val span = species.toleratedTempMax - species.idealTempMax
                if (span > 0) (species.toleratedTempMax - temp) / span else 0.0
            }
        }.coerceIn(0.0, 1.0)
    }

    // Calcolo continuo della pioggia cumulata
    fun rainScoreSmooth(rainMm: Double, species: MushroomSpecies = SPECIES_CATALOG[0]): Double {
        val target = species.minRainAccumulation.toDouble()
        return when {
            rainMm <= 0.0 -> 0.0
            rainMm >= target -> 1.0
            target > 0.0 -> (rainMm / target).coerceIn(0.0, 1.0)
            else -> 1.0
        }
    }

    // Calcolo continuo dell'umidità relativa
    fun humidityScoreSmooth(humidity: Double): Double {
        return when {
            humidity < 50.0 -> 0.0
            humidity >= 85.0 -> 1.0
            else -> ((humidity - 50.0) / 35.0).coerceIn(0.0, 1.0)
        }
    }

    // Altitudine calibrata sulla specie
    fun calculateSpeciesAltitudeScore(elevation: Float, species: MushroomSpecies): ScoreResult {
        val score = when {
            elevation < species.minElevation || elevation > species.maxElevation -> 0.4
            elevation in species.idealElevationMin.toFloat()..species.idealElevationMax.toFloat() -> 1.0
            elevation < species.idealElevationMin -> {
                val span = species.idealElevationMin - species.minElevation
                if (span > 0) 0.6 + 0.4 * ((elevation - species.minElevation).toDouble() / span) else 0.6
            }
            else -> {
                val span = species.maxElevation - species.idealElevationMax
                if (span > 0) 0.6 + 0.4 * ((species.maxElevation - elevation).toDouble() / span) else 0.6
            }
        }.coerceIn(0.0, 1.0)

        val desc = when {
            score >= 0.95 -> "Fascia ottimale"
            score >= 0.75 -> "Favorevole"
            else -> "Limite altimetrico"
        }
        return ScoreResult(score, "Altitudine: ${elevation.toInt()} m ($desc)")
    }

    // Stagionalità calibrata sui mesi attivi della specie
    fun calculateSpeciesSeasonalityScore(month: Int, species: MushroomSpecies): ScoreResult {
        val monthNames = listOf(
            "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
            "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
        )
        val monthName = monthNames.getOrNull(month) ?: "Sconosciuto"
        val isActive = species.activeMonths.contains(month)
        val isAdjacent = species.activeMonths.any { abs(it - month) == 1 || abs(it - month) == 11 }

        val (score, desc) = when {
            isActive -> 1.0 to "Picco stagionale"
            isAdjacent -> 0.6 to "Inizio o chiusura stagione"
            else -> 0.1 to "Fuori stagione"
        }
        return ScoreResult(score, "Stagione: $monthName ($desc)")
    }

    // Calcolo della probabilità giornaliera continua combinata
    fun dailyGrowthProbability(
        weatherScore: Int,
        habitatScore: Double,
        altitudeScore: Double,
        seasonalityScore: Double
    ): Int {
        val weightedWeatherScore = 100.0 * Math.pow(weatherScore / 100.0, 1.2)
        val combined = weightedWeatherScore * habitatScore * altitudeScore * seasonalityScore
        return combined.toInt().coerceIn(0, 100)
    }

    // Genera la lista tipizzata dei fattori ecologici e ambientali per la visualizzazione Herbarium
    fun calculateFactors(
        avgTemp: Double,
        totalRain: Double,
        avgHumidity: Double,
        habitatScore: Double,
        habitatText: String,
        elevation: Float,
        month: Int,
        growthPhaseText: String,
        moon: MoonPhaseResult,
        slopeText: String,
        species: MushroomSpecies = SPECIES_CATALOG[0],
        spunEcmText: String? = null,
        spunHyphalText: String? = null
    ): List<Factor> {
        val factors = mutableListOf<Factor>()

        // 1. Temperatura
        val tempNorm = tempScoreSmooth(avgTemp, species)
        val tempLevel = when {
            tempNorm >= 0.8 -> FactorLevel.FAVORABLE
            tempNorm >= 0.4 -> FactorLevel.NEUTRAL
            else -> FactorLevel.ADVERSE
        }
        factors.add(
            Factor(
                id = FactorId.TEMPERATURE,
                label = "Temperatura media",
                formattedValue = String.format(Locale.ITALIAN, "%.1f°C", avgTemp),
                level = tempLevel,
                detail = if (tempLevel == FactorLevel.FAVORABLE) "Range termico ideale" else "Range non ottimale"
            )
        )

        // 2. Precipitazioni
        val rainNorm = rainScoreSmooth(totalRain, species)
        val rainLevel = when {
            rainNorm >= 0.8 -> FactorLevel.FAVORABLE
            rainNorm >= 0.4 -> FactorLevel.NEUTRAL
            else -> FactorLevel.ADVERSE
        }
        factors.add(
            Factor(
                id = FactorId.PRECIPITATION,
                label = "Precipitazioni cumulate",
                formattedValue = String.format(Locale.ITALIAN, "%.0f mm", totalRain),
                level = rainLevel,
                detail = "Ultime 2 settimane"
            )
        )

        // 3. Umidità
        val humNorm = humidityScoreSmooth(avgHumidity)
        val humLevel = when {
            humNorm >= 0.7 -> FactorLevel.FAVORABLE
            humNorm >= 0.4 -> FactorLevel.NEUTRAL
            else -> FactorLevel.ADVERSE
        }
        factors.add(
            Factor(
                id = FactorId.HUMIDITY,
                label = "Umidità relativa",
                formattedValue = String.format(Locale.ITALIAN, "%.0f%%", avgHumidity),
                level = humLevel,
                detail = "Sensori suolo e aria"
            )
        )

        // 4. Habitat & Adattamento ecologico per specie saprofite da prato
        val isSaprotrophic = species.category == github.naturewhisp.myco.model.EcologicalCategory.SAPROTROPHIC
        val effectiveHabScore = if (isSaprotrophic) max(habitatScore, 0.85) else habitatScore
        val habLevel = when {
            effectiveHabScore >= 0.85 -> FactorLevel.FAVORABLE
            effectiveHabScore >= 0.5 -> FactorLevel.NEUTRAL
            else -> FactorLevel.ADVERSE
        }
        val cleanHabText = habitatText.replace("Habitat: ", "").trim()
        factors.add(
            Factor(
                id = FactorId.HABITAT,
                label = if (isSaprotrophic) "Idoneità suolo/margine" else "Copertura forestale",
                formattedValue = String.format(Locale.ITALIAN, "%.0f%%", effectiveHabScore * 100),
                level = habLevel,
                detail = if (isSaprotrophic && habitatScore < 0.4) "Habitat praticolo e lettiera organica" else cleanHabText
            )
        )

        // 5. Altitudine
        val altScore = calculateSpeciesAltitudeScore(elevation, species)
        val altLevel = when {
            altScore.score >= 0.85 -> FactorLevel.FAVORABLE
            altScore.score >= 0.6 -> FactorLevel.NEUTRAL
            else -> FactorLevel.ADVERSE
        }
        val altDetail = altScore.text.substringAfter("(").substringBefore(")").ifEmpty { "Fascia collinare/montana" }
        factors.add(
            Factor(
                id = FactorId.ALTITUDE,
                label = "Fascia altimetrica",
                formattedValue = "${elevation.toInt()} m",
                level = altLevel,
                detail = altDetail
            )
        )

        // 6. Stagionalità
        val seasonScore = calculateSpeciesSeasonalityScore(month, species)
        val seasonLevel = when {
            seasonScore.score >= 0.85 -> FactorLevel.FAVORABLE
            seasonScore.score >= 0.5 -> FactorLevel.NEUTRAL
            else -> FactorLevel.ADVERSE
        }
        factors.add(
            Factor(
                id = FactorId.SEASONALITY,
                label = "Finestra fenologica",
                formattedValue = seasonScore.text.substringAfter(": ").substringBefore(" ("),
                level = seasonLevel,
                detail = seasonScore.text.substringAfter("(").substringBefore(")")
            )
        )

        // 7. Fase miceliare
        val cleanPhase = growthPhaseText.replace("Fase: ", "").trim()
        val phaseName = cleanPhase.substringBefore(" (")
        val phaseDetail = cleanPhase.substringAfter("(", "").replace(")", "").ifEmpty { "Cronologia e latenza piogge" }
        factors.add(
            Factor(
                id = FactorId.MYCELIAL_PHASE,
                label = "Stato miceliare",
                formattedValue = phaseName,
                level = if (cleanPhase.contains("ottimale") || cleanPhase.contains("attiva") || cleanPhase.contains("Idratazione")) FactorLevel.FAVORABLE else FactorLevel.NEUTRAL,
                detail = phaseDetail
            )
        )

        // 8. Fase lunare
        factors.add(
            Factor(
                id = FactorId.LUNAR_PHASE,
                label = "Fase lunare",
                formattedValue = moon.text,
                level = if (moon.favorable) FactorLevel.FAVORABLE else FactorLevel.INFORMATIVE,
                detail = if (moon.favorable) "Fase crescente propizia" else "Influenza neutra",
                iconGlyph = null
            )
        )

        // 9. Versante orografico
        val slopeVal = slopeText.substringBefore(" (").replace("Versante: ", "").trim()
        val slopeDetail = slopeText.substringAfter("(", "").replace(")", "").trim().ifEmpty { "Orientamento orografico" }
        factors.add(
            Factor(
                id = FactorId.SLOPE,
                label = "Esposizione versante",
                formattedValue = slopeVal,
                level = FactorLevel.INFORMATIVE,
                detail = slopeDetail
            )
        )

        // 10. SPUN Biodiversità micorrizica
        if (!spunEcmText.isNullOrEmpty()) {
            val numSpecie = spunEcmText.filter { it.isDigit() }.ifEmpty { "60" }
            val ecmQuality = spunEcmText.substringAfter("(").substringBefore(")").ifEmpty { "Favorevole" }
            factors.add(
                Factor(
                    id = FactorId.SPUN_ECM,
                    label = "Simbiosi ectomicorrizica",
                    formattedValue = "$numSpecie specie",
                    level = FactorLevel.FAVORABLE,
                    detail = "$ecmQuality • Atlante SPUN"
                )
            )
        }
        if (!spunHyphalText.isNullOrEmpty()) {
            val hyphalNum = spunHyphalText.substringAfter(": ").substringBefore(" m").trim()
            val hyphalVitality = spunHyphalText.substringAfter("(").substringBefore(")").ifEmpty { "Attiva" }
            factors.add(
                Factor(
                    id = FactorId.SPUN_HYPHAL,
                    label = "Biomassa ifale sotterranea",
                    formattedValue = if (hyphalNum.isNotEmpty()) "$hyphalNum m/cm³" else "Attiva",
                    level = FactorLevel.FAVORABLE,
                    detail = "$hyphalVitality • Densità miceliare"
                )
            )
        }

        return factors
    }

    // Calcola le previsioni giornaliere per tutti i giorni previsionali
    fun calculateDailyOutlooks(
        processedDays: List<ProcessedDay>,
        startIndex: Int = 14,
        species: MushroomSpecies = SPECIES_CATALOG[0],
        habitatScore: Double = 1.0,
        elevation: Float = 800f,
        month: Int = 9,
        spunHyphalDensity: Float? = null
    ): List<DailyOutlook> {
        if (processedDays.isEmpty()) return emptyList()
        val altScore = calculateSpeciesAltitudeScore(elevation, species).score
        val seasonScore = calculateSpeciesSeasonalityScore(month, species).score
        val result = mutableListOf<DailyOutlook>()

        for (i in startIndex until processedDays.size) {
            val weatherScore = calculateWeatherScore(i, processedDays, spunHyphalDensity, species)
            val prob = dailyGrowthProbability(weatherScore, habitatScore, altScore, seasonScore)
            result.add(DailyOutlook.fromProcessedDay(processedDays[i], prob))
        }
        return result
    }
}
