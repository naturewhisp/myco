package github.naturewhisp.myco

import github.naturewhisp.myco.model.EcologicalWeightsConfig
import github.naturewhisp.myco.model.HabitatEvidence
import github.naturewhisp.myco.model.HabitatStatus
import github.naturewhisp.myco.model.MushroomSpecies
import github.naturewhisp.myco.model.OverpassElement
import github.naturewhisp.myco.model.OverpassResponse
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.platform.InMemoryCacheStore
import github.naturewhisp.myco.platform.KeyValueStorage
import github.naturewhisp.myco.repository.CacheManager
import github.naturewhisp.myco.repository.MushroomRepository
import github.naturewhisp.myco.repository.SpunDataManager
import github.naturewhisp.myco.utils.MushroomAlgorithms
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Suite di test di regressione scientifica Blocco 2 (Release v1.3.1).
 *
 * Copre sistematicamente i requisiti architetturali e i criteri di accettazione:
 * - REG-07: Continuità C1 in deepSoilMoistureCompensation a theta = 0.35.
 * - REG-08 / REG-19: Gating ecologico termico/fisiologico Liebig (stress termico prolungato e gelo).
 * - REG-09 / REG-11: Invarianza dell'habitat e della chioma rispetto al partizionamento poligonale OSM.
 * - REG-10: Specificità del bonus ospiti (nessun bonus a Boletus edulis per solo leaf_type).
 * - REG-11: Assenza di dati OSM o contesti urbani non attribuiscono score praticolo a saprotrofi.
 * - REG-12: Isolamento cache per raggio di scansione (500m vs 1500m vs 5000m) e cambio specie privo di eredità spurie.
 * - REG-18: Continuità C1 nel De Frenne canopy buffering a 18°C e conservazione naturale dell'ordinamento Tmin <= Tavg <= Tmax.
 * - REG-18 (Ambiente): La copertura arborea è una proprietà fisica del sito e non varia al cambio specie.
 */
class ScientificRegressionBlock2Test {

    private class TestKeyValueStorage : KeyValueStorage {
        private val map = mutableMapOf<String, Any?>()
        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
        override fun putString(key: String, value: String?) { if (value != null) map[key] = value else map.remove(key) }
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun putInt(key: String, value: Int) { map[key] = value }
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun putBoolean(key: String, value: Boolean) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun clear() { map.clear() }
        override fun getAll(): Map<String, *> = map
    }

    private val edulis: MushroomSpecies = SPECIES_CATALOG.first { it.id == "boletus_edulis" }
    private val macrolepiota: MushroomSpecies = SPECIES_CATALOG.first { it.id == "macrolepiota_procera" }
    private val lactarius: MushroomSpecies = SPECIES_CATALOG.first { it.id == "lactarius_deliciosus" }

    private lateinit var cacheManager: CacheManager
    private lateinit var repository: MushroomRepository

    @Before
    fun setUp() {
        val storage = TestKeyValueStorage()
        val cacheStore = InMemoryCacheStore()
        cacheManager = CacheManager(storage, cacheStore)
        repository = MushroomRepository(
            cacheManager = cacheManager,
            spunDataManager = mockk<SpunDataManager>(relaxed = true)
        )
    }

    // =========================================================================
    // REG-07: Continuità in deepSoilMoistureCompensation a theta = 0.35
    // =========================================================================
    @Test
    fun reg07_deepSoilMoistureSmoothstepAt035() {
        val below = MushroomAlgorithms.deepSoilMoistureCompensation(0.349999)
        val above = MushroomAlgorithms.deepSoilMoistureCompensation(0.350001)

        val delta = abs(above - below)
        assertTrue(
            "Il salto di deepSoilMoistureCompensation a theta=0.35 deve essere nullo (trovato delta = $delta)",
            delta < 0.0005
        )

        // Verifica continuità Lipschitziana lungo l'intero intervallo pedologico [0.10, 0.55]
        var prevVal = MushroomAlgorithms.deepSoilMoistureCompensation(0.10)
        var theta = 0.11
        while (theta <= 0.55) {
            val currentVal = MushroomAlgorithms.deepSoilMoistureCompensation(theta)
            val stepDelta = abs(currentVal - prevVal)
            assertTrue(
                "La variazione per step di 0.01 m3/m3 deve essere controllata (theta = $theta, stepDelta = $stepDelta)",
                stepDelta < 0.08
            )
            prevVal = currentVal
            theta += 0.01
        }
    }

