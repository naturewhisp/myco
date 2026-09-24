package github.naturewhisp.myco.core

object SpeciesCatalog {
    val all: List<MushroomSpecies> = listOf(
        species("general", "Valutazione Generale", "Modello polifito (Porcino e simbiotici)", EcologicalCategory.ECTOMYCORRHIZAL, 100, 2000, 400, 1400, 14.0, 22.0, 10.0, 25.0, 25.0, listOf("fagus", "castanea", "quercus", "picea", "pinus"), "Tarda primavera e autunno (Maggio-Novembre)", listOf(4, 5, 8, 9, 10), emptyList(), "Verificare sempre la commestibilità con un ispettorato micologico accreditato.", phenologyLatencyPeakDays = 11.0, phenologyShapeAlpha = 4.0),
        species("boletus_edulis", "Boletus edulis Bull.", "Porcino comune", EcologicalCategory.ECTOMYCORRHIZAL, 300, 1800, 600, 1500, 13.0, 20.0, 9.0, 24.0, 35.0, listOf("fagus", "picea", "abies", "castanea"), "Fine estate e autunno inoltrato", listOf(7, 8, 9, 10), listOf("Tylopilus felleus (Porcino del fiele, amaro)", "Rubroboletus satanas (Boletus satanas, tossico)"), "Verificare carne bianca immutabile e assenza di pori rossi o sapori amari.", phenologyLatencyPeakDays = 11.0, phenologyShapeAlpha = 4.0, optimalTemp = 14.0),
        species("boletus_aereus", "Boletus aereus Bull.", "Porcino nero / Bronzino", EcologicalCategory.ECTOMYCORRHIZAL, 50, 1000, 150, 800, 18.0, 26.0, 14.0, 28.0, 20.0, listOf("quercus", "castanea"), "Estate e primo autunno in boschi caldi e asciutti", listOf(5, 6, 7, 8, 9), listOf("Tylopilus felleus (amaro)", "Rubroboletus satanas (tossico)"), "Carne bianca immutabile, cappello scuro bruno-nerastro con riflessi bronzei.", phenologyLatencyPeakDays = 9.0, phenologyShapeAlpha = 4.0),
        species("boletus_pinophilus", "Boletus pinophilus Pilát & Dermek", "Porcino rosso / dei pini", EcologicalCategory.ECTOMYCORRHIZAL, 300, 1900, 700, 1700, 11.0, 19.0, 7.0, 23.0, 30.0, listOf("pinus", "fagus", "castanea"), "Primavera precoce e autunno montano", listOf(4, 5, 8, 9, 10), listOf("Tylopilus felleus (amaro)"), "Cuticola rugosa color rosso-vinato o granata con reticolo marcato sul gambo.", phenologyLatencyPeakDays = 12.0, phenologyShapeAlpha = 4.0),
        species("boletus_reticulatus", "Boletus reticulatus Schaeff.", "Porcino estatino", EcologicalCategory.ECTOMYCORRHIZAL, 100, 1400, 200, 1100, 16.0, 24.0, 12.0, 26.0, 25.0, listOf("quercus", "castanea", "fagus"), "Maggio-Giugno e ripresa a Settembre", listOf(4, 5, 8, 9), listOf("Tylopilus felleus (amaro)"), "Cuticola asciutta e finemente vellutata, frequentemente screpolata dalla siccità.", phenologyLatencyPeakDays = 8.0, phenologyShapeAlpha = 4.0),
        species("cantharellus_cibarius", "Cantharellus cibarius Fr.", "Finferlo / Gallinaccio", EcologicalCategory.ECTOMYCORRHIZAL, 200, 1700, 400, 1400, 14.0, 22.0, 10.0, 25.0, 40.0, listOf("fagus", "quercus", "castanea", "picea"), "Estate umida e autunno in presenza di muschi", listOf(5, 6, 7, 8, 9, 10), listOf("Omphalotus olearius (fungo dell'olivo, tossico grave)", "Hygrophoropsis aurantiaca (falso gallinaccio)"), "Presenta pseudolamelle (pliche venose decorrenti) e profumo fruttato; non ha mai lamelle fitte né cresce su ceppaie di olivo.", phenologyLatencyPeakDays = 14.0, phenologyShapeAlpha = 3.5),
        species("amanita_caesarea", "Amanita caesarea (Scop.) Pers.", "Ovolo buono", EcologicalCategory.ECTOMYCORRHIZAL, 50, 900, 150, 700, 18.0, 26.0, 15.0, 28.0, 20.0, listOf("quercus", "castanea"), "Estate inoltrata e primo autunno su suoli drenati", listOf(6, 7, 8, 9), listOf("Amanita muscaria (tossica)", "Amanita phalloides (mortale allo stadio di ovolo chiuso)"), "VIETATA la raccolta allo stadio di ovolo chiuso (art. 5 DPR 376/1995): rischio mortale di confusione con Amanita phalloides.", phenologyLatencyPeakDays = 8.0, phenologyShapeAlpha = 4.5),
        species("hydnum_repandum", "Hydnum repandum L.", "Steccherino dorato", EcologicalCategory.ECTOMYCORRHIZAL, 200, 1500, 400, 1200, 8.0, 16.0, 4.0, 20.0, 25.0, listOf("fagus", "castanea", "quercus", "coniferae"), "Autunno tardivo resistente alle prime brinate", listOf(8, 9, 10, 11), listOf("Hydnum albidum (amaro se vecchio)"), "Imenoforo ad aculei facilmente asportabili; togliere gli aculei dai campioni adulti prima della cottura.", phenologyLatencyPeakDays = 13.0, phenologyShapeAlpha = 4.0),
        species("macrolepiota_procera", "Macrolepiota procera (Scop.) Singer", "Mazza di tamburo", EcologicalCategory.SAPROTROPHIC, 100, 1600, 200, 1300, 15.0, 23.0, 11.0, 26.0, 20.0, listOf("prati", "radure", "margini boschivi"), "Dalla tarda estate all'autunno in radure soleggiate", listOf(6, 7, 8, 9, 10), listOf("Lepiota helveola e piccole lepiote (velenose/mortali)", "Chlorophyllum molybdites (tossico)"), "Consumare solo il cappello ben cotto. Scartare tassativamente esemplari con cappello inferiore a 10 cm.", phenologyLatencyPeakDays = 6.0, phenologyShapeAlpha = 4.0, optimalBasalAreaM2Ha = 10.0, hurdleStrictness = 0.3),
        species("armillaria_mellea", "Armillaria mellea (Vahl) P. Kumm.", "Chiodino", EcologicalCategory.PARASITIC, 50, 1300, 150, 1000, 9.0, 17.0, 5.0, 20.0, 30.0, listOf("latifoglie", "ceppaie", "boschi misti"), "Autunno inoltrato a cespi alla base dei tronchi", listOf(8, 9, 10), listOf("Hypholoma fasciculare (falso chiodino, tossico amaro)", "Galerina marginata (mortale)"), "Tossico da crudo: necessita di pre-bollitura prolungata (almeno 20 min) gettando l'acqua di cottura.", phenologyLatencyPeakDays = 10.0, phenologyShapeAlpha = 4.0),
        species("lactarius_deliciosus", "Lactarius deliciosus (L.) Gray", "Sanguinello / Fungo del pino", EcologicalCategory.ECTOMYCORRHIZAL, 200, 1700, 400, 1300, 10.0, 18.0, 6.0, 22.0, 25.0, listOf("pinus"), "Inizio autunno in pinete montane e collinari", listOf(8, 9, 10, 11), listOf("Lactarius torminosus (Peveraccio delle coliche, tossico grave)"), "Verificare lattice color carota/arancio immutabile o che vira al rosso/verde; scartare specie a lattice bianco acre.", phenologyLatencyPeakDays = 12.0, phenologyShapeAlpha = 4.0, optimalTemp = 13.5, optimalBasalAreaM2Ha = 20.0, hurdleStrictness = 1.0),
        species("morchella_esculenta", "Morchella esculenta (L.) Pers.", "Spugnola comune", EcologicalCategory.SAPROTROPHIC, 100, 1500, 200, 1000, 10.0, 18.0, 5.0, 22.0, 30.0, listOf("fraxinus", "ulmus", "populus", "radure", "frassino", "olmo"), "Fruttificazione primaverile (Marzo-Maggio) su suoli alcalini e radure", listOf(2, 3, 4), listOf("Gyromitra esculenta (Falsa spugnola, mortale da cruda, contiene giromitrina)"), "Tossica da cruda (contiene emolisine termolabili): necessita di cottura prolungata (almeno 20-25 min) o preventiva essiccazione.", phenologyLatencyPeakDays = 7.0, phenologyShapeAlpha = 4.0, optimalTemp = 14.0, optimalBasalAreaM2Ha = 15.0, hurdleStrictness = 0.5),
    )

