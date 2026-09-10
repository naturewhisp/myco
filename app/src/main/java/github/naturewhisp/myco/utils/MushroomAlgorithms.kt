package github.naturewhisp.myco.utils

import github.naturewhisp.myco.model.DailyOutlook
import github.naturewhisp.myco.model.EcologicalWeightsConfig
import github.naturewhisp.myco.model.Factor
import github.naturewhisp.myco.model.FactorId
import github.naturewhisp.myco.model.FactorLevel
import github.naturewhisp.myco.model.MushroomSpecies
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.model.TerrainAspectConfig
import github.naturewhisp.myco.model.TerrainAspectData
import github.naturewhisp.myco.model.TerrainAspectEvaluation
import github.naturewhisp.myco.model.WeatherResponse
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Risultato del calcolo di un punteggio normalizzato con relativa descrizione testuale.
 *
 * @property score Punteggio numerico normalizzato, solitamente compreso nell'intervallo [0.0, 1.0].
 * @property text Descrizione testuale qualitativa del fattore valutato.
 */
data class ScoreResult(val score: Double, val text: String)

/**
 * Rappresentazione dello stato e della fase del ciclo lunare.
 *
 * @property text Nome descrittivo della fase lunare (es. "Luna Nuova", "Crescente").
 * @property code Codice standard identificativo della fase (es. "NEW_MOON", "WAXING_CRESCENT").
 * @property favorable Indica se la fase lunare è considerata tradizionalmente favorevole per la fruttificazione.
 */
data class MoonPhaseResult(val text: String, val code: String, val favorable: Boolean) {
    val emoji: String get() = code
}

/**
 * Valutazione discreta legacy dello stato di pioggia cumulata.
 *
 * @property score Punteggio associato alla quantità di pioggia.
 * @property label Etichetta qualitativa (es. "Ottimale", "Scarsa").
 */
data class RainStatus(val score: Int, val label: String)

/**
 * Valutazione discreta legacy dello stato termico medio.
 *
 * @property score Punteggio associato al regime termico.
 * @property label Etichetta qualitativa (es. "Ideale", "Troppo freddo").
 */
data class TempStatus(val score: Int, val label: String)

/**
 * Motore matematico e biologico per la modellazione della crescita e fruttificazione fungina.
 *
 * Fornisce funzioni 100% pure Kotlin per:
 * - Aggregazione e normalizzazione serie storiche e previsionali meteo ([processWeatherData]).
 * - Calcolo probabilistico ecologico continuo con curve di risposta biologiche ([dailyGrowthProbability], [calculateWeatherScore]).
 * - Analisi orografica, slope, aspect e insolazione da DEM ([calculateTerrainAspect], [evaluateTerrainAspect]).
 * - Generazione scomposizione strutturata fattori ([calculateFactors]) e previsioni temporali ([calculateDailyOutlooks]).
 */
object MushroomAlgorithms {

    /**
     * Trasforma la risposta oraria e giornaliera Open-Meteo in una serie aggregata giornaliera di [ProcessedDay].
     *
     * @param data Dati grezzi restituiti dal servizio meteorologico [WeatherResponse].
     * @return Lista ordinata cronologicamente di giorni elaborati con temperatura media, pioggia cumulata e umidità.
     */
    fun processWeatherData(data: WeatherResponse): List<ProcessedDay> {
        val dailyMap = mutableMapOf<String, TempDailyAccumulator>()
        val hourlyTimes = data.hourly.time
        for (i in hourlyTimes.indices) {
            val date = hourlyTimes[i].split("T")[0]
            val acc = dailyMap.getOrPut(date) { TempDailyAccumulator() }
            acc.temps.add(data.hourly.temperature2m.getOrElse(i) { 0.0f })
            acc.precips.add(data.hourly.precipitation.getOrElse(i) { 0.0f })
            acc.humidities.add(data.hourly.relativeHumidity2m.getOrElse(i) { 0.0f })
            data.hourly.soilMoisture0To7cm?.getOrNull(i)?.let { acc.soilMoisture0To7.add(it) }
            data.hourly.soilMoisture7To28cm?.getOrNull(i)?.let { acc.soilMoisture7To28.add(it) }
            data.hourly.evapotranspiration?.getOrNull(i)?.let { acc.evapotranspirations.add(it) }
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
            val avgSoil0To7 = if (acc.soilMoisture0To7.isNotEmpty()) acc.soilMoisture0To7.sum() / acc.soilMoisture0To7.size else null
            val avgSoil7To28 = if (acc.soilMoisture7To28.isNotEmpty()) acc.soilMoisture7To28.sum() / acc.soilMoisture7To28.size else null
            val totalET0 = if (acc.evapotranspirations.isNotEmpty()) acc.evapotranspirations.sum() else null
            ProcessedDay(
                date = date,
                avgTemp = avgTemp,
                totalPrecip = totalPrecip,
                avgHumidity = avgHumidity,
                weatherCode = acc.weatherCode,
                avgSoilMoisture0To7cm = avgSoil0To7,
                avgSoilMoisture7To28cm = avgSoil7To28,
                totalEvapotranspiration = totalET0
            )
        }.sortedBy { it.date }
    }

