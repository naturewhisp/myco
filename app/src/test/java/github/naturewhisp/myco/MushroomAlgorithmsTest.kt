package github.naturewhisp.myco

import github.naturewhisp.myco.model.FactorId
import github.naturewhisp.myco.model.FactorLevel
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.utils.MushroomAlgorithms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun testCalculateTerrainAspect_flat() {
        // [Center, North, South, East, West] all equal
        val elevations = listOf(500f, 500f, 500f, 500f, 500f)
        val result = MushroomAlgorithms.calculateTerrainAspect(elevations)
        assertTrue(result.isFlat)
        assertEquals("Pianeggiante", result.cardinalDirection)
        assertEquals("Pian", result.cardinalAbbreviation)
        assertEquals(0f, result.slopeDegrees, 0.01f)
    }

    @Test
    fun testCalculateTerrainAspect_cardinalDirections() {
        val delta = 75.0
        // 1. Downhill North (North is lower: 950 vs South 1050)
        val northSample = listOf(1000f, 950f, 1050f, 1000f, 1000f)
        val northAspect = MushroomAlgorithms.calculateTerrainAspect(northSample, delta)
        assertFalse(northAspect.isFlat)
        assertEquals("Nord", northAspect.cardinalDirection)
        assertEquals("N", northAspect.cardinalAbbreviation)
        assertTrue(northAspect.aspectDegrees < 22.5f || northAspect.aspectDegrees >= 337.5f)
        assertTrue(northAspect.slopeDegrees > 0f)

        // 2. Downhill South (South is lower: 950 vs North 1050)
        val southSample = listOf(1000f, 1050f, 950f, 1000f, 1000f)
        val southAspect = MushroomAlgorithms.calculateTerrainAspect(southSample, delta)
        assertFalse(southAspect.isFlat)
        assertEquals("Sud", southAspect.cardinalDirection)
        assertEquals("S", southAspect.cardinalAbbreviation)
        assertEquals(180f, southAspect.aspectDegrees, 1.0f)

        // 3. Downhill East (East is lower: 950 vs West 1050)
        val eastSample = listOf(1000f, 1000f, 1000f, 950f, 1050f)
        val eastAspect = MushroomAlgorithms.calculateTerrainAspect(eastSample, delta)
        assertFalse(eastAspect.isFlat)
        assertEquals("Est", eastAspect.cardinalDirection)
        assertEquals("E", eastAspect.cardinalAbbreviation)
        assertEquals(90f, eastAspect.aspectDegrees, 1.0f)

        // 4. Downhill West (West is lower: 950 vs East 1050)
        val westSample = listOf(1000f, 1000f, 1000f, 1050f, 950f)
        val westAspect = MushroomAlgorithms.calculateTerrainAspect(westSample, delta)
        assertFalse(westAspect.isFlat)
        assertEquals("Ovest", westAspect.cardinalDirection)
        assertEquals("O", westAspect.cardinalAbbreviation)
        assertEquals(270f, westAspect.aspectDegrees, 1.0f)
    }

    @Test
    fun testEvaluateTerrainAspect_seasonalLogic() {
        val generalSpecies = SPECIES_CATALOG.first { it.id == "general" }
        val delta = 75.0

        val northTerrain = MushroomAlgorithms.calculateTerrainAspect(listOf(1000f, 950f, 1050f, 1000f, 1000f), delta)
        val southTerrain = MushroomAlgorithms.calculateTerrainAspect(listOf(1000f, 1050f, 950f, 1000f, 1000f), delta)

        // Estate calda (Luglio = mese 6, temp 24°C)
        val summerNorth = MushroomAlgorithms.evaluateTerrainAspect(northTerrain, 6, 24.0, 0.7, generalSpecies)
        val summerSouth = MushroomAlgorithms.evaluateTerrainAspect(southTerrain, 6, 24.0, 0.7, generalSpecies)

        assertEquals(FactorLevel.FAVORABLE, summerNorth.level)
        assertTrue(summerNorth.detail.contains("bacìo"))
        assertEquals(FactorLevel.ADVERSE, summerSouth.level)
        assertTrue(summerSouth.detail.contains("Solatìo"))

        // Autunno freddo (Novembre = mese 10, temp 9°C)
        val autumnNorth = MushroomAlgorithms.evaluateTerrainAspect(northTerrain, 10, 9.0, 0.8, generalSpecies)
        val autumnSouth = MushroomAlgorithms.evaluateTerrainAspect(southTerrain, 10, 9.0, 0.8, generalSpecies)

        assertEquals(FactorLevel.ADVERSE, autumnNorth.level)
        assertEquals(FactorLevel.FAVORABLE, autumnSouth.level)
        assertTrue(autumnSouth.detail.contains("Solatìo"))
    }

    @Test
    fun testEvaluateTerrainAspect_thermophilicSpecies() {
        val aereus = SPECIES_CATALOG.first { it.id == "boletus_aereus" }
        val delta = 75.0
        val southTerrain = MushroomAlgorithms.calculateTerrainAspect(listOf(500f, 550f, 450f, 500f, 500f), delta)
        val northTerrain = MushroomAlgorithms.calculateTerrainAspect(listOf(500f, 450f, 550f, 500f, 500f), delta)

        // Boletus aereus ama il calore: versante sud è sempre favorito
        val southEval = MushroomAlgorithms.evaluateTerrainAspect(southTerrain, 8, 20.0, 1.0, aereus)
        assertEquals(FactorLevel.FAVORABLE, southEval.level)

        // Versante nord per aereus con temp mite (20°C) è avverso per carenza di insolazione
        val northEval = MushroomAlgorithms.evaluateTerrainAspect(northTerrain, 8, 20.0, 1.0, aereus)
        assertEquals(FactorLevel.ADVERSE, northEval.level)
    }

    @Test
    fun testFactorsWithTerrainAspectEvaluationFormattedValuesAreCompact() {
        val moon = MushroomAlgorithms.getMoonPhase()
        val delta = 75.0
        val northTerrain = MushroomAlgorithms.calculateTerrainAspect(listOf(1000f, 950f, 1050f, 1000f, 1000f), delta)
        val eval = MushroomAlgorithms.evaluateTerrainAspect(northTerrain, 6, 23.0, 0.8)

        val factors = MushroomAlgorithms.calculateFactors(
            avgTemp = 23.0,
            totalRain = 35.0,
            avgHumidity = 75.0,
            habitatScore = 0.95,
            habitatText = "Ideale (area boschiva)",
            elevation = 1000f,
            month = 6,
            growthPhaseText = "Idratazione miceliare",
            moon = moon,
            slopeText = "Nord (Versanti più freschi)",
            spunEcmText = "60 specie (Ideale)",
            spunHyphalText = "5.0 m/cm³ (Attiva)",
            terrainEvaluation = eval
        )

        val slopeFactor = factors.first { it.id == FactorId.SLOPE }
        assertEquals(FactorLevel.FAVORABLE, slopeFactor.level)
        assertTrue("Formatted value '${slopeFactor.formattedValue}' must be compact", slopeFactor.formattedValue.length <= 15)

        for (f in factors) {
            assertTrue("Factor ${f.id} formattedValue '${f.formattedValue}' is too long", f.formattedValue.length <= 25)
        }
    }

    @Test
    fun testHeatmapColorPaletteAndDynamicAlpha() {
        // Probabilità inferiore a 16: completamente trasparente
        assertEquals(0, github.naturewhisp.myco.utils.HeatmapGenerator.getHeatmapColor(10))
        assertEquals(0, github.naturewhisp.myco.utils.HeatmapGenerator.getHeatmapColor(15))

        // Feathering di ingresso morbido (16..19)
        val c18 = github.naturewhisp.myco.utils.HeatmapGenerator.getHeatmapColor(18)
        val alpha18 = (c18 ushr 24) and 0xFF
        assertTrue("Alpha a prob 18 deve essere > 0 per morbidezza bordo", alpha18 > 0)
        assertTrue("Alpha a prob 18 deve essere inferiore ad alpha base 150", alpha18 < 150)

        // Verifica opacità progressiva
        val c25 = github.naturewhisp.myco.utils.HeatmapGenerator.getHeatmapColor(25)
        val c50 = github.naturewhisp.myco.utils.HeatmapGenerator.getHeatmapColor(50)
        val c68 = github.naturewhisp.myco.utils.HeatmapGenerator.getHeatmapColor(68)
        val c85 = github.naturewhisp.myco.utils.HeatmapGenerator.getHeatmapColor(85)

        val a25 = (c25 ushr 24) and 0xFF
        val a50 = (c50 ushr 24) and 0xFF
        val a68 = (c68 ushr 24) and 0xFF
        val a85 = (c85 ushr 24) and 0xFF

        assertTrue("Alpha deve crescere con la probabilità (a25=$a25, a50=$a50)", a25 <= a50)
        assertTrue("Alpha deve crescere con la probabilità (a50=$a50, a68=$a68)", a50 <= a68)
        assertTrue("Alpha deve crescere con la probabilità (a68=$a68, a85=$a85)", a68 <= a85)
        assertTrue("Alpha a 85 deve raggiungere opacità equilibrata (>= 175)", a85 >= 175)

        // Distinzione cromatica netta tra le 4 classi (RGB non coincidenti)
        val r25 = (c25 ushr 16) and 0xFF
        val g25 = (c25 ushr 8) and 0xFF
        val b25 = c25 and 0xFF

        val r50 = (c50 ushr 16) and 0xFF
        val g50 = (c50 ushr 8) and 0xFF
        val b50 = c50 and 0xFF

        val r68 = (c68 ushr 16) and 0xFF
        val g68 = (c68 ushr 8) and 0xFF
        val b68 = c68 and 0xFF

        val r85 = (c85 ushr 16) and 0xFF
        val g85 = (c85 ushr 8) and 0xFF
        val b85 = c85 and 0xFF

        // Innesco (25) è verde: G dominante su R
        assertTrue("Innesco deve avere componente verde dominante", g25 > r25)
        // Moderato (50) è ambra/oro: R dominante, G sostenuto, B basso
        assertTrue("Moderato deve avere R > B", r50 > b50 && g50 > b50)
        // Propizio (68) è terracotta: R più alto di G
        assertTrue("Propizio deve avere R nettamente superiore a G", r68 > g68)
        // Culmine (85) è cremisi profondo: R dominante, G basso
        assertTrue("Culmine deve essere rosso granato con G basso", r85 > g85 && g85 < 50)
    }
}
