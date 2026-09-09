package github.naturewhisp.myco.platform

/**
 * Posizione geografica live dell'utente con metadati di accuratezza.
 * 100% puro Kotlin senza alcuna dipendenza da android.* (pronto per CoreLocation su macOS).
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null
)

/**
 * Orientamento e azimut bussola del dispositivo (0..360 gradi rispetto al Nord).
 */
data class DeviceHeading(
    val azimuthDegrees: Float,
    val isReliable: Boolean = true
)

/**
 * Modalità di orientamento della mappa cartografica.
 */
enum class MapOrientationMode {
    /** Mappa fissa con il Nord verso l'alto (standard cartografico) */
    NORTH_UP,

    /** Mappa ruotata secondo la direzione in cui è puntato il dispositivo (bussola da campo) */
    HEADING_UP
}