    private class TempDailyAccumulator {
        val temps = mutableListOf<Float>()
        val precips = mutableListOf<Float>()
        val humidities = mutableListOf<Float>()
        val soilMoisture0To7 = mutableListOf<Float>()
        val soilMoisture7To28 = mutableListOf<Float>()
        val evapotranspirations = mutableListOf<Float>()
        var weatherCode: Int? = null
    }

    /**
     * Valuta discretamente il livello delle precipitazioni cumulate recenti (funzione legacy).
     *
     * @param totalRain Millimetri complessivi di pioggia caduta nel periodo di riferimento.
     * @return Istanza di [RainStatus] con punteggio e descrizione.
     */
    fun getRainStatus(totalRain: Double): RainStatus {
        return when {
            totalRain >= 40.0 -> RainStatus(40, "Ottimale")
            totalRain >= 25.0 -> RainStatus(35, "Molto buona")
            totalRain >= 15.0 -> RainStatus(25, "Buona")
            totalRain >= 5.0 -> RainStatus(10, "Sufficiente")
            else -> RainStatus(0, "Scarsa")
        }
    }

    /**
     * Valuta discretamente il regime termico medio recente (funzione legacy).
     *
     * @param avgTemp Temperatura media registrata in °C.
     * @return Istanza di [TempStatus] con punteggio e descrizione.
     */
    fun getTempStatus(avgTemp: Double): TempStatus {
        return when {
            avgTemp >= 14.0 && avgTemp <= 22.0 -> TempStatus(30, "Ideale")
            avgTemp >= 10.0 && avgTemp <= 25.0 -> TempStatus(15, "Favorevole")
            avgTemp < 10.0 -> TempStatus(0, "Troppo freddo")
            else -> TempStatus(0, "Troppo caldo")
        }
    }

    /**
     * Calcola il punteggio di umidità relativa secondo le soglie standard (funzione legacy).
     *
     * @param avgHumidity Percentuale media di umidità relativa dell'aria.
     * @return Punteggio intero (0, 10 o 15).
     */
    fun getHumidityScore(avgHumidity: Double): Int {
        return when {
            avgHumidity >= 85.0 -> 15
            avgHumidity >= 75.0 -> 10
            else -> 0
        }
    }

    /**
     * Calcola la risposta ecologica generica in base all'altitudine (funzione baseline per Boletus edulis s.l.).
     *
     * @param elevation Quota altimetrica sul livello del mare in metri.
     * @return Istanza di [ScoreResult] con moltiplicatore (0.6..1.0) e sintesi testuale.
     */
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

    /**
     * Calcola il coefficiente di stagionalità fenologica generico (0..11, 0 = Gennaio).
     *
     * @param month Indice del mese da 0 (Gennaio) a 11 (Dicembre).
     * @return Istanza di [ScoreResult] con moltiplicatore (0.1..1.0) e descrizione fenologica.
     */
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

    /**
     * Calcola la fase lunare corrente o per una specifica data basandosi sul ciclo sinodico medio (29.53 giorni).
     *
     * @param date Data di riferimento per il calcolo astronomico (default data odierna).
     * @return Istanza di [MoonPhaseResult] contenente etichetta, codice identificativo e indicazione di favorevolezza.
     */
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

