package github.naturewhisp.myco.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.naturewhisp.myco.model.SavedLocation
import github.naturewhisp.myco.model.WeatherCondition
import github.naturewhisp.myco.model.weatherCondition
import github.naturewhisp.myco.ui.theme.Forest
import github.naturewhisp.myco.ui.theme.MycoTheme
import github.naturewhisp.myco.ui.theme.NewsreaderFontFamily
import github.naturewhisp.myco.ui.theme.Scale2
import github.naturewhisp.myco.ui.theme.Scale3
import github.naturewhisp.myco.ui.theme.Scale4
import java.text.SimpleDateFormat
import java.util.Locale

// Card piana a zero ombre nello stile editoriale Herbarium
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(6.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(16.dp)
    ) {
        Column {
            content()
        }
    }
}

// Indicatore circolare con colori ancorati alla scala tassonomica
@Composable
fun RadialProgress(
    probability: Int,
    threshold: Int,
    size: Dp = 144.dp,
    strokeWidth: Dp = 8.dp
) {
    val mycoColors = MycoTheme.colors
    val progressColor = mycoColors.scaleForProbability(probability)
    val remainingColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            drawCircle(
                color = remainingColor,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )

            val sweepAngle = (probability / 100f) * 360f
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$probability%",
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = NewsreaderFontFamily,
                fontSize = 34.sp,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = "PROBABILITÀ",
                color = progressColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }
    }
}

// Riquadro previsionale giornaliero
@Composable
fun ForecastGridItem(
    dateStr: String,
    weatherCode: Int?,
    avgTemp: Float,
    probability: Int,
    threshold: Int,
    modifier: Modifier = Modifier
) {
    val date = try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outputFormat = SimpleDateFormat("EEE d MMM", Locale.ITALIAN)
        val parsedDate = inputFormat.parse(dateStr)
        if (parsedDate != null) outputFormat.format(parsedDate) else dateStr
    } catch (_: Exception) {
        dateStr
    }

    val formattedDate = date.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString() }
    val progressColor = MycoTheme.colors.scaleForProbability(probability)
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formattedDate,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            val condition = weatherCondition(weatherCode)
            val icon = when (condition) {
                WeatherCondition.CLEAR -> Icons.Outlined.WbSunny
                WeatherCondition.CLOUDY -> Icons.Outlined.Cloud
                WeatherCondition.RAIN -> Icons.Outlined.WaterDrop
                WeatherCondition.STORM -> Icons.Outlined.Thunderstorm
                WeatherCondition.UNKNOWN -> Icons.Outlined.Cloud
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = progressColor,
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .size(24.dp)
            )

            Text(
                text = String.format(Locale.ITALIAN, "%.1f°C", avgTemp),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "$probability%",
                color = progressColor,
                fontFamily = NewsreaderFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// Riga informativa testuale con colorazione contestuale
@Composable
fun InfoRow(
    text: String,
    modifier: Modifier = Modifier
) {
    if (text.isEmpty()) return
    val lower = text.lowercase(Locale.getDefault())
    val isSuccess = lower.contains("ideale") || lower.contains("favorevole") || lower.contains("bonus")
    val isAlert = lower.contains("non ideale") || lower.contains("scarso") || lower.contains("critico")
    val colors = MycoTheme.colors
    val textColor = when {
        isSuccess -> colors.favorable
        isAlert -> colors.adverse
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        color = textColor,
        fontSize = 13.sp,
        fontWeight = if (isSuccess) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier.padding(vertical = 4.dp)
    )
}

// Restituisce il colore corrispondente della scala tassonomica a 5 livelli
fun getProbabilityColor(probability: Int, threshold: Int): Color {
    val high = threshold
    val med = (threshold * 0.615).toInt()
    val low = (threshold * 0.23).toInt()
    return when {
        probability > high -> Forest
        probability > med -> Scale2
        probability > low -> Scale3
        else -> Scale4
    }
}

data class MetricData(
    val icon: String,
    val label: String,
    val value: String,
    val detail: String
)

fun parseMetric(text: String): MetricData? {
    if (text.isEmpty()) return null
    val colonIndex = text.indexOf(':')
    if (colonIndex == -1) {
        return MetricData("", "", text, "")
    }
    val beforeColon = text.substring(0, colonIndex).trim()
    val fullValue = text.substring(colonIndex + 1).trim()

    val spaceIndex = beforeColon.indexOf(' ')
    val icon: String
    val label: String
    if (spaceIndex != -1) {
        icon = beforeColon.substring(0, spaceIndex).trim()
        label = beforeColon.substring(spaceIndex + 1).trim()
    } else {
        icon = ""
        label = beforeColon
    }

    val parenIndex = fullValue.indexOf('(')
    val value: String
    val detail: String
    if (parenIndex != -1) {
        value = fullValue.substring(0, parenIndex).trim()
        detail = fullValue.substring(parenIndex + 1).replace(")", "").replace(".", "").trim()
    } else {
        value = fullValue.replace(".", "")
        detail = ""
    }

    return MetricData(icon, label, value, detail)
}

@Composable
fun getDetailColor(detail: String): Color {
    val lower = detail.lowercase(Locale.getDefault())
    val colors = MycoTheme.colors
    return when {
        lower.contains("favorevole") || lower.contains("ideale") || lower.contains("promettente") || lower.contains("ottimale") || lower.contains("buona") -> colors.favorable
        lower.contains("non ideale") || lower.contains("scarso") || lower.contains("insufficiente") || lower.contains("non favorevole") -> colors.adverse
        lower.contains("moderato") || lower.contains("bassa") || lower.contains("collinare") || lower.contains("misto") || lower.contains("sufficiente") -> colors.scale2
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

// Scheda metrica con design zero-shadow piatto
@Composable
fun MetricCard(
    metricText: String,
    modifier: Modifier = Modifier
) {
    val metric = parseMetric(metricText) ?: return
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (metric.icon.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = metric.icon, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.size(10.dp))
            }

            Column {
                Text(
                    text = metric.label.uppercase(Locale.getDefault()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )

                Text(
                    text = metric.value,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 1.dp)
                )

                if (metric.detail.isNotEmpty()) {
                    Text(
                        text = metric.detail,
                        color = getDetailColor(metric.detail),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
        }
    }
}

// Banner per condizioni ambientali e bonus
@Composable
fun BonusBanner(
    bonusText: String,
    modifier: Modifier = Modifier
) {
    if (bonusText.isEmpty()) return
    val lower = bonusText.lowercase(Locale.getDefault())
    val isSuccess = lower.contains("ottimali") || lower.contains("eccellente") || lower.contains("potenziato")
    val colors = MycoTheme.colors
    val accentColor = if (isSuccess) colors.favorable else colors.scale2
    val cleanText = bonusText.removePrefix("Bonus: ").removePrefix("Bonus SPUN: ").trim()
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, accentColor, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSuccess) "✳" else "i",
                color = accentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = cleanText,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 16.sp
            )
        }
    }
}

