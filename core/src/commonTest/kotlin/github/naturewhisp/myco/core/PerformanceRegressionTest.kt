package github.naturewhisp.myco.core

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

class PerformanceRegressionTest {
    @Test
    fun analysisAndHeatmapStayWithinInteractiveBudget() {
        val days = (0 until 21).map { index ->
            ProcessedDay(
                dateIso = "2026-09-${(index + 1).toString().padStart(2, '0')}",
                avgTemp = 16.0,
                totalPrecipMm = if (index % 3 == 0) 4.0 else 0.5,
                avgHumidityPercent = 78.0,
                weatherCode = 3,
                soilMoisture0To7 = 0.35,
                soilMoisture7To28 = 0.42,
                evapotranspiration = 0.2,
            )
        }
        val input = AnalysisInputs(
            days = days,
            todayIndex = 14,
            speciesId = "boletus_edulis",
            habitatScore = 0.95,
            habitatDescription = "Bosco",
            canopyTypes = listOf("fagus"),
            elevationSamples = listOf(800.0, 805.0, 795.0, 810.0, 790.0),
            monthIndex = 8,
            spunEcmRichness = 42.0,
            spunHyphalDensity = 4.2,
            missingSources = emptyList(),
        )
        val grid = SpunGrid(
            header = SpunRegionHeader(1, "IT", 35.0, 48.0, 6.0, 19.0, 8, 8, 30),
            ecmData = ByteArray(64) { 45 },
            hyphalData = ByteArray(64) { 84 },
        )

        val analysisDuration = measureTime {
            repeat(1_000) { MycoAnalysisEngine().analyze(input) }
        }
        val heatmapDuration = measureTime {
            repeat(20) {
                assertNotNull(HeatmapEngine().generate(42.0, 12.0, grid, 70.0, 1.0, 1.0, "boletus_edulis", false))
            }
        }

        assertTrue(analysisDuration.inWholeSeconds < 5, "Analysis regression: $analysisDuration")
        assertTrue(heatmapDuration.inWholeSeconds < 5, "Heatmap regression: $heatmapDuration")
    }
}
