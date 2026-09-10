package github.naturewhisp.myco.model

/**
 * Scala canonica a 5 livelli per la probabilità di fruttificazione e accrescimento fungino (Herbarium).
 *
 * Costituisce l'unica fonte di verità (Single Source of Truth) per le soglie percentuali (20, 40, 60, 75, 100),
 * le etichette sintetiche e descrittive e i token cromatici minerali.
 *
 * @property tierIndex Indice ordinale del livello (0..4).
 * @property minProbability Soglia minima percentuale inclusa nel livello (0, 20, 40, 60, 75).
 * @property maxProbability Soglia massima percentuale inclusa nel livello (19, 39, 59, 74, 100).
 * @property shortLabel Etichetta sintetica compatta per badge e chip (es. "Inattivo", "Innesco", "Discreto", "Propizio", "Culmine").
 * @property descriptiveLabel Etichetta estesa per testate editoriali e responsi (es. "INATTIVO • CONDIZIONI SFAVOREVOLI").
 * @property colorTokenName Nome del token colore corrispondente nel design system Herbarium ("Scale0" .. "Scale4").
 * @property lightRgb Valore esadecimale RGB a 32-bit per il tema chiaro Naturalist.
 * @property darkRgb Valore esadecimale RGB a 32-bit per il tema scuro Nocturne.
 */
enum class ProbabilityTier(
    val tierIndex: Int,
    val minProbability: Int,
    val maxProbability: Int,
    val shortLabel: String,
    val descriptiveLabel: String,
    val colorTokenName: String,
    val lightRgb: Int,
    val darkRgb: Int
) {
    VERY_LOW(
        tierIndex = 0,
        minProbability = 0,
        maxProbability = 19,
        shortLabel = "Inattivo",
        descriptiveLabel = "INATTIVO • CONDIZIONI SFAVOREVOLI",
        colorTokenName = "Scale0",
        lightRgb = 0xEFE7D6,
        darkRgb = 0x3A3128
    ),
    LOW(
        tierIndex = 1,
        minProbability = 20,
        maxProbability = 39,
        shortLabel = "Innesco",
        descriptiveLabel = "EMERGENTE • INNESCO MICELIARE",
        colorTokenName = "Scale1",
        lightRgb = 0x4E9648,
        darkRgb = 0x62B058
    ),
    MODERATE(
        tierIndex = 2,
        minProbability = 40,
        maxProbability = 59,
        shortLabel = "Discreto",
        descriptiveLabel = "MODERATO • POTENZIALE DISCRETO",
        colorTokenName = "Scale2",
        lightRgb = 0xD49B24,
        darkRgb = 0xFAB22A
    ),
    HIGH(
        tierIndex = 3,
        minProbability = 60,
        maxProbability = 74,
        shortLabel = "Propizio",
        descriptiveLabel = "PROPIZIO • BUTTATA IN CORSO",
        colorTokenName = "Scale3",
        lightRgb = 0xC86430,
        darkRgb = 0xEB6E34
    ),
    VERY_HIGH(
        tierIndex = 4,
        minProbability = 75,
        maxProbability = 100,
        shortLabel = "Culmine",
        descriptiveLabel = "CULMINE • MASSIMA PROBABILITÀ",
        colorTokenName = "Scale4",
        lightRgb = 0x9E262C,
        darkRgb = 0xD8343E
    );

    companion object {
        /**
         * Determina il corrispondente [ProbabilityTier] a partire dal valore percentuale intero (0..100).
         *
         * @param probability Valore percentuale calcolato di probabilità.
         * @return [ProbabilityTier] corrispondente.
         */
        fun fromProbability(probability: Int): ProbabilityTier = when {
            probability < 20 -> VERY_LOW
            probability < 40 -> LOW
            probability < 60 -> MODERATE
            probability < 75 -> HIGH
            else -> VERY_HIGH
        }

        /**
         * Risolve il [ProbabilityTier] a partire dal suo indice ordinale 0..4.
         *
         * @param tier Indice ordinale del livello (0..4).
         * @return [ProbabilityTier] associato, con fallback su [VERY_HIGH] per indici superiori a 4.
         */
        fun fromTierIndex(tier: Int): ProbabilityTier = when (tier) {
            0 -> VERY_LOW
            1 -> LOW
            2 -> MODERATE
            3 -> HIGH
            else -> VERY_HIGH
        }
    }
}
