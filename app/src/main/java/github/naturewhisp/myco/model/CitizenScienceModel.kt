package github.naturewhisp.myco.model

/**
 * Modelli di dominio e strutture dati per il sottosistema Citizen Science e la modellazione
 * predittiva DeepMaxent con correzione Target-Group Background (TGB) su griglia Uber H3 (Risoluzione 7).
 *
 * Questa architettura disaccoppia la raccolta opportunistica di dati biologici sul campo
 * dall'esatta localizzazione geografica, implementando la privacy differenziale spaziale
 * e compensando matematicamente il bias di accessibilità dei foraggiatori (Ryckewaert et al., 2024).
 */

/**
 * Stadio fenologico del carpoforo fungino rilevato durante il campionamento.
 *
 * @property code Identificativo testuale serializzabile.
 * @property displayName Descrizione sintetica per l'interfaccia utente.
 * @property description Dettaglio biologico dello stadio morfologico.
 * @property weightMultiplier Moltiplicatore di verosimiglianza per l'aggiornamento bayesiano della buttata.
 */
enum class PhenologicalStage(
    val code: String,
    val displayName: String,
    val description: String,
    val weightMultiplier: Double
) {
    /**
     * Primordio o carpoforo giovanissimo a cappello chiuso o emisferico (diametro <= 4 cm).
     * Indicatore inequivocabile di buttata nascente e imminente picco riproduttivo.
     */
    FRESH_BUTTON(
        code = "FRESH_BUTTON",
        displayName = "Bottone Fresco (Giovane)",
        description = "Carpoforo a cappello chiuso, sodo, imenio immaturo. Segnale di buttata in corso.",
        weightMultiplier = 1.20
    ),

    /**
     * Esemplare adulto a cappello espanso con imenio fertile maturo e spore in dispersione attiva.
     */
    MATURE_EXPANDED(
        code = "MATURE_EXPANDED",
        displayName = "Adulto Formato",
        description = "Cappello completamente aperto ed espanso, imenio maturo in piena sporulazione.",
        weightMultiplier = 1.00
    ),

    /**
     * Esemplare senescente, spugnoso o in fase di rammollimento/decomposizione post-maturazione.
     * Indica il termine o la coda conclusiva della buttata termica/idrica.
     */
    OVERRIPE_SENESCENT(
        code = "OVERRIPE_SENESCENT",
        displayName = "Senescente / Sfatto",
        description = "Carpoforo oltre maturazione, polpa spugnosa o degradata. Buttata verso esaurimento.",
        weightMultiplier = 0.70
    );

    companion object {
        /**
         * Risolve lo stadio fenologico da codice testuale con fallback su [MATURE_EXPANDED].
         */
        fun fromCode(code: String?): PhenologicalStage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: MATURE_EXPANDED
        }
    }
}

/**
 * Livello di verifica e affidabilità tassonomica associato all'osservazione.
 *
 * @property code Identificativo del livello di verifica.
 * @property displayName Etichetta per l'utente e il registro scientifico.
 * @property reliabilityWeight Peso di confidenza probabilistica (0.0..1.0).
 */
enum class VerificationTier(
    val code: String,
    val displayName: String,
    val reliabilityWeight: Double
) {
    /**
     * Esemplare convalidato on-device tramite Google AI Edge Gemini Nano / AICore (confidenza >= 0.85).
     */
    ON_DEVICE_AI_CONFIRMED(
        code = "ON_DEVICE_AI_CONFIRMED",
        displayName = "Validato da AI On-Device",
        reliabilityWeight = 0.85
    ),

    /**
     * Segnalazione inserita manualmente dal raccoglitore senza validazione fotografica diretta.
     */
    MANUAL_UNCONFIRMED(
        code = "MANUAL_UNCONFIRMED",
        displayName = "Segnalazione Manuale",
        reliabilityWeight = 0.40
    ),

    /**
     * Osservazione confermata ex-post da un micologo accreditato o esperto della gilda.
     */
    EXPERT_VERIFIED(
        code = "EXPERT_VERIFIED",
        displayName = "Confermato da Esperto",
        reliabilityWeight = 1.00
    );

    companion object {
        fun fromCode(code: String?): VerificationTier {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: MANUAL_UNCONFIRMED
        }
    }
}

