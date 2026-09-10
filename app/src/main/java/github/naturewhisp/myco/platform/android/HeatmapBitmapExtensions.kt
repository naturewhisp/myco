package github.naturewhisp.myco.platform.android

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import github.naturewhisp.myco.model.HeatmapRaster

/**
 * Rappresenta una superficie raster georeferenziata (mappa di calore / nuvola di probabilità)
 * con i suoi limiti geografici (bounding box WGS84) e [Bitmap] nativa per OsmDroid su Android.
 *
 * Disaccoppia la presentazione cartografica Android dal modello di dominio puro [HeatmapRaster].
 *
 * @property bitmap Bitmap ARGB_8888 pronta per essere disegnata come overlay cartografico.
 * @property north Limite settentrionale WGS84 in gradi decimali.
 * @property south Limite meridionale WGS84 in gradi decimali.
 * @property west Limite occidentale WGS84 in gradi decimali.
 * @property east Limite orientale WGS84 in gradi decimali.
 * @property raster Buffer raster puro di origine [HeatmapRaster], se disponibile.
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
 * Converte un [HeatmapRaster] puro nel formato [Bitmap] nativo Android tramite bitwise pixel array.
 *
 * @return [Bitmap] con configurazione ARGB_8888 e dimensioni pari a quelle del raster.
 */
fun HeatmapRaster.toBitmap(): Bitmap {
    val bmp = createBitmap(width, height)
    bmp.setPixels(argbPixels, 0, width, 0, 0, width, height)
    return bmp
}

/**
 * Adatta un [HeatmapRaster] di puro dominio in [HeatmapData] per il rendering cartografico su OsmDroid.
 *
 * @return Istanza georeferenziata [HeatmapData] contenente la [Bitmap] generata.
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
