package github.naturewhisp.myco.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for the canonical observation windows used by the weather model.
 *
 * These are deliberately written against the shared [EnvironmentalWindows] API;
 * the Android implementation must not grow a second, subtly different slicing
 * policy.
 */
class EnvironmentalWindowsTest {
    @Test
    fun todayIndexFourteenUsesCanonicalHalfOpenWindows() {
        val windows = EnvironmentalWindows.derive(days(), todayIndex = 14)

        assertEquals((9..13).toList(), windows.temperature.map(::dayIndex))
        assertEquals((4..11).toList(), windows.rain.map(::dayIndex))
        assertEquals((11..14).toList(), windows.humidity.map(::dayIndex))
        assertEquals(windows.humidity.map(::dayIndex), windows.soil.map(::dayIndex))
        assertEquals(windows.humidity.map(::dayIndex), windows.evapotranspiration.map(::dayIndex))
    }

    @Test
    fun exactlyFourteenHistoricalDaysExcludeTodayFromTemperatureButIncludeItInHumidity() {
        val windows = EnvironmentalWindows.derive(days(), todayIndex = 14)

        assertEquals(5, windows.temperature.size)
        assertEquals(8, windows.rain.size)
        assertEquals(4, windows.humidity.size)
        assertFalse(windows.temperature.any { dayIndex(it) == 14 })
        assertTrue(windows.humidity.any { dayIndex(it) == 14 })
    }

    @Test
    fun insufficientHistoryClipsAtZeroWithoutInventingSamples() {
        val windows = EnvironmentalWindows.derive(days(), todayIndex = 3)

        assertEquals((0..2).toList(), windows.temperature.map(::dayIndex))
        assertEquals(listOf(0), windows.rain.map(::dayIndex))
        assertEquals((0..3).toList(), windows.humidity.map(::dayIndex))
    }

    @Test
    fun noSoilKeepsSoilAndEt0MetricsAbsent() {
        val windows = EnvironmentalWindows.derive(days().map { it.copy(soilMoisture0To7 = null, soilMoisture7To28 = null, evapotranspiration = null) }, 14)

        assertNull(windows.soilAverage)
        assertNull(windows.et0Total)
    }

    @Test
    fun partialSoilUsesOnlyAvailableValuesOnHumidityWindow() {
        val windows = EnvironmentalWindows.derive(days(), todayIndex = 14)

        assertEquals(0.30, assertNotNull(windows.soilAverage), absoluteTolerance = 0.0001)
        assertEquals(10.0, assertNotNull(windows.et0Total), absoluteTolerance = 0.0001)
    }

    @Test
    fun rainBoundaryIsInclusiveAtTwelveMillimetres() {
        val windows = EnvironmentalWindows.derive(days(rainOnCanonicalWindow = 1.5), todayIndex = 14)

        assertEquals(12.0, windows.rainTotalMm, absoluteTolerance = 0.0001)
        assertTrue(windows.rainMeetsShockThreshold)
    }

    @Test
    fun shockBoundaryRequiresStrictlyMoreThanConfiguredTemperatureDrop() {
        val atBoundary = EnvironmentalWindows.derive(days(rainOnCanonicalWindow = 1.5, temperatureDrop = 3.0), todayIndex = 14)
        val overBoundary = EnvironmentalWindows.derive(days(rainOnCanonicalWindow = 1.5, temperatureDrop = 3.01), todayIndex = 14)

        assertFalse(atBoundary.thermalShockEligible)
        assertTrue(overBoundary.thermalShockEligible)
    }

    private fun days(
        rainOnCanonicalWindow: Double = 0.0,
        temperatureDrop: Double = 0.0,
    ): List<ProcessedDay> = (0..14).map { index ->
        ProcessedDay(
            dateIso = "2026-09-${(index + 1).toString().padStart(2, '0')}",
            avgTemp = if (index == 10) 20.0 else if (index == 13) 20.0 - temperatureDrop else 20.0,
            totalPrecipMm = if (index in 4..11) rainOnCanonicalWindow else 0.0,
            avgHumidityPercent = 80.0,
            weatherCode = null,
            soilMoisture0To7 = if (index in 11..14) 0.30 else null,
            soilMoisture7To28 = null,
            evapotranspiration = if (index in 11..14) 2.5 else null,
        )
    }

    private fun dayIndex(day: ProcessedDay): Int = day.dateIso.substringAfterLast('-').toInt() - 1
}
