package github.naturewhisp.myco.ui.components

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

import github.naturewhisp.myco.model.HeatmapData

@Composable
fun MapViewContainer(
    latitude: Double?,
    longitude: Double?,
    mapStyle: String,
    onMapClick: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(300.dp)
        .clip(RoundedCornerShape(20.dp)),
    heatmapData: HeatmapData? = null,
    showHeatmap: Boolean = true
) {
    val darkTileSource = remember {
        XYTileSource(
            "CartoDBDark",
            1, 20, 256, ".png",
            arrayOf(
                "https://a.basemaps.cartocdn.com/dark_all/",
                "https://b.basemaps.cartocdn.com/dark_all/",
                "https://c.basemaps.cartocdn.com/dark_all/",
                "https://d.basemaps.cartocdn.com/dark_all/"
            ),
            "© OpenStreetMap contributors, © CARTO"
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setMultiTouchControls(true)
                tileProvider.tileSource = when (mapStyle) {
                    "topo" -> TileSourceFactory.OpenTopo
                    "dark" -> darkTileSource
                    else -> TileSourceFactory.MAPNIK
                }

                // Capture clicks on the map to trigger location updates
                val mapEventsReceiver = object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        p?.let {
                            onMapClick(it.latitude, it.longitude)
                        }
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean {
                        return false
                    }
                }
                
                overlays.add(MapEventsOverlay(mapEventsReceiver))

                // Layer nuvola di probabilità / calore (sotto il marker)
                val heatmapOverlay = HeatmapOverlay(if (showHeatmap) heatmapData else null)
                overlays.add(heatmapOverlay)

                if (latitude != null && longitude != null) {
                    val geoPoint = GeoPoint(latitude, longitude)
                    val marker = Marker(this).apply {
                        position = geoPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Punto selezionato"
                    }
                    overlays.add(marker)
                    controller.setCenter(geoPoint)
                    controller.setZoom(13.0)
                }
            }
        },
        update = { mapView ->
            mapView.tileProvider.tileSource = when (mapStyle) {
                "topo" -> TileSourceFactory.OpenTopo
                "dark" -> darkTileSource
                else -> TileSourceFactory.MAPNIK
            }

            // Aggiorna l'overlay della nuvola termica
            val currentHeatmapOverlay = mapView.overlays.filterIsInstance<HeatmapOverlay>().firstOrNull()
            if (currentHeatmapOverlay != null) {
                currentHeatmapOverlay.heatmapData = if (showHeatmap) heatmapData else null
            } else if (showHeatmap && heatmapData != null) {
                mapView.overlays.add(0, HeatmapOverlay(heatmapData))
            }

            if (latitude != null && longitude != null) {
                val geoPoint = GeoPoint(latitude, longitude)
                
                val markers = mapView.overlays.filterIsInstance<Marker>()
                markers.forEach { mapView.overlays.remove(it) }

                val marker = Marker(mapView).apply {
                    position = geoPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Punto selezionato"
                }
                mapView.overlays.add(marker)
                
                mapView.controller.setCenter(geoPoint)
            }
            mapView.invalidate()
        }
    )
}
