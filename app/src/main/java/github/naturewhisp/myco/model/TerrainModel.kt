package github.naturewhisp.myco.model

import com.google.gson.annotations.SerializedName

/**
 * Risposta dall'API di elevazione batch di Open-Meteo per campionamento DEM a 5 punti.
 *
 * @property elevation Lista delle elevazioni restituite in metri s.l.m. [Centro, Nord, Sud, Est, Ovest].
 */
data class ElevationResponse(
    @SerializedName("elevation") val elevation: List<Float>
)

/**
 * Dati orografici calcolati dal gradiente altimetrico del terreno tramite differenze finite.
 *
 * @property centerElevation Quota altimetrica al centro del campionamento in metri s.l.m.
 * @property slopeDegrees Inclinazione del versante rispetto al piano orizzontale in gradi sessagesimali.
 * @property slopePercent Pendenza del terreno espressa in percentuale (rapporto dislivello/distanza).
 * @property aspectDegrees Azimut dell'esposizione del versante (0° = Nord, 90° = Est, 180° = Sud, 270° = Ovest).
 * @property cardinalDirection Nome italiano esteso dell'esposizione (es. "Nord", "Sud-Est", "Pianeggiante").
 * @property cardinalAbbreviation Sigla cardinale compatta (es. "N", "SE", "Pian").
 * @property isFlat Flag indicante se il terreno ha pendenza trascurabile (altopiano o pianura).
 */
data class TerrainAspectData(
    val centerElevation: Float,
    val slopeDegrees: Float,
    val slopePercent: Float,
    val aspectDegrees: Float,
    val cardinalDirection: String,
    val cardinalAbbreviation: String,
    val isFlat: Boolean
)

/**
 * Valutazione ecologica dell'esposizione e pendenza rispetto alle esigenze biologiche della specie e alla stagione.
 *
 * @property terrain Modello orografico di origine [TerrainAspectData], o null in caso di fallback.
 * @property level Giudizio qualitativo e cromatico Herbarium [FactorLevel].
 * @property formattedValue Testo compatto formattato per la riga del fattore (es. "18° SO").
 * @property detail Descrizione ecologica estesa del microclima del versante.
 * @property modifier Moltiplicatore probabilistico continuo (0.50..1.10) applicato alla probabilità complessiva.
 */
data class TerrainAspectEvaluation(
    val terrain: TerrainAspectData?,
    val level: FactorLevel,
    val formattedValue: String,
    val detail: String,
    val modifier: Double = 1.0
)
