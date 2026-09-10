package github.naturewhisp.myco.model

/**
 * Categoria ecologica della specie fungina in base alla modalità di nutrizione.
 *
 * @property label Etichetta sintetica della categoria.
 * @property description Descrizione ecologica delle modalità trofiche e simbiosi.
 */
enum class EcologicalCategory(val label: String, val description: String) {
    /** Funghi mutualistici legati alle radici di piante arboree forestali. */
    ECTOMYCORRHIZAL("Simbiotico EcM", "Legato a radici di alberi specifici (castagno, faggio, abete, quercia)"),
    /** Funghi decompositori della sostanza organica della lettiera e humus. */
    SAPROTROPHIC("Saprofita umicolo", "Cresce su lettiera organica, prati e margini boschivi"),
    /** Funghi agenti di carie del legno o parassiti su ceppaie. */
    PARASITIC("Lignicolo / Parassita", "Sviluppo su tronchi vivi o ceppaie in decomposizione")
}

/**
 * Profilo biologico, altimetrico ed ecologico di una specie fungina bersaglio.
 *
 * Incapsula l'escursione altimetrica tollerata e ottimale, il regime termico preferenziale,
 * la soglia pluviometrica minima, le essenze forestali simbionti e i mesi fenologici attivi.
 *
 * @property id Identificatore univoco della specie (es. "boletus_edulis", "general").
 * @property binomialName Nomenclatura binomiale scientifica latina con autore.
 * @property vernacularName Nome comune o vernacolare italiano.
 * @property category Categoria trofica ed ecologica [EcologicalCategory].
 * @property minElevation Quota altimetrica minima assoluta in metri s.l.m.
 * @property maxElevation Quota altimetrica massima assoluta in metri s.l.m.
 * @property idealElevationMin Quota minima della fascia ottimale in metri s.l.m.
 * @property idealElevationMax Quota massima della fascia ottimale in metri s.l.m.
 * @property idealTempMin Temperatura minima dell'intervallo termico ideale in °C.
 * @property idealTempMax Temperatura massima dell'intervallo termico ideale in °C.
 * @property toleratedTempMin Temperatura minima assoluta di tolleranza miceliare in °C.
 * @property toleratedTempMax Temperatura massima assoluta di tolleranza miceliare in °C.
 * @property minRainAccumulation Precipitazione minima cumulata richiesta per l'innesco in mm.
 * @property preferredCanopyTypes Generi arborei forestali simbionti o associati (nomi botanici minuscoli).
 * @property fruitingPeriodDescription Descrizione discorsiva del calendario di fruttificazione.
 * @property activeMonths Lista degli indici dei mesi attivi (0-indexed: 0 = Gennaio .. 11 = Dicembre).
 */
data class MushroomSpecies(
    val id: String,
    val binomialName: String,
    val vernacularName: String,
    val category: EcologicalCategory,
    val minElevation: Int,
    val maxElevation: Int,
    val idealElevationMin: Int,
    val idealElevationMax: Int,
    val idealTempMin: Float,
    val idealTempMax: Float,
    val toleratedTempMin: Float,
    val toleratedTempMax: Float,
    val minRainAccumulation: Float,
    val preferredCanopyTypes: List<String>,
    val fruitingPeriodDescription: String,
    val activeMonths: List<Int>,
    val toxicLookAlikes: List<String> = emptyList(),
    val edibilityWarning: String? = null
) {
    /**
     * Indica se la specie rappresenta il modello baseline polifito generale.
     */
    val isGeneralBaseline: Boolean
        get() = id == "general"
}

/**
 * Catalogo tassonomico canonico delle specie fungine supportate da Myco.
 */