/**
 * Fascia altimetrica discretizzata a passi di 400 metri.
 * Garantisce la privacy differenziale dell'esatta quota barometrica o altimetrica DEM.
 *
 * @property minElevation Quota minima in metri sul livello del mare.
 * @property maxElevation Quota massima in metri sul livello del mare.
 * @property label Etichetta sintetica dell'intervallo.
 * @property description Orizzonte vegetazionale corrispondente.
 */
enum class ElevationBracket(
    val minElevation: Int,
    val maxElevation: Int,
    val label: String,
    val description: String
) {
    SUB_MONTANE_0_400(0, 400, "0-400 m", "Pianura e bassa collina"),
    LOW_MONTANE_400_800(400, 800, "400-800 m", "Media collina e fascia basale"),
    MID_MONTANE_800_1200(800, 1200, "800-1200 m", "Fascia montana inferiore (castagno/faggio)"),
    HIGH_MONTANE_1200_1600(1200, 1600, "1200-1600 m", "Fascia montana superiore (abete/faggio)"),
    SUB_ALPINE_1600_2000(1600, 2000, "1600-2000 m", "Fascia subalpina (larice/pino cembro)"),
    ALPINE_ABOVE_2000(2000, 4500, ">2000 m", "Prateria alpina e brughiera d'altitudine");

    companion object {
        /**
         * Mappa una quota continua in metri nella corrispondente fascia discretizzata.
         */
        fun fromElevation(meters: Double): ElevationBracket {
            val clamped = meters.coerceAtLeast(0.0)
            return entries.firstOrNull { clamped >= it.minElevation && clamped < it.maxElevation }
                ?: ALPINE_ABOVE_2000
        }
    }
}

/**
 * Categoria della copertura vegetazionale dominante associata alla cella o all'osservazione.
 *
 * @property displayName Nome leggibile dell'associazione forestale.
 * @property description Descrizione fitosociologica.
 */
enum class CanopyCategory(
    val displayName: String,
    val description: String
) {
    DECIDUOUS_BEECH("Faggeta", "Bosco puro o prevalente di Fagus sylvatica"),
    DECIDUOUS_CHESTNUT("Castagneto", "Bosco ceduo o d'alto fusto di Castanea sativa"),
    DECIDUOUS_OAK("Querceto", "Bosco termofilo o mesofilo di Quercus robur / petraea / cerris"),
    CONIFEROUS_SPRUCE_FIR("Abetina / Piceeta", "Foresta di conifere montane (Picea abies / Abies alba)"),
    CONIFEROUS_PINE("Pineta", "Pineta a Pinus sylvestris / Pinus nigra / Pinus pinaster"),
    MIXED_WOODLAND("Bosco Misto", "Formazione mista latifoglie e conifere"),
    OPEN_MEADOW("Prato / Radura", "Prateria aperta o pascolo per specie saprotrofe"),
    RIPARIAN_SHRUB("Ripario / Macchia", "Fascia ripariale o macchia arbustiva umida");

    companion object {
        fun fromName(name: String?): CanopyCategory {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MIXED_WOODLAND
        }
    }
}

/**
 * Payload anonimizzato di sottomissione di un ritrovamento micologico sul campo.
 * Rispetta la privacy differenziale spaziale e la distruzione locale istantanea delle coordinate WGS84.
 *
 * @property h3Index Indice esagonale Uber H3 a Risoluzione 7 (~5.16 km² di area).
 * @property speciesId Identificativo univoco della specie nel catalogo Myco (es. "boletus_edulis").
 * @property phenologicalStage Stadio fenologico rilevato (bottone, maturo, senescente).
 * @property elevationBracket Fascia altimetrica discretizzata (passo 400 m).
 * @property canopyCategory Categoria forestale della stazione.
 * @property timestampEpochDay Giorni trascorsi dall'epoca Unix (troncamento dell'orario preciso).
 * @property verificationTier Livello di affidabilità della segnalazione.
 * @property entropyNonce Valore casuale per impedire attacchi di correlazione o replay.
 */
