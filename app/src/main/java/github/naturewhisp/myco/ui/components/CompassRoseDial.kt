package github.naturewhisp.myco.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import github.naturewhisp.myco.ui.theme.MycoTheme

/**
 * Quadrante bussola Herbarium interattivo (Compass Rose dial).
 * Mostra l'orientamento del Nord cartografico. Ruota dinamicamente rispetto alla mappa.
 * Toccandolo, riallinea dolcemente la mappa a Nord (0°).
 */
@Composable
fun CompassRoseDial(
    rotationDegrees: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mycoColors = MycoTheme.colors
    val needleRotation by animateFloatAsState(
        targetValue = rotationDegrees,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "CompassNeedleRotation"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, mycoColors.ruleHairline),
        modifier = modifier.size(44.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val needleHalfWidth = 3.5.dp.toPx()
            val needleLength = (size.minDimension / 2f) - 1.dp.toPx()

            withTransform({
                rotate(needleRotation, pivot = Offset(cx, cy))
            }) {
                // Ago Nord (Cinnabar / Terracotta Herbarium)
                val northPath = Path().apply {
                    moveTo(cx, cy - needleLength)
                    lineTo(cx - needleHalfWidth, cy)
                    lineTo(cx + needleHalfWidth, cy)
                    close()
                }
                drawPath(northPath, color = mycoColors.scale4)

                // Ago Sud (Muted Slate / Hairline)
                val southPath = Path().apply {
                    moveTo(cx, cy + needleLength)
                    lineTo(cx - needleHalfWidth, cy)
                    lineTo(cx + needleHalfWidth, cy)
                    close()
                }
                drawPath(southPath, color = mycoColors.ruleHairline)

                // Perno centrale
                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = mycoColors.ruleHairline,
                    radius = 2.5.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

/**
 * Icona vettoriale dinamica per il FAB a 3 stati di navigazione da campo:
 * 1. Non centrato: Mirino GPS a cerchio aperto con assi
 * 2. Centrato Nord-Up: Bersaglio GPS pieno
 * 3. Centrato Heading-Up: Freccia direzionale di navigazione campo
 */
@Composable
fun NavigationModeIcon(
    isCentered: Boolean,
    isHeadingUp: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val strokeWidth = 1.8.dp.toPx()

            when {
                // Stato 3: Modalità bussola attiva (Heading-Up) -> Freccia navigatore
                isCentered && isHeadingUp -> {
                    val arrowPath = Path().apply {
                        moveTo(cx, cy - 8.dp.toPx())
                        lineTo(cx + 7.dp.toPx(), cy + 7.dp.toPx())
                        lineTo(cx, cy + 3.dp.toPx())
                        lineTo(cx - 7.dp.toPx(), cy + 7.dp.toPx())
                        close()
                    }
                    drawPath(arrowPath, color = tint)
                }

                // Stato 2: Centrato su utente (Nord-Up) -> Cerchio bersaglio pieno
                isCentered -> {
                    // Anello esterno
                    drawCircle(
                        color = tint,
                        radius = 8.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = strokeWidth)
                    )
                    // Punto pieno interno
                    drawCircle(
                        color = tint,
                        radius = 4.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }

                // Stato 1: Non centrato / esplorazione libera -> Mirino GPS
                else -> {
                    val ringRadius = 7.dp.toPx()
                    drawCircle(
                        color = tint,
                        radius = ringRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = strokeWidth)
                    )
                    val tickLen = 3.dp.toPx()
                    // 4 tacche del mirino
                    drawLine(tint, Offset(cx, cy - ringRadius - tickLen), Offset(cx, cy - ringRadius), strokeWidth, StrokeCap.Round)
                    drawLine(tint, Offset(cx, cy + ringRadius), Offset(cx, cy + ringRadius + tickLen), strokeWidth, StrokeCap.Round)
                    drawLine(tint, Offset(cx - ringRadius - tickLen, cy), Offset(cx - ringRadius, cy), strokeWidth, StrokeCap.Round)
                    drawLine(tint, Offset(cx + ringRadius, cy), Offset(cx + ringRadius + tickLen, cy), strokeWidth, StrokeCap.Round)
                }
            }
        }
    }
}