    /**
     * Calcola il punteggio meteorologico composito (0..100) per una specifica data.
     *
     * Integra le quattro componenti continue ponderate secondo [EcologicalWeightsConfig]:
     * - Idratazione da precipitazioni cumulate ([EcologicalWeightsConfig.rainWeight]%)
     * - Regime termico medio recente ([EcologicalWeightsConfig.tempWeight]%)
     * - Umidità relativa aria/suolo ([EcologicalWeightsConfig.humidityWeight]%)
     * - Shock termico induttivo primordiale ([EcologicalWeightsConfig.thermalShockWeight]%)
     *
     * @param dayIndex Indice del giorno bersaglio all'interno della lista cronologica [allData].
     * @param allData Serie temporale completa dei dati meteorologici giornalieri [ProcessedDay].
     * @param spunHyphalDensity Densità ifale sotterranea SPUN in m/cm³, se disponibile.
     * @param species Profilo ecologico della specie target [MushroomSpecies].
     * @param config Configurazione tipizzata dei pesi e delle finestre climatiche [EcologicalWeightsConfig].
     * @return Punteggio meteorologico intero normalizzato nell'intervallo [0, 100].
     */
    fun calculateWeatherScore(
        dayIndex: Int,
        allData: List<ProcessedDay>,
        spunHyphalDensity: Float? = null,
        species: MushroomSpecies = SPECIES_CATALOG[0],
        config: EcologicalWeightsConfig = EcologicalWeightsConfig.DEFAULT
    ): Int {
        if (dayIndex < 0 || dayIndex >= allData.size) return 0

        // Calcolo continuo della pioggia cumulata (finestra da rainWindowDays a rainLagDays giorni fa)
        val rainStart = max(0, dayIndex - config.rainWindowDays)
        val rainEnd = max(0, dayIndex - config.rainLagDays)
        val rainWindow = if (rainStart < rainEnd && rainEnd <= allData.size) {
            allData.subList(rainStart, rainEnd)
        } else {
            emptyList()
        }
        val totalRainLast10Days = rainWindow.sumOf { it.totalPrecip.toDouble() }
        var rainScore = rainScoreSmooth(totalRainLast10Days, species) * config.rainWeight

        // Modulatore biologico SPUN: rete ifale densa (>5.0 m/cm3) amplifica la risposta a piogge moderate
        if (spunHyphalDensity != null && spunHyphalDensity >= 5.0f && totalRainLast10Days >= config.minRainForShockMm) {
            rainScore = min(config.rainWeight, rainScore + 6.0)
        } else if (spunHyphalDensity != null && spunHyphalDensity < 2.5f) {
            rainScore = max(0.0, rainScore - 4.0)
        }

        // Calcolo continuo della temperatura media (finestra ultimi tempWindowDays giorni)
        val tempStart = max(0, dayIndex - config.tempWindowDays)
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
        val tempScore = tempScoreSmooth(avgTempLast5Days, species) * config.tempWeight

        // Calcolo continuo dell'umidità relativa e idratazione suolo (finestra humidityWindowDays giorni fino a oggi)
        val humStart = max(0, dayIndex - config.humidityWindowDays)
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

        // Integrazione pedologica: contenuto idrico volumetrico del suolo multi-orizzonte ed ET0
        val soil0To7Vals = humWindow.mapNotNull { it.avgSoilMoisture0To7cm?.toDouble() }
        val soil7To28Vals = humWindow.mapNotNull { it.avgSoilMoisture7To28cm?.toDouble() }
        val et0Vals = humWindow.mapNotNull { it.totalEvapotranspiration?.toDouble() }
        val hasSoilMoisture = soil0To7Vals.isNotEmpty() || soil7To28Vals.isNotEmpty()

        val humScore = if (hasSoilMoisture) {
            val avgSoil0To7 = if (soil0To7Vals.isNotEmpty()) soil0To7Vals.average() else null
            val avgSoil7To28 = if (soil7To28Vals.isNotEmpty()) soil7To28Vals.average() else null
            val avgET0 = if (et0Vals.isNotEmpty()) et0Vals.average() else null
            val soilNorm = soilMoistureScoreSmooth(avgSoil0To7, avgSoil7To28, avgET0)
            val airHumNorm = humidityScoreSmooth(avgHumidityRecent)
            (0.40 * airHumNorm + 0.60 * soilNorm) * config.humidityWeight
        } else {
            humidityScoreSmooth(avgHumidityRecent) * config.humidityWeight
        }

        // Calcolo continuo dello shock termico induttivo dei primordi
        var shockScore = 0.0
        if (dayIndex > 4 && totalRainLast10Days >= config.minRainForShockMm) {
            val tempBefore = allData[dayIndex - 4].avgTemp
            val tempAfter = allData[dayIndex - 1].avgTemp
            val drop = (tempBefore - tempAfter).toDouble()
            val minDrop = if (spunHyphalDensity != null && spunHyphalDensity >= 5.0f) {
                config.spunAssistedThermalDropMin
            } else {
                config.standardThermalDropMin
            }
            if (drop > minDrop) {
                val dropFactor = ((drop - minDrop) / config.shockDropSaturationSpan).coerceIn(0.0, 1.0)
                val rainFactor = (totalRainLast10Days / config.shockRainSaturationMm).coerceIn(0.0, 1.0)
                shockScore = config.thermalShockWeight * dropFactor * rainFactor
            }
        }

        return (rainScore + tempScore + humScore + shockScore).coerceIn(0.0, 100.0).roundToInt()
    }

    /**
     * Determina la fase fenologica di sviluppo miceliare e fruttificazione a partire dalla serie storica recente.
     *
     * @param processedData Serie temporale dei giorni elaborati contenente lo storico meteo.
     * @return Stringa descrittiva della fase fenologica corrente (es. Idratazione, Incubazione primordi, Buttata attiva).
     */
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

