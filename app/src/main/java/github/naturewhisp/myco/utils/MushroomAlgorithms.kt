package github.naturewhisp.myco.utils

import github.naturewhisp.myco.model.DailyOutlook
import github.naturewhisp.myco.model.EcologicalCategory
import github.naturewhisp.myco.model.EcologicalWeightsConfig
import github.naturewhisp.myco.model.Factor
import github.naturewhisp.myco.model.FactorId
import github.naturewhisp.myco.model.FactorLevel
import github.naturewhisp.myco.model.HabitatEvidence
import github.naturewhisp.myco.model.HabitatStatus
import github.naturewhisp.myco.model.MushroomSpecies
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.model.SpunData
import github.naturewhisp.myco.model.TerrainAspectConfig
import github.naturewhisp.myco.model.TerrainAspectData
import github.naturewhisp.myco.model.TerrainAspectEvaluation
import github.naturewhisp.myco.model.WeatherResponse
import github.naturewhisp.myco.core.MycoAlgorithms as SharedMycoAlgorithms
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
 * Rappresentazione interna di un evento di innesco pluviometrico per la fenologia.
 */
internal data class RainTrigger(val triggerIndex: Int, val rainAmount: Float)

/**
 * Valutazione biotica e strutturale dell'habitat stazionale specifica per taxon micologico.
 *
 * @property score Punteggio finale normalizzato dell'habitat [0.10..1.00].
 * @property baseText Descrizione qualitativa dell'idoneità forestale o praticola.
 * @property bonusText Descrizione di eventuali essenze arboree simbionti o reti SPUN rilevate.
 * @property basalAreaM2Ha Area basimetrica equivalente stimata in m²/ha.
 * @property standDensityScore Modificatore biometrico unimodale della densità del popolamento (CTFC).
 */
data class SpeciesHabitatEvaluation(
    val score: Double,
    val baseText: String,
    val bonusText: String,
    val basalAreaM2Ha: Float,
    val standDensityScore: Double
)

/**
 * Fasi biologiche evolutive del ciclo di fruttificazione macrofungina.
 */
enum class GrowthStage {
    WAITING_FOR_RAIN,
    MYCELIAL_HYDRATION,
    PRIMORDIA_INCUBATION,
    ACTIVE_FRUITING,
    WANING
}

/**
 * Valutazione strutturata della fase fenologica di crescita fungina.
 *
 * @property phaseText Stringa discorsiva completa formattata per la UI.
 * @property multiplier Moltiplicatore di probabilità fenologica continua (0.25..1.00).
 * @property daysSinceTrigger Giorni trascorsi dall'evento pluviometrico scatenante.
 * @property stage Fase biologica discreta corrispondente [GrowthStage].
 */
