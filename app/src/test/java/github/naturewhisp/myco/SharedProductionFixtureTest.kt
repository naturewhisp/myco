package github.naturewhisp.myco

import com.google.gson.Gson
import github.naturewhisp.myco.model.OverpassResponse
import github.naturewhisp.myco.model.WeatherResponse
import github.naturewhisp.myco.repository.CacheManager
import github.naturewhisp.myco.repository.MushroomRepository
import github.naturewhisp.myco.repository.SpunDataManager
import github.naturewhisp.myco.utils.MushroomAlgorithms
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SharedProductionFixtureTest {
    private fun fixture(path: String) = File(requireNotNull(System.getProperty("myco.fixture.root")), path).readText()

    @Test
    fun productionMapperUsesLocal23And25HourDays() {
        for ((date, count) in listOf("2026-03-29" to 23, "2026-10-25" to 25)) {
            val start = java.time.LocalDate.parse(date).atStartOfDay(java.time.ZoneId.of("Europe/Rome"))
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmXXX", java.util.Locale.US)
            val times = List(count) { formatter.format(start.plusHours(it.toLong())) }
            val source = WeatherResponse(500f, "Europe/Rome", github.naturewhisp.myco.model.HourlyData(times, List(count) { 18f }, List(count) { 80f }, List(count) { 1f }), github.naturewhisp.myco.model.DailyData(listOf(date), listOf(1)))
            val day = MushroomAlgorithms.processWeatherData(source).single()
            assertEquals(count, day.coverage!!.expectedHours)
            assertTrue(day.coverage.weatherUsable)
            assertEquals(count.toFloat(), day.totalPrecip, 1e-5f)
        }
    }

    @Test
    fun commonHourlyFixtureUsesCanonicalJsonNamesAndRetainsTruncatedDay() {
        val response = Gson().fromJson(fixture("weather/coverage.json"), WeatherResponse::class.java)
        val days = MushroomAlgorithms.processWeatherData(response)
        assertEquals(2, days.size)
        assertEquals(21.5f, days.first().avgTemp, 1e-5f)
        assertEquals(24f, days.first().totalPrecip, 1e-5f)
        assertEquals(2.4f, days.first().totalEvapotranspiration!!, 1e-5f)
        assertEquals(61, days.first().weatherCode)
        assertTrue(days.first().coverage!!.weatherUsable)
        assertFalse(days.last().coverage!!.weatherUsable)
        assertTrue(days.last().totalPrecip.isNaN())
        assertEquals(null, days.last().avgSoilMoisture0To7cm)
    }

    @Test
    fun commonGeometryFixtureHandlesRelationHolesDuplicatesAndMissingCoordinates() {
        val repository = MushroomRepository(mockk<CacheManager>(), mockk<SpunDataManager>(), mockk(), mockk(), emptyList())
        val response = Gson().fromJson(fixture("habitat/surfaces.json"), OverpassResponse::class.java)
        val evidence = repository.extractHabitatEvidence(response, 44.2149, 7.9755, 1500)
        assertTrue(evidence.forestCoverFraction in 0.96..0.99)
        assertTrue(evidence.distanceToNearestForestMeters in 155.0..165.0)
        assertTrue(evidence.forestProximityIndex in 0.9..1.0)
        assertEquals(setOf("fagus"), evidence.confirmedHostGenera)
        assertFalse(evidence.geometryComplete)
    }
}

