package github.naturewhisp.myco.model

import java.util.Locale

/**
 * Entità persistente per la gestione delle località preferite e della cronologia recente.
 *
 * Supporta la ridenominazione personalizzata dell'utente (soprannome del punto di raccolta)
 * e la distinzione tra punti da geolocalizzazione GPS e ricerche toponomastiche manuali.
 *
 * @property lat Latitudine WGS84 in gradi decimali.
 * @property lon Longitudine WGS84 in gradi decimali.
 * @property displayName Toponimo completo restituito dal geocoding.
 * @property shortName Nome compatto per visualizzazione in chip (primo segmento toponomastico).
 * @property savedAt Timestamp Unix dell'ultimo accesso o salvataggio in millisecondi.
 * @property isFavorite Flag che indica se la località è contrassegnata come preferita.
 * @property isGpsLocation Flag che indica se la posizione è stata acquisita via sensore GPS live.
 * @property customName Nome personalizzato opzionale assegnato dall'utente.
 */
data class SavedLocation(
    val lat: Double,
    val lon: Double,
    val displayName: String,
    val shortName: String,
    val savedAt: Long,
    val isFavorite: Boolean = false,
    val isGpsLocation: Boolean = false,
    val customName: String? = null
) {
    /**
     * Restituisce il nome effettivo da mostrare: [customName] se presente e non vuoto, altrimenti [shortName].
     */
    val effectiveName: String
        get() = customName?.takeIf { it.isNotBlank() } ?: shortName

    companion object {
        /**
         * Verifica se il toponimo è un segnaposto temporaneo di sistema o un nome geografico reale.
         *
         * @param name Nome o etichetta da testare.
         * @return True se il nome è un placeholder provvisorio, False se toponimo effettivo.
         */
        fun isPlaceholderName(name: String?): Boolean {
            if (name.isNullOrBlank()) return true
            val lower = name.lowercase(Locale.US).trim()
            return lower == "punto selezionato" ||
                   lower == "punto cartografico" ||
                   lower == "posizione gps" ||
                   lower == "localizzazione in corso..." ||
                   lower == "determinazione località..." ||
                   lower.startsWith("punto selezionato") ||
                   lower.startsWith("punto cartografico") ||
                   lower.startsWith("posizione gps") ||
                   lower.startsWith("localizzazione in corso")
        }
    }
}
