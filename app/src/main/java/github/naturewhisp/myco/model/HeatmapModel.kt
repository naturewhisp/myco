package github.naturewhisp.myco.model

/**
 * Buffer raster georeferenziato 100% puro Kotlin (agnostico dalla piattaforma).
 *
 * Contiene i pixel di colore a 32-bit (ARGB_8888) e il bounding box geografico WGS84.
 * Privo di qualsiasi dipendenza da `android.*`, può essere convertito in un `android.graphics.Bitmap`
 * su Android o in un `NSImage` / Skia `ImageBitmap` su macOS.
 *
 * @property argbPixels Array primitivo monodimensionale contenente i valori di colore ARGB a 32-bit.
 * @property width Larghezza della griglia raster in celle/pixel.
 * @property height Altezza della griglia raster in celle/pixel.
 * @property north Limite settentrionale del bounding box in gradi WGS84.
 * @property south Limite meridionale del bounding box in gradi WGS84.
 * @property west Limite occidentale del bounding box in gradi WGS84.
 * @property east Limite orientale del bounding box in gradi WGS84.
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
