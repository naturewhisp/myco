package github.naturewhisp.myco

import github.naturewhisp.myco.utils.MushroomAlgorithms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MushroomAlgorithmsTest {

    @Test
    fun testRainStatusThresholds() {
        assertEquals("Ottimale", MushroomAlgorithms.getRainStatus(41.0).label)
        assertEquals(40, MushroomAlgorithms.getRainStatus(40.0).score)
        assertEquals("Molto buona", MushroomAlgorithms.getRainStatus(26.0).label)
        assertEquals(35, MushroomAlgorithms.getRainStatus(25.0).score)
        assertEquals("Buona", MushroomAlgorithms.getRainStatus(16.0).label)
        assertEquals(25, MushroomAlgorithms.getRainStatus(15.0).score)
        assertEquals("Sufficiente", MushroomAlgorithms.getRainStatus(6.0).label)
        assertEquals(10, MushroomAlgorithms.getRainStatus(5.0).score)
        assertEquals("Scarsa", MushroomAlgorithms.getRainStatus(0.0).label)
        assertEquals(0, MushroomAlgorithms.getRainStatus(0.0).score)
    }

    @Test
    fun testTempStatusThresholds() {
        assertEquals("Ideale", MushroomAlgorithms.getTempStatus(18.0).label)
        assertEquals(30, MushroomAlgorithms.getTempStatus(14.0).score)
        assertEquals(30, MushroomAlgorithms.getTempStatus(22.0).score)

        assertEquals("Favorevole", MushroomAlgorithms.getTempStatus(13.0).label)
        assertEquals(15, MushroomAlgorithms.getTempStatus(10.0).score)
        assertEquals(15, MushroomAlgorithms.getTempStatus(23.0).score)
        assertEquals(15, MushroomAlgorithms.getTempStatus(24.9).score)

        assertEquals("Troppo freddo", MushroomAlgorithms.getTempStatus(9.9).label)
        assertEquals("Troppo caldo", MushroomAlgorithms.getTempStatus(25.1).label)
    }

    @Test
    fun testHumidityScoreThresholds() {
        assertEquals(15, MushroomAlgorithms.getHumidityScore(86.0))
        assertEquals(15, MushroomAlgorithms.getHumidityScore(85.0))
        assertEquals(10, MushroomAlgorithms.getHumidityScore(80.0))
        assertEquals(10, MushroomAlgorithms.getHumidityScore(75.0))
        assertEquals(0, MushroomAlgorithms.getHumidityScore(70.0))
    }

    @Test
    fun testAltitudeScore() {
        val low = MushroomAlgorithms.calculateAltitudeScore(150f)
        assertEquals(0.7, low.score, 0.001)

        val hill = MushroomAlgorithms.calculateAltitudeScore(350f)
        assertEquals(0.9, hill.score, 0.001)

        val ideal = MushroomAlgorithms.calculateAltitudeScore(1000f)
        assertEquals(1.0, ideal.score, 0.001)

        val mountain = MushroomAlgorithms.calculateAltitudeScore(1600f)
        assertEquals(0.9, mountain.score, 0.001)

        val high = MushroomAlgorithms.calculateAltitudeScore(2100f)
        assertEquals(0.6, high.score, 0.001)
    }

    @Test
    fun testSeasonalityScore() {
        // Month 8 = Settembre, 9 = Ottobre (Peak)
        val sept = MushroomAlgorithms.calculateSeasonalityScore(8)
        assertEquals(1.0, sept.score, 0.001)
        assertTrue(sept.text.contains("Settembre"))

        val oct = MushroomAlgorithms.calculateSeasonalityScore(9)
        assertEquals(1.0, oct.score, 0.001)

        // May = 4, June = 5 (Spring)
        val may = MushroomAlgorithms.calculateSeasonalityScore(4)
        assertEquals(0.9, may.score, 0.001)

        // July = 6, Aug = 7 (Summer)
        val july = MushroomAlgorithms.calculateSeasonalityScore(6)
        assertEquals(0.5, july.score, 0.001)

        // Off season: Jan = 0
        val jan = MushroomAlgorithms.calculateSeasonalityScore(0)
        assertEquals(0.1, jan.score, 0.001)
    }

    @Test
    fun testMoonPhaseCalculation() {
        val cal = Calendar.getInstance()
        cal.set(2024, Calendar.SEPTEMBER, 3, 12, 0, 0)
        val result = MushroomAlgorithms.getMoonPhase(cal.time)
        assertTrue(result.text.isNotEmpty())
        assertTrue(result.emoji.isNotEmpty())
    }

    @Test
    fun testSmoothScoringFunctions() {
        // Temp smooth: baseline 14-22 ideal, 10-25 tolerated
        assertEquals(1.0, MushroomAlgorithms.tempScoreSmooth(18.0), 0.001)
        assertEquals(0.0, MushroomAlgorithms.tempScoreSmooth(8.0), 0.001)
        assertEquals(0.0, MushroomAlgorithms.tempScoreSmooth(27.0), 0.001)
        assertTrue(MushroomAlgorithms.tempScoreSmooth(12.0) in 0.1..0.9)

        // Rain smooth
        assertEquals(1.0, MushroomAlgorithms.rainScoreSmooth(40.0), 0.001)
        assertEquals(0.0, MushroomAlgorithms.rainScoreSmooth(0.0), 0.001)
        assertTrue(MushroomAlgorithms.rainScoreSmooth(15.0) in 0.4..0.8)

        // Humidity smooth
        assertEquals(1.0, MushroomAlgorithms.humidityScoreSmooth(90.0), 0.001)
        assertEquals(0.0, MushroomAlgorithms.humidityScoreSmooth(40.0), 0.001)
        assertTrue(MushroomAlgorithms.humidityScoreSmooth(75.0) in 0.6..0.9)
    }

    @Test
    fun testSpeciesSpecificProfiles() {
        val pinophilus = github.naturewhisp.myco.model.SPECIES_CATALOG.first { it.id == "boletus_pinophilus" }
        // Pinophilus loves higher elevation (700-1700 ideal)
        val highAltScore = MushroomAlgorithms.calculateSpeciesAltitudeScore(1200f, pinophilus)
        assertEquals(1.0, highAltScore.score, 0.001)

        val lowAltScore = MushroomAlgorithms.calculateSpeciesAltitudeScore(100f, pinophilus)
        assertEquals(0.4, lowAltScore.score, 0.001)
    }

    @Test
    fun testDailyGrowthProbability() {
        val prob = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = 80,
            habitatScore = 1.0,
            altitudeScore = 1.0,
            seasonalityScore = 1.0
        )
        // 100 * (0.80)^1.2 ≈ 76.5 -> 76
        assertEquals(76, prob)

        // 0 weather score produces 0
        assertEquals(0, MushroomAlgorithms.dailyGrowthProbability(0, 1.0, 1.0, 1.0))
        // 100 weather score produces 100
        assertEquals(100, MushroomAlgorithms.dailyGrowthProbability(100, 1.0, 1.0, 1.0))
    }

    @Test
    fun testFactorsFormattedValuesAreCompact() {
        val moon = MushroomAlgorithms.getMoonPhase()
        val factors = MushroomAlgorithms.calculateFactors(
            avgTemp = 18.0,
            totalRain = 35.0,
            avgHumidity = 80.0,
            habitatScore = 0.95,
            habitatText = "Ideale (area boschiva)",
            elevation = 900f,
            month = 8,
            growthPhaseText = "Idratazione miceliare (Primordi in formazione)",
            moon = moon,
            slopeText = "Est (Versante riparato dai venti secchi)",
            spunEcmText = "63 specie (Ideale)",
            spunHyphalText = "5.9 m/cm³ (Attiva)"
        )

        // Assert all formattedValues are compact (< 25 chars) to prevent layout squashing
        for (f in factors) {
            assertTrue("Factor ${f.id} formattedValue '${f.formattedValue}' is too long", f.formattedValue.length <= 25)
        }
    }
}
