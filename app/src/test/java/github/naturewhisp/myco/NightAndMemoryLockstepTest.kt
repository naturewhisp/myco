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
 * Lockstep Android/:core delle soglie notturne (A2) e dell'estinzione della memoria pluviometrica (B1),
 * revisione 2026-11-09. Scenari SINTETICI.
 */
class NightAndMemoryLockstepTest {
    private val edulis = SPECIES_CATALOG.first { it.id == "boletus_edulis" }

    private fun series(rainLag: Int, nightMinAtLag3: Float = 16.0f): List<ProcessedDay> {
        val start = LocalDate.of(2026, 9, 1)
        return (0 until SERIES_LENGTH).map { index ->
            val lag = SERIES_LENGTH - 1 - index
            ProcessedDay(
                date = start.plusDays(index.toLong()).toString(),
                avgTemp = 18.0f,
                minTemp = if (lag == 3) nightMinAtLag3 else 16.0f,
                maxTemp = 21.0f,
                totalPrecip = if (lag == rainLag) 40.0f else 0.0f,
                avgHumidity = 88.0f,
                weatherCode = if (lag == rainLag) 61 else 1,
            )
        }
    }

    private fun legacy(rainLag: Int, nightMinAtLag3: Float = 16.0f, species: github.naturewhisp.myco.model.MushroomSpecies = edulis) =
        MushroomAlgorithms.calculateEffectiveRainfall(SERIES_LENGTH - 1, series(rainLag, nightMinAtLag3), species)

    private fun core(rainLag: Int, species: github.naturewhisp.myco.model.MushroomSpecies): Double {
        val days = series(rainLag).map { d ->
            github.naturewhisp.myco.core.ProcessedDay(
                dateIso = d.date, avgTemp = d.avgTemp.toDouble(), totalPrecipMm = d.totalPrecip.toDouble(),
                avgHumidityPercent = d.avgHumidity.toDouble(), weatherCode = d.weatherCode,
                minTemp = d.minTemp.toDouble(), maxTemp = d.maxTemp.toDouble(),
            )
        }
        return MycoAlgorithms.calculateEffectiveRainfall(SERIES_LENGTH - 1, days, species.toCore())
    }

    @Test
    fun legacyNocturnalFactorUsesNightThresholdsLikeTheCore() {
        // B. edulis: media ideale 13 °C, tollerata 9 °C -> soglie notturne 7 °C e 3 °C.
        assertEquals(1.0, MushroomAlgorithms.nocturnalChillingInhibition(7.0f, edulis), 1e-9)
        assertEquals(0.3, MushroomAlgorithms.nocturnalChillingInhibition(3.0f, edulis), 1e-9)
        assertEquals(0.65, MushroomAlgorithms.nocturnalChillingInhibition(5.0f, edulis), 1e-9)
        for (species in SPECIES_CATALOG) {
            for (t in listOf(-3.0f, 2.0f, 5.5f, 9.0f, 14.0f)) {
                assertEquals(
                    "${species.id} a $t °C",
                    MycoAlgorithms.nocturnalChillingInhibition(t.toDouble(), species.toCore()),
                    MushroomAlgorithms.nocturnalChillingInhibition(t, species),
                    1e-9,
                )
            }
        }
    }

    @Test
    fun legacyLatencyIsNotStretchedByAMildNight() {
        // Una notte a 6 °C (sopra la soglia notturna tollerata di 3 °C) non allunga la latenza.
        assertEquals(legacy(11, 16.0f), legacy(11, 6.0f), 1e-9)
        // Controllo: a 0 °C la latenza si allunga ancora.
        assertTrue(kotlin.math.abs(legacy(11, 16.0f) - legacy(11, 0.0f)) > 0.5)
    }

    @Test
    fun legacyRainMemoryFadesOutLikeTheCore() {
        for (species in SPECIES_CATALOG) {
            // Memoria della specie (minimo 26 gg) presa dal :core: stessa estinzione C1 nei due motori.
            val memory = MycoAlgorithms.rainMemoryDays(species.toCore())
            assertTrue("${species.id}: ${legacy(memory, species = species)} mm a $memory gg", legacy(memory, species = species) <= 0.05 * 40.0)
            assertEquals("${species.id} a ${memory + 1} gg", 0.0, legacy(memory + 1, species = species), 1e-12)
            for (lag in listOf(11, 20, 22, 24, 26, 27, 30, 33, 36, 39, 42, 43, memory - 6, memory, memory + 1)) {
                assertEquals("${species.id} a $lag gg", core(lag, species), legacy(lag, species = species), 1e-3)
            }
        }
    }

    @Test
    fun legacyKeepsRainBeyondTwentySixDaysForLongLatencySpecies() {
        // Cantharellus (picco 14 gg, memoria 42 gg): a 30 gg il kernel pesa ancora, a 22 gg resta intatto.
        val cantharellus = SPECIES_CATALOG.first { it.id == "cantharellus_cibarius" }
        assertTrue(legacy(30, species = cantharellus) > 1.0)
        val pure22 = 40.0 * MycoAlgorithms.phenologyKernel(22.0, cantharellus.phenologyLatencyPeakDays, cantharellus.phenologyShapeAlpha)
        assertEquals(pure22, legacy(22, species = cantharellus), 1e-3)
    }

    private companion object {
        /** 60 giorni di calendario reale (1 settembre – 30 ottobre 2026): copre la memoria massima del catalogo (42 gg). */
        const val SERIES_LENGTH = 60
    }
}