    /**
     * Fornisce una raccomandazione euristica sull'esposizione del versante ottimale in base al mese e al clima.
     *
     * @param seasonalityScore Punteggio stagionale corrente (0.0..1.0).
     * @param avgTemp Temperatura media attuale in °C.
     * @param month Mese dell'anno (0..11).
     * @return Testo sintetico con il versante consigliato e la motivazione termica.
     */
    fun getSlopeRecommendation(seasonalityScore: Double, avgTemp: Double, month: Int): String {
        return when {
            seasonalityScore < 0.2 -> "Indifferente (Fuori stagione fenologica)"
            month in 5..7 -> "Nord (Versanti più freschi e umidi)"
            month == 4 || month >= 8 -> "Sud (Versanti più caldi e soleggiati)"
            else -> "Est (Soleggiamento mattutino)"
        }
    }

    /**
     * Calcola pendenza, direzione ed esposizione cardinale del versante a partire da 5 quote DEM.
     *
     * Utilizza il metodo delle differenze finite centrate sulle 4 direzioni cardinali (N, S, E, W)
     * e quota centrale, calcolando il gradiente vettoriale e l'orientamento di massima pendenza:
     * $$\frac{\partial z}{\partial x} = \frac{z_E - z_W}{2 \cdot \Delta}, \quad \frac{\partial z}{\partial y} = \frac{z_N - z_S}{2 \cdot \Delta}$$
     *
     * @param elevations Lista ordinata di almeno 5 elevazioni DEM: [Centro, Nord, Sud, Est, Ovest].
     * @param deltaMeters Distanza cartesiana in metri tra il centro e i punti cardinali (default [TerrainAspectConfig.deltaMeters]).
     * @param config Configurazione dei parametri orografici e soglie di pendenza [TerrainAspectConfig].
     * @return [TerrainAspectData] contenente pendenza percentuale/in gradi, azimut del versante e direzione cardinale.
     */
    fun calculateTerrainAspect(
        elevations: List<Float>,
        deltaMeters: Double = 75.0,
        config: TerrainAspectConfig = TerrainAspectConfig.DEFAULT
    ): TerrainAspectData {
        val effectiveDelta = if (deltaMeters != 75.0) deltaMeters else config.deltaMeters
        if (elevations.size < 5) {
            val center = elevations.firstOrNull() ?: 0f
            return TerrainAspectData(
                centerElevation = center,
                slopeDegrees = 0f,
                slopePercent = 0f,
                aspectDegrees = 0f,
                cardinalDirection = "Pianeggiante",
                cardinalAbbreviation = "Pian",
                isFlat = true
            )
        }
        val zCenter = elevations[0]
        val zNorth = elevations[1]
        val zSouth = elevations[2]
        val zEast = elevations[3]
        val zWest = elevations[4]

        // Derivate parziali dell'elevazione (differenze finite centrate)
        val dzdx = (zEast - zWest).toDouble() / (2.0 * effectiveDelta)
        val dzdy = (zNorth - zSouth).toDouble() / (2.0 * effectiveDelta)

        val slopeRatio = Math.sqrt(dzdx * dzdx + dzdy * dzdy)
        val slopeDegrees = Math.toDegrees(Math.atan(slopeRatio)).toFloat()
        val slopePercent = (slopeRatio * 100.0).toFloat()

        val isFlat = slopeDegrees < config.flatSlopeThresholdDegrees

        // Vettore di massima discesa: verso cui il versante scende
        val vx = -dzdx
        val vy = -dzdy

        var aspectDeg = Math.toDegrees(kotlin.math.atan2(vx, vy)).toFloat()
        if (aspectDeg < 0f) aspectDeg += 360f

        val (dir, abbr) = if (isFlat) {
            "Pianeggiante" to "Pian"
        } else {
            when {
                aspectDeg >= 337.5f || aspectDeg < 22.5f -> "Nord" to "N"
                aspectDeg < 67.5f -> "Nord-Est" to "NE"
                aspectDeg < 112.5f -> "Est" to "E"
                aspectDeg < 157.5f -> "Sud-Est" to "SE"
                aspectDeg < 202.5f -> "Sud" to "S"
                aspectDeg < 247.5f -> "Sud-Ovest" to "SO"
                aspectDeg < 292.5f -> "Ovest" to "O"
                else -> "Nord-Ovest" to "NO"
            }
        }

        return TerrainAspectData(
            centerElevation = zCenter,
            slopeDegrees = slopeDegrees,
            slopePercent = slopePercent,
            aspectDegrees = aspectDeg,
            cardinalDirection = dir,
            cardinalAbbreviation = abbr,
            isFlat = isFlat
        )
    }

