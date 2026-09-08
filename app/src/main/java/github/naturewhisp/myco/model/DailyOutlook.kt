package github.naturewhisp.myco.model

import java.text.SimpleDateFormat
import java.util.Locale

enum class WeatherCondition { CLEAR, CLOUDY, RAIN, STORM, UNKNOWN }

fun weatherCondition(code: Int?): WeatherCondition = when (code) {
    null -> WeatherCondition.UNKNOWN
    0 -> WeatherCondition.CLEAR
    in 1..3 -> WeatherCondition.CLOUDY
    in 51..67, in 80..82 -> WeatherCondition.RAIN
    in 95..99 -> WeatherCondition.STORM
    else -> WeatherCondition.CLOUDY
}

// Previsione giornaliera unificata con probabilità e classificazione tassonomica
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
    val tierLabel: String
        get() = when (tier) {
            0 -> "Inattivo"
            1 -> "Innesco"
            2 -> "Discreto"
            3 -> "Propizio"
            else -> "Culmine"
        }

    companion object {
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

            val tier = when {
                probability < 20 -> 0
                probability < 40 -> 1
                probability < 60 -> 2
                probability < 75 -> 3
                else -> 4
            }

            return DailyOutlook(
                dateIso = day.date,
                dayOfWeek = dayOfWeek,
                dayOfMonth = dayOfMonth,
                weatherCode = day.weatherCode,
                avgTemp = day.avgTemp,
                totalPrecipMm = day.totalPrecip,
                avgHumidityPercent = day.avgHumidity,
                probability = probability,
                tier = tier
            )
        }
    }
}
