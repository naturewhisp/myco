package github.naturewhisp.myco.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import github.naturewhisp.myco.platform.android.HeatmapData
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Overlay per OsmDroid che renderizza la nuvola di calore / probabilità raster
 * con georeferenziazione precisa e filtraggio bilineare morbido.
 */
class HeatmapOverlay(
    var heatmapData: HeatmapData? = null
) : Overlay() {

    private val paint = Paint().apply {
        isFilterBitmap = true // Bilinear filtering per l'effetto nuvola sfumata tipo radar meteo
        isAntiAlias = true
    }

    private val topLeftPoint = Point()
    private val bottomRightPoint = Point()
    private val dstRect = Rect()

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        if (shadow || canvas == null || mapView == null) return
        val data = heatmapData
        if (data == null) {
            return
        }

        val projection = mapView.projection
        val northWest = GeoPoint(data.north, data.west)
        val southEast = GeoPoint(data.south, data.east)

        projection.toPixels(northWest, topLeftPoint)
        projection.toPixels(southEast, bottomRightPoint)

        dstRect.set(topLeftPoint.x, topLeftPoint.y, bottomRightPoint.x, bottomRightPoint.y)

        canvas.drawBitmap(data.bitmap, null, dstRect, paint)
    }
}