    fun byId(id: String): MushroomSpecies = all.firstOrNull { it.id == id } ?: all.first()

    @Suppress("LongParameterList")
    private fun species(
        id: String,
        binomialName: String,
        vernacularName: String,
        category: EcologicalCategory,
        minElevation: Int,
        maxElevation: Int,
        idealElevationMin: Int,
        idealElevationMax: Int,
        idealTempMin: Double,
        idealTempMax: Double,
        toleratedTempMin: Double,
        toleratedTempMax: Double,
        minRainAccumulation: Double,
        preferredCanopyTypes: List<String>,
        fruitingPeriodDescription: String,
        activeMonths: List<Int>,
        toxicLookAlikes: List<String>,
        edibilityWarning: String?,
        phenologyLatencyPeakDays: Double = 11.0,
        phenologyShapeAlpha: Double = 4.0,
        optimalTemp: Double = (idealTempMin + idealTempMax) / 2.0,
        optimalBasalAreaM2Ha: Double = 32.0,
        hurdleStrictness: Double = 1.0,
    ) = MushroomSpecies(
        id, binomialName, vernacularName, category, minElevation, maxElevation,
        idealElevationMin, idealElevationMax, idealTempMin, idealTempMax,
        toleratedTempMin, toleratedTempMax, minRainAccumulation, preferredCanopyTypes,
        fruitingPeriodDescription, activeMonths, toxicLookAlikes, edibilityWarning,
        phenologyLatencyPeakDays, phenologyShapeAlpha, optimalTemp, optimalBasalAreaM2Ha,
        hurdleStrictness
    )
}
