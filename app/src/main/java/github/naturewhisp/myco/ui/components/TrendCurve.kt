package github.naturewhisp.myco.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.naturewhisp.myco.model.DailyOutlook
import github.naturewhisp.myco.ui.theme.MycoTheme

// Curva di andamento temporale della probabilità (Trend a 7 giorni)
@Composable
fun TrendCurve(
    days: List<DailyOutlook>,
    modifier: Modifier = Modifier
) {
    if (days.size < 2) return

    val mycoColors = MycoTheme.colors
    val lineColor = MaterialTheme.colorScheme.outline
    val gridColor = mycoColors.ruleSubtle

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "EVOLUZIONE SETTIMANALE",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val surfaceColor = MaterialTheme.colorScheme.surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                val width = size.width
                val height = size.height
                val padY = 16f
                val availableHeight = height - padY * 2
                val stepX = width / (days.size - 1)

                // Linee guida orizzontali
                drawLine(
                    color = gridColor,
                    start = Offset(0f, padY),
                    end = Offset(width, padY),
                    strokeWidth = 1f
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, height / 2),
                    end = Offset(width, height / 2),
                    strokeWidth = 1f
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, height - padY),
                    end = Offset(width, height - padY),
                    strokeWidth = 1f
                )

                // Costruzione percorso continuo
                val path = Path()
                val points = days.mapIndexed { index, day ->
                    val x = index * stepX
                    val y = padY + availableHeight * (1f - (day.probability.coerceIn(0, 100) / 100f))
                    Offset(x, y)
                }

                points.forEachIndexed { i, pt ->
                    if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }

                // Tracciamento curva
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Punti di flesso colorati per livello tassonomico
                points.forEachIndexed { i, pt ->
                    val tierColor = mycoColors.scaleForTier(days[i].tier)
                    drawCircle(
                        color = surfaceColor,
                        radius = 5.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = tierColor,
                        radius = 3.5.dp.toPx(),
                        center = pt
                    )
                }
            }
        }
    }
}
