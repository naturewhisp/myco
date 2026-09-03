package github.naturewhisp.myco.model

import android.graphics.Bitmap

/**
 * Rappresenta una superficie raster georeferenziata (mappa di calore / nuvola di probabilità)
 * con i suoi limiti geografici (bounding box WGS84).
 */
data class HeatmapData(
    val bitmap: Bitmap,
    val north: Double,
    val south: Double,
    val west: Double,
    val east: Double
)