    /**
     * Valuta l'idoneità micologica del versante orografico incrociando esposizione, pendenza, mese e termofilia.
     *
     * Modula il moltiplicatore probabilistico continuo (0.50..1.10) in base al regime microclimatico:
     * - Versanti *solatìi* (Sud/Est): favoriti in autunno/primavera o per specie termofile.
     * - Versanti *bacìi* (Nord/Ovest): favoriti in estate o periodi torridi per conservazione dell'umidità.
     * - Forti pendenze (> [config.steepSlopeThresholdDegrees]°): penalizzate per eccessivo ruscellamento idrico.
     *
     * @param terrain Dati orografici calcolati [TerrainAspectData], o null in caso di fallback.
     * @param month Mese dell'anno (0-indexed: 0 = Gennaio .. 11 = Dicembre).
     * @param avgTemp Temperatura media recente in gradi Celsius.
     * @param seasonalityScore Punteggio stagionale della specie bersaglio.
     * @param species Profilo biologico della specie target [MushroomSpecies].
     * @param config Configurazione dei moltiplicatori e soglie orografiche [TerrainAspectConfig].
     * @return [TerrainAspectEvaluation] con livello Herbarium, descrizione e moltiplicatore continuo.
     */
    fun evaluateTerrainAspect(
        terrain: TerrainAspectData?,
        month: Int,
        avgTemp: Double,
        seasonalityScore: Double,
        species: MushroomSpecies = SPECIES_CATALOG[0],
        config: TerrainAspectConfig = TerrainAspectConfig.DEFAULT
    ): TerrainAspectEvaluation {
        if (terrain == null) {
            val rec = getSlopeRecommendation(seasonalityScore, avgTemp, month)
            val slopeVal = rec.substringBefore(" (").replace("Versante: ", "").trim()
            val slopeDetail = rec.substringAfter("(", "").replace(")", "").trim().ifEmpty { "Orientamento orografico" }
            return TerrainAspectEvaluation(
                terrain = null,
                level = FactorLevel.INFORMATIVE,
                formattedValue = slopeVal,
                detail = slopeDetail,
                modifier = 1.0
            )
        }

        if (terrain.isFlat) {
            val formatted = String.format(Locale.US, "%.0f° (Pian)", terrain.slopeDegrees)
            return TerrainAspectEvaluation(
                terrain = terrain,
                level = FactorLevel.NEUTRAL,
                formattedValue = formatted,
                detail = "Altopiano o pianura • Drenaggio regolare",
                modifier = 1.0
            )
        }

        val formatted = String.format(Locale.US, "%d° %s", terrain.slopeDegrees.roundToInt(), terrain.cardinalAbbreviation)
        val isSteep = terrain.slopeDegrees > config.steepSlopeThresholdDegrees

        val aspect = terrain.aspectDegrees
        val isNorth = aspect >= 315f || aspect <= 45f // N, NO, NE
        val isSouth = aspect in 135f..225f            // S, SE, SO
        val isEast = aspect in 45f..135f              // E, NE, SE

        var level: FactorLevel
        var detail: String
        var modifier: Double

        val isThermophilic = species.idealTempMin >= 17f

        if (isThermophilic) {
            when {
                isSouth || (isEast && aspect > 90f) -> {
                    level = FactorLevel.FAVORABLE
                    detail = "Solatìo caldo • Ottimale per specie termofila"
                    modifier = config.favorableMultiplier
                }
                isNorth -> {
                    if (avgTemp >= 24.0) {
                        level = FactorLevel.NEUTRAL
                        detail = "Esposizione Nord • Mitiga calore estivo"
                        modifier = 1.0
                    } else {
                        level = FactorLevel.ADVERSE
                        detail = "Versante freddo a bacìo • Insolazione scarsa"
                        modifier = config.adverseMultiplier
                    }
                }
                else -> {
                    level = FactorLevel.NEUTRAL
                    detail = "Esposizione intermedia • Soleggiamento moderato"
                    modifier = 1.0
                }
            }
        } else {
            val isHotSeason = month in 5..7 || avgTemp > 21.0
            val isColdSeason = month in listOf(3, 10, 11) || (month == 4 && avgTemp < 13.0) || avgTemp < 13.0

            when {
                isHotSeason -> {
                    when {
                        isNorth -> {
                            level = FactorLevel.FAVORABLE
                            detail = "Versante fresco a bacìo • Ottima umidità estiva"
                            modifier = config.favorableMultiplier
                        }
                        isSouth -> {
                            level = FactorLevel.ADVERSE
                            detail = "Solatìo arido • Elevata evapotraspirazione"
                            modifier = config.adverseMultiplier
                        }
                        else -> {
                            level = FactorLevel.NEUTRAL
                            detail = "Esposizione intermedia • Soleggiamento parziale"
                            modifier = 1.0
                        }
                    }
                }
                isColdSeason -> {
                    when {
                        isSouth -> {
                            level = FactorLevel.FAVORABLE
                            detail = "Solatìo soleggiato • Accumulo termico autunnale"
                            modifier = config.favorableMultiplier
                        }
                        isNorth -> {
                            level = FactorLevel.ADVERSE
                            detail = "Bacìo freddo • Rischio blocco termico miceliare"
                            modifier = config.adverseMultiplier
                        }
                        else -> {
                            level = FactorLevel.NEUTRAL
                            detail = "Esposizione intermedia • Soleggiamento moderato"
                            modifier = 1.0
                        }
                    }
                }
                else -> {
                    when {
                        isEast -> {
                            level = FactorLevel.FAVORABLE
                            detail = "Esposizione Est • Soleggiamento mattutino mite"
                            modifier = config.morningSunMultiplier
                        }
                        isSouth -> {
                            level = FactorLevel.FAVORABLE
                            detail = "Esposizione Sud • Buon soleggiamento"
                            modifier = config.moderateSunMultiplier
                        }
                        else -> {
                            level = FactorLevel.NEUTRAL
                            detail = "Esposizione ordinaria • Microclima temperato"
                            modifier = 1.0
                        }
                    }
                }
            }
        }

        if (isSteep) {
            level = if (level == FactorLevel.FAVORABLE) FactorLevel.NEUTRAL else FactorLevel.ADVERSE
            detail = "Forte pendenza (${terrain.slopeDegrees.roundToInt()}°) • Ruscellamento elevato"
            modifier = min(modifier, config.steepSlopePenaltyMax)
        }

        return TerrainAspectEvaluation(
            terrain = terrain,
            level = level,
            formattedValue = formatted,
            detail = detail,
            modifier = modifier
        )
    }

