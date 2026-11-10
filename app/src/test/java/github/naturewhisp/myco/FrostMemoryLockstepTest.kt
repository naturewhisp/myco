package github.naturewhisp.myco

import github.naturewhisp.myco.core.MycoAlgorithms
import github.naturewhisp.myco.model.EcologicalWeightsConfig
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.model.toCore
import github.naturewhisp.myco.utils.MushroomAlgorithms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Lockstep Android/:core del danno da gelo sulla memoria pluviometrica (revisione 2026-11-09, rilievo A4).
 * Scenario SINTETICO: 40 mm a 11 giorni dal target, una notte fredda a 7 giorni.
 */
class FrostMemoryLockstepTest {
    private val edulis = SPECIES_CATALOG.first { it.id == "boletus_edulis" }

    /** La notte fredda cade a 7 giorni: fuori dalla finestra di 5 giorni che allunga la latenza. */
    private fun series(nightMin: Float): List<ProcessedDay> {
        val start = LocalDate.of(2026, 9, 1)
        return (0 until 45).map { index ->
            val lag = 44 - index
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

    private fun legacy(nightMin: Float) = MushroomAlgorithms.calculateEffectiveRainfall(44, series(nightMin), edulis)

    private fun core(nightMin: Float): Double {
        val days = series(nightMin).map { d ->
            github.naturewhisp.myco.core.ProcessedDay(
                dateIso = d.date, avgTemp = d.avgTemp.toDouble(), totalPrecipMm = d.totalPrecip.toDouble(),
                avgHumidityPercent = d.avgHumidity.toDouble(), weatherCode = d.weatherCode,
                minTemp = d.minTemp.toDouble(), maxTemp = d.maxTemp.toDouble(),
            )
        }
        return MycoAlgorithms.calculateEffectiveRainfall(44, days, edulis.toCore())
    }

    @Test
    fun legacyInertiaBranchGivesNoShockBonusForAColdSnapBelowTheToleratedMean() {
        // Una configurazione diversa da PHENOLOGICAL percorre il ramo Android non delegato al core.
        val customConfig = EcologicalWeightsConfig.PHENOLOGICAL.copy(probabilityKneeThreshold = 70.5)
        fun weather(coldSnap: Boolean): Int {
            val start = LocalDate.of(2026, 9, 1)
            val days = (0 until 45).map { index ->
                val lag = 44 - index
                val cold = coldSnap && lag in 7..9
                ProcessedDay(
                    date = start.plusDays(index.toLong()).toString(),
                    avgTemp = if (cold) 6.0f else 12.0f,
                    minTemp = if (cold) 3.0f else 8.0f,
                    maxTemp = if (cold) 14.0f else 17.0f,
                    totalPrecip = if (lag == 11) 40.0f else 0.0f,
                    avgHumidity = 88.0f,
                    weatherCode = if (lag == 11) 61 else 1,
                )
            }
            return MushroomAlgorithms.calculateWeatherScore(44, days, species = edulis, config = customConfig)
        }
        assertTrue("Ondata fredda ${weather(true)} > regime mite ${weather(false)}", weather(true) <= weather(false))
    }

    @Test
    fun legacyRainMemoryIsErasedByFrostExactlyLikeTheCore() {
        val coldNoFrost = legacy(3.0f)
        assertTrue("Senza gelo la memoria deve restare consistente (attuale: $coldNoFrost)", coldNoFrost > 25.0)
        assertEquals(0.0, legacy(-2.0f), 1e-9)
        assertEquals(coldNoFrost * 0.5, legacy(-0.5f), 1e-4)
        for (nightMin in listOf(3.0f, 0.0f, -0.5f, -2.0f)) {
            assertEquals("Parità a minima $nightMin °C", core(nightMin), legacy(nightMin), 1e-4)
        }
    }
}