data class MushroomSightingSubmission(
    val h3Index: String,
    val speciesId: String,
    val phenologicalStage: PhenologicalStage,
    val elevationBracket: ElevationBracket,
    val canopyCategory: CanopyCategory,
    val timestampEpochDay: Long,
    val verificationTier: VerificationTier,
    val entropyNonce: String
)

/**
 * Sito di background del gruppo target (Target-Group Background, TGB) utilizzato
 * in DeepMaxent per neutralizzare il bias di campionamento e accessibilità antropica.
 *
 * In conformità con Ryckewaert et al. (2024), il background è campionato unicamente da celle H3
 * in cui è stata riscontrata la presenza di almeno una specie della gilda dei macromiceti epigei.
 * Poiché il pattern di ricerca dei fungaioli è identico tra specie della stessa gilda,
 * il campionamento TGB elimina matematicamente la funzione di accessibilità s(x).
 *
 * @property h3Index Indice esagonale H3 della cella di background.
 * @property recordedSpeciesIds Insieme degli identificativi tassonomici osservati in questa cella.
 * @property environmentalVector Vettore numerico normalizzato delle covariate ambientali ed ecologiche
 *   (precipitazione 26gg, temperatura 20gg, umidità suolo van Genuchten, canopy cover, pendenza, SPUN, ecc.).
 */
data class TargetGroupBackgroundSite(
    val h3Index: String,
    val recordedSpeciesIds: Set<String>,
    val environmentalVector: FloatArray
) {
    /**
     * Verifica se il sito possiede almeno una presenza documentata della gilda micologica.
     */
    fun hasGuildPresence(): Boolean = recordedSpeciesIds.isNotEmpty()

    /**
     * Verifica se la specie specificata è presente in questo sito.
     */
    fun containsSpecies(speciesId: String): Boolean = recordedSpeciesIds.contains(speciesId)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TargetGroupBackgroundSite

        if (h3Index != other.h3Index) return false
        if (recordedSpeciesIds != other.recordedSpeciesIds) return false
        if (!environmentalVector.contentEquals(other.environmentalVector)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = h3Index.hashCode()
        result = 31 * result + recordedSpeciesIds.hashCode()
        result = 31 * result + environmentalVector.contentHashCode()
        return result
    }
}

/**
 * Risultato dell'inferenza predittiva DeepMaxent per una specifica cella esagonale H3 e taxon target.
 *
 * @property h3Index Indice esagonale H3 a risoluzione 7.
 * @property speciesId Specie fungina target.
 * @property intensityScore Intensità stimata del Processo di Poisson Inomogeneo (lambda_j(x)).
 * @property calibratedProbability Probabilità di occorrenza/fruttificazione ricalibrata (0.0..100.0).
 * @property confidenceIntervalLower Limite inferiore dell'intervallo di confidenza al 95%.
 * @property confidenceIntervalUpper Limite superiore dell'intervallo di confidenza al 95%.
 */
data class DeepMaxentPrediction(
    val h3Index: String,
    val speciesId: String,
    val intensityScore: Double,
    val calibratedProbability: Double,
    val confidenceIntervalLower: Double,
    val confidenceIntervalUpper: Double
)

/**
 * Configurazione degli iperparametri architetturali e di addestramento per DeepMaxent
 * derivati dalla letteratura scientifica di riferimento (Ryckewaert et al., 2024).
 *
 * @property batchSize Dimensione dei mini-batch stocastici (|B| = 100..250).
 *   Un batch size compatto agisce da regolarizzatore spaziale naturale nella funzione di partizione.
 * @property weightDecayL2 Coefficiente di regolarizzazione L2 (tau = 3e-4).
 * @property latentDimensions Dimensione dello spazio latente estratto dal Residual MLP (C = 64).
 * @property spatialCvFolds Numero di partizioni per la validazione incrociata a blocchi spaziali (10-fold).
 * @property h3Resolution Risoluzione della griglia Uber H3 (default 7, ~5.16 km²).
 * @property learningRate Passo di apprendimento iniziale dell'ottimizzatore (es. AdamW).
 */
data class DeepMaxentModelConfig(
    val batchSize: Int = 128,
    val weightDecayL2: Double = 3e-4,
    val latentDimensions: Int = 64,
    val spatialCvFolds: Int = 10,
    val h3Resolution: Int = 7,
    val learningRate: Double = 1e-3
)