    /**
     * Analizza la finestra previsionale futura (+1..+5 giorni) per stimare il trend di crescita.
     *
     * @param processedData Serie temporale dei giorni con storico e previsioni future.
     * @return Paragrafo descrittivo Markdown con l'evoluzione del trend idrico e di fruttificazione.
     */
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

    /**
     * Sintetizza in un testo discorsivo i punti di forza, i fattori limitanti e il contesto biologico generale.
     *
     * @param weatherScore Punteggio meteo complessivo (0..100).
     * @param habitatScore Punteggio dell'habitat forestale (0..1).
     * @param habitatText Descrizione qualitativa dell'habitat.
     * @param altitudeScore Risposta altimetrica (0..1).
     * @param altitudeText Descrizione dell'altitudine.
     * @param seasonalityScore Coefficiente stagionale (0..1).
     * @param seasonalityText Descrizione del periodo fenologico.
     * @param totalRain Pioggia cumulata recente in mm.
     * @param futureTrend Analisi del trend meteo futuro.
     * @param spunEcmText Descrizione del livello di ectomicorrize SPUN, se presente.
     * @param spunHyphalText Descrizione della densità ifale SPUN, se presente.
     * @return Testo discorsivo completo per l'interfaccia utente.
     */
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

    /**
     * Curva di risposta termica biologica continua normalizzata nell'intervallo [0.0, 1.0].
     *
     * @param temp Temperatura media registrata in °C.
     * @param species Profilo biologico della specie micologica target [MushroomSpecies].
     * @return Punteggio continuo normalizzato [0.0, 1.0].
     */
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

    /**
     * Risposta idrica continua da precipitazioni cumulate [0.0, 1.0] parametrata sul fabbisogno della specie.
     *
     * @param rainMm Millimetri complessivi di pioggia caduta.
     * @param species Specie micologica target [MushroomSpecies].
     * @return Punteggio continuo normalizzato [0.0, 1.0].
     */
    fun rainScoreSmooth(rainMm: Double, species: MushroomSpecies = SPECIES_CATALOG[0]): Double {
        val target = species.minRainAccumulation.toDouble()
        return when {
            rainMm <= 0.0 -> 0.0
            rainMm >= target -> 1.0
            target > 0.0 -> (rainMm / target).coerceIn(0.0, 1.0)
            else -> 1.0
        }
    }

    /**
     * Risposta continua all'umidità relativa dell'aria [0.0, 1.0].
     *
     * @param humidity Percentuale media di umidità relativa (0..100).
     * @return Punteggio continuo normalizzato [0.0, 1.0].
     */
    fun humidityScoreSmooth(humidity: Double): Double {
        return when {
            humidity < 50.0 -> 0.0
            humidity >= 85.0 -> 1.0
            else -> ((humidity - 50.0) / 35.0).coerceIn(0.0, 1.0)
        }
    }

