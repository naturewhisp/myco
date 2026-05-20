package github.naturewhisp.myco.utils

import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.WeatherResponse
import java.util.Calendar
import java.util.Date
import kotlin.math.max
import kotlin.math.min

data class ScoreResult(val score: Double, val text: String)
data class MoonPhaseResult(val text: String, val emoji: String, val favorable: Boolean)
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
        return ScoreResult(score, "🏔️ Altitudine: ${elevation.toInt()}m ($desc).")
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
        return ScoreResult(score, "🗓️ Stagione: $monthName ($desc).")
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
        val emoji: String
        val favorable: Boolean

        when {
            currentCyclePos < 1.845 -> {
                phaseText = "Luna Nuova"
                emoji = "🌑"
                favorable = true
            }
            currentCyclePos < 5.535 -> {
                phaseText = "Crescente"
                emoji = "🌒"
                favorable = true
            }
            currentCyclePos < 9.225 -> {
                phaseText = "Primo Quarto"
                emoji = "🌓"
                favorable = false
            }
            currentCyclePos < 12.915 -> {
                phaseText = "Gibbosa Crescente"
                emoji = "🌔"
                favorable = false
            }
            currentCyclePos < 16.605 -> {
                phaseText = "Luna Piena"
                emoji = "🌕"
                favorable = false
            }
            currentCyclePos < 20.295 -> {
                phaseText = "Gibbosa Calante"
                emoji = "🌖"
                favorable = false
            }
            currentCyclePos < 23.985 -> {
                phaseText = "Ultimo Quarto"
                emoji = "🌗"
                favorable = false
            }
            else -> {
                phaseText = "Calante"
                emoji = "🌘"
                favorable = false
            }
        }
        return MoonPhaseResult(phaseText, emoji, favorable)
    }

    fun calculateWeatherScore(dayIndex: Int, allData: List<ProcessedDay>): Int {
        if (dayIndex < 0 || dayIndex >= allData.size) return 0
        var score = 0

        // Rain Score: last 10 days to 2 days ago (equivalent to JS: slice(max(0, index-10), index-2))
        val rainStart = max(0, dayIndex - 10)
        val rainEnd = max(0, dayIndex - 2)
        val rainWindow = if (rainStart < rainEnd && rainEnd <= allData.size) {
            allData.subList(rainStart, rainEnd)
        } else {
            emptyList()
        }
        val totalRainLast10Days = rainWindow.sumOf { it.totalPrecip.toDouble() }
        score += getRainStatus(totalRainLast10Days).score

        // Temp Score: last 5 days (equivalent to JS: slice(max(0, index-5), index))
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
        score += getTempStatus(avgTempLast5Days).score

        // Humidity Score: last 3 days to today (equivalent to JS: slice(max(0, index-3), index+1))
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
        score += getHumidityScore(avgHumidityRecent)

        // Temp Shock Bonus: drop of >6C in last 4 days + rain > 15mm
        if (dayIndex > 4 && totalRainLast10Days > 15.0) {
            val tempBefore = allData[dayIndex - 4].avgTemp
            val tempAfter = allData[dayIndex - 1].avgTemp
            if (tempBefore - tempAfter > 6.0f) {
                score += 10
            }
        }

        return min(score, 100)
    }

    fun calculateGrowthPhase(processedData: List<ProcessedDay>): String {
        val todayIndex = 14
        if (processedData.size <= todayIndex) {
            return "⏳ Fase: Dati insufficienti per il calcolo della fase."
        }

        var triggerDayIndex = -1
        for (i in todayIndex downTo 0) {
            if (i < processedData.size && processedData[i].totalPrecip > 10.0f) {
                triggerDayIndex = i
                break
            }
            if (i >= 2 && i < processedData.size) {
                val threeDayRain = processedData[i].totalPrecip +
                        processedData[i - 1].totalPrecip +
                        processedData[i - 2].totalPrecip
                if (threeDayRain > 15.0f) {
                    triggerDayIndex = i - 2
                    break
                }
            }
        }

        if (triggerDayIndex == -1) {
            return "⏳ Fase: Crescita assente (in attesa di piogge)."
        }

        val daysSinceTrigger = todayIndex - triggerDayIndex
        return when {
            daysSinceTrigger <= 4 -> {
                "⏳ Fase: In crescita (piogge recenti $daysSinceTrigger giorni fa)."
            }
            daysSinceTrigger <= 7 -> {
                val daysToHarvest = 8 - daysSinceTrigger
                "⏳ Fase: Maturazione finale (raccolta stimata in $daysToHarvest-${daysToHarvest + 2} giorni)."
            }
            daysSinceTrigger <= 12 -> {
                "⏳ Fase: Periodo ideale per la raccolta!"
            }
            else -> {
                "⏳ Fase: Ciclo di crescita in esaurimento."
            }
        }
    }

    fun getSlopeRecommendation(seasonalityScore: Double, avgTemp: Double, month: Int): String {
        val emoji = "🧭"
        val text = when {
            seasonalityScore < 0.2 -> "Versante: Indifferente (fuori stagione)."
            month in 5..7 -> "Versante: Prediligi versanti a NORD (più freschi e umidi)."
            month == 4 || month >= 9 -> "Versante: Prediligi versanti a SUD (più caldi e soleggiati)."
            else -> "Versante: Controlla tutte le esposizioni, con preferenza per EST."
        }
        return "$emoji $text"
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
        futureTrend: String
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

        summary += futureTrend
        return summary
    }
}