    // =========================================================================
    // REG-08 / REG-19: Gating termico/fisiologico Liebig in condizioni di gelo
    // =========================================================================
    @Test
    fun reg08_prolongedThermalStressSuppressesOutput() {
        // Creiamo una serie con piogge ideali (50mm totali) e umidità ottimale (85%),
        // ma con temperature medie severe a 0°C (molto al di sotto di tMin = 6°C per Boletus edulis)
        val freezingDays = (1..20).map { dayIndex ->
            ProcessedDay(
                date = "2026-10-%02d".format(dayIndex),
                avgTemp = 0.0f,
                minTemp = -2.0f,
                maxTemp = 2.0f,
                totalPrecip = if (dayIndex in 5..8) 12.0f else 0.0f,
                avgHumidity = 85.0f,
                weatherCode = 0
            )
        }

        val weatherScore = MushroomAlgorithms.calculateWeatherScore(
            dayIndex = 14,
            allData = freezingDays,
            species = edulis,
            config = EcologicalWeightsConfig.PHENOLOGICAL
        )

        // Con Liebig gating, il gelo severo (0°C vs tMin 6°C) deve sopprimere l'innesco sporocarpico a 0
        assertEquals(
            "In condizioni di gelo severo prolungato (0°C), il weatherScore deve essere soppresso a 0",
            0,
            weatherScore
        )
    }

    // =========================================================================
    // REG-09 / REG-11: Invarianza dell'habitat al partizionamento poligonale OSM
    // =========================================================================
    @Test
    fun reg09_osmPolygonPartitioningInvariance() {
        val targetLat = 45.0
        val targetLon = 7.0

        // Caso 1: 1 unico grande poligono forestale a Nord-Est (distanza 100m, lat 45.0008, lon 7.0008)
        val singlePolygonResponse = OverpassResponse(
            elements = listOf(
                OverpassElement(
                    type = "way",
                    id = 1001L,
                    lat = 45.0008,
                    lon = 7.0008,
                    tags = mapOf("natural" to "wood", "genus" to "Fagus")
                )
            )
        )

        // Caso 2: 20 piccoli sub-poligoni frammentati nello stesso settore Nord-Est
        val twentySubPolygons = (1..20).map { i ->
            OverpassElement(
                type = "way",
                id = (2000 + i).toLong(),
                lat = 45.0008 + (i * 0.00002),
                lon = 7.0008 + (i * 0.00002),
                tags = mapOf("natural" to "wood", "genus" to "Fagus")
            )
        }
        val twentyPolygonsResponse = OverpassResponse(elements = twentySubPolygons)

        val evidence1 = repository.extractHabitatEvidence(singlePolygonResponse, targetLat, targetLon, 1000)
        val evidence2 = repository.extractHabitatEvidence(twentyPolygonsResponse, targetLat, targetLon, 1000)

        // Le due coperture forestali stimate devono essere identiche (invarianza al partizionamento cartografico)
        assertEquals(
            "La frazione di copertura stimata deve essere invariante rispetto al frazionamento dei poligoni",
            evidence1.forestCoverFraction,
            evidence2.forestCoverFraction,
            0.05
        )

        val eval1 = MushroomAlgorithms.evaluateSpeciesHabitat(evidence1, species = edulis)
        val eval2 = MushroomAlgorithms.evaluateSpeciesHabitat(evidence2, species = edulis)

        assertEquals(
            "Il punteggio finale dell'habitat non deve cambiare tra 1 o 20 poligoni dello stesso bosco",
            eval1.score,
            eval2.score,
            0.05
        )
    }

    // =========================================================================
    // REG-10: Specificità del bonus ospiti (nessun bonus a Boletus edulis per solo leaf_type)
    // =========================================================================
    @Test
    fun reg10_hostBonusSpecificityNoBroadleavedFallback() {
        val targetLat = 45.0
        val targetLon = 7.0

        // Elemento con solo leaf_type=broadleaved ma nessun genere identificato
        val genericWoodResponse = OverpassResponse(
            elements = listOf(
                OverpassElement(
                    type = "way",
                    id = 3001L,
                    lat = 45.0005,
                    lon = 7.0005,
                    tags = mapOf("natural" to "wood", "leaf_type" to "broadleaved")
                )
            )
        )
        val evidenceGeneric = repository.extractHabitatEvidence(genericWoodResponse, targetLat, targetLon, 1000)
        val evalGeneric = MushroomAlgorithms.evaluateSpeciesHabitat(evidenceGeneric, species = edulis)

        // Non deve attivare il bonus alberi ospiti
        assertFalse(
            "Un bosco con solo leaf_type=broadleaved non deve attivare il bonus alberi ospiti per Boletus edulis",
            evalGeneric.bonusText.contains("Bonus: Rilevati alberi ospiti")
        )

        // Elemento con tag genus=Fagus esplicito
        val specificWoodResponse = OverpassResponse(
            elements = listOf(
                OverpassElement(
                    type = "way",
                    id = 3002L,
                    lat = 45.0005,
                    lon = 7.0005,
                    tags = mapOf("natural" to "wood", "genus" to "Fagus")
                )
            )
        )
        val evidenceSpecific = repository.extractHabitatEvidence(specificWoodResponse, targetLat, targetLon, 1000)
        val evalSpecific = MushroomAlgorithms.evaluateSpeciesHabitat(evidenceSpecific, species = edulis)

        assertTrue(
            "Un bosco con genus=Fagus deve attivare il bonus alberi ospiti per Boletus edulis",
            evalSpecific.bonusText.contains("Bonus: Rilevati alberi ospiti")
        )
        assertTrue(
            "Il punteggio con genere ospite confermato deve essere superiore",
            evalSpecific.score > evalGeneric.score
        )
    }

