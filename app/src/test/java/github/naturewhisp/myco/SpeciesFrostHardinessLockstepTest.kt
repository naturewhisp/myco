package github.naturewhisp.myco

import github.naturewhisp.myco.core.MycoAlgorithms
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.model.toCore
import github.naturewhisp.myco.utils.MushroomAlgorithms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Lockstep Android/:core della resistenza al gelo per specie (revisione 2026-11-10, §11).
 * Scenario SINTETICO: 62 giorni reali, 40 mm a 11 giorni dal target, una notte fredda a 7 giorni
 * (fuori dalla finestra di 5 giorni che allunga la latenza).
 */
class SpeciesFrostHardinessLockstepTest {
    private val hydnum = SPECIES_CATALOG.first { it.id == "hydnum_repandum" }
    private val length = 62

    private fun series(nightMin: Float): List<ProcessedDay> {
        val start = LocalDate.of(2026, 8, 15)
        return (0 until length).map { index ->
            val lag = length - 1 - index
            ProcessedDay(
                date = start.plusDays(index.toLong()).toString(),
                avgTemp = if (lag == 7) 6.0f else 12.0f,
                minTemp = if (lag == 7) nightMin else 10.0f,
                maxTemp = if (lag == 7) 14.0f else 17.0f,
                totalPrecip = if (lag == 11) 40.0f else 0.0f,
                avgHumidity = 88.0f,
                weatherCode = if (lag == 11) 61 else 1,
            )
        }
    }

    private fun legacy(nightMin: Float) = MushroomAlgorithms.calculateEffectiveRainfall(length - 1, series(nightMin), hydnum)

    private fun core(nightMin: Float): Double {
        val days = series(nightMin).map { d ->
            github.naturewhisp.myco.core.ProcessedDay(
                dateIso = d.date, avgTemp = d.avgTemp.toDouble(), totalPrecipMm = d.totalPrecip.toDouble(),
                avgHumidityPercent = d.avgHumidity.toDouble(), weatherCode = d.weatherCode,
                minTemp = d.minTemp.toDouble(), maxTemp = d.maxTemp.toDouble(),
            )
        }
        return MycoAlgorithms.calculateEffectiveRainfall(length - 1, days, hydnum.toCore())
    }

    @Test
    fun legacyRainMemoryOfAHardySpeciesMatchesTheCoreUnderFrost() {
        val control = legacy(3.0f)
        assertTrue("Memoria di controllo H. repandum: $control", control > 25.0)
        // Soglie H. repandum -1 / -4 °C: a -2 °C sopravvive 20/27 della memoria, a -4 °C nulla.
        assertEquals(control * 20.0 / 27.0, legacy(-2.0f), 1e-4)
        assertEquals(0.0, legacy(-4.0f), 1e-9)
        for (nightMin in listOf(3.0f, 0.0f, -1.0f, -2.0f, -2.5f, -3.0f, -4.0f)) {
            assertEquals("Parità a minima $nightMin °C", core(nightMin), legacy(nightMin), 1e-4)
        }
    }
}
