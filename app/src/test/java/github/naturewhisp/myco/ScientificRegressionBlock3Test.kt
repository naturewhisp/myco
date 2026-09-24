package github.naturewhisp.myco

import com.google.gson.JsonParser
import github.naturewhisp.myco.core.SpeciesCatalog
import github.naturewhisp.myco.model.EcologicalWeightsConfig
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.platform.AssetProvider
import github.naturewhisp.myco.repository.SpunDataManager
import github.naturewhisp.myco.utils.MushroomAlgorithms
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Suite di test di non-regressione scientifica e invarianti per il Blocco 3:
 * Pedologia Idraulica, Dataset SPUN F10 con Dati Originali e Memoria Idrica Invariante.
 *
 * Risolve i seguenti rilievi della revisione scientifica:
 * - F07: Risposta idrica empirica continua a due strati, rimozione etichetta impropria van Genuchten,
 *        smorzamento controllato per anossia/ipossia con pavimenti biologici a 0.52 m³/m³ e 0.50 m³/m³.
 * - F10: Ricostruzione asset SPUN dai GeoTIFF originali in C:\Users\dendo\Documents\Spun con normalized
 *        convolution NoData masking per proteggere le coste; generazione manifest formale con DOI e SHA256;
 *        isolamento della biomassa ifale AM dal modello operativo di fruttificazione (PHENOLOGICAL).
 * - F17: Memoria idrica fenologica fissa a 26 giorni; invarianza del calcolo rispetto alla lunghezza
 *        dell'archivio meteo pregresso oltre la finestra di supporto.
 */
class ScientificRegressionBlock3Test {

    private val manifestFile = File("src/main/assets/spun/SPUN_MANIFEST.json")
    private val binaryAssetFile = File("src/main/assets/spun/spun_italy.bin")

    /**
     * REG-08 (F07): Risposta idrica continua del suolo, assenza di scalini e smorzamento anossico.
     * Verifica che lo sweep di umidita superficiale e profonda non produca salti > 5% per variazioni
     * infinitesimali (step 0.001 m³/m³) e rispetti rigorosamente i pavimenti e le soglie biologiche dichiarate.
     */
    @Test
    fun reg08_hydraulicSoilMoistureResponseAndAnoxiaContinuity() {
        // 1. Sweep orizzonte superficiale 0-7 cm (da 0.00 a 0.60 m3/m3 con passo fine 0.001)
        var prevScore: Double? = null
        var theta = 0.0
        while (theta <= 0.60) {
            val score = MushroomAlgorithms.soilMoistureScoreSmooth(m0To7 = theta, m7To28 = null)
            assertTrue("Score deve essere compreso in [0.10, 1.0]", score in 0.10..1.0)
            if (prevScore != null) {
                val delta = abs(score - prevScore)
                assertTrue(
                    "Raccordo discontinuo a theta=$theta: delta=$delta > 0.02 per step 0.001",
                    delta <= 0.02
                )
            }
            prevScore = score
            theta += 0.001
        }

        // Verifica soglie chiave superficiale:
        // Sotto 0.10 -> 0.10 (disseccamento)
        assertEquals(0.10, MushroomAlgorithms.soilMoistureScoreSmooth(0.05, null), 0.001)
        // Range ottimale [0.22, 0.38] -> 1.0
        assertEquals(1.00, MushroomAlgorithms.soilMoistureScoreSmooth(0.30, null), 0.001)
        // A 0.44 -> 0.50 (inizio anossia avanzata)
        assertEquals(0.50, MushroomAlgorithms.soilMoistureScoreSmooth(0.44, null), 0.001)
        // A 0.52 -> 0.15 (pavimento biologico asfittico)
        assertEquals(0.15, MushroomAlgorithms.soilMoistureScoreSmooth(0.52, null), 0.001)
        // Oltre 0.52 -> 0.15 costante
        assertEquals(0.15, MushroomAlgorithms.soilMoistureScoreSmooth(0.58, null), 0.001)

        // 2. Sweep orizzonte profondo 7-28 cm
        assertEquals(0.20, MushroomAlgorithms.soilMoistureScoreSmooth(null, 0.08), 0.001)
        assertEquals(1.00, MushroomAlgorithms.soilMoistureScoreSmooth(null, 0.28), 0.001)
        assertEquals(0.55, MushroomAlgorithms.soilMoistureScoreSmooth(null, 0.42), 0.001)
        assertEquals(0.20, MushroomAlgorithms.soilMoistureScoreSmooth(null, 0.50), 0.001)
    }

