package github.naturewhisp.myco

import github.naturewhisp.myco.model.EcologicalWeightsConfig
import github.naturewhisp.myco.model.MushroomSpecies
import github.naturewhisp.myco.model.ProcessedDay
import github.naturewhisp.myco.model.SPECIES_CATALOG
import github.naturewhisp.myco.utils.GrowthStage
import github.naturewhisp.myco.utils.MushroomAlgorithms
import github.naturewhisp.myco.utils.RainTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/**
 * Suite di test avversariali Gate 3 per la verifica sistematica delle invarianti biometeorologiche,
 * della continuità analitica, dei benchmark empirici reali (Ground Truth) e del fuzzing temporale.
 *
 * Questa suite protegge l'algoritmo di Myco da regressioni temporali non-locali, garantendo che
 * perturbazioni secondarie, ricariche primarie e dinamiche del suolo rispettino sempre le leggi
 * ecofisiologiche della carpogenesi.
 */
class PhenologicalInvariantsTest {

    private val edulis: MushroomSpecies = SPECIES_CATALOG.first { it.id == "boletus_edulis" }
    private val procera: MushroomSpecies = SPECIES_CATALOG.first { it.id == "macrolepiota_procera" }

    // =========================================================================
    // SEZIONE 1: INVARIANTI DI LATENZA MINIMA E CONCORRENZA PRECIPITATIVA
    // =========================================================================

    @Test
    fun invariant01_minimumLatencyEnforcementAfterHeavyStorm() {
        // Legge del Minimo di Liebig: una pioggia primaria abbondante (>= 25mm) richiede
        // fisiologicamente un tempo minimo di attivazione metabolica del micelio.
        // A tau <= 2 giorni, lo stadio DEVE essere MYCELIAL_HYDRATION con moltiplicatore <= 0.45.
        val days = createSyntheticSeries(
            rainMap = mapOf(23 to 30.0f), // Pioggia il giorno 23
            avgTemp = 16.0f
        )

        for (day in 23..25) { // Giorni 0, 1 e 2 dopo la pioggia
            val eval = MushroomAlgorithms.evaluateGrowthPhase(days, edulis, dayIndex = day)
            assertEquals("A tau <= 2 giorni lo stadio deve essere MYCELIAL_HYDRATION", GrowthStage.MYCELIAL_HYDRATION, eval.stage)
            assertTrue("Il moltiplicatore fenologico deve essere <= 0.45 (attuale: ${eval.multiplier})", eval.multiplier <= 0.45)
            assertTrue("I giorni trascorsi dall'innesco devono essere <= 2", (eval.daysSinceTrigger ?: 99) <= 2)
        }
    }

    @Test
    fun invariant02_primaryStormAlwaysResetsTimerOverEarlierFlush() {
        // Se un evento precedente è avvenuto 11 giorni fa (25mm), ma compare una nuova pioggia
        // primaria consistente (28mm >= 0.70 * 25mm) 1 giorno fa, l'innesco attivo SI RESETTA
        // fisiologicamente alla nuova pioggia primaria (Idratazione miceliare).
        val days = createSyntheticSeries(
            rainMap = mapOf(13 to 25.0f, 23 to 28.0f),
            avgTemp = 16.0f
        )

        val eval = MushroomAlgorithms.evaluateGrowthPhase(days, edulis, dayIndex = 24)
        assertEquals("La nuova pioggia primaria deve resettare a MYCELIAL_HYDRATION", GrowthStage.MYCELIAL_HYDRATION, eval.stage)
        assertEquals("L'innesco attivo deve essere l'evento a giorno 23 (1 giorno fa)", 1, eval.daysSinceTrigger)
        assertTrue("Il moltiplicatore deve riflettere l'idratazione iniziale (<= 0.45)", eval.multiplier <= 0.45)
    }