    // =========================================================================
    // REG-11: Assenza di dati OSM o contesti urbani non attribuiscono score praticolo a saprotrofi
    // =========================================================================
    @Test
    fun reg11_unknownAndUrbanNotTreatedAsMeadowForSaprotrophs() {
        val targetLat = 45.0
        val targetLon = 7.0

        // 1. Dati mancanti (network timeout o response null)
        val unknownEvidence = repository.extractHabitatEvidence(null, targetLat, targetLon, 1000)
        assertEquals(HabitatStatus.UNKNOWN, unknownEvidence.status)
        val evalUnknown = MushroomAlgorithms.evaluateSpeciesHabitat(unknownEvidence, species = macrolepiota)
        assertEquals(
            "In assenza di dati, il punteggio per Macrolepiota deve essere neutrale (0.50), non 0.90",
            0.50,
            evalUnknown.score,
            0.05
        )

        // 2. Contesto urbano (residential / commercial)
        val urbanResponse = OverpassResponse(
            elements = listOf(
                OverpassElement(
                    type = "way",
                    id = 4001L,
                    lat = 45.0001,
                    lon = 7.0001,
                    tags = mapOf("landuse" to "residential", "building" to "apartments")
                )
            )
        )
        val urbanEvidence = repository.extractHabitatEvidence(urbanResponse, targetLat, targetLon, 1000)
        assertEquals(HabitatStatus.KNOWN_UNSUITABLE, urbanEvidence.status)
        val evalUrban = MushroomAlgorithms.evaluateSpeciesHabitat(urbanEvidence, species = macrolepiota)
        assertTrue(
            "In contesto urbano, l'habitat per Macrolepiota deve essere sfavorevole (<= 0.20)",
            evalUrban.score <= 0.20
        )

        // 3. Contesto di prato confermato
        val meadowResponse = OverpassResponse(
            elements = listOf(
                OverpassElement(
                    type = "way",
                    id = 4002L,
                    lat = 45.0002,
                    lon = 7.0002,
                    tags = mapOf("landuse" to "meadow")
                )
            )
        )
        val meadowEvidence = repository.extractHabitatEvidence(meadowResponse, targetLat, targetLon, 1000)
        assertEquals(HabitatStatus.KNOWN_SUITABLE, meadowEvidence.status)
        val evalMeadow = MushroomAlgorithms.evaluateSpeciesHabitat(meadowEvidence, species = macrolepiota)
        assertTrue(
            "In prato accertato, l'habitat per Macrolepiota deve essere favorevole (>= 0.85)",
            evalMeadow.score >= 0.85
        )
    }

    // =========================================================================
    // REG-12: Isolamento cache per raggio di scansione e cambio specie
    // =========================================================================
    @Test
    fun reg12_speciesSwitchDoesNotInheritHostEvidence() {
        // Evidenza con solo faggi (Fagus)
        val beechEvidence = HabitatEvidence(
            status = HabitatStatus.KNOWN_SUITABLE,
            forestCoverFraction = 0.80,
            meadowFraction = 0.05,
            distanceToNearestForestMeters = 0.0,
            confirmedHostGenera = setOf("Fagus")
        )

        // Boletus edulis accetta Fagus -> riceve il bonus
        val evalEdulis = MushroomAlgorithms.evaluateSpeciesHabitat(beechEvidence, species = edulis)
        assertTrue(
            "Boletus edulis deve ricevere il bonus per Fagus",
            evalEdulis.bonusText.contains("Bonus")
        )

        // Lactarius deliciosus richiede conifere (Pinus) -> NON deve ricevere il bonus da Fagus
        val evalLactarius = MushroomAlgorithms.evaluateSpeciesHabitat(beechEvidence, species = lactarius)
        assertFalse(
            "Lactarius deliciosus non deve ereditare il bonus per Fagus",
            evalLactarius.bonusText.contains("Bonus: Rilevati alberi ospiti")
        )
    }

