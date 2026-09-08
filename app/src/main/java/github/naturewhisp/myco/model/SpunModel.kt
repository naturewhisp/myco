package github.naturewhisp.myco.model

/**
 * Risultato delle metriche micorriziche SPUN aggregate per un'area geografica.
 */
data class SpunData(
    val ecmRichness: Float,
    val hyphalDensity: Float,
    val ecmScore: Double,
    val hyphalScore: Double,
    val ecmText: String,
    val hyphalText: String,
    val regionCode: String,
    val regionName: String,
    val ecmQualityLabel: String = "Ideale",
    val hyphalVitalityLabel: String = "Attiva"
)

/**
 * Metadati letti dall'header binario standard di una regione SPUN (32 byte).
 */
data class SpunRegionHeader(
    val magic: String,
    val version: Int,
    val regionCode: String,
    val minLat: Float,
    val maxLat: Float,
    val minLon: Float,
    val maxLon: Float,
    val width: Int,
    val height: Int,
    val stepArcSec: Int
)

/**
 * Descrittore di una regione SPUN supportata per l'on-demand loading.
 */
data class SpunRegionDescriptor(
    val regionCode: String,
    val displayName: String,
    val assetFileName: String,
    val minLat: Float,
    val maxLat: Float,
    val minLon: Float,
    val maxLon: Float
) {
    fun contains(lat: Double, lon: Double): Boolean {
        return lat in minLat..maxLat && lon in minLon..maxLon
    }
}
