package github.naturewhisp.myco.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import github.naturewhisp.myco.platform.DeviceHeading
import github.naturewhisp.myco.platform.UserLocation
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Overlay per OsmDroid che disegna la posizione live dell'utente con fascio direzionale
 * (beam / cone of vision) e cerchio di accuratezza GPS.
 * Utilizza la palette botanica Herbarium (#4E6273).
 */
class UserBearingOverlay(
    context: Context
) : Overlay() {

    var userLocation: UserLocation? = null
    var deviceHeading: DeviceHeading? = null
    var showHeadingBeam: Boolean = true

    private val density = context.resources.displayMetrics.density
    private val screenCoords = Point()
    private val geoPoint = GeoPoint(0.0, 0.0)

    private val coneRadius = 60f * density
    private val coneOval = RectF(-coneRadius, -coneRadius, coneRadius, coneRadius)
    private val conePath = Path().apply {
        moveTo(0f, 0f)
        arcTo(coneOval, 245f, 50f)
        close()
    }

    private val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = RadialGradient(
            0f,
            0f,
            coneRadius,
            intArrayOf(
                Color.argb(120, 0x4E, 0x62, 0x73),
                Color.argb(45, 0x4E, 0x62, 0x73),
                Color.argb(0, 0x4E, 0x62, 0x73)
            ),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private val accuracyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(25, 0x4E, 0x62, 0x73)
    }

    private val accuracyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = Color.argb(60, 0x4E, 0x62, 0x73)
    }

    private val dotShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(60, 0, 0, 0)
    }

    private val dotWhiteHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val dotCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(0x4E, 0x62, 0x73)
    }

    private val dotInnerPingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        if (shadow || canvas == null || mapView == null) return
        val loc = userLocation ?: return

        geoPoint.latitude = loc.latitude
        geoPoint.longitude = loc.longitude
        mapView.projection.toPixels(geoPoint, screenCoords)
        val cx = screenCoords.x.toFloat()
        val cy = screenCoords.y.toFloat()

        // 1. Cerchio di accuratezza GPS
        val accuracy = loc.accuracyMeters
        if (accuracy != null && accuracy > 6f) {
            val pixelRadius = mapView.projection.metersToPixels(
                accuracy,
                loc.latitude,
                mapView.zoomLevelDouble
            )
            if (pixelRadius > 8f * density) {
                canvas.drawCircle(cx, cy, pixelRadius, accuracyFillPaint)
                canvas.drawCircle(cx, cy, pixelRadius, accuracyStrokePaint)
            }
        }

        // 2. Fascio direzionale di visione (cono della bussola)
        val heading = deviceHeading
        if (showHeadingBeam && heading != null) {
            canvas.withTranslation(cx, cy) {
                withRotation(heading.azimuthDegrees) {
                    drawPath(conePath, conePaint)
                }
            }
        }

        // 3. Punto di localizzazione utente (Ombra GPU + Bordo bianco + Indigo botanico + Centro)
        val outerRadius = 8.5f * density
        val coreRadius = 6.5f * density
        val pingRadius = 2.0f * density

        canvas.drawCircle(cx, cy + 1f * density, outerRadius + 0.8f * density, dotShadowPaint)
        canvas.drawCircle(cx, cy, outerRadius, dotWhiteHaloPaint)
        canvas.drawCircle(cx, cy, coreRadius, dotCorePaint)
        canvas.drawCircle(cx, cy, pingRadius, dotInnerPingPaint)
    }
}