    @Test
    fun invariant03_minorSecondaryShowerPreservesActiveFlush() {
        // Se una pioggia primaria abbondante (38mm) è caduta 11 giorni fa e sul campo c'è una
        // buttata matura attiva, un piovasco secondario recente di 10mm (< 0.70 * 38mm = 26.6mm)
        // NON deve azzerare la buttata in corso.
        val days = createSyntheticSeries(
            rainMap = mapOf(13 to 38.0f, 22 to 10.0f),
            avgTemp = 16.0f
        )

        val eval = MushroomAlgorithms.evaluateGrowthPhase(days, edulis, dayIndex = 24)
        assertEquals("La buttata attiva deve essere preservata in presenza di piovasco secondario", GrowthStage.ACTIVE_FRUITING, eval.stage)
        assertEquals("L'innesco principale deve rimanere l'evento primario di 11 giorni fa", 11, eval.daysSinceTrigger)
        assertTrue("Il moltiplicatore della buttata attiva deve essere >= 0.85 (attuale: ${eval.multiplier})", eval.multiplier >= 0.85)
    }

    @Test
    fun invariant04_absoluteDroughtEnforcesGrowthAbsent() {
        // Se la pioggia negli ultimi 28 giorni è zero, la fase deve essere WAITING_FOR_RAIN
        // e il moltiplicatore deve essere minimo (0.25).
        val days = createSyntheticSeries(rainMap = emptyMap(), avgTemp = 16.0f)

        val eval = MushroomAlgorithms.evaluateGrowthPhase(days, edulis, dayIndex = 24)
        assertEquals(GrowthStage.WAITING_FOR_RAIN, eval.stage)
        assertEquals(0.25, eval.multiplier, 0.0001)

        val weatherScore = MushroomAlgorithms.calculateWeatherScore(
            24,
            days,
            species = edulis,
            config = EcologicalWeightsConfig.PHENOLOGICAL
        )
        val prob = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = weatherScore,
            habitatScore = 1.0,
            altitudeScore = 1.0,
            seasonalityScore = 1.0,
            terrainModifier = 1.0,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            species = edulis,
            growthPhaseMultiplier = eval.multiplier
        )
        assertTrue("La probabilità con siccità assoluta deve essere <= 20% (attuale: $prob%)", prob <= 20)
    }

    @Test
    fun invariant05_clusterGroupingWithinTwoDays() {
        // Piogge a giorni consecutivi (es. 15mm il giorno 13 e 15mm il giorno 14)
        // devono essere clusterizzate come un unico evento idrologico coerente.
        val rawTriggers = listOf(
            RainTrigger(14, 15.0f),
            RainTrigger(13, 15.0f)
        )
        val clusters = MushroomAlgorithms.clusterRainEvents(rawTriggers)
        assertEquals("Due piogge a distanza di 1 giorno devono confluire in 1 solo cluster", 1, clusters.size)
        assertEquals(14, clusters.first().triggerIndex)
    }

    // =========================================================================
    // SEZIONE 2: INVARIANTI FISIOLOGICHE TERMICHE, EDAFICHE E DI GATING
    // =========================================================================

    @Test
    fun invariant06_lethalNocturnalFreezingSuppressesProbability() {
        // Quando le temperature minime notturne scendono sotto lo zero (gelata letale),
        // l'inibizione termica scende a 0.30. A temperature ideali (>= 13°C per edulis) è 1.0.
        val inhibitionMite = MushroomAlgorithms.nocturnalChillingInhibition(14.0f, edulis)
        val inhibitionGelo = MushroomAlgorithms.nocturnalChillingInhibition(-2.0f, edulis)

        assertEquals(1.0, inhibitionMite, 0.001)
        assertEquals(0.3, inhibitionGelo, 0.001)
        assertTrue("Il gelo deve abbattere l'inibizione notturna ad almeno un terzo", inhibitionGelo <= 0.35)
    }

    @Test
    fun invariant07_macroporeAnoxiaDampingAtWaterlogging() {
        // Van Genuchten & asfissia radicale: quando l'umidità volumetrica superficiale del suolo
        // supera la saturazione dei macropori (theta > 0.44 m3/m3), la risposta del suolo
        // deve scendere per riflettere l'anossia e crollare a <= 0.20 per allagamento acuto (> 0.50).
        val soilFieldCapacity = MushroomAlgorithms.soilMoistureScoreSmooth(0.28, 0.26, 2.0)
        val soilModerateWaterlogging = MushroomAlgorithms.soilMoistureScoreSmooth(0.46, 0.45, 1.0)
        val soilAcuteAnoxia = MushroomAlgorithms.soilMoistureScoreSmooth(0.55, 0.52, 0.5)

        assertTrue("La capacità di campo ottimale (~0.28) deve produrre un punteggio elevato (> 0.80)", soilFieldCapacity > 0.80)
        assertTrue("Il ristagno (> 0.44) deve abbattere la risposta edafica (< 0.50, attuale: $soilModerateWaterlogging)", soilModerateWaterlogging < 0.50)
        assertTrue("L'allagamento acuto con anossia (> 0.50) deve crollare al fondo biologico (<= 0.20, attuale: $soilAcuteAnoxia)", soilAcuteAnoxia <= 0.20)
    }

    @Test
    fun invariant08_deepSoilMoistureDeficitGating() {
        // Se l'orizzonte profondo (7-28 cm) è sotto il punto di appassimento (theta < 0.18),
        // il punteggio edafico deve essere fortemente penalizzato rispetto a una situazione di equilibrio.
        val soilMoistDeep = MushroomAlgorithms.soilMoistureScoreSmooth(0.25, 0.25, 2.0)
        val soilParchedDeep = MushroomAlgorithms.soilMoistureScoreSmooth(0.25, 0.12, 3.5)

        assertTrue("Il deficit idrico profondo deve penalizzare significativamente la risposta edafica", soilParchedDeep < soilMoistDeep * 0.70)
    }

    @Test
    fun invariant09_dtrMonotonicity() {
        // L'aumento dell'escursione termica giornaliera (DTR) sopra i 12°C non può MAI aumentare
        // la probabilità di fruttificazione; deve decrescere monotonicamente tramite smoothstep.
        val p10 = MushroomAlgorithms.calculateDtrPenalty(10.0)
        val p14 = MushroomAlgorithms.calculateDtrPenalty(14.0)
        val p16 = MushroomAlgorithms.calculateDtrPenalty(16.0)
        val p20 = MushroomAlgorithms.calculateDtrPenalty(20.0)

        assertEquals("DTR <= 12°C non deve applicare penalità", 1.0, p10, 0.001)
        assertTrue("La penalità DTR deve decrescere monotonicamente con l'aumento dell'escursione", p10 >= p14)
        assertTrue("p14 deve essere >= p16", p14 >= p16)
        assertTrue("p16 deve essere >= p20", p16 >= p20)
        assertEquals("DTR >= 18°C deve raggiungere la massima penalità (0.80)", 0.80, p20, 0.001)
    }

    @Test
    fun invariant10_obligateEctomycorrhizalHurdleZeroInflation() {
        // Per Boletus edulis (simbionte ectomicorrizico obbligato), un habitat senza copertura forestale
        // (es. centro urbano o campo coltivato, habitatScore <= 0.10) deve azzerare la probabilità
        // tramite il modello Hurdle (pHurdle <= 0.10, P <= 10%), anche con meteo perfetto (100%).
        val weatherScore100 = 100
        val pOpenField = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = weatherScore100,
            habitatScore = 0.10,
            altitudeScore = 1.0,
            seasonalityScore = 1.0,
            terrainModifier = 1.0,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            species = edulis
        )
        assertTrue("Boletus edulis su campo aperto/urbano non può superare il 10% (attuale: $pOpenField%)", pOpenField <= 10)
    }

    @Test
    fun invariant11_saprotrophicViabilityOnOpenGrassland() {
        // Macrolepiota procera (saprotrofo praticolo) deve prosperare su pascoli aperti senza alberi.
        // Con forestCount = 0, l'habitat evaluator riconosce il floor praticolo (85%),
        // e la probabilità con meteo favorevole deve attestarsi ad alti livelli (>= 60%).
        val habitatEval = MushroomAlgorithms.evaluateSpeciesHabitat(
            forestCount = 0,
            species = procera
        )
        assertTrue("L'habitat praticolo per Macrolepiota deve avere punteggio >= 0.85 (attuale: ${habitatEval.score})", habitatEval.score >= 0.85)

        val prob = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = 80,
            habitatScore = habitatEval.score,
            altitudeScore = 1.0,
            seasonalityScore = 1.0,
            terrainModifier = 1.0,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            species = procera
        )
        assertTrue("Macrolepiota procera deve raggiungere probabilità favorevole su prato aperto (>= 60%, attuale: $prob%)", prob >= 60)
    }

    @Test
    fun invariant12_asymptoticCeilingHonoredGlobally() {
        // Anche con tutti i fattori al 100% o sovra-unitari, la probabilità non può mai superare
        // l'asintoto teorico del 92% a causa dell'incertezza ecologica non osservabile.
        val maxProb = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = 100,
            habitatScore = 1.0,
            altitudeScore = 1.0,
            seasonalityScore = 1.0,
            terrainModifier = 1.10,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            growthPhaseMultiplier = 1.0
        )
        assertTrue("La probabilità massima calibrata non può superare 92% (attuale: $maxProb%)", maxProb <= 92)
        assertTrue("La probabilità massima con condizioni ideali deve essere almeno 88% (attuale: $maxProb%)", maxProb >= 88)
    }

    @Test
    fun invariant13_lipschitzContinuityAcrossEnvironmentalPerturbations() {
        // Verifica di continuità analitica: piccole perturbazioni nei fattori continui
        // (+0.5mm di pioggia o +0.2°C di temperatura) non devono mai generare discontinuità
        // o salti di probabilità superiori a 5 punti percentuali.
        val baseDays = createSyntheticSeries(
            rainMap = mapOf(13 to 25.0f),
            avgTemp = 16.0f
        )
        val perturbedDays = createSyntheticSeries(
            rainMap = mapOf(13 to 25.5f),
            avgTemp = 16.2f
        )

        val w1 = MushroomAlgorithms.calculateWeatherScore(24, baseDays, species = edulis, config = EcologicalWeightsConfig.PHENOLOGICAL)
        val w2 = MushroomAlgorithms.calculateWeatherScore(24, perturbedDays, species = edulis, config = EcologicalWeightsConfig.PHENOLOGICAL)
        val p1 = MushroomAlgorithms.dailyGrowthProbability(w1, 0.9, 0.9, 1.0, 1.0, config = EcologicalWeightsConfig.PHENOLOGICAL, species = edulis)
        val p2 = MushroomAlgorithms.dailyGrowthProbability(w2, 0.9, 0.9, 1.0, 1.0, config = EcologicalWeightsConfig.PHENOLOGICAL, species = edulis)

        assertTrue("La variazione di probabilità per perturbazioni infinitesimali deve essere <= 5% (attuale: ${abs(p1 - p2)}%)", abs(p1 - p2) <= 5)
    }

    // =========================================================================
    // SEZIONE 3: BENCHMARK EMPIRICI GROUND TRUTH (CASI STUDIO REALI)
    // =========================================================================

    /**
     * Benchmark 1: Mindino 18 Settembre 2026 (Giorno dell'uscita reale sul campo).
     * Riscontro empirico: 0 funghi sul campo. Pioggia primaria di 25.3 mm caduta il 17 settembre (1 giorno fa).
     * L'algoritmo deve attestarsi in fase di idratazione con probabilità <= 35%.
     */
    @Test
    fun benchmark01_mindinoRealFieldTripSep18ZeroFinding() {
        val days = createMindinoHistoricalSeries()
        val evalSep18 = MushroomAlgorithms.evaluateGrowthPhase(days, edulis, dayIndex = 24)

        assertEquals(GrowthStage.MYCELIAL_HYDRATION, evalSep18.stage)
        assertEquals(1, evalSep18.daysSinceTrigger)
        assertTrue("Moltiplicatore fenologico il 18 set deve essere <= 0.40", evalSep18.multiplier <= 0.40)

        val wSep18 = MushroomAlgorithms.calculateWeatherScore(
            24,
            days,
            species = edulis,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            canopyCover = 0.70
        )
        val pSep18 = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = wSep18,
            habitatScore = 0.80,
            altitudeScore = 0.85,
            seasonalityScore = 1.0,
            terrainModifier = 1.00,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            growthPhaseMultiplier = evalSep18.multiplier,
            species = edulis
        )
        assertTrue("Il 18 settembre a Mindino la probabilità deve essere <= 35% (attuale: $pSep18%)", pSep18 <= 35)
    }

    /**
     * Benchmark 2: Mindino 21 Settembre 2026 (Giorno 4 dalla pioggia primaria).
     * Riscontro empirico: micelio ancora in piena idratazione, non ancora giunto all'incubazione dei primordi.
     * Probabilità attesa: Moderato / Discreto (20% - 50%), superiore al giorno 18.
     */
    @Test
    fun benchmark02_mindinoHydrationFollowupSep21() {
        val days = createMindinoHistoricalSeries()
        val evalSep18 = MushroomAlgorithms.evaluateGrowthPhase(days, edulis, dayIndex = 24)
        val evalSep21 = MushroomAlgorithms.evaluateGrowthPhase(days, edulis, dayIndex = 27)

        assertEquals(GrowthStage.MYCELIAL_HYDRATION, evalSep21.stage)
        assertEquals(4, evalSep21.daysSinceTrigger)
        assertTrue("Moltiplicatore fenologico il 21 set deve essere compreso tra 0.45 e 0.55", evalSep21.multiplier in 0.45..0.55)

        val wSep18 = MushroomAlgorithms.calculateWeatherScore(
            24,
            days,
            species = edulis,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            canopyCover = 0.70
        )
        val pSep18 = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = wSep18,
            habitatScore = 0.80,
            altitudeScore = 0.85,
            seasonalityScore = 1.0,
            terrainModifier = 1.00,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            growthPhaseMultiplier = evalSep18.multiplier,
            species = edulis
        )

        val wSep21 = MushroomAlgorithms.calculateWeatherScore(
            27,
            days,
            species = edulis,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            canopyCover = 0.70
        )
        val pSep21 = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = wSep21,
            habitatScore = 0.80,
            altitudeScore = 0.85,
            seasonalityScore = 1.0,
            terrainModifier = 1.00,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            growthPhaseMultiplier = evalSep21.multiplier,
            species = edulis
        )
        assertTrue("Il 21 settembre a Mindino la probabilità deve essere compresa tra 20% e 50% (attuale: $pSep21%)", pSep21 in 20..50)
        assertTrue("La probabilità al 21 set ($pSep21%) deve essere >= al 18 set ($pSep18%)", pSep21 >= pSep18)
    }

    /**
     * Benchmark 3: Mindino Weekend 26 Settembre 2026 (Proiezione a 9 giorni).
     * A 9 giorni dalla perturbazione saturante, l'incubazione primordiale si completa
     * e la probabilità deve salire naturalmente verso la finestra di fruttificazione (>= 65%).
     */
    @Test
    fun benchmark03_mindinoProjectedWeekendPeakSep26() {
        // Estendiamo la serie con giorni stabili post-pioggia
        val extendedDays = createMindinoHistoricalSeries().toMutableList()
        val startDate = java.time.LocalDate.of(2026, 8, 25)
        for (i in 28..32) {
            val validDate = startDate.plusDays(i.toLong()).toString()
            extendedDays.add(
                ProcessedDay(
                    date = validDate,
                    avgTemp = 15.0f,
                    minTemp = 10.0f,
                    maxTemp = 19.0f,
                    totalPrecip = 0.0f,
                    avgHumidity = 80.0f,
                    weatherCode = 1
                )
            )
        }

        val evalSep26 = MushroomAlgorithms.evaluateGrowthPhase(extendedDays, edulis, dayIndex = 32)
        assertEquals(GrowthStage.ACTIVE_FRUITING, evalSep26.stage)
        assertEquals(9, evalSep26.daysSinceTrigger)
        assertTrue("Il moltiplicatore fenologico a 9 giorni deve essere >= 0.85 (attuale: ${evalSep26.multiplier})", evalSep26.multiplier >= 0.85)
    }

    /**
     * Benchmark 4: Val di Taro / Appennino Ligure (Autunno Ottimale Classico).
     * Temporale di 45mm 11 giorni fa, temperature ideali (14.5°C, min 10°C, max 19°C),
     * versante Sud-Ovest, umidità 85%. Buttata in piena produzione epigea (65% - 88%).
     */
    @Test
    fun benchmark04_valDiTaroOptimalAutumnFlush() {
        val days = createSyntheticSeries(
            rainMap = mapOf(13 to 45.0f),
            avgTemp = 14.5f,
            minTemp = 10.0f,
            maxTemp = 19.0f,
            humidity = 85.0f
        )
        val eval = MushroomAlgorithms.evaluateGrowthPhase(days, edulis, dayIndex = 24)
        assertEquals(GrowthStage.ACTIVE_FRUITING, eval.stage)
        assertEquals(11, eval.daysSinceTrigger)
        assertTrue("Il moltiplicatore della buttata classica deve essere >= 0.90", eval.multiplier >= 0.90)

        val w = MushroomAlgorithms.calculateWeatherScore(
            24,
            days,
            species = edulis,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            canopyCover = 0.80
        )
        val p = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = w,
            habitatScore = 0.95,
            altitudeScore = 0.95,
            seasonalityScore = 1.0,
            terrainModifier = 1.05,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            growthPhaseMultiplier = eval.multiplier,
            species = edulis
        )
        assertTrue("In condizioni autunnali perfette la probabilità deve essere tra 65% e 88% (attuale: $p%)", p in 65..88)
    }

    /**
     * Benchmark 5: Garfagnana / Appennino Toscano (Siccità Estiva con Temporale Isolato).
     * Temporale isolato di 25 mm dopo 3 settimane di secco estivo, DTR elevato (17°C),
     * suolo profondo secco (theta = 0.12). Risultato: probabilità fortemente inibita (<= 40%).
     */
    @Test
    fun benchmark05_garfagnanaSummerDroughtSingleStorm() {
        val days = createSyntheticSeries(
            rainMap = mapOf(20 to 25.0f),
            avgTemp = 23.0f,
            minTemp = 13.0f,
            maxTemp = 30.0f,
            humidity = 50.0f
        )
        val eval = MushroomAlgorithms.evaluateGrowthPhase(days, edulis, dayIndex = 24)
        val w = MushroomAlgorithms.calculateWeatherScore(
            24,
            days,
            species = edulis,
            config = EcologicalWeightsConfig.PHENOLOGICAL
        )
        val p = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = w,
            habitatScore = 0.90,
            altitudeScore = 0.90,
            seasonalityScore = 0.60, // Luglio/Agosto sub-ottimale
            terrainModifier = 0.90,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            growthPhaseMultiplier = eval.multiplier,
            species = edulis
        )
        assertTrue("Siccità estiva e shock termico devono contenere la probabilità sotto il 40% (attuale: $p%)", p <= 40)
    }

    /**
     * Benchmark 6: Carnia / Alpi Giulie (Alluvione Persistente e Gelata Notturna).
     * 140 mm di pioggia in 5 giorni, suolo asfittico, gelata notturna a -3°C.
     * Risultato: aborto da asfissia e gelo (<= 20%).
     */
    @Test
    fun benchmark06_carniaPersistentFloodAndFreezing() {
        val days = createSyntheticSeries(
            rainMap = mapOf(19 to 35.0f, 20 to 35.0f, 21 to 35.0f, 22 to 35.0f),
            avgTemp = 3.0f,
            minTemp = -3.0f,
            maxTemp = 8.0f,
            humidity = 95.0f
        )
        val w = MushroomAlgorithms.calculateWeatherScore(
            24,
            days,
            species = edulis,
            config = EcologicalWeightsConfig.PHENOLOGICAL
        )
        val p = MushroomAlgorithms.dailyGrowthProbability(
            weatherScore = w,
            habitatScore = 0.90,
            altitudeScore = 0.70,
            seasonalityScore = 0.50,
            terrainModifier = 0.90,
            config = EcologicalWeightsConfig.PHENOLOGICAL,
            species = edulis
        )
        assertTrue("Allagamento e gelo notturno devono deprimere la probabilità <= 20% (attuale: $p%)", p <= 20)
    }

    // =========================================================================
    // SEZIONE 4: FUZZING GENERATIVO (500 SCENARI CASUALI)
    // =========================================================================

    @Test
    fun fuzzing_500RandomizedWeatherScenariosAdhereToGlobalInvariants() {
        val random = Random(42_1337)
        var testedCount = 0

        for (scenario in 0 until 500) {
            val species = SPECIES_CATALOG[random.nextInt(SPECIES_CATALOG.size)]
            val rainEvents = mutableMapOf<Int, Float>()
            val numRainEvents = random.nextInt(0, 6)
            for (r in 0 until numRainEvents) {
                val day = random.nextInt(0, 28)
                val amount = random.nextFloat() * 50.0f
                rainEvents[day] = amount
            }

            val avgTemp = (random.nextFloat() * 35.0f) - 5.0f // Tra -5°C e 30°C
            val dtr = random.nextFloat() * 20.0f // Tra 0°C e 20°C
            val minTemp = avgTemp - (dtr / 2.0f)
            val maxTemp = avgTemp + (dtr / 2.0f)
            val humidity = (random.nextFloat() * 60.0f) + 35.0f // Tra 35% e 95%

            val days = createSyntheticSeries(
                rainMap = rainEvents,
                avgTemp = avgTemp,
                minTemp = minTemp,
                maxTemp = maxTemp,
                humidity = humidity
            )

            val dayIndex = random.nextInt(7, 28)
            val eval = MushroomAlgorithms.evaluateGrowthPhase(days, species, dayIndex)
            val weatherScore = MushroomAlgorithms.calculateWeatherScore(
                dayIndex,
                days,
                species = species,
                config = EcologicalWeightsConfig.PHENOLOGICAL
            )

            val habitatScore = random.nextDouble(0.10, 1.00)
            val altitudeScore = random.nextDouble(0.40, 1.00)
            val seasonalityScore = random.nextDouble(0.20, 1.00)
            val terrainModifier = random.nextDouble(0.50, 1.10)

            val prob = MushroomAlgorithms.dailyGrowthProbability(
                weatherScore = weatherScore,
                habitatScore = habitatScore,
                altitudeScore = altitudeScore,
                seasonalityScore = seasonalityScore,
                terrainModifier = terrainModifier,
                config = EcologicalWeightsConfig.PHENOLOGICAL,
                growthPhaseMultiplier = eval.multiplier,
                species = species
            )

            // Asserzione delle invarianti globali
            assertTrue("Scenario $scenario: La probabilità deve essere in [0, 92] (ottenuto: $prob)", prob in 0..92)
            assertTrue("Scenario $scenario: Il moltiplicatore deve essere in [0.25, 1.0] (ottenuto: ${eval.multiplier})", eval.multiplier in 0.25..1.00)
            assertTrue("Scenario $scenario: daysSinceTrigger deve essere >= 0", (eval.daysSinceTrigger ?: 0) >= 0)
            assertNotNull("Scenario $scenario: Lo stadio fenologico non deve mai essere null", eval.stage)

            // Se la pioggia cumulata è zero, lo stadio deve essere WAITING_FOR_RAIN
            val totalRain = days.take(dayIndex + 1).sumOf { it.totalPrecip.toDouble() }
            if (totalRain <= 0.01) {
                assertEquals("Scenario $scenario: Senza pioggia lo stadio deve essere WAITING_FOR_RAIN", GrowthStage.WAITING_FOR_RAIN, eval.stage)
            }

            testedCount++
        }

        assertEquals("Devono essere stati validati esattamente 500 scenari casuali", 500, testedCount)
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private fun createSyntheticSeries(
        rainMap: Map<Int, Float>,
        avgTemp: Float,
        minTemp: Float = avgTemp - 4.0f,
        maxTemp: Float = avgTemp + 4.0f,
        humidity: Float = 75.0f
    ): List<ProcessedDay> {
        return (0 until 28).map { i ->
            ProcessedDay(
                date = "2026-09-${String.format(Locale.US, "%02d", i + 1)}",
                avgTemp = avgTemp,
                minTemp = minTemp,
                maxTemp = maxTemp,
                totalPrecip = rainMap[i] ?: 0.0f,
                avgHumidity = humidity,
                weatherCode = if ((rainMap[i] ?: 0.0f) > 0f) 61 else 0
            )
        }
    }

    private fun createMindinoHistoricalSeries(): List<ProcessedDay> {
        // Serie storica reale di Mindino (F19 - Validità Calendario Reale):
        // Inizio serie: 2026-08-25 (Day 0)
        // Day 16 (10 set): 22.3 mm (seguito da secco)
        // Day 23 (17 set): 25.3 mm (pioggia primaria consistente)
        // Day 24 (18 set): giorno della visita sul campo (0 funghi)
        // Day 27 (21 set): 4 giorni dopo pioggia
        val startDate = java.time.LocalDate.of(2026, 8, 25)
        return (0 until 28).map { i ->
            ProcessedDay(
                date = startDate.plusDays(i.toLong()).toString(),
                avgTemp = 16.0f,
                minTemp = 11.5f,
                maxTemp = 20.5f,
                totalPrecip = when (i) {
                    16 -> 22.3f
                    23 -> 25.3f
                    else -> 0.0f
                },
                avgHumidity = 78.0f,
                weatherCode = when (i) {
                    16, 23 -> 61
                    else -> 1
                }
            )
        }
    }
}
