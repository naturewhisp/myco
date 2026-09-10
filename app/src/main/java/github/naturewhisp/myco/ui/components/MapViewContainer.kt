package github.naturewhisp.myco.ui.components

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import github.naturewhisp.myco.platform.DeviceHeading
import github.naturewhisp.myco.platform.UserLocation
import github.naturewhisp.myco.platform.android.HeatmapData
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
@Composable
fun MapViewContainer(
    latitude: Double?,
    longitude: Double?,
    mapStyle: String = "standard",
    locationName: String = "",
    onMapClick: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(300.dp)
        .clip(RoundedCornerShape(20.dp)),
    heatmapData: HeatmapData? = null,
    showHeatmap: Boolean = true,
    userLocation: UserLocation? = null,
    deviceHeading: DeviceHeading? = null,
    mapOrientationDegrees: Float = 0f,
    isMapCenteredOnUser: Boolean = false,
    centerOnPointTrigger: Int = 0,
    onMapDragged: () -> Unit = {}
) {
    val lastLocationRef = remember {
        object {
            var lat: Double? = null
            var lon: Double? = null
        }
    }

    val lastCenteredTargetRef = remember {
        object {
            var targetLat: Double? = null
            var targetLon: Double? = null
            var lastTrigger: Int = -1
            var wasCenteredOnUser: Boolean = false
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setMultiTouchControls(true)
                isVerticalMapRepetitionEnabled = false
                minZoomLevel = 4.0
                maxZoomLevel = 20.0

                // Stile cartografico: OpenStreetMap Mapnik (toponimi completi) vs OpenTopoMap (rilievi)
                val targetTileSource = if (mapStyle == "standard") TileSourceFactory.MAPNIK else TileSourceFactory.OpenTopo
                setTileSource(targetTileSource)

                // Rileva trascinamento mappa manuale da parte dell'utente
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_MOVE) {
                        onMapDragged()
                    }
                    false
                }

                // Cattura click sulla mappa per selezionare coordinate
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

                // Layer 1: Nuvola di probabilità / calore (renderizzata sopra le mattonelle OSM)
                val heatmapOverlay = HeatmapOverlay(if (showHeatmap) heatmapData else null)
                overlays.add(heatmapOverlay)

                // Layer 2: Posizione live e fascio direzionale dell'utente (sopra l'heatmap)
                val userBearingOverlay = UserBearingOverlay(context).apply {
                    this.userLocation = userLocation
                    this.deviceHeading = deviceHeading
                }
                overlays.add(userBearingOverlay)

                // Layer 3: Marker punto selezionato
                if (latitude != null && longitude != null) {
                    val geoPoint = GeoPoint(latitude, longitude)
                    val marker = Marker(this).apply {
                        position = geoPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = locationName.ifEmpty { "Punto selezionato" }
                    }
                    overlays.add(marker)
                    controller.setCenter(geoPoint)
                    controller.setZoom(13.0)
                    lastLocationRef.lat = latitude
                    lastLocationRef.lon = longitude
                } else {
                    controller.setZoom(6.0)
                    controller.setCenter(GeoPoint(42.5, 12.5))
                }
            }
        },
        update = { mapView ->

            // 1. Aggiorna TileSource se mapStyle è variato
            val targetTileSource = if (mapStyle == "standard") TileSourceFactory.MAPNIK else TileSourceFactory.OpenTopo
            if (mapView.tileProvider.tileSource != targetTileSource) {
                mapView.setTileSource(targetTileSource)
                mapView.postInvalidate()
            }

            // 2. Aggiorna overlay Heatmap solo su reale variazione
            val currentHeatmapOverlay = mapView.overlays.filterIsInstance<HeatmapOverlay>().firstOrNull()
            val desiredHeatmap = if (showHeatmap) heatmapData else null
            if (currentHeatmapOverlay != null) {
                if (currentHeatmapOverlay.heatmapData != desiredHeatmap) {
                    currentHeatmapOverlay.heatmapData = desiredHeatmap
                    mapView.postInvalidate()
                }
            } else if (desiredHeatmap != null) {
                mapView.overlays.add(0, HeatmapOverlay(desiredHeatmap))
                mapView.postInvalidate()
            }

            // 3. Aggiorna overlay posizione live e orientamento utente
            val currentBearingOverlay = mapView.overlays.filterIsInstance<UserBearingOverlay>().firstOrNull()
            if (currentBearingOverlay != null) {
                val locChanged = currentBearingOverlay.userLocation != userLocation
                val headingChanged = currentBearingOverlay.deviceHeading != deviceHeading
                if (locChanged || headingChanged) {
                    currentBearingOverlay.userLocation = userLocation
                    currentBearingOverlay.deviceHeading = deviceHeading
                    mapView.postInvalidate()
                }
            }

            // 4. Applica rotazione mappa (Nord-Up vs Heading-Up) con deadband anti-jitter
            if (mapOrientationDegrees == 0f) {
                if (abs(mapView.mapOrientation) > 0.01f) {
                    mapView.setMapOrientation(0f, false)
                }
            } else {
                var orientationDiff = abs(mapView.mapOrientation - mapOrientationDegrees) % 360f
                if (orientationDiff > 180f) orientationDiff = 360f - orientationDiff
                if (orientationDiff > 1.2f) {
                    mapView.setMapOrientation(mapOrientationDegrees, false)
                }
            }

            // 5. Centratura telecamera (su utente o su punto focalizzato)
            if (isMapCenteredOnUser && userLocation != null) {
                lastCenteredTargetRef.wasCenteredOnUser = true
                val userLat = userLocation.latitude
                val userLon = userLocation.longitude
                val prevLat = lastCenteredTargetRef.targetLat ?: 0.0
                val prevLon = lastCenteredTargetRef.targetLon ?: 0.0
                val movedSignificantly = abs(userLat - prevLat) > 0.00005 || abs(userLon - prevLon) > 0.00005

                if (lastCenteredTargetRef.targetLat == null || movedSignificantly) {
                    lastCenteredTargetRef.targetLat = userLat
                    lastCenteredTargetRef.targetLon = userLon
                    mapView.controller.setCenter(GeoPoint(userLat, userLon))
                }
            } else {
                val justExitedUserCentering = lastCenteredTargetRef.wasCenteredOnUser
                lastCenteredTargetRef.wasCenteredOnUser = false

                if (latitude != null && longitude != null) {
                    val triggerFired = centerOnPointTrigger != lastCenteredTargetRef.lastTrigger
                    val coordinateChanged = latitude != lastCenteredTargetRef.targetLat || longitude != lastCenteredTargetRef.targetLon
                    if (triggerFired || coordinateChanged || justExitedUserCentering) {
                        lastCenteredTargetRef.lastTrigger = centerOnPointTrigger
                        lastCenteredTargetRef.targetLat = latitude
                        lastCenteredTargetRef.targetLon = longitude
                        lastLocationRef.lat = latitude
                        lastLocationRef.lon = longitude
                        val geoPoint = GeoPoint(latitude, longitude)
                        mapView.controller.setCenter(geoPoint)
                        if (mapView.zoomLevelDouble < 11.0) {
                            mapView.controller.setZoom(13.0)
                        }
                    }
                }
            }

            // 6. Aggiorna posizione e titolo del marker del punto selezionato
            val markers = mapView.overlays.filterIsInstance<Marker>()
            val existingMarker = markers.firstOrNull()
            val targetTitle = locationName.ifEmpty { "Punto selezionato" }
            if (latitude != null && longitude != null) {
                val geoPoint = GeoPoint(latitude, longitude)
                if (existingMarker != null) {
                    var needsRedraw = false
                    if (existingMarker.position?.latitude != latitude || existingMarker.position?.longitude != longitude) {
                        existingMarker.position = geoPoint
                        needsRedraw = true
                    }
                    if (existingMarker.title != targetTitle) {
                        existingMarker.title = targetTitle
                        needsRedraw = true
                    }
                    if (needsRedraw) {
                        mapView.postInvalidate()
                    }
                } else {
                    val marker = Marker(mapView).apply {
                        position = geoPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = targetTitle
                    }
                    mapView.overlays.add(marker)
                    mapView.postInvalidate()
                }
            } else if (existingMarker != null) {
                mapView.overlays.remove(existingMarker)
                mapView.postInvalidate()
            }
        }
    )
}
