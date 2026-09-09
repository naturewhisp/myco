package github.naturewhisp.myco.model

import java.util.Locale

data class SavedLocation(
    val lat: Double,
    val lon: Double,
    val displayName: String,         // nome completo Nominatim
    val shortName: String,           // primo segmento prima della virgola, per chip
    val savedAt: Long,               // timestamp ultimo utilizzo
    val isFavorite: Boolean = false,
    val isGpsLocation: Boolean = false,  // true = proveniente da geolocalizzazione GPS
    val customName: String? = null       // nome personalizzato opzionale assegnato dall'utente
) {
    val effectiveName: String
        get() = customName?.takeIf { it.isNotBlank() } ?: shortName

    companion object {
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
