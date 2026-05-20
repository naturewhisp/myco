package github.naturewhisp.myco.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(16.dp, shape, clip = false)
            .background(Color(0x990D1423), shape) // rgba(13, 20, 35, 0.6)
            .border(1.dp, Color(0x14FFFFFF), shape) // rgba(255, 255, 255, 0.08)
            .padding(20.dp)
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun RadialProgress(
    probability: Int,
    threshold: Int,
    size: Dp = 144.dp,
    strokeWidth: Dp = 8.dp
) {
    val progressColor = getProbabilityColor(probability, threshold)
    val remainingColor = Color(0xFF1E293B) // dark slate circle background

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
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
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "CRESCITA",
                color = progressColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

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
    } catch (e: Exception) {
        dateStr
    }

    val formattedDate = date.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString() }
    val progressColor = getProbabilityColor(probability, threshold)

    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .background(Color(0x660F172A), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formattedDate,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = getWeatherEmoji(weatherCode),
                fontSize = 28.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            Text(
                text = String.format(Locale.ITALIAN, "%.1f°C", avgTemp),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "$probability%",
                color = progressColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun InfoRow(
    text: String,
    modifier: Modifier = Modifier
) {
    if (text.isEmpty()) return
    val isSuccess = text.startsWith("✅")
    val isInfo = text.startsWith("ℹ️")
    val isAlert = text.startsWith("❌")
    val textColor = when {
        isSuccess -> Color(0xFF34D399) // Emerald 400
        isInfo -> Color(0xFF94A3B8)    // Slate 400
        isAlert -> Color(0xFFF87171)   // Red 400
        else -> Color(0xFFE2E8F0)      // Slate 200
    }
    Text(
        text = text,
        color = textColor,
        fontSize = 13.sp,
        fontWeight = if (isSuccess) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier.padding(vertical = 4.dp)
    )
}

fun getProbabilityColor(probability: Int, threshold: Int): Color {
    val high = threshold
    val med = (threshold * 0.615).toInt()
    val low = (threshold * 0.23).toInt()
    return when {
        probability > high -> Color(0xFF34D399) // Emerald
        probability > med -> Color(0xFFFBBF24)  // Amber
        probability > low -> Color(0xFFF97316)  // Orange
        else -> Color(0xFFEF4444)              // Red
    }
}

fun getWeatherEmoji(code: Int?): String {
    if (code == null) return "☁️"
    return when {
        code == 0 -> "☀️"
        code in 1..3 -> "☁️"
        (code in 51..67) || (code in 80..82) -> "🌧️"
        code in 95..99 -> "⛈️"
        else -> "☁️"
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
    
    // Extract emoji and label
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
    
    // Extract main value and detail in parentheses
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

fun getDetailColor(detail: String): Color {
    val lower = detail.lowercase(Locale.getDefault())
    return when {
        lower.contains("favorevole") || lower.contains("ideale") || lower.contains("promettente") || lower.contains("ottimale") || lower.contains("buona") -> Color(0xFF34D399) // Emerald 400
        lower.contains("non ideale") || lower.contains("scarso") || lower.contains("insufficiente") || lower.contains("non favorevole") -> Color(0xFFF87171) // Red 400
        lower.contains("moderato") || lower.contains("bassa") || lower.contains("collinare") || lower.contains("misto") || lower.contains("sufficiente") -> Color(0xFFFBBF24) // Amber 400
        else -> Color(0xFF94A3B8) // Slate 400
    }
}

@Composable
fun MetricCard(
    metricText: String,
    modifier: Modifier = Modifier
) {
    val metric = parseMetric(metricText) ?: return
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x1F0F172A)) // rgba(15, 23, 42, 0.12)
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Container
            if (metric.icon.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x1434D399)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = metric.icon, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.size(10.dp))
            }
            
            Column {
                Text(
                    text = metric.label.uppercase(Locale.getDefault()),
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                
                Text(
                    text = metric.value,
                    color = Color.White,
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

@Composable
fun BonusBanner(
    bonusText: String,
    modifier: Modifier = Modifier
) {
    if (bonusText.isEmpty()) return
    val isSuccess = bonusText.contains("✅") || bonusText.lowercase(Locale.getDefault()).contains("alberi ottimali")
    val bannerBg = if (isSuccess) Color(0x1A10B981) else Color(0x1F94A3B8)
    val bannerBorder = if (isSuccess) Color(0x3310B981) else Color(0x2694A3B8)
    val bannerTextColor = if (isSuccess) Color(0xFF6EE7B7) else Color(0xFF94A3B8)
    
    val cleanText = bonusText.replace("✅", "").replace("ℹ️", "").trim()
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bannerBg)
            .border(1.dp, bannerBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSuccess) "🎉" else "ℹ️",
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = cleanText,
                color = bannerTextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun GrowthPhaseBadge(
    phaseText: String,
    modifier: Modifier = Modifier
) {
    val parsed = parseMetric(phaseText) ?: return
    val lowerValue = parsed.value.lowercase(Locale.getDefault())
    val tintColor = when {
        lowerValue.contains("esaurimento") || lowerValue.contains("fermo") || lowerValue.contains("blocco") -> Color(0xFFF87171) // Red 400
        lowerValue.contains("partenza") || lowerValue.contains("inizio") || lowerValue.contains("luna") -> Color(0xFFFBBF24) // Amber 400
        else -> Color(0xFF34D399) // Emerald 400
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tintColor.copy(alpha = 0.08f))
            .border(1.dp, tintColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = "STATO DELLA CRESCITA",
                color = Color(0xFF94A3B8),
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
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
