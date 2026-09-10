package github.naturewhisp.myco.model

/**
 * Identificatore univoco tassonomico di ciascun fattore ecologico, climatico o fenologico monitorato.
 */
enum class FactorId {
    /** Regime termico medio recente (ultimi 5 giorni). */
    TEMPERATURE,
    /** Idratazione da pioggia cumulata (finestra 10..2 giorni fa). */
    PRECIPITATION,
    /** Umidità relativa aria/suolo recente (ultimi 3 giorni). */
    HUMIDITY,
    /** Copertura vegetazionale e presenza di essenze arboree simbionti (OSM). */
    HABITAT,
    /** Compatibilità della fascia altimetrica con il profilo della specie. */
    ALTITUDE,
    /** Corrispondenza del mese corrente con il periodo fenologico attivo. */
    SEASONALITY,
    /** Stadio di accrescimento miceliare (induzione primordiale, buttata o stasi). */
    MYCELIAL_PHASE,
    /** Fase lunare e relativa influenza storica/popolare. */
    LUNAR_PHASE,
    /** Pendenza orografica ed esposizione del versante (*solatìo* vs *bacìo*). */
    SLOPE,
    /** Ricchezza tassonomica di funghi ectomicorrizici nel suolo (SPUN). */
    SPUN_ECM,
    /** Densità della biomassa ifale sotterranea in m/cm³ (SPUN). */
    SPUN_HYPHAL
}

/**
 * Giudizio qualitativo e cromatico del singolo fattore ecologico.
 */
enum class FactorLevel {
    /** Condizione favorevole o ideale (+ premiale). */
    FAVORABLE,
    /** Condizione neutra o tollerabile. */
    NEUTRAL,
    /** Condizione avversa o penalizzante. */
    ADVERSE,
    /** Informazione descrittiva o orografica neutra priva di penalità. */
    INFORMATIVE
}

/**
 * Modello tipizzato per un fattore analitico di crescita rappresentato nel registro Herbarium.
 *
 * @property id Identificatore tassonomico del fattore [FactorId].
 * @property label Titolo o etichetta del fattore (es. "PRECIPITAZIONI", "HABITAT").
 * @property formattedValue Valore compatto formattato per tabella (es. "38 mm", "Castagno, Faggio").
 * @property level Livello qualitativo [FactorLevel] per la colorazione del badge.
 * @property detail Descrizione analitica o nota biologica di dettaglio.
 * @property iconGlyph Glifo tipografico opzionale o simbolo testuale.
 */
data class Factor(
    val id: FactorId,
    val label: String,
    val formattedValue: String,
    val level: FactorLevel,
    val detail: String,
    val iconGlyph: String? = null
)
