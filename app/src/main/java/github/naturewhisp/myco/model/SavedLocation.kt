package github.naturewhisp.myco.model

data class SavedLocation(
    val lat: Double,
    val lon: Double,
    val displayName: String,         // nome completo Nominatim
    val shortName: String,           // primo segmento prima della virgola, per chip
    val savedAt: Long,               // timestamp ultimo utilizzo
    val isFavorite: Boolean = false,
    val isGpsLocation: Boolean = false  // true = proveniente da geolocalizzazione GPS
)