    /**
     * Interpolazione ermitiana cubica smoothstep continua tra [edge0] ed [edge1].
     */
    private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    /**
     * Valuta in modo continuo il contenuto idrico del suolo [0.0, 1.0] combinando l'orizzonte superficiale (0-7 cm)
     * e l'orizzonte radicale profondo (7-28 cm), modulati dall'evapotraspirazione di riferimento FAO ET0.
     *
     * @param m0To7 Umidità volumetrica superficiale in m³/m³ (orizzonte primordi/lettiera). Range ottimale: 0.22..0.38.
     * @param m7To28 Umidità volumetrica profonda in m³/m³ (orizzonte miceliare perenne). Range ottimale: 0.20..0.35.
     * @param et0 Evapotraspirazione cumulata giornaliera di riferimento FAO ET0 in mm/giorno.
     * @return Punteggio continuo normalizzato [0.0, 1.0]. Se entrambi gli orizzonti sono nulli, restituisce 1.0 (neutro).
     */
    fun soilMoistureScoreSmooth(m0To7: Double?, m7To28: Double?, et0: Double? = null): Double {
        if (m0To7 == null && m7To28 == null) return 1.0

        // Calcolo continuo orizzonte superficiale 0-7 cm (induzione e idratazione primordiale)
        val s0To7 = if (m0To7 != null) {
            when {
                m0To7 < 0.10 -> 0.10
                m0To7 in 0.10..0.22 -> 0.10 + 0.90 * smoothstep(0.10, 0.22, m0To7)
                m0To7 in 0.22..0.38 -> 1.0
                m0To7 in 0.38..0.48 -> 1.0 - 0.50 * smoothstep(0.38, 0.48, m0To7)
                else -> 0.50
            }
        } else null

        // Calcolo continuo orizzonte profondo 7-28 cm (rete ifale perenne e assorbimento)
        val s7To28 = if (m7To28 != null) {
            when {
                m7To28 < 0.12 -> 0.20
                m7To28 in 0.12..0.20 -> 0.20 + 0.80 * smoothstep(0.12, 0.20, m7To28)
                m7To28 in 0.20..0.35 -> 1.0
                m7To28 in 0.35..0.45 -> 1.0 - 0.40 * smoothstep(0.35, 0.45, m7To28)
                else -> 0.60
            }
        } else null

        val baseSoilScore = when {
            s0To7 != null && s7To28 != null -> 0.55 * s0To7 + 0.45 * s7To28
            s0To7 != null -> s0To7
            s7To28 != null -> s7To28
            else -> 1.0
        }

        // Modulazione evapotraspirativa: vento secco e forte insolazione (ET0 > 3.0 mm/die) accentuano il disseccamento
        val etMod = if (et0 != null && et0 > 3.0) {
            val excess = (et0 - 3.0).coerceIn(0.0, 3.0) / 3.0
            1.0 - (0.15 * excess)
        } else {
            1.0
        }

        return (baseSoilScore * etMod).coerceIn(0.0, 1.0)
    }

    /**
     * Valuta la risposta altimetrica continua specifica per la specie selezionata.
     *
     * @param elevation Quota sul livello del mare in metri.
     * @param species Specie micologica target [MushroomSpecies].
     * @return [ScoreResult] con punteggio continuo (0.4..1.0) e descrizione della fascia altimetrica.
     */
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

    /**
     * Valuta la stagionalità fenologica calibrata sui mesi attivi e limitrofi della specie.
     *
     * @param month Mese dell'anno da 0 (Gennaio) a 11 (Dicembre).
     * @param species Specie micologica target [MushroomSpecies].
     * @return [ScoreResult] con punteggio continuo (0.1..1.0) e descrizione della fase stagionale.
     */
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

    /**
     * Calcola la probabilità giornaliera continua combinata di fruttificazione (0..100%).
     *
     * Applica la formula canonica calibrata descritta in AGENTS.md:
     * $$P = 100 \times \left(\frac{W}{100}\right)^{1.2} \times H \times A \times S \times T$$
     * dove:
     * - $W$ = punteggio meteo ponderato tramite [config.weatherExponent]
     * - $H$ = moltiplicatore habitat boschivo (0.10..1.00)
     * - $A$ = moltiplicatore altimetrico continuo per specie (0.40..1.00)
     * - $S$ = moltiplicatore stagionale fenologico (0.10..1.00)
     * - $T$ = modificatore orografico del versante ed esposizione (0.50..1.10)
     *
     * @param weatherScore Punteggio meteorologico calcolato (0..100).
     * @param habitatScore Punteggio vegetazionale / boschivo (0.0..1.0).
     * @param altitudeScore Risposta altimetrica continua della specie (0.0..1.0).
     * @param seasonalityScore Risposta fenologica stagionale del mese corrente (0.0..1.0).
     * @param terrainModifier Modificatore continuo del versante, pendenza ed esposizione orografica (default 1.0).
     * @param config Configurazione dei pesi ed esponenti ecologici [EcologicalWeightsConfig].
     * @return Probabilità percentuale complessiva normalizzata nell'intervallo [0, 100].
     */
    fun dailyGrowthProbability(
        weatherScore: Int,
        habitatScore: Double,
        altitudeScore: Double,
        seasonalityScore: Double,
        terrainModifier: Double = 1.0,
        config: EcologicalWeightsConfig = EcologicalWeightsConfig.DEFAULT
    ): Int {
        val weightedWeatherScore = 100.0 * Math.pow(weatherScore / 100.0, config.weatherExponent)
        val combined = weightedWeatherScore * habitatScore * altitudeScore * seasonalityScore * terrainModifier
        return combined.toInt().coerceIn(0, 100)
    }

