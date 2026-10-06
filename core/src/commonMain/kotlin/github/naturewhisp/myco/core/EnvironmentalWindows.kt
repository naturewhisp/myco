package github.naturewhisp.myco.core


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
    val temperatureAvailableDays: Int = temperature.size,
    val rainAvailableDays: Int = rain.size,
    val humidityAvailableDays: Int = humidity.size,
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

            val targetDay = days[todayIndex]
            val targetEpoch = MycoAlgorithms.isoDateToEpochDay(targetDay.dateIso)

            if (targetEpoch == null) return empty()
            val dayByEpoch = days.mapNotNull { d -> MycoAlgorithms.isoDateToEpochDay(d.dateIso)?.let { it to d } }.toMap()
            val temperature = (5 downTo 1).mapNotNull { dayByEpoch[targetEpoch - it] }
            val rain = (10 downTo 3).mapNotNull { dayByEpoch[targetEpoch - it] }
            val humidity = (3 downTo 0).mapNotNull { dayByEpoch[targetEpoch - it] }
            val soil = (2 downTo 0).mapNotNull { dayByEpoch[targetEpoch - it] }
            val shallow = soil.mapNotNull { it.soilMoisture0To7 }
            val deep = soil.mapNotNull { it.soilMoisture7To28 }
            val et0 = humidity.mapNotNull { it.evapotranspiration }
            val dayMinus4 = dayByEpoch[targetEpoch - 4]
            val dayMinus1 = dayByEpoch[targetEpoch - 1]
            val drop = if (dayMinus4 != null && dayMinus1 != null) dayMinus4.avgTemp - dayMinus1.avgTemp else null

            return EnvironmentalWindows(
                temperature = temperature,
                rain = rain,
                humidity = humidity,
                soil = soil,
                evapotranspiration = humidity,
                rainWindowTotalMm = rain.sumOf { it.liquidPrecipMm },
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

private inline fun List<ProcessedDay>.averageOfOrZero(selector: (ProcessedDay) -> Double): Double =
    if (isEmpty()) 0.0 else sumOf(selector) / size

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