val SPECIES_CATALOG: List<MushroomSpecies> = listOf(
    MushroomSpecies(
        id = "general",
        binomialName = "Valutazione Generale",
        vernacularName = "Modello polifito (Porcino e simbiotici)",
        category = EcologicalCategory.ECTOMYCORRHIZAL,
        minElevation = 100,
        maxElevation = 2000,
        idealElevationMin = 400,
        idealElevationMax = 1400,
        idealTempMin = 14.0f,
        idealTempMax = 22.0f,
        toleratedTempMin = 10.0f,
        toleratedTempMax = 25.0f,
        minRainAccumulation = 25.0f,
        preferredCanopyTypes = listOf("fagus", "castanea", "quercus", "picea", "pinus"),
        fruitingPeriodDescription = "Tarda primavera e autunno (Maggio-Novembre)",
        activeMonths = listOf(4, 5, 8, 9, 10),
        edibilityWarning = "Verificare sempre la commestibilità con un ispettorato micologico accreditato."
    ),
    MushroomSpecies(
        id = "boletus_edulis",
        binomialName = "Boletus edulis Bull.",
        vernacularName = "Porcino comune",
        category = EcologicalCategory.ECTOMYCORRHIZAL,
        minElevation = 300,
        maxElevation = 1800,
        idealElevationMin = 600,
        idealElevationMax = 1500,
        idealTempMin = 13.0f,
        idealTempMax = 20.0f,
        toleratedTempMin = 9.0f,
        toleratedTempMax = 24.0f,
        minRainAccumulation = 35.0f,
        preferredCanopyTypes = listOf("fagus", "picea", "abies", "castanea"),
        fruitingPeriodDescription = "Fine estate e autunno inoltrato",
        activeMonths = listOf(7, 8, 9, 10),
        toxicLookAlikes = listOf("Tylopilus felleus (Porcino del fiele, amaro)", "Rubroboletus satanas (Boletus satanas, tossico)"),
        edibilityWarning = "Verificare carne bianca immutabile e assenza di pori rossi o sapori amari."
    ),
    MushroomSpecies(
        id = "boletus_aereus",
        binomialName = "Boletus aereus Bull.",
        vernacularName = "Porcino nero / Bronzino",
        category = EcologicalCategory.ECTOMYCORRHIZAL,
        minElevation = 50,
        maxElevation = 1000,
        idealElevationMin = 150,
        idealElevationMax = 800,
        idealTempMin = 18.0f,
        idealTempMax = 26.0f,
        toleratedTempMin = 14.0f,
        toleratedTempMax = 28.0f,
        minRainAccumulation = 20.0f,
        preferredCanopyTypes = listOf("quercus", "castanea"),
        fruitingPeriodDescription = "Estate e primo autunno in boschi caldi e asciutti",
        activeMonths = listOf(5, 6, 7, 8, 9),
        toxicLookAlikes = listOf("Tylopilus felleus (amaro)", "Rubroboletus satanas (tossico)"),
        edibilityWarning = "Carne bianca immutabile, cappello scuro bruno-nerastro con riflessi bronzei."
    ),
    MushroomSpecies(
        id = "boletus_pinophilus",
        binomialName = "Boletus pinophilus Pilát & Dermek",
        vernacularName = "Porcino rosso / dei pini",
        category = EcologicalCategory.ECTOMYCORRHIZAL,
        minElevation = 300,
        maxElevation = 1900,
        idealElevationMin = 700,
        idealElevationMax = 1700,
        idealTempMin = 11.0f,
        idealTempMax = 19.0f,
        toleratedTempMin = 7.0f,
        toleratedTempMax = 23.0f,
        minRainAccumulation = 30.0f,
        preferredCanopyTypes = listOf("pinus", "fagus", "castanea"),
        fruitingPeriodDescription = "Primavera precoce e autunno montano",
        activeMonths = listOf(4, 5, 8, 9, 10),
        toxicLookAlikes = listOf("Tylopilus felleus (amaro)"),
        edibilityWarning = "Cuticola rugosa color rosso-vinato o granata con reticolo marcato sul gambo."
    ),
    MushroomSpecies(
        id = "boletus_reticulatus",
        binomialName = "Boletus reticulatus Schaeff.",
        vernacularName = "Porcino estatino",
        category = EcologicalCategory.ECTOMYCORRHIZAL,
        minElevation = 100,
        maxElevation = 1400,
        idealElevationMin = 200,
        idealElevationMax = 1100,
        idealTempMin = 16.0f,
        idealTempMax = 24.0f,
        toleratedTempMin = 12.0f,
        toleratedTempMax = 26.0f,
        minRainAccumulation = 25.0f,
        preferredCanopyTypes = listOf("quercus", "castanea", "fagus"),
        fruitingPeriodDescription = "Maggio-Giugno e ripresa a Settembre",
        activeMonths = listOf(4, 5, 8, 9),
        toxicLookAlikes = listOf("Tylopilus felleus (amaro)"),
        edibilityWarning = "Cuticola asciutta e finemente vellutata, frequentemente screpolata dalla siccità."
    ),
    MushroomSpecies(
        id = "cantharellus_cibarius",
        binomialName = "Cantharellus cibarius Fr.",
        vernacularName = "Finferlo / Gallinaccio",
        category = EcologicalCategory.ECTOMYCORRHIZAL,
        minElevation = 200,
        maxElevation = 1700,
        idealElevationMin = 400,
        idealElevationMax = 1400,
        idealTempMin = 14.0f,
        idealTempMax = 22.0f,
        toleratedTempMin = 10.0f,
        toleratedTempMax = 25.0f,
        minRainAccumulation = 40.0f,
        preferredCanopyTypes = listOf("fagus", "quercus", "castanea", "picea"),
        fruitingPeriodDescription = "Estate umida e autunno in presenza di muschi",
        activeMonths = listOf(5, 6, 7, 8, 9, 10),
        toxicLookAlikes = listOf("Omphalotus olearius (fungo dell'olivo, tossico grave)", "Hygrophoropsis aurantiaca (falso gallinaccio)"),
        edibilityWarning = "Presenta pseudolamelle (pliche venose decorrenti) e profumo fruttato; non ha mai lamelle fitte né cresce su ceppaie di olivo."
    ),
    MushroomSpecies(
        id = "amanita_caesarea",
        binomialName = "Amanita caesarea (Scop.) Pers.",
        vernacularName = "Ovolo buono",
        category = EcologicalCategory.ECTOMYCORRHIZAL,
        minElevation = 50,
        maxElevation = 900,
        idealElevationMin = 150,
        idealElevationMax = 700,
        idealTempMin = 18.0f,
        idealTempMax = 26.0f,
        toleratedTempMin = 15.0f,
        toleratedTempMax = 28.0f,
        minRainAccumulation = 20.0f,
        preferredCanopyTypes = listOf("quercus", "castanea"),
        fruitingPeriodDescription = "Estate inoltrata e primo autunno su suoli drenati",
        activeMonths = listOf(6, 7, 8, 9),
        toxicLookAlikes = listOf("Amanita muscaria (tossica)", "Amanita phalloides (mortale allo stadio di ovolo chiuso)"),
        edibilityWarning = "VIETATA la raccolta allo stadio di ovolo chiuso (art. 5 DPR 376/1995): rischio mortale di confusione con Amanita phalloides."
    ),
    MushroomSpecies(
        id = "hydnum_repandum",
        binomialName = "Hydnum repandum L.",
        vernacularName = "Steccherino dorato",
        category = EcologicalCategory.ECTOMYCORRHIZAL,
        minElevation = 200,
        maxElevation = 1500,
        idealElevationMin = 400,
        idealElevationMax = 1200,
        idealTempMin = 8.0f,
        idealTempMax = 16.0f,
        toleratedTempMin = 4.0f,
        toleratedTempMax = 20.0f,
        minRainAccumulation = 25.0f,
        preferredCanopyTypes = listOf("fagus", "castanea", "quercus", "coniferae"),
        fruitingPeriodDescription = "Autunno tardivo resistente alle prime brinate",
        activeMonths = listOf(8, 9, 10, 11),
        toxicLookAlikes = listOf("Hydnum albidum (amaro se vecchio)"),
        edibilityWarning = "Imenoforo ad aculei facilmente asportabili; togliere gli aculei dai campioni adulti prima della cottura."
    ),
    MushroomSpecies(
        id = "macrolepiota_procera",
        binomialName = "Macrolepiota procera (Scop.) Singer",
        vernacularName = "Mazza di tamburo",
        category = EcologicalCategory.SAPROTROPHIC,
        minElevation = 100,
        maxElevation = 1600,
        idealElevationMin = 200,
        idealElevationMax = 1300,
        idealTempMin = 15.0f,
        idealTempMax = 23.0f,
        toleratedTempMin = 11.0f,
        toleratedTempMax = 26.0f,
        minRainAccumulation = 20.0f,
        preferredCanopyTypes = listOf("prati", "radure", "margini boschivi"),
        fruitingPeriodDescription = "Dalla tarda estate all'autunno in radure soleggiate",
        activeMonths = listOf(6, 7, 8, 9, 10),
        toxicLookAlikes = listOf("Lepiota helveola e piccole lepiote (velenose/mortali)", "Chlorophyllum molybdites (tossico)"),
        edibilityWarning = "Consumare solo il cappello ben cotto. Scartare tassativamente esemplari con cappello inferiore a 10 cm."
    ),
    MushroomSpecies(
        id = "armillaria_mellea",
        binomialName = "Armillaria mellea (Vahl) P. Kumm.",
        vernacularName = "Chiodino",
        category = EcologicalCategory.PARASITIC,
        minElevation = 50,
        maxElevation = 1300,
        idealElevationMin = 150,
        idealElevationMax = 1000,
        idealTempMin = 9.0f,
        idealTempMax = 17.0f,
        toleratedTempMin = 5.0f,
        toleratedTempMax = 20.0f,
        minRainAccumulation = 30.0f,
        preferredCanopyTypes = listOf("latifoglie", "ceppaie", "boschi misti"),
        fruitingPeriodDescription = "Autunno inoltrato a cespi alla base dei tronchi",
        activeMonths = listOf(8, 9, 10),
        toxicLookAlikes = listOf("Hypholoma fasciculare (falso chiodino, tossico amaro)", "Galerina marginata (mortale)"),
        edibilityWarning = "Tossico da crudo: necessita di pre-bollitura prolungata (almeno 20 min) gettando l'acqua di cottura."
    )
)