    /**
     * REG-11 (F10): Tracciabilita, integrita SHA256 e protezione delle coste SPUN.
     * Verifica l'esistenza e validita del manifest formale, il matching del checksum del binario
     * e che i pixel costieri liguri/tirrenici non siano stati azzerati da NoData bleeding.
     */
    @Test
    fun reg11_spunNoDataMaskingPreservesCoastlineAndManifestVerifiability() = runBlocking {
        assertTrue("Manifest SPUN deve essere presente negli asset", manifestFile.isFile)
        assertTrue("Asset binario SPUN deve essere presente negli asset", binaryAssetFile.isFile)

        // 1. Parsing del manifest JSON con Gson
        val manifestJson = JsonParser.parseString(manifestFile.readText(Charsets.UTF_8)).asJsonObject
        assertEquals("1.0.0", manifestJson.get("manifest_version").asString)
        assertEquals("Society for the Protection of Underground Networks (SPUN)", manifestJson.get("dataset_provider").asString)
        assertTrue(manifestJson.get("doi_citation").asString.contains("10.1038"))

        val layers = manifestJson.getAsJsonArray("layers")
        assertEquals(2, layers.size())

        val ecmLayer = layers[0].asJsonObject
        assertEquals("ecm_fungi_richness", ecmLayer.get("id").asString)
        assertEquals("ECTOMYCORRHIZAL", ecmLayer.get("guild").asString)
        assertEquals("506d61e42190b61c7c6c96d6074b907825fe1f8a3237750be6682976cec7965a", ecmLayer.get("source_sha256").asString)
        assertTrue("Deve avere oltre 1.000.000 pixel validi con normalized convolution", ecmLayer.get("valid_pixels").asInt > 1_000_000)

        val hypLayer = layers[1].asJsonObject
        assertEquals("hyphal_density", hypLayer.get("id").asString)
        assertEquals("ARBUSCULAR_MYCORRHIZAL", hypLayer.get("guild").asString)
        assertEquals("43a5c1bacb53d34f9fdf34b3108777a28e02669cebc05ce8b98ea9b218ee065a", hypLayer.get("source_sha256").asString)

        // 2. Verifica hash SHA256 del file binario generato
        val binDigest = MessageDigest.getInstance("SHA-256")
        val computedBinSha256 = binDigest.digest(binaryAssetFile.readBytes()).joinToString("") { "%02x".format(it) }
        val manifestBinSha256 = manifestJson.getAsJsonObject("binary_asset").get("sha256").asString
        assertEquals("SHA256 del binario deve corrispondere esattamente al manifest", manifestBinSha256, computedBinSha256)

        // 3. Verifica preservazione dei pixel costieri (es. Portofino / Promontorio Ligure 44.31 N, 9.21 E)
        val manager = SpunDataManager(AssetProvider { binaryAssetFile.inputStream() })
        manager.ensureRegionLoadedFor(44.31, 9.21)
        val coastalSample = manager.getSpunData(44.31, 9.21, radiusMeters = 1500)
        assertNotNull("La coordinata costiera ligure non deve essere priva di dati o fuori copertura", coastalSample)
        assertTrue("La ricchezza EcM costiera deve essere non-nulla (> 0)", (coastalSample?.ecmRichness ?: 0f) > 0f)
    }

    /**
     * REG-17 (F17): Invarianza della memoria idrica fenologica (supporto a 26 giorni).
     * Dimostra che estendere lo storico da 28 a 63 giorni aggiungendo dati antecedenti
     * alla finestra dei 26 giorni non altera in alcun modo il risultato per il giorno bersaglio.
     */
    @Test
    fun reg17_fixedMemoryWindowInvariance26Days() {
        val species = SPECIES_CATALOG.first { it.id == "boletus_edulis" }

        // Creazione di 28 giorni di storico meteo standard (con pioggia e umidità variabile)
        val base28Days = List(28) { idx ->
            ProcessedDay(
                date = "2026-09-${(idx + 1).toString().padStart(2, '0')}",
                avgTemp = 18.0f,
                minTemp = 12.0f,
                maxTemp = 24.0f,
                totalPrecip = if (idx in 12..20) 8.0f else 0.0f,
                avgHumidity = 78.0f,
                weatherCode = 1,
                avgSoilMoisture0To7cm = 0.28f,
                avgSoilMoisture7To28cm = 0.26f,
                totalEvapotranspiration = 2.0f
            )
        }

        val targetDayIndex28 = 27 // Giorno 28 (indice 27)
        val effectiveRain28 = MushroomAlgorithms.calculateEffectiveRainfall(targetDayIndex28, base28Days, species)

        // Creazione di uno storico esteso aggiungendo 35 giorni pregressi (per un totale di 63 giorni)
        val older35Days = List(35) { idx ->
            ProcessedDay(
                date = "2026-08-${(idx + 1).toString().padStart(2, '0')}",
                avgTemp = 22.0f,
                minTemp = 16.0f,
                maxTemp = 28.0f,
                totalPrecip = 15.0f, // forti piogge remote antecedenti alla finestra dei 26 giorni
                avgHumidity = 85.0f,
                weatherCode = 1,
                avgSoilMoisture0To7cm = 0.35f,
                avgSoilMoisture7To28cm = 0.10f, // deficit o eccessi remoti
                totalEvapotranspiration = 4.0f
            )
        }
        val extended63Days = older35Days + base28Days
        val targetDayIndex63 = 35 + 27 // Stesso giorno bersaglio traslato in avanti di 35 giorni

        val effectiveRain63 = MushroomAlgorithms.calculateEffectiveRainfall(targetDayIndex63, extended63Days, species)

        // L'invarianza a 26 giorni garantisce che le piogge e il suolo oltre i 26 giorni non alterano il calcolo
        assertEquals(
            "La precipitazione efficace deve essere identica a prescindere dalla presenza di storico remoto oltre i 26 giorni",
            effectiveRain28,
            effectiveRain63,
            1e-5
        )
    }

