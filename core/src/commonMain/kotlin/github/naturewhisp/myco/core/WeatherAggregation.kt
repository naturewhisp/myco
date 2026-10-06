package github.naturewhisp.myco.core

import kotlin.math.ceil

/** Model/reanalysis/forecast coverage, never an on-device observation. */
data class WeatherCoverage(
    val expectedHours: Int,
    val temperatureHours: Int,
    val humidityHours: Int,
    val precipitationHours: Int,
    val shallowSoilHours: Int,
    val deepSoilHours: Int,
    val et0Hours: Int,
    val conflictingVariables: List<String>,
    val provenance: String,
) {
    val minimumMeanHours: Int
        get() = ceil(expectedHours * ParameterRegistry.WEATHER_MEAN_COVERAGE.value).toInt()
    val weatherUsable: Boolean
        get() = temperatureHours >= minimumMeanHours && humidityHours >= minimumMeanHours &&
            precipitationHours == expectedHours && conflictingVariables.none {
                it in listOf("temperature", "humidity", "precipitation", "timestamp")
            }
    val shallowSoilUsable: Boolean
        get() = shallowSoilHours >= minimumMeanHours && "shallowSoil" !in conflictingVariables
    val deepSoilUsable: Boolean
        get() = deepSoilHours >= minimumMeanHours && "deepSoil" !in conflictingVariables
    val et0Usable: Boolean
        get() = et0Hours == expectedHours && "et0" !in conflictingVariables
}

data class WeatherHour(
    val timestamp: String,
    val temperature: Double?,
    val humidity: Double?,
    val precipitation: Double?,
    val shallowSoil: Double?,
    val deepSoil: Double?,
    val et0: Double?,
)

data class ExpectedDayHours(val dateIso: String, val hours: Int)
data class DailyWeatherCode(val dateIso: String, val code: Int?)

/** Both production adapters call this mapper; no missing value becomes dry weather. */
object WeatherAggregation {
    fun aggregate(
        observations: List<WeatherHour>,
        expectedDays: List<ExpectedDayHours>,
        codes: List<DailyWeatherCode>,
        provenance: String,
    ): List<ProcessedDay> {
        val expected = expectedDays.associate { it.dateIso to it.hours }
        return observations.groupBy { it.timestamp.take(10) }.entries.sortedBy { it.key }.mapNotNull { (date, hours) ->
            if (MycoAlgorithms.isoDateToEpochDay(date) == null) return@mapNotNull null
            val conflicts = mutableListOf<String>()
            val validHours = hours.filter { hour ->
                val time = hour.timestamp
                val valid = time.length >= 16 && time[10] == 'T' && time[13] == ':' &&
                    time.substring(11, 13).toIntOrNull() in 0..23 && time.substring(14, 16) == "00"
                if (!valid) conflicts.add("timestamp")
                valid
            }
            fun values(name: String, selector: (WeatherHour) -> Double?): List<Double> =
                validHours.groupBy { it.timestamp }.values.mapNotNull { duplicate ->
                    val entries = duplicate.map(selector).distinct()
                    if (entries.size > 1 || entries.any { it != null && !it.isFinite() }) {
                        conflicts.add(name)
                        null
                    } else entries.singleOrNull()
                }
            val temperatures = values("temperature") { it.temperature }
            val humidities = values("humidity") { it.humidity }
            val rain = values("precipitation") { it.precipitation }
            val shallow = values("shallowSoil") { it.shallowSoil }
            val deep = values("deepSoil") { it.deepSoil }
            val et0 = values("et0") { it.et0 }
            val coverage = WeatherCoverage(
                expected[date] ?: 24, temperatures.size, humidities.size, rain.size,
                shallow.size, deep.size, et0.size, conflicts.distinct(), provenance,
            )
            val dayCodes = codes.filter { it.dateIso == date }.map { it.code }.distinct()
            ProcessedDay(
                dateIso = date,
                avgTemp = temperatures.takeIf { it.isNotEmpty() }?.average() ?: Double.NaN,
                totalPrecipMm = if (rain.size == coverage.expectedHours && "precipitation" !in conflicts) rain.sum() else Double.NaN,
                avgHumidityPercent = humidities.takeIf { it.isNotEmpty() }?.average() ?: Double.NaN,
                weatherCode = dayCodes.singleOrNull(),
                soilMoisture0To7 = shallow.takeIf { coverage.shallowSoilUsable }?.average(),
                soilMoisture7To28 = deep.takeIf { coverage.deepSoilUsable }?.average(),
                evapotranspiration = et0.takeIf { coverage.et0Usable }?.sum(),
                minTemp = temperatures.minOrNull() ?: Double.NaN,
                maxTemp = temperatures.maxOrNull() ?: Double.NaN,
                coverage = coverage,
            )
        }
    }
}
