package github.naturewhisp.myco.model

/**
 * Stato di certezza dell'evidenza vegetazionale e territoriale OSM.
 *
 * Distingue rigorosamente contesti idonei verificati, ambienti incompatibili
 * e assenza di copertura/dati (F08).
 */
enum class HabitatStatus {
    /** Presenza confermata di bosco, foresta, pascolo o radura biologicamente compatibile. */
    KNOWN_SUITABLE,

    /** Presenza confermata di contesto artificiale, urbano, industriale o specchio d'acqua. */
    KNOWN_UNSUITABLE,

    /** Nessun dato OSM o copertura geografica disponibile (offline, timeout, fuori copertura). */
    UNKNOWN
}

/**
 * Evidenza vegetazionale, strutturale e tassonomica estratta dall'intorno geografico.
 *
 * Supera il semplice conteggio scalare dei nodi OSM rappresentando coperture relative,
 * distanze geometriche e generi arborei effettivamente identificati.
 *
 * @property status Stato di classificazione dell'ambiente stazionale circostante.
 * @property forestCoverFraction Frazione stimata di copertura boschiva nell'area [0.0, 1.0].
 * @property meadowFraction Frazione stimata di copertura a prato/pascolo/radura [0.0, 1.0].
 * @property distanceToNearestForestMeters Distanza in metri dall'elemento boschivo più vicino (0.0 se immerso).
 * @property confirmedHostGenera Generi arborei confermati esplicitamente dai tag OSM (es. "Fagus", "Quercus").
 * @property dominantLeafType Tipologia fogliare prevalente ("broadleaved", "needleleaved", "mixed", null).
 */
data class HabitatEvidence(
    val status: HabitatStatus,
    val forestCoverFraction: Double,
    val meadowFraction: Double,
    val distanceToNearestForestMeters: Double,
    val confirmedHostGenera: Set<String>,
    val dominantLeafType: String? = null
) {
    companion object {
        /**
         * Istanza di fallback quando i dati geografici OSM non sono disponibili o non recuperabili.
         */
        val UNKNOWN_HABITAT = HabitatEvidence(
            status = HabitatStatus.UNKNOWN,
            forestCoverFraction = 0.50,
            meadowFraction = 0.30,
            distanceToNearestForestMeters = 500.0,
            confirmedHostGenera = emptySet(),
            dominantLeafType = null
        )
    }
}
