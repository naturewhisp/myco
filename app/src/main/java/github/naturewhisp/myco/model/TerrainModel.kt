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
    val isFlat: Boolean,
    val rawElevations: List<Float>? = null
) {
    /**
     * Restituisce i 5 campioni DEM [Centro, Nord, Sud, Est, Ovest] se disponibili, oppure li
     * ricostruisce matematicamente in modo esatto dal gradiente orografico (quota, pendenza, esposizione).
     */
    fun getOrSynthesizeRawElevations(deltaMeters: Double = 75.0): List<Double> {
        val raw = rawElevations
        if (!raw.isNullOrEmpty() && raw.size >= 5) {
            return raw.map { it.toDouble() }
        }
        if (isFlat || slopeDegrees < 0.1f) {
            val c = centerElevation.toDouble()
            return listOf(c, c, c, c, c)
        }
        val slopeRad = Math.toRadians(slopeDegrees.toDouble())
        val aspectRad = Math.toRadians(aspectDegrees.toDouble())
        val g = kotlin.math.tan(slopeRad)
        val dzdx = -g * kotlin.math.sin(aspectRad)
        val dzdy = -g * kotlin.math.cos(aspectRad)
        val zC = centerElevation.toDouble()
        val zN = zC + dzdy * deltaMeters
        val zS = zC - dzdy * deltaMeters
        val zE = zC + dzdx * deltaMeters
        val zW = zC - dzdx * deltaMeters
        return listOf(zC, zN, zS, zE, zW)
    }
}

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