// Badge dello stato della crescita miceliare
@Composable
fun GrowthPhaseBadge(
    phaseText: String,
    modifier: Modifier = Modifier
) {
    val parsed = parseMetric(phaseText) ?: return
    val lowerValue = parsed.value.lowercase(Locale.getDefault())
    val colors = MycoTheme.colors
    val tintColor = when {
        lowerValue.contains("esaurimento") || lowerValue.contains("fermo") || lowerValue.contains("blocco") -> colors.adverse
        lowerValue.contains("partenza") || lowerValue.contains("inizio") || lowerValue.contains("luna") -> colors.scale2
        else -> colors.favorable
    }
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, tintColor, shape)
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = "STATO DELLA CRESCITA",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (parsed.icon.isNotEmpty()) {
                    Text(text = parsed.icon, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = parsed.value,
                    color = tintColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// Chip compatto per località salvate o recenti
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocationChip(
    loc: SavedLocation,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onRemoveClick: (() -> Unit)? = null
) {
    val colors = MycoTheme.colors
    val accentColor = when {
        loc.isFavorite -> colors.scale2
        loc.isGpsLocation -> colors.meteo
        else -> colors.neutral
    }
    val icon = when {
        loc.isFavorite -> Icons.Default.Star
        loc.isGpsLocation -> Icons.Default.LocationOn
        else -> null
    }
    val shape = RoundedCornerShape(4.dp)

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Row(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(clickModifier)
            .padding(
                start = 10.dp,
                end = if (onRemoveClick != null) 6.dp else 10.dp,
                top = 6.dp,
                bottom = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = loc.effectiveName,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = if (loc.isFavorite) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
        if (onRemoveClick != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onRemoveClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Rimuovi",
                    tint = accentColor,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

