package github.naturewhisp.myco.model

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap

/**
 * Buffer raster georeferenziato 100% puro Kotlin (agnostico dalla piattaforma).
 * Contiene i pixel di colore a 32-bit (ARGB_8888) e il bounding box geografico WGS84.
 * Può essere convertito in un `android.graphics.Bitmap` su Android o in un `NSImage` / Skia `ImageBitmap` su macOS.
 */
data class HeatmapRaster(
    val argbPixels: IntArray,
    val width: Int,
    val height: Int,
    val north: Double,
    val south: Double,
    val west: Double,
    val east: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HeatmapRaster) return false

        if (!argbPixels.contentEquals(other.argbPixels)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (north != other.north) return false
        if (south != other.south) return false
        if (west != other.west) return false
        if (east != other.east) return false

        return true
    }

    override fun hashCode(): Int {
        var result = argbPixels.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + north.hashCode()
        result = 31 * result + south.hashCode()
        result = 31 * result + west.hashCode()
        result = 31 * result + east.hashCode()
        return result
    }
}

/**
 * Rappresenta una superficie raster georeferenziata (mappa di calore / nuvola di probabilità)
 * con i suoi limiti geografici (bounding box WGS84) per OsmDroid.
 */
data class HeatmapData(
    val bitmap: Bitmap,
    val north: Double,
    val south: Double,
    val west: Double,
    val east: Double,
    val raster: HeatmapRaster? = null
)

/**
 * Converte un [HeatmapRaster] puro nel formato [Bitmap] Android.
 */
fun HeatmapRaster.toBitmap(): Bitmap {
    val bmp = createBitmap(width, height)
    bmp.setPixels(argbPixels, 0, width, 0, 0, width, height)
    return bmp
}

/**
 * Adatta un [HeatmapRaster] in [HeatmapData] per il rendering su Android / OsmDroid.
 */
fun HeatmapRaster.toHeatmapData(): HeatmapData {
    return HeatmapData(
        bitmap = toBitmap(),
        north = north,
        south = south,
        west = west,
        east = east,
        raster = this
    )
}
