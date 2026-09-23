package github.naturewhisp.myco.model

/**
 * Configurazione tipizzata dei pesi ecologici e parametri climatici per il calcolo meteorologico.
 *
 * I pesi standard sono calibrati secondo la formula canonica descritta in AGENTS.md:
 * - Pioggia (idratazione del substrato): 40.0%
 * - Temperatura media recente (regime termico): 30.0%
 * - Umidità relativa (interfaccia lettiera-aria): 15.0%
 * - Shock termico induttivo (differenziale termico negativo post-pioggia): 15.0%
 *
 * La ponderazione finale del meteo adotta una risposta convessa non-lineare con esponente 1.2:
 * $$W_{ponderato} = 100 \times \left(\frac{W}{100}\right)^{1.2}$$
 *
 * @property rainWeight Peso relativo massimo del fattore precipitazioni (default 40.0).
 * @property tempWeight Peso relativo massimo del fattore temperatura (default 30.0).
 * @property humidityWeight Peso relativo massimo dell'umidità relativa (default 15.0).
 * @property thermalShockWeight Peso relativo massimo dello shock termico induttivo (default 15.0).
 * @property weatherExponent Esponente non-lineare di penalizzazione per meteo mediocre (default 1.2).
 * @property rainWindowDays Giorni complessivi della finestra di precipitazione cumulata passata (default 10).
 * @property rainLagDays Giorni di latenza iniziale esclusi dal cumulo pioggia recente (default 2).
 * @property tempWindowDays Giorni della finestra temporale per la media termica (default 5).
 * @property humidityWindowDays Giorni della finestra temporale per l'umidità relativa recente (default 3).
 * @property minRainForShockMm Precipitazione minima cumulata per abilitare l'innesco da shock termico (default 12.0 mm).
 * @property standardThermalDropMin Soglia minima di calo termico per innescare lo shock in condizioni standard (default 3.0°C).
 * @property spunAssistedThermalDropMin Soglia agevolata di calo termico con alta biomassa miceliare SPUN (default 2.0°C).
 * @property shockDropSaturationSpan Intervallo di saturazione dell'ampiezza del calo termico (default 3.0°C).
 * @property shockRainSaturationMm Valore di pioggia cumulata di saturazione per lo shock termico (default 25.0 mm).
 */
data class EcologicalWeightsConfig(
    val rainWeight: Double = 40.0,
    val tempWeight: Double = 30.0,
    val humidityWeight: Double = 15.0,
    val thermalShockWeight: Double = 15.0,
    val weatherExponent: Double = 1.2,
    val rainWindowDays: Int = 10,
    val rainLagDays: Int = 2,
    val tempWindowDays: Int = 5,
    val humidityWindowDays: Int = 3,
    val minRainForShockMm: Double = 12.0,
    val standardThermalDropMin: Double = 3.0,
    val spunAssistedThermalDropMin: Double = 2.0,
    val shockDropSaturationSpan: Double = 3.0,
    val shockRainSaturationMm: Double = 25.0,
    val usePhenologicalInertia: Boolean = false,
    val probabilityKneeThreshold: Double = 70.0,
    val probabilityMaxAsymptote: Double = 92.0,
    val suitabilityKneeThreshold: Double = probabilityKneeThreshold,
    val suitabilityMaxAsymptote: Double = probabilityMaxAsymptote
) {
    init {
        require(rainWeight >= 0.0 && tempWeight >= 0.0 && humidityWeight >= 0.0 && thermalShockWeight >= 0.0) {
            "I pesi meteorologici non possono essere negativi"
        }
        require(rainWindowDays > 0 && rainLagDays >= 0 && rainLagDays < rainWindowDays) {
            "Finestre temporali di precipitazione non valide: window=$rainWindowDays, lag=$rainLagDays"
        }
        require(tempWindowDays > 0 && humidityWindowDays > 0) {
            "Finestre termiche o igrometriche devono essere strettamente positive"
        }
        require(suitabilityKneeThreshold in 0.0..100.0 && suitabilityMaxAsymptote >= suitabilityKneeThreshold) {
            "Soglie asintotiche non valide: knee=$suitabilityKneeThreshold, max=$suitabilityMaxAsymptote"
        }
    }

    companion object {
        /**
         * Istanza predefinita baseline con finestra rigida [10 gg - 2 gg].
         */
        val DEFAULT = EcologicalWeightsConfig(usePhenologicalInertia = false)

        /**
         * Configurazione biologica avanzata basata su inerzia fenologica continua f(tau).
         */
        val PHENOLOGICAL = EcologicalWeightsConfig(usePhenologicalInertia = true)
    }
}
