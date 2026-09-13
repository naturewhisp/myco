package github.naturewhisp.myco.core

import kotlin.math.max
import kotlin.math.min

/**
 * Canonical observation windows shared by scoring, factor display and parity fixtures.
 *
 * Temperature intentionally excludes the target day, rain excludes the two-day
 * biological lag, while humidity and soil observations include the target day.
 */
data class EnvironmentalWindows(
    val temperature: List<ProcessedDay>,
    val rain: List<ProcessedDay>,
    val humidity: List<ProcessedDay>,
    val soil: List<ProcessedDay>,
    val evapotranspiration: List<ProcessedDay>,
    val rainWindowTotalMm: Double,
    val averageTempWindowC: Double,
    val averageHumidityWindowPercent: Double,
    val averageSoil0To7: Double?,
    val averageSoil7To28: Double?,
    val averageEt0: Double?,
    val temperatureDropC: Double?,
) {
    val rainTotalMm: Double get() = rainWindowTotalMm

    val soilAverage: Double?
        get() = listOfNotNull(averageSoil0To7, averageSoil7To28).takeIf { it.isNotEmpty() }?.average()

    val et0Total: Double?
        get() = evapotranspiration.mapNotNull { it.evapotranspiration }.takeIf { it.isNotEmpty() }?.sum()

    val rainMeetsShockThreshold: Boolean get() = rainWindowTotalMm >= MIN_RAIN_FOR_SHOCK_MM

    val thermalShockEligible: Boolean
        get() = rainMeetsShockThreshold && temperatureDropC?.let { it > STANDARD_THERMAL_DROP_MIN_C } == true

    companion object {
        const val MIN_RAIN_FOR_SHOCK_MM = 12.0
        const val STANDARD_THERMAL_DROP_MIN_C = 3.0
        const val SPUN_ASSISTED_THERMAL_DROP_MIN_C = 2.0

        fun derive(days: List<ProcessedDay>, todayIndex: Int): EnvironmentalWindows {
            if (todayIndex !in days.indices) return empty()

            val temperature = days.slice(max(0, todayIndex - 5), todayIndex)
            val rain = days.slice(max(0, todayIndex - 10), max(0, todayIndex - 2))
            val humidity = days.slice(max(0, todayIndex - 3), min(days.size, todayIndex + 1))
            val shallow = humidity.mapNotNull { it.soilMoisture0To7 }
            val deep = humidity.mapNotNull { it.soilMoisture7To28 }
            val et0 = humidity.mapNotNull { it.evapotranspiration }
            val drop = if (todayIndex > 4) {
                days[todayIndex - 4].avgTemp - days[todayIndex - 1].avgTemp
            } else {
                null
            }

            return EnvironmentalWindows(
                temperature = temperature,
                rain = rain,
                humidity = humidity,
                soil = humidity,
                evapotranspiration = humidity,
                rainWindowTotalMm = rain.sumOf { it.totalPrecipMm },
                averageTempWindowC = temperature.averageOfOrZero { it.avgTemp },
                averageHumidityWindowPercent = humidity.averageOfOrZero { it.avgHumidityPercent },
                averageSoil0To7 = shallow.averageOrNull(),
                averageSoil7To28 = deep.averageOrNull(),
                averageEt0 = et0.averageOrNull(),
                temperatureDropC = drop,
            )
        }

        private fun empty() = EnvironmentalWindows(
            temperature = emptyList(),
            rain = emptyList(),
            humidity = emptyList(),
            soil = emptyList(),
            evapotranspiration = emptyList(),
            rainWindowTotalMm = 0.0,
            averageTempWindowC = 0.0,
            averageHumidityWindowPercent = 0.0,
            averageSoil0To7 = null,
            averageSoil7To28 = null,
            averageEt0 = null,
            temperatureDropC = null,
        )
    }
}

private fun List<ProcessedDay>.slice(start: Int, endExclusive: Int): List<ProcessedDay> =
    if (start < endExclusive) subList(start, endExclusive) else emptyList()

private inline fun List<ProcessedDay>.averageOfOrZero(selector: (ProcessedDay) -> Double): Double =
    if (isEmpty()) 0.0 else sumOf(selector) / size

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