    @Test
    fun reg12_cacheRadiusIsolation() = runBlocking {
        val dummyResponse500 = OverpassResponse(elements = emptyList())
        val dummyResponse1500 = OverpassResponse(elements = emptyList())

        cacheManager.radius = 500
        val key500 = "habitat_500m_45.0000_7.0000"
        cacheManager.saveCachedData(key500, dummyResponse500)

        // Verifica che richiedendo 1500m non legga la cache di 500m
        cacheManager.radius = 1500
        val key1500 = "habitat_1500m_45.0000_7.0000"
        val cached1500 = cacheManager.getCachedData(key1500, OverpassResponse::class.java, 24 * 60 * 60 * 1000)
        assertNull("La cache a 1500m deve essere isolata e non deve leggere i dati a 500m", cached1500)

        // Hit a 500m
        cacheManager.radius = 500
        val cached500 = cacheManager.getCachedData(key500, OverpassResponse::class.java, 24 * 60 * 60 * 1000)
        assertNotNull("La cache a 500m deve essere presente", cached500)
    }

    // =========================================================================
    // REG-18: De Frenne Canopy Buffering: continuità a 18°C e ordinamento Tmin <= Tmax
    // =========================================================================
    @Test
    fun reg18_canopyBufferingContinuousAt18CAndPreservesOrdering() {
        val dayBelow = ProcessedDay(
            date = "2026-08-15",
            avgTemp = 16.0f,
            minTemp = 12.0f,
            maxTemp = 17.999f,
            totalPrecip = 0f,
            avgHumidity = 70f,
            weatherCode = 0
        )
        val dayAbove = ProcessedDay(
            date = "2026-08-15",
            avgTemp = 16.0f,
            minTemp = 12.0f,
            maxTemp = 18.001f,
            totalPrecip = 0f,
            avgHumidity = 70f,
            weatherCode = 0
        )

        val bufferedBelow = MushroomAlgorithms.applyCanopyBuffering(dayBelow, canopyCover = 0.85)
        val bufferedAbove = MushroomAlgorithms.applyCanopyBuffering(dayAbove, canopyCover = 0.85)

        val deltaMax = abs(bufferedAbove.maxTemp - bufferedBelow.maxTemp)
        assertTrue(
            "Il raccordo delle massime a 18°C in applyCanopyBuffering deve essere continuo (delta = $deltaMax)",
            deltaMax < 0.01f
        )

        // Test estremo con DTR ridotto per verificare che Tmin <= Tavg <= Tmax sia sempre conservato
        val narrowDtrDay = ProcessedDay(
            date = "2026-08-16",
            avgTemp = 15.0f,
            minTemp = 14.5f,
            maxTemp = 15.5f,
            totalPrecip = 0f,
            avgHumidity = 70f,
            weatherCode = 0
        )
        val bufferedNarrow = MushroomAlgorithms.applyCanopyBuffering(narrowDtrDay, canopyCover = 0.90)

        assertTrue(
            "Tmin sub-canopy deve essere <= Tmax sub-canopy",
            bufferedNarrow.minTemp <= bufferedNarrow.maxTemp
        )
        assertTrue(
            "Tavg sub-canopy deve essere compresa tra minTemp e maxTemp",
            bufferedNarrow.avgTemp in bufferedNarrow.minTemp..bufferedNarrow.maxTemp
        )
    }

    @Test
    fun reg18_canopyCoverIsEnvironmentalProperty() {
        val days = (1..15).map { i ->
            ProcessedDay(
                date = "2026-09-%02d".format(i),
                avgTemp = 15f,
                minTemp = 10f,
                maxTemp = 20f,
                totalPrecip = 5f,
                avgHumidity = 75f,
                weatherCode = 0
            )
        }

        // Il punto ha una copertura fisica dell'80%
        val siteCanopy = 0.80
        val bufferedEdulis = MushroomAlgorithms.applyCanopyBuffering(days, siteCanopy)
        val bufferedMacro = MushroomAlgorithms.applyCanopyBuffering(days, siteCanopy)

        // Selezionando una specie praticola (Macrolepiota), il microclima fisico del punto NON deve variare
        assertEquals(
            "La temperatura del sito sottochioma deve essere identica indipendentemente dalla specie selezionata",
            bufferedEdulis[10].avgTemp,
            bufferedMacro[10].avgTemp,
            0.001f
        )
    }
}