    /**
     * REG-10 (F10): Isolamento della biomassa ifale AM nel modello operativo PHENOLOGICAL.
     * Verifica che in modalita di produzione PHENOLOGICAL l'input di spunHyphalDensity
     * sia isolato dal calcolo del punteggio meteorologico, mentre la modalita legacy DEFAULT
     * mantenga la retrocompatibilita per i test oracle.
     */
    @Test
    fun reg10_spunAmHyphalDensityIsolatedInPhenologicalMode() {
        val species = SPECIES_CATALOG.first { it.id == "boletus_edulis" }
        // 15 mm di pioggia nella finestra (in 18..20): sufficienti per abilitare lo shock (>= 12 mm), ma non saturi (< 30 mm)
        val testDays = List(28) { idx ->
            ProcessedDay(
                date = "2026-09-${(idx + 1).toString().padStart(2, '0')}",
                avgTemp = 17.0f,
                minTemp = 11.0f,
                maxTemp = 23.0f,
                totalPrecip = if (idx in 18..20) 5.0f else 0.0f,
                avgHumidity = 80.0f,
                weatherCode = 1,
                avgSoilMoisture0To7cm = 0.28f,
                avgSoilMoisture7To28cm = 0.26f,
                totalEvapotranspiration = 2.0f
            )
        }

        // 1. In modalita PHENOLOGICAL (produzione), spunHyphalDensity non deve alterare weatherScore
        val scoreWithoutSpun = MushroomAlgorithms.calculateWeatherScore(
            dayIndex = 27,
            allData = testDays,
            spunHyphalDensity = null,
            species = species,
            config = EcologicalWeightsConfig.PHENOLOGICAL
        )
        val scoreWithDenseSpun = MushroomAlgorithms.calculateWeatherScore(
            dayIndex = 27,
            allData = testDays,
            spunHyphalDensity = 10.0f,
            species = species,
            config = EcologicalWeightsConfig.PHENOLOGICAL
        )
        val scoreWithLowSpun = MushroomAlgorithms.calculateWeatherScore(
            dayIndex = 27,
            allData = testDays,
            spunHyphalDensity = 1.0f,
            species = species,
            config = EcologicalWeightsConfig.PHENOLOGICAL
        )

        assertEquals("PHENOLOGICAL: nessun bonus ifale AM deve alterare il meteo", scoreWithoutSpun, scoreWithDenseSpun)
        assertEquals("PHENOLOGICAL: nessuna penalita ifale AM deve alterare il meteo", scoreWithoutSpun, scoreWithLowSpun)

        // 2. In modalita DEFAULT (oracle baseline), la retrocompatibilita e preservata
        val defaultWithoutSpun = MushroomAlgorithms.calculateWeatherScore(
            dayIndex = 27,
            allData = testDays,
            spunHyphalDensity = null,
            species = species,
            config = EcologicalWeightsConfig.DEFAULT
        )
        val defaultWithDenseSpun = MushroomAlgorithms.calculateWeatherScore(
            dayIndex = 27,
            allData = testDays,
            spunHyphalDensity = 10.0f,
            species = species,
            config = EcologicalWeightsConfig.DEFAULT
        )
        assertTrue("DEFAULT oracle: densita >= 5.0f deve concedere il bonus storico", defaultWithDenseSpun > defaultWithoutSpun)
    }
}