data class GrowthPhaseEvaluation(
    val phaseText: String,
    val multiplier: Double,
    val daysSinceTrigger: Int? = null,
    val stage: GrowthStage = GrowthStage.WAITING_FOR_RAIN
)

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
            val minTemp = acc.temps.minOrNull() ?: avgTemp
            val maxTemp = acc.temps.maxOrNull() ?: avgTemp
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
                totalEvapotranspiration = totalET0,
                minTemp = minTemp,
                maxTemp = maxTemp
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
     */
    /**
     * Kernel unimodale continuo normalizzato per la convoluzione fenologica dell'inerzia biologica.
     *
     * Modellato come curva gamma asimmetrica a picco unitario calibrata sulla specie:
     * $$f(\tau) = \left(\frac{\tau}{\tau_{peak}}\right)^\alpha \cdot \exp\left(-\alpha \left(\frac{\tau}{\tau_{peak}} - 1\right)\right)$$
     * con $f(\tau_{peak}) = 1.0$, $f(\tau \le 0) = 0.0$.
     *
     * @param tauDays Ritardo temporale in giorni ($\tau \ge 0$).
     * @param tauPeak Latenza di picco biologico in giorni (default 11.0).
     * @param alpha Parametro di forma del kernel (default 4.0).
     * @return Peso fenologico continuo normalizzato nell'intervallo [0.0, 1.0].
     */
    fun phenologyKernel(
        tauDays: Double,
        tauPeak: Double = 11.0,
        alpha: Double = 4.0
    ): Double {
        if (tauDays <= 0.0 || tauPeak <= 0.0 || alpha <= 0.0) return 0.0
        val r = tauDays / tauPeak
        val logVal = alpha * (kotlin.math.ln(r) - (r - 1.0))
        return kotlin.math.exp(logVal).coerceIn(0.0, 1.0)
    }

    /**
     * Calcola il fattore di compensazione o penalizzazione derivante dal deficit idrico
     * pregresso dell'orizzonte radicale profondo (7-28 cm).
     *
     * Se il suolo profondo ha subito un forte deficit (< 0.12 m³/m³), parte dell'acqua piovana
     * viene assorbita per ricaricare la matrice pedologica prima di rendersi disponibile
     * per la biomassa fungina (fattore riduttivo fino a 0.70). Se il suolo profondo ha mantenuto
     * un'ottima riserva idrica (0.20..0.35 m³/m³), funge da volano idrico compensando brevi
     * periodi asciutti superficiali (bonus fino a 1.10).
     *
     * @param historicalDeepSoil Media dell'umidità volumetrica profonda pregressa in m³/m³.
     * @return Moltiplicatore continuo normalizzato nell'intervallo [0.70, 1.15].
     */
    fun deepSoilMoistureCompensation(historicalDeepSoil: Double?): Double {
        if (historicalDeepSoil == null) return 1.0
        return when {
            historicalDeepSoil < 0.12 -> 0.70
            historicalDeepSoil < 0.20 -> 0.70 + 0.30 * smoothstep(0.12, 0.20, historicalDeepSoil)
            historicalDeepSoil <= 0.35 -> 1.0 + 0.10 * smoothstep(0.20, 0.28, historicalDeepSoil)
            historicalDeepSoil < 0.44 -> 1.10 - 0.10 * smoothstep(0.35, 0.44, historicalDeepSoil)
            else -> 1.0 - 0.15 * smoothstep(0.44, 0.52, historicalDeepSoil)
        }.coerceIn(0.70, 1.15)
    }

    /**
     * Calcola la precipitazione efficace biologicamente attiva tramite convoluzione fenologica continua.
     *
     * Sostituisce la somma piatta nella finestra rigida [10 gg - 2 gg] integrando le precipitazioni
     * passate ponderate secondo il kernel di latenza unimodale della specie e modulate dalla
     * compensazione del deficit idrico profondo (7-28 cm).
     *
     * @param dayIndex Indice del giorno target all'interno di [allData].
     * @param allData Serie temporale completa dei giorni elaborati.
     * @param species Specie fungina target con i relativi parametri fenologici.
     * @return Precipitazione efficace ponderata in mm.
     */
    fun calculateEffectiveRainfall(
        dayIndex: Int,
        allData: List<ProcessedDay>,
        species: MushroomSpecies = SPECIES_CATALOG[0]
    ): Double {
        if (dayIndex <= 0 || allData.isEmpty()) return 0.0

        val hysteresisWindowStart = max(0, dayIndex - 5)
        val recentWindow = allData.subList(hysteresisWindowStart, dayIndex)
        val hasChillingTrauma = recentWindow.any { it.minTemp < species.toleratedTempMin }
        val effectiveTauPeak = if (hasChillingTrauma) species.phenologyLatencyPeakDays + 1.5 else species.phenologyLatencyPeakDays

        var weightedRain = 0.0
        val pastDeepSoilList = mutableListOf<Double>()
        for (i in 0 until dayIndex) {
            val tau = (dayIndex - i).toDouble()
            val precip = allData[i].totalPrecip.toDouble()
            if (precip > 0.0) {
                val weight = phenologyKernel(
                    tauDays = tau,
                    tauPeak = effectiveTauPeak,
                    alpha = species.phenologyShapeAlpha
                )
                weightedRain += precip * weight
            }
            allData[i].avgSoilMoisture7To28cm?.toDouble()?.let { pastDeepSoilList.add(it) }
        }
        val avgPastDeepSoil = if (pastDeepSoilList.isNotEmpty()) pastDeepSoilList.average() else null
        val comp = deepSoilMoistureCompensation(avgPastDeepSoil)
        return (weightedRain * comp).coerceAtLeast(0.0)
    }

    /**
     * Calcola l'inibizione continua da freddo notturno per penalizzare le notti
     * sotto la soglia di tolleranza o ideale.
     *
     * @param minTemp Temperatura minima in °C
     * @param species Specie micologica target
     * @return Moltiplicatore continuo nell'intervallo [0.3, 1.0]
     */
    fun nocturnalChillingInhibition(minTemp: Float, species: MushroomSpecies): Double {
        val idealMin = species.idealTempMin.toDouble()
        val toleratedMin = species.toleratedTempMin.toDouble()
        if (minTemp >= idealMin) return 1.0
        if (minTemp <= toleratedMin) return 0.3
        
        return 0.3 + 0.7 * smoothstep(toleratedMin, idealMin, minTemp.toDouble())
    }

    /**
     * Applica il modello microclimatico di volta forestale (Canopy Buffering / De Frenne Offset)
     * a una singola giornata meteorologica.
     *
     * I dati macroclimatici da stazioni o reanalisi (Open-Meteo 2m in campo aperto) vengono corretti
     * per riflettere le condizioni effettive del sottobosco (De Frenne et al., Nature Ecol. Evol. 2019/2021):
     * 1. Attenuazione delle temperature massime estive per ombreggiamento ed evapotraspirazione (cooling offset ΔT_max).
     * 2. Isolamento radiativo notturno che riduce le dispersioni verso il cielo sereno (warming offset ΔT_min).
     * 3. Attenuazione dell'escursione termica diurna (DTR).
     * 4. Intercettazione idrica fogliare con riduzione della pioggia netta al suolo (throughfall).
     * 5. Incremento moderato dell'umidità relativa dell'aria sub-canopy.
     *
     * @param day Giornata meteorologica grezza [ProcessedDay].
     * @param canopyCover Frazione di copertura arborea [0.0, 1.0] (0.0 = campo aperto, 0.85 = foresta densa).
     * @return Istanza di [ProcessedDay] con parametri microclimatici sub-canopy corretti.
     */
    fun applyCanopyBuffering(
        day: ProcessedDay,
        canopyCover: Double = 0.80
    ): ProcessedDay {
        val c = canopyCover.coerceIn(0.0, 1.0)
        if (c <= 0.001) return day

        // 1. Attenuazione diurna massime (De Frenne offset estivo continuo C1 senza scalini a 18°C - F18, REG-18)
        val baseCooling = 0.5 * kotlin.math.max(0.0, (day.maxTemp - 5.0) / 13.0)
        val hotDayExtra = smoothstep(12.0, 24.0, day.maxTemp.toDouble()) * kotlin.math.min(3.5, 0.18 * kotlin.math.max(0.0, day.maxTemp - 12.0))
        val maxOffset = c * kotlin.math.min(4.0, baseCooling + hotDayExtra)

        // 2. Isolamento radiativo notturno (Effetto serra della volta forestale che blocca dispersioni a onde lunghe)
        val rawMinOffset = c * (1.2 + 0.5 * smoothstep(0.0, 10.0, 10.0 - day.minTemp))

        // 3. Rispetto naturale del gradiente termico diurno DTR senza inversione forzata né swap artificioso (F18)
        val rawDtr = kotlin.math.max(0.0, (day.maxTemp - day.minTemp).toDouble())
        val maxAllowedOffset = rawDtr * 0.45
        val effectiveMaxOffset = kotlin.math.min(maxOffset, maxAllowedOffset)
        val effectiveMinOffset = kotlin.math.min(rawMinOffset, maxAllowedOffset)

        val subMaxTemp = (day.maxTemp - effectiveMaxOffset).toFloat()
        val subMinTemp = (day.minTemp + effectiveMinOffset).toFloat()

        val deltaAvg = (effectiveMinOffset - effectiveMaxOffset) / 2.0
        val subAvgTemp = (day.avgTemp + deltaAvg).coerceIn(subMinTemp.toDouble(), subMaxTemp.toDouble()).toFloat()

        // 4. Intercettazione idrica chiome e throughfall (Bonet et al. / CTFC)
        val grossPrecip = day.totalPrecip.toDouble()
        val throughfall = if (grossPrecip > 0.0) {
            val interceptionLossFraction = c * (0.15 + 0.20 * kotlin.math.exp(-grossPrecip / 8.0))
            (grossPrecip * (1.0 - interceptionLossFraction)).coerceAtLeast(0.0)
        } else {
            0.0
        }

        // 5. Umidità relativa sub-canopy (minore ventilazione ed evapotraspirazione interna)
        val humOffset = c * 6.0 * (1.0 - day.avgHumidity.toDouble() / 100.0)
        val subHumidity = (day.avgHumidity + humOffset).coerceIn(0.0, 100.0).toFloat()

        return day.copy(
            avgTemp = subAvgTemp,
            minTemp = subMinTemp,
            maxTemp = subMaxTemp,
            totalPrecip = throughfall.toFloat(),
            avgHumidity = subHumidity
        )
    }

    /**
     * Mappa l'intera serie temporale applicando il modello di volta forestale (De Frenne Offset).
     */
    fun applyCanopyBuffering(
        days: List<ProcessedDay>,
        canopyCover: Double = 0.80
    ): List<ProcessedDay> {
        if (canopyCover <= 0.001) return days
        return days.map { applyCanopyBuffering(it, canopyCover) }
    }

    /**
     * Calcola la penalità continua dell'escursione termica diurna (DTR).
     *
     * @param dtr Escursione termica giornaliera in °C (T_max - T_min).
     * @param usePhenologicalInertia Se true, applica la transizione continua C1 smoothstep in [12°C, 18°C].
     * @return Moltiplicatore continuo nell'intervallo [0.80, 1.00].
     */
    fun calculateDtrPenalty(dtr: Double, usePhenologicalInertia: Boolean = true): Double {
        return if (usePhenologicalInertia) {
            1.0 - 0.20 * smoothstep(12.0, 18.0, dtr)
        } else {
            if (dtr > 15.0) 0.8 else 1.0
        }
    }

    /**
     * Calcola il punteggio meteorologico composito (0..100) per una specifica data.
     *
     * Integra le quattro componenti continue ponderate secondo [EcologicalWeightsConfig]:
     * - Idratazione da precipitazioni con inerzia fenologica continua f(tau) ([EcologicalWeightsConfig.rainWeight]%)
     * - Regime termico medio recente ([EcologicalWeightsConfig.tempWeight]%)
     * - Umidità relativa aria/suolo multi-orizzonte ([EcologicalWeightsConfig.humidityWeight]%)
     * - Shock termico induttivo con latenza biologica differita ([EcologicalWeightsConfig.thermalShockWeight]%)
     *
     * Integra opzionalmente il microclima di volta forestale (De Frenne Offset) tramite [canopyCover].
     *
     * @param dayIndex Indice del giorno bersaglio all'interno della lista cronologica [allData].
     * @param allData Serie temporale completa dei dati meteorologici giornalieri [ProcessedDay].
     * @param spunHyphalDensity Densità ifale sotterranea SPUN in m/cm³, se disponibile.
     * @param species Profilo ecologico della specie target [MushroomSpecies].
     * @param config Configurazione tipizzata dei pesi e delle finestre climatiche [EcologicalWeightsConfig].
     * @param canopyCover Frazione di copertura boschiva [0.0, 1.0] per il microclima sub-canopy.
     * @return Punteggio meteorologico intero normalizzato nell'intervallo [0, 100].
     */
    fun calculateWeatherScore(
        dayIndex: Int,
        allData: List<ProcessedDay>,
        spunHyphalDensity: Float? = null,
        species: MushroomSpecies = SPECIES_CATALOG[0],
        config: EcologicalWeightsConfig = EcologicalWeightsConfig.DEFAULT,
        canopyCover: Double? = null
    ): Int {
        if (dayIndex < 0 || dayIndex >= allData.size) return 0

        val effectiveCanopy = canopyCover?.coerceIn(0.0, 1.0) ?: 0.0
        val effectiveData = if (effectiveCanopy > 0.001) applyCanopyBuffering(allData, effectiveCanopy) else allData

        // Calcolo della componente idrica (convoluzione fenologica f(tau) o finestra legacy)
        val effectiveRain: Double
        if (config.usePhenologicalInertia) {
            effectiveRain = calculateEffectiveRainfall(dayIndex, effectiveData, species)
        } else {
            val rainStart = max(0, dayIndex - config.rainWindowDays)
            val rainEnd = max(0, dayIndex - config.rainLagDays)
            val rainWindow = if (rainStart < rainEnd && rainEnd <= effectiveData.size) {
                effectiveData.subList(rainStart, rainEnd)
            } else {
                emptyList()
            }
            effectiveRain = rainWindow.sumOf { it.totalPrecip.toDouble() }
        }
        var rainScore = rainScoreSmooth(effectiveRain, species) * config.rainWeight

        // Modulatore biologico SPUN: rete ifale densa (>5.0 m/cm3) amplifica la risposta a piogge moderate
        if (spunHyphalDensity != null && spunHyphalDensity >= 5.0f && effectiveRain >= config.minRainForShockMm) {
            rainScore = min(config.rainWeight, rainScore + 6.0)
        } else if (spunHyphalDensity != null && spunHyphalDensity < 2.5f) {
            rainScore = max(0.0, rainScore - 4.0)
        }

        // Calcolo continuo della temperatura media (finestra ultimi tempWindowDays giorni)
        val tempStart = max(0, dayIndex - config.tempWindowDays)
        val tempWindow = if (tempStart < dayIndex && dayIndex <= effectiveData.size) {
            effectiveData.subList(tempStart, dayIndex)
        } else {
            emptyList()
        }
        val avgTempLast5Days = if (tempWindow.isNotEmpty()) {
            tempWindow.sumOf { it.avgTemp.toDouble() } / tempWindow.size
        } else {
            0.0
        }
        val minTempRecent = if (tempWindow.isNotEmpty()) tempWindow.minOf { it.minTemp.toDouble() } else avgTempLast5Days
        val nocturnalInhibition = nocturnalChillingInhibition(minTempRecent.toFloat(), species)
        
        val currentDay = if (dayIndex < effectiveData.size) effectiveData[dayIndex] else effectiveData.lastOrNull()
        val dtr = if (currentDay != null) (currentDay.maxTemp - currentDay.minTemp).toDouble() else 0.0
        val dtrPenalty = calculateDtrPenalty(dtr, config.usePhenologicalInertia)

        val effectiveTempScore: Double
        if (config.usePhenologicalInertia && dayIndex >= 10) {
            // Condizionamento termico di medio termine a 20 giorni (Brejon Lamartinière & Hoffman, 2025/2026)
            val mediumStart = max(0, dayIndex - 20)
            val mediumEnd = max(0, dayIndex - config.tempWindowDays)
            val mediumWindow = if (mediumStart < mediumEnd && mediumEnd <= effectiveData.size) {
                effectiveData.subList(mediumStart, mediumEnd)
            } else {
                emptyList()
            }
            val avgTempMediumTerm = if (mediumWindow.isNotEmpty()) {
                mediumWindow.sumOf { it.avgTemp.toDouble() } / mediumWindow.size
            } else {
                avgTempLast5Days
            }
            val mediumScore = ctmi(
                temp = avgTempMediumTerm,
                tMin = species.toleratedTempMin.toDouble(),
                tOpt = species.optimalTemp.toDouble(),
                tMax = species.toleratedTempMax.toDouble()
            )
            val shortScore = tempScoreSmooth(avgTempLast5Days, species)
            effectiveTempScore = 0.75 * shortScore + 0.25 * mediumScore
        } else {
            effectiveTempScore = tempScoreSmooth(avgTempLast5Days, species)
        }

        val tempScore = effectiveTempScore * config.tempWeight * nocturnalInhibition * dtrPenalty

        // Calcolo continuo dell'umidità relativa e idratazione suolo (finestra humidityWindowDays giorni fino a oggi)
        val humStart = max(0, dayIndex - config.humidityWindowDays)
        val humEnd = min(effectiveData.size, dayIndex + 1)
        val humWindow = if (humStart < humEnd) {
            effectiveData.subList(humStart, humEnd)
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
        val minDrop = if (spunHyphalDensity != null && spunHyphalDensity >= 5.0f) {
            config.spunAssistedThermalDropMin
        } else {
            config.standardThermalDropMin
        }

        if (config.usePhenologicalInertia) {
            if (dayIndex >= 2 && effectiveRain >= config.minRainForShockMm) {
                var bestShock = 0.0
                for (j in 2 until dayIndex) {
                    val tempBefore = effectiveData[max(0, j - 3)].avgTemp
                    val tempAfter = effectiveData[j].avgTemp
                    val drop = (tempBefore - tempAfter).toDouble()
                    if (drop > minDrop) {
                        val tau = (dayIndex - j).toDouble()
                        val phenoWeight = phenologyKernel(
                            tauDays = tau,
                            tauPeak = species.phenologyLatencyPeakDays,
                            alpha = species.phenologyShapeAlpha
                        )
                        val dropFactor = ((drop - minDrop) / config.shockDropSaturationSpan).coerceIn(0.0, 1.0)
                        val rainFactor = (effectiveRain / config.shockRainSaturationMm).coerceIn(0.0, 1.0)
                        val candidateShock = config.thermalShockWeight * dropFactor * rainFactor * phenoWeight
                        if (candidateShock > bestShock) {
                            bestShock = candidateShock
                        }
                    }
                }
                shockScore = bestShock
            }
        } else {
            if (dayIndex > 4 && effectiveRain >= config.minRainForShockMm) {
                val tempBefore = effectiveData[dayIndex - 4].avgTemp
                val tempAfter = effectiveData[dayIndex - 1].avgTemp
                val drop = (tempBefore - tempAfter).toDouble()
                if (drop > minDrop) {
                    val dropFactor = ((drop - minDrop) / config.shockDropSaturationSpan).coerceIn(0.0, 1.0)
                    val rainFactor = (effectiveRain / config.shockRainSaturationMm).coerceIn(0.0, 1.0)
                    shockScore = config.thermalShockWeight * dropFactor * rainFactor
                }
            }
        }
        val rawWeather = (rainScore + tempScore + humScore + shockScore)

        // Gating ecologico termico/fisiologico Liebig (F06, REG-08, REG-19)
        // Quando le condizioni termiche medie recenti sono severamente incompatibili per la specie
        // (T < Tmin - 3°C o T > Tmax + 3°C), la formazione di nuovi sporocarpi non può avvenire:
        // l'additività di pioggia e umidità non deve generare punteggi favorevoli illusori (es. score 48 a 0°C).
        val thermalViability = if (config.usePhenologicalInertia) {
            when {
                avgTempLast5Days < species.toleratedTempMin.toDouble() -> {
                    smoothstep(
                        species.toleratedTempMin.toDouble() - 3.0,
                        species.toleratedTempMin.toDouble(),
                        avgTempLast5Days
                    )
                }
                avgTempLast5Days > species.toleratedTempMax.toDouble() -> {
                    1.0 - smoothstep(
                        species.toleratedTempMax.toDouble(),
                        species.toleratedTempMax.toDouble() + 3.0,
                        avgTempLast5Days
                    )
                }
                else -> 1.0
            }
        } else {
            1.0
        }

        return (rawWeather * thermalViability).coerceIn(0.0, 100.0).roundToInt()
    }

    /**
     * Valuta in dettaglio la fase fenologica di crescita fungina e il relativo moltiplicatore continuo.
     *
     * Supera la finestra rigida a 10 giorni parametrando la progressione biologica sulla specifica
     * latenza di picco della specie ([species.phenologyLatencyPeakDays]) e forma del kernel [species.phenologyShapeAlpha].
     *
     * @param processedData Serie temporale dei giorni elaborati contenente lo storico meteo.
     * @param species Specie micologica target [MushroomSpecies].
     * @param dayIndex Indice del giorno target (default 14).
     * @return Istanza strutturata di [GrowthPhaseEvaluation].
     */
    fun evaluateGrowthPhase(
        processedData: List<ProcessedDay>,
        species: MushroomSpecies = SPECIES_CATALOG[0],
        dayIndex: Int = 14
    ): GrowthPhaseEvaluation {
        val effectiveToday = min(dayIndex, processedData.size - 1)
        if (effectiveToday < 0 || processedData.isEmpty()) {
            return GrowthPhaseEvaluation(
                phaseText = "Fase: Dati insufficienti per il calcolo fenologico.",
                multiplier = 0.25,
                stage = GrowthStage.WAITING_FOR_RAIN
            )
        }

        val tauPeak = species.phenologyLatencyPeakDays
        val hydrationThreshold = max(2, (0.35 * tauPeak).roundToInt())
        val incubationThreshold = max(hydrationThreshold + 1, (0.75 * tauPeak).roundToInt())
        val fruitingThreshold = max(incubationThreshold + 1, (1.35 * tauPeak).roundToInt())
        val maxLookback = max(0, effectiveToday - (2.5 * tauPeak).roundToInt())

        val candidateEvents = extractCandidateRainEvents(processedData, effectiveToday, maxLookback)
        if (candidateEvents.isEmpty()) {
            return GrowthPhaseEvaluation(
                phaseText = "Fase: Crescita assente (in attesa di precipitazioni).",
                multiplier = 0.25,
                stage = GrowthStage.WAITING_FOR_RAIN
            )
        }

        val distinctEvents = clusterRainEvents(candidateEvents)
        val recentTrigger = distinctEvents.first()
        val targetRain = species.minRainAccumulation
        val earlierCandidate = distinctEvents.drop(1).firstOrNull { earlier ->
            val daysSinceEarlier = effectiveToday - earlier.triggerIndex
            val inFruitingWindow = daysSinceEarlier in (hydrationThreshold + 1)..fruitingThreshold
            val isSaturatingRain = earlier.rainAmount >= max(25.0f, targetRain * 0.70f)
            inFruitingWindow && isSaturatingRain
        }

        val evalRecent = evaluateStageFromTrigger(
            activeTrigger = recentTrigger,
            effectiveToday = effectiveToday,
            species = species,
            hydrationThreshold = hydrationThreshold,
            incubationThreshold = incubationThreshold,
            fruitingThreshold = fruitingThreshold,
            tauPeak = tauPeak
        )

        if (earlierCandidate == null) {
            return evalRecent
        }

        val evalEarlier = evaluateStageFromTrigger(
            activeTrigger = earlierCandidate,
            effectiveToday = effectiveToday,
            species = species,
            hydrationThreshold = hydrationThreshold,
            incubationThreshold = incubationThreshold,
            fruitingThreshold = fruitingThreshold,
            tauPeak = tauPeak
        )

        // Raccordo continuo Lipschitziano (F04 / REG-03) attorno al reset 0.70
        val ratio = (recentTrigger.rainAmount / earlierCandidate.rainAmount.coerceAtLeast(1.0f)).toDouble()
        val transition = smoothstep(0.50, 0.90, ratio)
        val blendedMultiplier = (1.0 - transition) * evalEarlier.multiplier + transition * evalRecent.multiplier

        return if (transition < 0.5) {
            evalEarlier.copy(multiplier = blendedMultiplier)
        } else {
            evalRecent.copy(multiplier = blendedMultiplier)
        }
    }

    internal fun extractCandidateRainEvents(
        processedData: List<ProcessedDay>,
        effectiveToday: Int,
        maxLookback: Int
    ): List<RainTrigger> {
        val candidateEvents = mutableListOf<RainTrigger>()
        var i = effectiveToday
        while (i >= maxLookback) {
            val precip = processedData[i].liquidPrecip
            if (precip >= 10.0f) {
                candidateEvents.add(RainTrigger(i, precip))
                i--
            } else if (precip >= 0.5f && i >= 2) {
                val threeDayRain = processedData[i].liquidPrecip +
                        processedData[i - 1].liquidPrecip +
                        processedData[i - 2].liquidPrecip
                if (threeDayRain >= 17.5f) {
                    candidateEvents.add(RainTrigger(i - 2, threeDayRain))
                    i -= 3
                } else if (i >= 4) {
                    val fiveDayRain = threeDayRain +
                            processedData[i - 3].liquidPrecip +
                            processedData[i - 4].liquidPrecip
                    if (fiveDayRain >= 24.0f) {
                        candidateEvents.add(RainTrigger(i - 4, fiveDayRain))
                        i -= 5
                    } else {
                        i--
                    }
                } else {
                    i--
                }
            } else {
                i--
            }
        }
        return candidateEvents
    }

    internal fun clusterRainEvents(candidateEvents: List<RainTrigger>): List<RainTrigger> {
        val distinctEvents = mutableListOf<RainTrigger>()
        candidateEvents.sortedByDescending { it.triggerIndex }.forEach { ev ->
            val existing = distinctEvents.firstOrNull { abs(it.triggerIndex - ev.triggerIndex) <= 2 }
            if (existing == null) {
                distinctEvents.add(ev.copy())
            } else {
                val mergedRain = existing.rainAmount + ev.rainAmount
                val latestIndex = max(existing.triggerIndex, ev.triggerIndex)
                val idx = distinctEvents.indexOf(existing)
                distinctEvents[idx] = RainTrigger(latestIndex, mergedRain)
            }
        }
        return distinctEvents
    }

    internal fun resolveActiveRainTrigger(
        distinctEvents: List<RainTrigger>,
        effectiveToday: Int,
        species: MushroomSpecies,
        hydrationThreshold: Int,
        fruitingThreshold: Int
    ): RainTrigger {
        val recentTrigger = distinctEvents.first()
        val targetRain = species.minRainAccumulation
        val earlierActiveFlush = distinctEvents.drop(1).firstOrNull { earlier ->
            val daysSinceEarlier = effectiveToday - earlier.triggerIndex
            val inFruitingWindow = daysSinceEarlier in (hydrationThreshold + 1)..fruitingThreshold
            val isSaturatingRain = earlier.rainAmount >= max(25.0f, targetRain * 0.70f)
            val isRecentOnlySecondaryShower = recentTrigger.rainAmount < earlier.rainAmount * 0.70f
            inFruitingWindow && isSaturatingRain && isRecentOnlySecondaryShower
        }
        return earlierActiveFlush ?: recentTrigger
    }

    internal fun evaluateStageFromTrigger(
        activeTrigger: RainTrigger,
        effectiveToday: Int,
        species: MushroomSpecies,
        hydrationThreshold: Int,
        incubationThreshold: Int,
        fruitingThreshold: Int,
        tauPeak: Double
    ): GrowthPhaseEvaluation {
        val daysSinceTrigger = max(0, effectiveToday - activeTrigger.triggerIndex)
        val k = phenologyKernel(
            tauDays = daysSinceTrigger.toDouble(),
            tauPeak = tauPeak,
            alpha = species.phenologyShapeAlpha
        )

        val stage: GrowthStage
        val phaseText: String
        val baseMultiplier: Double

        when {
            daysSinceTrigger <= hydrationThreshold -> {
                stage = GrowthStage.MYCELIAL_HYDRATION
                phaseText = "Fase: Idratazione miceliare (piogge recenti $daysSinceTrigger giorni fa)."
                baseMultiplier = (0.35 + 0.15 * (daysSinceTrigger.toDouble() / hydrationThreshold.coerceAtLeast(1)))
                    .coerceIn(0.35, 0.50)
            }
            daysSinceTrigger <= incubationThreshold -> {
                stage = GrowthStage.PRIMORDIA_INCUBATION
                val daysToFruiting = max(1, (tauPeak - daysSinceTrigger).roundToInt())
                phaseText = "Fase: Incubazione primordi (differenziazione in circa $daysToFruiting giorni)."
                val kHydration = phenologyKernel(hydrationThreshold.toDouble(), tauPeak, species.phenologyShapeAlpha)
                val denom = (1.0 - kHydration).coerceAtLeast(0.01)
                val t = ((k - kHydration) / denom).coerceIn(0.0, 1.0)
                baseMultiplier = (0.50 + 0.50 * t).coerceIn(0.50, 0.95)
            }
            daysSinceTrigger <= fruitingThreshold -> {
                stage = GrowthStage.ACTIVE_FRUITING
                phaseText = "Fase: Buttata attiva (finestra ottimale di raccolta)."
                val kHydration = phenologyKernel(hydrationThreshold.toDouble(), tauPeak, species.phenologyShapeAlpha)
                val denom = (1.0 - kHydration).coerceAtLeast(0.01)
                val t = ((k - kHydration) / denom).coerceIn(0.0, 1.0)
                baseMultiplier = (0.50 + 0.50 * t).coerceIn(0.85, 1.00)
            }
            else -> {
                stage = GrowthStage.WANING
                phaseText = "Fase: Flusso in esaurimento (in attesa di nuove piogge)."
                val kHydration = phenologyKernel(hydrationThreshold.toDouble(), tauPeak, species.phenologyShapeAlpha)
                val denom = (1.0 - kHydration).coerceAtLeast(0.01)
                val t = ((k - kHydration) / denom).coerceIn(0.0, 1.0)
                baseMultiplier = if (k >= kHydration) {
                    (0.50 + 0.50 * t).coerceIn(0.25, 0.85)
                } else {
                    (0.25 + 0.25 * (k / kHydration.coerceAtLeast(0.01))).coerceIn(0.25, 0.50)
                }
            }
        }

        return GrowthPhaseEvaluation(
            phaseText = phaseText,
            multiplier = baseMultiplier,
            daysSinceTrigger = daysSinceTrigger,
            stage = stage
        )
    }

    /**
     * Determina la fase fenologica di sviluppo miceliare e fruttificazione a partire dalla serie storica recente.
     *
     * @param processedData Serie temporale dei giorni elaborati contenente lo storico meteo.
     * @param species Profilo ecologico della specie target [MushroomSpecies].
     * @param dayIndex Indice del giorno target all'interno di [processedData] (default 14).
     * @return Stringa descrittiva della fase fenologica corrente (es. Idratazione, Incubazione primordi, Buttata attiva).
     */
    fun calculateGrowthPhase(
        processedData: List<ProcessedDay>,
        species: MushroomSpecies = SPECIES_CATALOG[0],
        dayIndex: Int = 14
    ): String {
        return evaluateGrowthPhase(processedData, species, dayIndex).phaseText
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
            val isColdSeason = month in listOf(3, 9, 10, 11) || (month in listOf(4, 8) && avgTemp < 15.0) || avgTemp < 13.0

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
     * Ricava dinamicamente l'indice del giorno odierno all'interno della serie temporale [processedData],
     * tenendo conto del fuso orario geografico della località per evitare slittamenti a mezzanotte (F13 / REG-13).
     */
    fun deriveTodayIndex(
        processedData: List<ProcessedDay>,
        timezone: String? = null
    ): Int {
        if (processedData.isEmpty()) return 0
        val tz = if (!timezone.isNullOrBlank()) {
            try { java.util.TimeZone.getTimeZone(timezone) } catch (_: Exception) { java.util.TimeZone.getDefault() }
        } else {
            java.util.TimeZone.getDefault()
        }
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = tz
        }
        val todayIso = sdf.format(java.util.Date())
        val idx = processedData.indexOfFirst { it.date == todayIso }
        return if (idx >= 0) idx else min(processedData.size - 1, 14.coerceAtLeast(min(28, processedData.size - 1)))
    }

    /**
     * Analizza la finestra previsionale futura (+1..+5 giorni) per stimare il trend di crescita.
     *
     * @param processedData Serie temporale dei giorni con storico e previsioni future.
     * @param todayIndex Indice del giorno odierno (default ricavato dinamicamente con [deriveTodayIndex]).
     * @return Paragrafo descrittivo Markdown con l'evoluzione del trend idrico e di fruttificazione.
     */
    fun analyzeFutureTrend(
        processedData: List<ProcessedDay>,
        todayIndex: Int = deriveTodayIndex(processedData)
    ): String {
        if (todayIndex < 0 || todayIndex >= processedData.size) return ""
        if (processedData.size <= todayIndex + 1) return ""

        val futureWindow = processedData.subList(todayIndex + 1, min(processedData.size, todayIndex + 6))
        if (futureWindow.isEmpty()) return ""
        val futureRain = futureWindow.sumOf { it.liquidPrecip.toDouble() }

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
     * Modello Termico Cardinale con Flessione (Yin et al., 1995; Yan & Hunt, 1999).
     *
     * Modella con rigore termodinamico la cinetica cellulare ed enzimatica dei macromiceti:
     * - Risposta nulla per temp <= tMin o temp >= tMax.
     * - Massimo unitario (1.0) esattamente alla temperatura ottimale tOpt, con derivata prima nulla.
     * - Formulazione priva di singolarità interne o divisioni per zero (F02 / REG-01, REG-02).
     * - Esponenti sempre >= 1.0 garantendo pendenza limitata e Lipschitz-continuità.
     *
     * @param temp Temperatura media in °C.
     * @param tMin Temperatura minima cardinale di tolleranza miceliare in °C.
     * @param tOpt Temperatura ottimale di carpogenesi in °C.
     * @param tMax Temperatura massima cardinale di tolleranza miceliare in °C.
     * @return Risposta termica cardinale normalizzata in [0.0, 1.0].
     */
    fun ctmi(temp: Double, tMin: Double, tOpt: Double, tMax: Double): Double {
        if (tMin >= tOpt || tOpt >= tMax) return 0.0
        if (temp <= tMin || temp >= tMax) return 0.0

        val spanMin = tOpt - tMin
        val spanMax = tMax - tOpt
        val x = (temp - tMin) / spanMin
        val y = (tMax - temp) / spanMax

        return if (spanMin <= spanMax) {
            val alpha = spanMax / spanMin
            (x * Math.pow(y, alpha)).coerceIn(0.0, 1.0)
        } else {
            val beta = spanMin / spanMax
            (Math.pow(x, beta) * y).coerceIn(0.0, 1.0)
        }
    }

    /**
     * Curva di risposta termica biologica continua normalizzata nell'intervallo [0.0, 1.0].
     *
     * Valuta l'idoneità termica istantanea o a breve termine:
     * - Valore nullo per temperature esterne all'intervallo di tolleranza [toleratedTempMin .. toleratedTempMax].
     * - Valore unitario (1.0) all'interno dell'intervallo termico ideale [idealTempMin .. idealTempMax].
     * - Rampa lineare continua Lipschitziana con pendenza controllata sulle fasce di transizione.
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
    fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
        if (edge1 <= edge0) return if (x >= edge1) 1.0 else 0.0
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    /**
     * Valuta in modo continuo il contenuto idrico del suolo [0.0, 1.0] combinando l'orizzonte superficiale (0-7 cm)
     * e l'orizzonte radicale profondo (7-28 cm), modulati dall'evapotraspirazione di riferimento FAO ET0.
     *
     * Integra la dinamica idraulica di van Genuchten penalizzando sia il deficit idrico/disseccamento (< 0.20 m³/m³),
     * sia la saturazione asfittica dei macropori (> 0.40 m³/m³) che induce ipossia e lisi batterica dei primordi.
     *
     * @param m0To7 Umidità volumetrica superficiale in m³/m³ (orizzonte primordi/lettiera). Range ottimale: 0.22..0.38.
     * @param m7To28 Umidità volumetrica profonda in m³/m³ (orizzonte miceliare perenne). Range ottimale: 0.20..0.35.
     * @param et0 Evapotraspirazione cumulata giornaliera di riferimento FAO ET0 in mm/giorno.
     * @return Punteggio continuo normalizzato [0.0, 1.0]. Se entrambi gli orizzonti sono nulli, restituisce 1.0 (neutro).
     */
    fun soilMoistureScoreSmooth(m0To7: Double?, m7To28: Double?, et0: Double? = null): Double {
        if (m0To7 == null && m7To28 == null) return 1.0

        // Calcolo continuo orizzonte superficiale 0-7 cm (induzione e idratazione primordiale)
        // Dinamica van Genuchten: capacità di campo ottimale 0.22..0.38 m³/m³;
        // decadimento per asfissia e lisi dei primordi per saturazione dei macropori oltre 0.38 m³/m³,
        // con crollo ipossico severo oltre 0.44 m³/m³.
        val s0To7 = if (m0To7 != null) {
            when {
                m0To7 < 0.10 -> 0.10
                m0To7 in 0.10..0.22 -> 0.10 + 0.90 * smoothstep(0.10, 0.22, m0To7)
                m0To7 in 0.22..0.38 -> 1.0
                m0To7 in 0.38..0.44 -> 1.0 - 0.50 * smoothstep(0.38, 0.44, m0To7)
                m0To7 in 0.44..0.52 -> 0.50 - 0.35 * smoothstep(0.44, 0.52, m0To7)
                else -> 0.15
            }
        } else null

        // Calcolo continuo orizzonte profondo 7-28 cm (rete ifale perenne e assorbimento)
        // Saturazione prolungata oltre 0.42 m³/m³ induce stasi respiratoria radicale e miceliare.
        val s7To28 = if (m7To28 != null) {
            when {
                m7To28 < 0.12 -> 0.20
                m7To28 in 0.12..0.20 -> 0.20 + 0.80 * smoothstep(0.12, 0.20, m7To28)
                m7To28 in 0.20..0.35 -> 1.0
                m7To28 in 0.35..0.42 -> 1.0 - 0.45 * smoothstep(0.35, 0.42, m7To28)
                m7To28 in 0.42..0.50 -> 0.55 - 0.35 * smoothstep(0.42, 0.50, m7To28)
                else -> 0.20
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
     * Converte la frazione di copertura chiome [canopyCover] in Area Basimetrica equivalente $G$ (in m²/ha).
     *
     * Basata sulle relazioni dendrometriche dei boschi temperati e mediterranei europei (CTFC / de-Miguel et al. 2014):
     * - Copertura chiome 0.0 (prato/campo aperto): 0 m²/ha
     * - Copertura chiome 0.45 (bosco rado/aperto): ~18-20 m²/ha
     * - Copertura chiome 0.70 (bosco gestito a densità media): ~32-35 m²/ha
     * - Copertura chiome 0.85-0.95 (bosco denso/chiuso non diradato): ~42-50 m²/ha
     *
     * @param canopyCover Frazione di copertura arborea [0.0, 1.0].
     * @return Area basimetrica stimata in m²/ha.
     */
    fun canopyCoverToBasalArea(canopyCover: Double): Double {
        val c = canopyCover.coerceIn(0.0, 1.0)
        if (c <= 0.001) return 0.0
        return 50.0 * Math.pow(c, 1.15)
    }

    /**
     * Calcola la risposta ecologica continua e unimodale alla densità del popolamento arboreo e all'Area Basimetrica $G$
     * (de-Miguel et al. 2014, Bonet et al. 2012 - CTFC).
     *
     * Nei popolamenti forestali, la produttività di sporocarpi segue la relazione empirica unimodale:
     * $$\ln(\text{yield}) \propto b_1 \ln(G) - b_2 \sqrt{G}$$
     * con massimo al valore ottimale specifico della specie ($G_{\text{opt}} \approx 32\text{ m}^2/\text{ha}$ per Boletus edulis,
     * $G_{\text{opt}} \approx 20\text{ m}^2/\text{ha}$ per Lactarius deliciosus).
     *
     * Popolamenti troppo radi ($G < 15\text{ m}^2/\text{ha}$) dispongono di radici ospiti insufficienti, mentre
     * popolamenti troppo densi ($G > 50\text{ m}^2/\text{ha}$) soffrono di eccessiva intercettazione idrica delle fronde,
     * oscuramento e forte competizione radicale.
     *
     * @param canopyCover Frazione di copertura chiome [0.0, 1.0].
     * @param species Profilo ecologico della specie target [MushroomSpecies].
     * @return Moltiplicatore continuo normalizzato nell'intervallo [0.65, 1.00].
     */
    fun standDensityResponseUnimodal(
        canopyCover: Double,
        species: MushroomSpecies = SPECIES_CATALOG[0]
    ): Double {
        if (species.category == EcologicalCategory.SAPROTROPHIC) {
            return 1.0
        }
        val g = canopyCoverToBasalArea(canopyCover)
        val gOpt = species.optimalBasalAreaM2Ha.toDouble()
        if (g <= 0.5 || gOpt <= 0.5) return 0.65

        val u = kotlin.math.sqrt(g / gOpt)
        val deltaPhi = 2.0 * (kotlin.math.ln(u) - u + 1.0)
        val gamma = 0.75
        val factor = 0.65 + 0.35 * kotlin.math.exp(gamma * deltaPhi)

        return factor.coerceIn(0.65, 1.0)
    }

    /**
     * Calcola la probabilità di presenza biologica stazionale (Stadio 1 del Modello Hurdle, de-Miguel et al. 2014).
     *
     * Modella la barriera logistica di presenza/assenza della specie:
     * $$p_{\text{hurdle}} = \frac{1}{1 + \exp\left(-\gamma \cdot (H_{\text{eff}} \cdot A - H_{\text{crit}})\right)}$$
     *
     * Per specie ectomicorriziche con alta selettività (es. Boletus edulis, Lactarius deliciosus),
     * l'assenza di copertura boschiva (habitatScore < 0.20) o una quota fuori tolleranza biologica
     * abbatte la probabilità di comparsa verso zero indipendentemente dall'accumulo piovoso.
     * Per specie saprofite (es. Macrolepiota procera, Morchella esculenta), le aree aperte o i prati
     * non costituiscono una barriera limitante, garantendo una transizione favorevole.
     *
     * @param habitatScore Punteggio dell'habitat vegetazionale / boschivo (0.0..1.0).
     * @param altitudeScore Punteggio dell'idoneità altimetrica della stazione (0.0..1.0).
     * @param species Profilo biologico della specie micologica target [MushroomSpecies].
     * @return Moltiplicatore continuo normalizzato nell'intervallo [0.0, 1.0].
     */
    fun hurdleOccurrenceProbability(
        habitatScore: Double,
        altitudeScore: Double,
        species: MushroomSpecies = SPECIES_CATALOG[0]
    ): Double {
        val effectiveHab = if (species.category == EcologicalCategory.SAPROTROPHIC) {
            max(habitatScore, 0.85)
        } else {
            habitatScore
        }

        val stationSuitability = (effectiveHab * altitudeScore).coerceIn(0.0, 1.0)
        if (stationSuitability <= 0.001) return 0.0

        val sigma = (0.35 * species.hurdleStrictness).coerceIn(0.05, 0.50)
        val beta = 2.5
        val ratio = stationSuitability / sigma
        val exponent = -Math.pow(ratio, beta)

        return (1.0 - kotlin.math.exp(exponent)).coerceIn(0.0, 1.0)
    }

    /**
     * Valuta in modo integrato e puro l'idoneità stazionale dell'habitat in funzione della specie,
     * della gilda ecologica, delle essenze arboree e della densità dendrometrica delle chiome.
     *
     * @param forestCount Conteggio elementi boschivi rilevati dal radar Overpass.
     * @param specificElementsCount Conteggio alberi ospiti o essenze specifiche target.
     * @param spunData Dati micorrizici SPUN regionali, se disponibili.
     * @param species Specie micologica target [MushroomSpecies].
     * @param canopyCover Frazione di copertura chiome [0.0, 1.0], se già stimata.
     * @return Risultato tipizzato [SpeciesHabitatEvaluation].
     */
    /**
     * Valuta l'idoneità stazionale e la compatibilità ecologica sulla base di evidenze territoriali
     * strutturate [HabitatEvidence] (F08, F09).
     *
     * Supera il mero conteggio dei nodi OSM applicando stime continue di copertura, distanze
     * dal margine e verifica rigorosa dei generi arborei confermati.
     *
     * @param evidence Evidenza strutturata della vegetazione e classificazione territoriale [HabitatEvidence].
     * @param spunData Dati micorrizici SPUN regionali, se disponibili.
     * @param species Specie micologica target [MushroomSpecies].
     * @return Risultato tipizzato [SpeciesHabitatEvaluation].
     */
    fun evaluateSpeciesHabitat(
        evidence: HabitatEvidence,
        spunData: SpunData? = null,
        species: MushroomSpecies = SPECIES_CATALOG[0]
    ): SpeciesHabitatEvaluation {
        val effectiveCanopy = evidence.forestCoverFraction.coerceIn(0.0, 1.0)
        val basalArea = canopyCoverToBasalArea(effectiveCanopy).toFloat()
        val standScore = standDensityResponseUnimodal(effectiveCanopy, species)

        val rawScore: Double
        val baseText: String
        var bonusText = "Nessuna essenza specifica o dato vegetativo rilevato."
        var bonusMult = 1.0

        when (species.category) {
            EcologicalCategory.SAPROTROPHIC -> {
                when (evidence.status) {
                    HabitatStatus.KNOWN_UNSUITABLE -> {
                        rawScore = 0.15
                        baseText = "Habitat: Inadatto (area urbana o artificiale priva di lettiera o prato)."
                    }
                    HabitatStatus.UNKNOWN -> {
                        rawScore = 0.50
                        baseText = "Habitat: Dati geografici non disponibili (stima neutrale per specie umicola)."
                    }
                    HabitatStatus.KNOWN_SUITABLE -> {
                        when {
                            evidence.meadowFraction >= 0.25 -> {
                                rawScore = 0.95
                                baseText = "Habitat: Praticolo e pascoli aperti (favorevole per specie umicola)."
                            }
                            evidence.forestCoverFraction in 0.10..0.50 -> {
                                rawScore = 0.90
                                baseText = "Habitat: Margini boschivi e radure (ottimale per specie umicola)."
                            }
                            evidence.forestCoverFraction > 0.50 -> {
                                rawScore = 0.75
                                baseText = "Habitat: Bosco fitto (meno favorevole per specie eliofile da radura)."
                            }
                            else -> {
                                rawScore = 0.85
                                baseText = "Habitat: Ambiente aperto idoneo per specie da prato."
                            }
                        }
                        if (evidence.meadowFraction > 0.20 || evidence.confirmedHostGenera.isNotEmpty()) {
                            bonusText = "Bonus: Rilevate radure e microhabitat idonei per ${species.vernacularName}!"
                        }
                    }
                }
            }
            EcologicalCategory.PARASITIC -> {
                when (evidence.status) {
                    HabitatStatus.KNOWN_UNSUITABLE -> {
                        rawScore = 0.10
                        baseText = "Habitat: Inadatto (assenza di formazioni arboree o ceppaie per specie lignicola)."
                    }
                    HabitatStatus.UNKNOWN -> {
                        rawScore = 0.45
                        baseText = "Habitat: Dati geografici non disponibili (stima neutrale per specie lignicola)."
                    }
                    HabitatStatus.KNOWN_SUITABLE -> {
                        when {
                            evidence.forestCoverFraction >= 0.60 -> {
                                rawScore = 1.0
                                baseText = "Habitat: Bosco con abbondante necromassa e substrato lignicolo."
                            }
                            evidence.forestCoverFraction >= 0.20 -> {
                                rawScore = 0.85
                                baseText = "Habitat: Presenza di formazioni arboree e ceppaie adatte."
                            }
                            else -> {
                                rawScore = 0.30
                                baseText = "Habitat: Formazioni arboree scarse o rade per specie lignicola."
                            }
                        }
                        if (evidence.confirmedHostGenera.isNotEmpty()) {
                            bonusText = "Bonus: Rilevate essenze ospiti e ceppaie idonee!"
                        }
                    }
                }
            }
            EcologicalCategory.ECTOMYCORRHIZAL -> {
                when (evidence.status) {
                    HabitatStatus.KNOWN_UNSUITABLE -> {
                        rawScore = 0.10
                        baseText = "Habitat: Inadatto (area urbana o artificiale priva di copertura boschiva)."
                    }
                    HabitatStatus.UNKNOWN -> {
                        rawScore = 0.50
                        baseText = "Habitat: Dati geografici non disponibili (stima neutrale di copertura)."
                    }
                    HabitatStatus.KNOWN_SUITABLE -> {
                        when {
                            evidence.forestCoverFraction >= 0.65 -> {
                                rawScore = 1.0
                                baseText = "Habitat: Ideale (punto immerso in area boschiva)."
                            }
                            evidence.forestCoverFraction >= 0.35 -> {
                                rawScore = 0.90
                                baseText = "Habitat: Promettente (vicinanza a boschi e foreste)."
                            }
                            evidence.forestCoverFraction > 0.05 -> {
                                rawScore = 0.65
                                baseText = "Habitat: Misto (presenza di formazioni arboree sparse)."
                            }
                            else -> {
                                rawScore = 0.15
                                baseText = "Habitat: Non ideale (assenza di boschi o alberi ospiti)."
                            }
                        }

                        // Bonus ospiti: concesso SOLO se c'è un genere confermato tra le preferenze della specie (F09, REG-10)
                        val matchingHostGenus = species.preferredCanopyTypes.firstOrNull { pref ->
                            evidence.confirmedHostGenera.any { it.equals(pref, ignoreCase = true) }
                        }
                        if (matchingHostGenus != null) {
                            bonusMult = 1.15
                            bonusText = "Bonus: Rilevati alberi ospiti ($matchingHostGenus) confermati!"
                        }

                        if (spunData != null) {
                            if (spunData.ecmRichness >= 50.0f) {
                                bonusMult = max(bonusMult, 1.15)
                                bonusText = if (matchingHostGenus != null) {
                                    "Bonus: Alberi ($matchingHostGenus) e simbiosi EcM SPUN ottimali (${spunData.ecmRichness.toInt()} specie)!"
                                } else {
                                    "Bonus SPUN: Rete ectomicorrizica eccellente (${spunData.ecmRichness.toInt()} specie)!"
                                }
                            } else if (spunData.ecmRichness < 15.0f && evidence.forestCoverFraction > 0.10) {
                                bonusMult *= 0.80
                            }
                        }
                    }
                }
            }
        }

        val finalScore = (rawScore * bonusMult * standScore).coerceIn(0.10, 1.0)
        return SpeciesHabitatEvaluation(
            score = finalScore,
            baseText = baseText,
            bonusText = bonusText,
            basalAreaM2Ha = basalArea,
            standDensityScore = standScore
        )
    }

    /**
     * Valuta dinamicamente l'idoneità stazionale dell'habitat in funzione della specie selezionata,
     * della gilda ecologica, delle essenze arboree e della densità dendrometrica delle chiome.
     *
     * Adapter compatibile che delega alla valutazione strutturata [evaluateSpeciesHabitat].
     *
     * @param forestCount Conteggio elementi boschivi rilevati dal radar Overpass.
     * @param specificElementsCount Conteggio alberi ospiti o essenze specifiche target.
     * @param spunData Dati micorrizici SPUN regionali, se disponibili.
     * @param species Specie micologica target [MushroomSpecies].
     * @param canopyCover Frazione di copertura chiome [0.0, 1.0], se già stimata.
     * @return Risultato tipizzato [SpeciesHabitatEvaluation].
     */
    fun evaluateSpeciesHabitat(
        forestCount: Int,
        specificElementsCount: Int = 0,
        spunData: SpunData? = null,
        species: MushroomSpecies = SPECIES_CATALOG[0],
        canopyCover: Double? = null
    ): SpeciesHabitatEvaluation {
        val effectiveCanopy = canopyCover ?: when {
            forestCount >= 15 -> 0.85
            forestCount >= 5 -> 0.70
            forestCount >= 1 -> 0.45
            else -> 0.0
        }
        val evidence = HabitatEvidence(
            status = HabitatStatus.KNOWN_SUITABLE,
            forestCoverFraction = effectiveCanopy,
            meadowFraction = if (forestCount == 0) 0.80 else 0.10,
            distanceToNearestForestMeters = if (forestCount > 0) 0.0 else 500.0,
            confirmedHostGenera = if (specificElementsCount > 0) species.preferredCanopyTypes.toSet() else emptySet()
        )
        return evaluateSpeciesHabitat(evidence, spunData, species)
    }

    /**
     * Calcola la probabilità giornaliera continua combinata di fruttificazione (0..100%).
     *
     * Integra il Modello Hurdle a Due Stadi (de-Miguel et al. 2014) con calibrazione asintotica:
     * - Stadio 1: Barriera di presenza/assenza logistica dell'habitat stazionale $p_{\text{hurdle}}$
     * - Stadio 2: Carpogenesi ed emissione sporocarpica condizionata all'afflusso meteorologico
     *
     * @param weatherScore Punteggio meteorologico calcolato (0..100).
     * @param habitatScore Punteggio vegetazionale / boschivo (0.0..1.0).
     * @param altitudeScore Risposta altimetrica continua della specie (0.0..1.0).
     * @param seasonalityScore Risposta fenologica stagionale del mese corrente (0.0..1.0).
     * @param terrainModifier Modificatore continuo del versante, pendenza ed esposizione orografica (default 1.0).
     * @param config Configurazione dei pesi ed esponenti ecologici [EcologicalWeightsConfig].
     * @param species Specie micologica target [MushroomSpecies] per il modello Hurdle (se null, usa prodotto baseline).
     * @return Probabilità percentuale complessiva normalizzata nell'intervallo [0, 100].
     */
    /**
     * Calcola l'indice normalizzato continuo di favorevolezza/idoneità ambientale (0.0..100.0).
     *
     * In conformità con la Revisione Scientifica (Percorso A / Indice Euristico):
     * S_raw = 100 * (W/100)^1.2 * H * A * S * T * p_hurdle * Phi_phase
     * S_calibrated = S_raw se <= 70, altrimenti 70 + 22 * tanh((S_raw - 70)/22)
     *
     * @param weatherScore Punteggio meteorologico calcolato (0..100).
     * @param habitatScore Punteggio vegetazionale / boschivo (0.0..1.0).
     * @param altitudeScore Risposta altimetrica continua della specie (0.0..1.0).
     * @param seasonalityScore Risposta fenologica stagionale del mese corrente (0.0..1.0).
     * @param terrainModifier Modificatore continuo del versante, pendenza ed esposizione orografica (default 1.0).
     * @param config Configurazione dei pesi ed esponenti ecologici [EcologicalWeightsConfig].
     * @param species Specie micologica target [MushroomSpecies] per il modello Hurdle (se null, usa prodotto baseline).
     * @param growthPhaseMultiplier Moltiplicatore continuo di fase fenologica Phi_phase.
     * @return Indice continuo normalizzato nell'intervallo [0.0, 100.0].
     */
    fun calculateSuitabilityScore(
        weatherScore: Int,
        habitatScore: Double,
        altitudeScore: Double,
        seasonalityScore: Double,
        terrainModifier: Double = 1.0,
        config: EcologicalWeightsConfig = EcologicalWeightsConfig.PHENOLOGICAL,
        species: MushroomSpecies? = null,
        growthPhaseMultiplier: Double = 1.0
    ): Double {
        val clampedWeather = weatherScore.coerceIn(0, 100)
        val clampedHabitat = habitatScore.coerceIn(0.0, 1.0)
        val clampedAltitude = altitudeScore.coerceIn(0.0, 1.0)
        val clampedSeasonality = seasonalityScore.coerceIn(0.0, 1.0)
        val clampedTerrain = terrainModifier.coerceIn(0.0, 2.0)
        val clampedPhase = growthPhaseMultiplier.coerceIn(0.0, 1.0)

        if (config == EcologicalWeightsConfig.DEFAULT) {
            return SharedMycoAlgorithms.calculateSuitabilityScore(
                weatherScore = clampedWeather,
                habitatScore = clampedHabitat,
                altitudeScore = clampedAltitude,
                seasonalityScore = clampedSeasonality,
                terrainModifier = clampedTerrain,
                growthPhaseMultiplier = clampedPhase
            )
        }
        val weightedWeatherScore = 100.0 * Math.pow(clampedWeather / 100.0, config.weatherExponent)

        val rawScore = if (species != null && config.usePhenologicalInertia) {
            // Modello Hurdle a Due Stadi (de-Miguel et al. 2014)
            val pHurdle = hurdleOccurrenceProbability(clampedHabitat, clampedAltitude, species)
            val combined = weightedWeatherScore * clampedHabitat * clampedAltitude * clampedSeasonality * clampedTerrain * pHurdle * clampedPhase
            combined.coerceAtLeast(0.0)
        } else {
            // Formulazione moltiplicativa classica pura (piena invarianza e retrocompatibilità per oracolo)
            val combined = weightedWeatherScore * clampedHabitat * clampedAltitude * clampedSeasonality * clampedTerrain * clampedPhase
            combined.coerceAtLeast(0.0)
        }

        val pKnee = config.probabilityKneeThreshold
        val pMax = config.probabilityMaxAsymptote

        val calibrated = if (rawScore > pKnee) {
            pKnee + (pMax - pKnee) * kotlin.math.tanh((rawScore - pKnee) / (pMax - pKnee))
        } else {
            rawScore
        }

        return calibrated.coerceIn(0.0, 100.0)
    }

    /**
     * Calcola la probabilità giornaliera combinata di fruttificazione (0..100%).
     * Compatibile retroattivamente con l'oracolo e i test legacy.
     */
    fun dailyGrowthProbability(
        weatherScore: Int,
        habitatScore: Double,
        altitudeScore: Double,
        seasonalityScore: Double,
        terrainModifier: Double = 1.0,
        config: EcologicalWeightsConfig = EcologicalWeightsConfig.DEFAULT,
        species: MushroomSpecies? = null,
        growthPhaseMultiplier: Double = 1.0
    ): Int {
        if (config == EcologicalWeightsConfig.DEFAULT) {
            return SharedMycoAlgorithms.growthProbability(
                weatherScore,
                habitatScore,
                altitudeScore,
                seasonalityScore,
                terrainModifier,
            )
        }
        return calculateSuitabilityScore(
            weatherScore = weatherScore,
            habitatScore = habitatScore,
            altitudeScore = altitudeScore,
            seasonalityScore = seasonalityScore,
            terrainModifier = terrainModifier,
            config = config,
            species = species,
            growthPhaseMultiplier = growthPhaseMultiplier
        ).toInt().coerceIn(0, 100)
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
        totalEvapotranspiration: Float? = null,
        canopyCover: Double? = null,
        effectiveRainMm: Double? = null
    ): List<Factor> {
        val factors = mutableListOf<Factor>()

        // 1. Temperatura
        val tempNorm = tempScoreSmooth(avgTemp, species)
        val tempLevel = when {
            tempNorm >= 0.8 -> FactorLevel.FAVORABLE
            tempNorm >= 0.4 -> FactorLevel.NEUTRAL
            else -> FactorLevel.ADVERSE
        }
        val tempDetail = buildString {
            append(if (tempLevel == FactorLevel.FAVORABLE) "Range termico ideale" else "Range non ottimale")
            if (canopyCover != null && canopyCover >= 0.40) {
                append(String.format(Locale.ITALIAN, " • Chioma boschiva %.0f%% (De Frenne)", canopyCover * 100))
            }
        }
        factors.add(
            Factor(
                id = FactorId.TEMPERATURE,
                label = "Temperatura media",
                formattedValue = String.format(Locale.ITALIAN, "%.1f°C", avgTemp),
                level = tempLevel,
                detail = tempDetail
            )
        )

        // 2. Precipitazioni
        val displayRain = effectiveRainMm ?: totalRain
        val rainNorm = rainScoreSmooth(displayRain, species)
        val rainLevel = when {
            rainNorm >= 0.8 -> FactorLevel.FAVORABLE
            rainNorm >= 0.4 -> FactorLevel.NEUTRAL
            else -> FactorLevel.ADVERSE
        }
        val rainDetail = buildString {
            if (effectiveRainMm != null) {
                append("Convoluzione fenologica f(τ)")
            } else {
                append("Ultime 2 settimane")
            }
            if (canopyCover != null && canopyCover >= 0.40) {
                append(" • Throughfall al suolo")
            }
        }
        factors.add(
            Factor(
                id = FactorId.PRECIPITATION,
                label = if (effectiveRainMm != null) "Precipitazioni efficaci" else "Precipitazioni cumulate",
                formattedValue = String.format(Locale.ITALIAN, "%.0f mm", displayRain),
                level = rainLevel,
                detail = rainDetail
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
                detail = "Modellazione agrometeo (aria 2m & suolo ERA5-Land)"
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
                if (avgSoilMoisture0To7 != null && avgSoilMoisture0To7 > 0.42f) {
                    append(" • Ristagno/asfissia")
                } else if (avgSoilMoisture0To7 != null && avgSoilMoisture0To7 < 0.14f) {
                    append(" • Stress idrico/secco")
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
        val phaseLevel = when {
            cleanPhase.contains("ottimale") || cleanPhase.contains("attiva") -> FactorLevel.FAVORABLE
            cleanPhase.contains("Incubazione") || cleanPhase.contains("Idratazione") -> FactorLevel.NEUTRAL
            else -> FactorLevel.ADVERSE
        }
        factors.add(
            Factor(
                id = FactorId.MYCELIAL_PHASE,
                label = "Stato miceliare",
                formattedValue = phaseName,
                level = phaseLevel,
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
     * @param terrainModifier Modificatore orografico del versante ed esposizione (default 1.0).
     * @param config Configurazione pesi ecologici [EcologicalWeightsConfig].
     * @param canopyCover Frazione di copertura arborea [0.0, 1.0] per il microclima sub-canopy.
     * @return Lista di [DailyOutlook] per ciascun giorno previsionale.
     */
    fun calculateDailyOutlooks(
        processedDays: List<ProcessedDay>,
        startIndex: Int = 14,
        species: MushroomSpecies = SPECIES_CATALOG[0],
        habitatScore: Double = 1.0,
        elevation: Float = 800f,
        month: Int = 9,
        spunHyphalDensity: Float? = null,
        terrainModifier: Double = 1.0,
        config: EcologicalWeightsConfig = EcologicalWeightsConfig.DEFAULT,
        canopyCover: Double? = null
    ): List<DailyOutlook> {
        if (processedDays.isEmpty()) return emptyList()
        val altScore = calculateSpeciesAltitudeScore(elevation, species).score
        val seasonScore = calculateSpeciesSeasonalityScore(month, species).score
        val result = mutableListOf<DailyOutlook>()

        val effectiveDays = if (canopyCover != null && canopyCover > 0.001) applyCanopyBuffering(processedDays, canopyCover) else processedDays

        for (i in startIndex until processedDays.size) {
            val weatherScore = calculateWeatherScore(i, processedDays, spunHyphalDensity, species, config, canopyCover)
            val growthPhaseMultiplier = evaluateGrowthPhase(processedDays, species, i).multiplier
            val prob = dailyGrowthProbability(weatherScore, habitatScore, altScore, seasonScore, terrainModifier, config, species = species, growthPhaseMultiplier = growthPhaseMultiplier)
            result.add(DailyOutlook.fromProcessedDay(effectiveDays[i], prob))
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
