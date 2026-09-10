package github.naturewhisp.myco.model

/**
 * Configurazione tipizzata per l'analisi orografica, il calcolo della pendenza ed esposizione (DEM a 5 punti).
 *
 * Incapsula la distanza di campionamento delle differenze finite, le soglie angolari di pendenza
 * (pianura vs forte pendenza) e i coefficienti moltiplicativi dell'esposizione del versante
 * (*solatìo* vs *bacìo*) in relazione alla termofilia della specie e al regime climatico stagionale.
 *
 * @property deltaMeters Distanza cartesiana in metri per il campionamento altimetrico DEM centrate (default 75.0 m).
 * @property flatSlopeThresholdDegrees Pendenza massima in gradi al di sotto della quale il terreno è considerato pianeggiante (default 3.0°).
 * @property steepSlopeThresholdDegrees Pendenza critica oltre la quale il ruscellamento riduce il potenziale miceliare (default 38.0°).
 * @property steepSlopePenaltyMax Moltiplicatore massimo consentito su versanti ripidi (default 0.92, penalità minima -8%).
 * @property favorableMultiplier Coefficiente premiale per esposizione solare o fresca ottimale (default 1.05, +5%).
 * @property adverseMultiplier Coefficiente penalizzante per esposizione sfavorevole (default 0.90, -10%).
 * @property morningSunMultiplier Coefficiente premiale per soleggiamento mattutino a Est nei periodi temperati (default 1.03, +3%).
 * @property moderateSunMultiplier Coefficiente premiale per soleggiamento moderato a Sud nei periodi temperati (default 1.02, +2%).
 */
data class TerrainAspectConfig(
    val deltaMeters: Double = 75.0,
    val flatSlopeThresholdDegrees: Float = 3.0f,
    val steepSlopeThresholdDegrees: Float = 38.0f,
    val steepSlopePenaltyMax: Double = 0.92,
    val favorableMultiplier: Double = 1.05,
    val adverseMultiplier: Double = 0.90,
    val morningSunMultiplier: Double = 1.03,
    val moderateSunMultiplier: Double = 1.02
) {
    companion object {
        /**
         * Istanza predefinita con i parametri orografici standard di Myco.
         */
        val DEFAULT = TerrainAspectConfig()
    }
}