    /**
     * Scompone le variabili ambientali e geografiche nella lista tipizzata di fattori [Factor] per la visualizzazione Herbarium.
     *
     * @param avgTemp Temperatura media attuale in °C.
     * @param totalRain Pioggia cumulata recente in mm.
     * @param avgHumidity Percentuale media di umidità relativa.
     * @param habitatScore Punteggio dell'habitat forestale (0..1).
     * @param habitatText Descrizione dell'habitat botanico.
     * @param elevation Quota altimetrica in metri.
     * @param month Mese dell'anno (0..11).
     * @param growthPhaseText Descrizione fenologica della fase di crescita.
     * @param moon Risultato del calcolo della fase lunare [MoonPhaseResult].
     * @param slopeText Raccomandazione del versante.
     * @param species Profilo biologico della specie micologica target [MushroomSpecies].
     * @param spunEcmText Descrizione del livello di ectomicorrize SPUN, se disponibile.
     * @param spunHyphalText Descrizione della densità ifale SPUN, se disponibile.
     * @param terrainEvaluation Valutazione dettagliata di pendenza ed esposizione [TerrainAspectEvaluation], se disponibile.
     * @return Lista ordinata di elementi [Factor] pronti per il rendering nelle schede ecologiche.
     */
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
        spunHyphalText: String? = null,
        terrainEvaluation: TerrainAspectEvaluation? = null,
        avgSoilMoisture0To7: Float? = null,
        avgSoilMoisture7To28: Float? = null,
        totalEvapotranspiration: Float? = null
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

        // 3b. Idratazione suolo multi-orizzonte & Evapotraspirazione
        if (avgSoilMoisture0To7 != null || avgSoilMoisture7To28 != null) {
            val soilNorm = soilMoistureScoreSmooth(
                avgSoilMoisture0To7?.toDouble(),
                avgSoilMoisture7To28?.toDouble(),
                totalEvapotranspiration?.toDouble()
            )
            val soilLevel = when {
                soilNorm >= 0.80 -> FactorLevel.FAVORABLE
                soilNorm >= 0.45 -> FactorLevel.NEUTRAL
                else -> FactorLevel.ADVERSE
            }
            val primaryVal = avgSoilMoisture0To7 ?: avgSoilMoisture7To28 ?: 0f
            val formattedVal = String.format(Locale.ITALIAN, "%.2f m³/m³", primaryVal)

            val detailStr = buildString {
                append("Orizzonte primordi 0-7 cm")
                if (avgSoilMoisture7To28 != null) {
                    append(String.format(Locale.ITALIAN, " • Radici %.2f", avgSoilMoisture7To28))
                }
                if (totalEvapotranspiration != null) {
                    append(String.format(Locale.ITALIAN, " • ET0 %.1f mm", totalEvapotranspiration))
                }
            }

            factors.add(
                Factor(
                    id = FactorId.SOIL_MOISTURE,
                    label = "Idratazione suolo",
                    formattedValue = formattedVal,
                    level = soilLevel,
                    detail = detailStr
                )
            )
        }

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
        if (terrainEvaluation != null) {
            factors.add(
                Factor(
                    id = FactorId.SLOPE,
                    label = "Esposizione versante",
                    formattedValue = terrainEvaluation.formattedValue,
                    level = terrainEvaluation.level,
                    detail = terrainEvaluation.detail
                )
            )
        } else {
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
        }

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

    /**
     * Calcola le proiezioni giornaliere [DailyOutlook] per tutti i giorni futuri della serie temporale.
     *
     * @param processedDays Serie completa dei giorni elaborati.
     * @param startIndex Indice da cui iniziare il calcolo (default 14, corrispondente alla data odierna).
     * @param species Specie micologica target [MushroomSpecies].
     * @param habitatScore Punteggio dell'habitat forestale (0..1).
     * @param elevation Quota altimetrica in metri.
     * @param month Mese dell'anno (0..11).
     * @param spunHyphalDensity Densità ifale sotterranea SPUN, se disponibile.
     * @return Lista di [DailyOutlook] per ciascun giorno previsionale.
     */
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

    /**
     * Calcola la distanza ortodromica in chilometri tra due coordinate geografiche WGS84
     * utilizzando la formula di Haversine.
     *
     * @param lat1 Latitudine del primo punto in gradi decimali.
     * @param lon1 Longitudine del primo punto in gradi decimali.
     * @param lat2 Latitudine del secondo punto in gradi decimali.
     * @param lon2 Longitudine del secondo punto in gradi decimali.
     * @return Distanza stimata lungo l'arco di cerchio massimo in chilometri.
     */
    fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Raggio medio terrestre in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2).let { it * it }
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
        return r * c
    }
}
