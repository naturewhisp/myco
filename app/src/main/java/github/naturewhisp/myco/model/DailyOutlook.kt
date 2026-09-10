package github.naturewhisp.myco.model

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Condizioni meteorologiche sintetizzate per la rappresentazione iconografica.
 */
enum class WeatherCondition {
    CLEAR,
    CLOUDY,
    RAIN,
    STORM,
    UNKNOWN
}

/**
 * Converte un codice meteorologico WMO Open-Meteo nell'enumerazione [WeatherCondition].
 *
 * @param code Codice WMO numerico fornito dal servizio meteorologico.
 * @return [WeatherCondition] corrispondente.
 */
fun weatherCondition(code: Int?): WeatherCondition = when (code) {
    null -> WeatherCondition.UNKNOWN
    0 -> WeatherCondition.CLEAR
    in 1..3 -> WeatherCondition.CLOUDY
    in 51..67, in 80..82 -> WeatherCondition.RAIN
    in 95..99 -> WeatherCondition.STORM
    else -> WeatherCondition.CLOUDY
}

/**
 * Modello unificato per la previsione fenologica giornaliera a 7 giorni.
 *
 * Combina i parametri meteorologici fisici con la probabilità percentuale di crescita fungina
 * e il corrispondente livello ordinale [ProbabilityTier].
 *
 * @property dateIso Data in formato ISO standard ("YYYY-MM-DD").
 * @property dayOfWeek Nome abbreviato del giorno della settimana in italiano (es. "Lun", "Mar").
 * @property dayOfMonth Giorno e mese formattati in italiano (es. "12 Ott").
 * @property weatherCode Codice meteorologico WMO Open-Meteo.
 * @property avgTemp Temperatura media del giorno in gradi Celsius.
 * @property totalPrecipMm Precipitazioni cumulate nelle 24 ore in millimetri.
 * @property avgHumidityPercent Umidità relativa media nelle 24 ore in percentuale.
 * @property probability Probabilità di crescita stimata (0..100%).
 * @property tier Livello ordinale della scala di probabilità (0..4).
 * @property condition Categoria sintetica della condizione atmosferica [WeatherCondition].
 */
data class DailyOutlook(
    val dateIso: String,
    val dayOfWeek: String,
    val dayOfMonth: String,
    val weatherCode: Int?,
    val avgTemp: Float,
    val totalPrecipMm: Float,
    val avgHumidityPercent: Float,
    val probability: Int,
    val tier: Int,
    val condition: WeatherCondition = weatherCondition(weatherCode)
) {
    /**
     * Risolve il livello ordinale [tier] nel corrispondente [ProbabilityTier].
     */
    val probabilityTier: ProbabilityTier
        get() = ProbabilityTier.fromTierIndex(tier)

    /**
     * Etichetta sintetica del livello di probabilità (es. "Inattivo", "Innesco", "Discreto", "Propizio", "Culmine").
     */
    val tierLabel: String
        get() = probabilityTier.shortLabel

    companion object {
        /**
         * Crea un'istanza di [DailyOutlook] a partire da un [ProcessedDay] meteorologico e la probabilità calcolata.
         *
         * @param day Dati meteorologici giornalieri aggregati [ProcessedDay].
         * @param probability Probabilità di crescita percentuale stimata per il giorno.
         * @return [DailyOutlook] formattato con classificazione [ProbabilityTier].
         */
        fun fromProcessedDay(
            day: ProcessedDay,
            probability: Int
        ): DailyOutlook {
            val (dayOfWeek, dayOfMonth) = try {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = parser.parse(day.date)
                if (date != null) {
                    val dow = SimpleDateFormat("EEE", Locale.ITALIAN).format(date)
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString() }
                    val dom = SimpleDateFormat("d MMM", Locale.ITALIAN).format(date)
                    dow to dom
                } else {
                    day.date to ""
                }
            } catch (_: Exception) {
                day.date to ""
            }

            val pTier = ProbabilityTier.fromProbability(probability)

            return DailyOutlook(
                dateIso = day.date,
                dayOfWeek = dayOfWeek,
                dayOfMonth = dayOfMonth,
                weatherCode = day.weatherCode,
                avgTemp = day.avgTemp,
                totalPrecipMm = day.totalPrecip,
                avgHumidityPercent = day.avgHumidity,
                probability = probability,
                tier = pTier.tierIndex
            )
        }
    }
}
