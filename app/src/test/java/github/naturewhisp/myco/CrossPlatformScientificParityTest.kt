package github.naturewhisp.myco

import github.naturewhisp.myco.core.HeatmapEngine
import github.naturewhisp.myco.core.MycoAlgorithms
import github.naturewhisp.myco.core.SpeciesCatalog
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.utils.HeatmapGenerator
import github.naturewhisp.myco.utils.MushroomAlgorithms
import org.junit.Assert.assertEquals
import org.junit.Test

class CrossPlatformScientificParityTest {
    @Test
    fun biologicalResponseCurvesMatchLegacyAndroid() {
        val legacy = SPECIES_CATALOG.first { it.id == "boletus_edulis" }
        val shared = SpeciesCatalog.byId("boletus_edulis")
        for (temperature in listOf(8.0, 9.0, 12.0, 13.0, 18.0, 20.0, 22.0, 24.0, 25.0)) {
            assertEquals(MushroomAlgorithms.tempScoreSmooth(temperature, legacy), MycoAlgorithms.temperatureResponse(temperature, shared), 0.000_001)
        }
        for (rain in listOf(0.0, 5.0, 15.0, 25.0, 35.0, 50.0)) {
            assertEquals(MushroomAlgorithms.rainScoreSmooth(rain, legacy), MycoAlgorithms.rainResponse(rain, shared), 0.000_001)
        }
        for (humidity in listOf(40.0, 50.0, 65.0, 75.0, 85.0, 95.0)) {
            assertEquals(MushroomAlgorithms.humidityScoreSmooth(humidity), MycoAlgorithms.humidityResponse(humidity), 0.000_001)
        }
    }

    @Test
    fun canonicalProbabilityMatchesLegacyAndroid() {
        for (weather in listOf(0, 20, 40, 60, 75, 100)) {
            val legacy = MushroomAlgorithms.dailyGrowthProbability(weather, 0.95, 0.9, 1.0, 1.05)
            val shared = MycoAlgorithms.growthProbability(weather, 0.95, 0.9, 1.0, 1.05)
            assertEquals(legacy, shared)
        }
    }

    @Test
    fun fullHeatmapPaletteMatchesLegacyAndroid() {
        val shared = HeatmapEngine()
        for (dark in listOf(false, true)) {
            for (probability in 0..100) {
                assertEquals(
                    "probability=$probability dark=$dark",
                    HeatmapGenerator.getHeatmapColor(probability, dark),
                    shared.color(probability, dark),
                )
            }
        }
    }
}
