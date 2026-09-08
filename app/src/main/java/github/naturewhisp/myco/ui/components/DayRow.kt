package github.naturewhisp.myco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.naturewhisp.myco.model.DailyOutlook
import github.naturewhisp.myco.model.WeatherCondition
import github.naturewhisp.myco.ui.theme.MycoTheme
import github.naturewhisp.myco.ui.theme.NewsreaderFontFamily
import java.util.Locale

// Riga tabellare per una giornata di previsione
@Composable
fun DayRow(
    outlook: DailyOutlook,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    showDivider: Boolean = true
) {
    val mycoColors = MycoTheme.colors
    val scoreColor = if (outlook.tier == 0) MaterialTheme.colorScheme.onSurfaceVariant else mycoColors.scaleForTier(outlook.tier)
    val background = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Giorno della settimana e data
            Column(modifier = Modifier.width(72.dp)) {
                Text(
                    text = outlook.dayOfWeek,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = outlook.dayOfMonth,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            // Icona meteo
            val weatherIcon = when (outlook.condition) {
                WeatherCondition.CLEAR -> Icons.Outlined.WbSunny
                WeatherCondition.CLOUDY -> Icons.Outlined.Cloud
                WeatherCondition.RAIN -> Icons.Outlined.WaterDrop
                WeatherCondition.STORM -> Icons.Outlined.Thunderstorm
                WeatherCondition.UNKNOWN -> Icons.Outlined.Cloud
            }
            val weatherDesc = when (outlook.condition) {
                WeatherCondition.CLEAR -> "Sereno"
                WeatherCondition.CLOUDY -> "Nuvoloso"
                WeatherCondition.RAIN -> "Pioggia"
                WeatherCondition.STORM -> "Temporale"
                WeatherCondition.UNKNOWN -> "Variabile"
            }
            Icon(
                imageVector = weatherIcon,
                contentDescription = weatherDesc,
                tint = mycoColors.meteo,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(20.dp)
            )

            // Temperatura e pioggia
            Column(modifier = Modifier.width(80.dp)) {
                Text(
                    text = String.format(Locale.ITALIAN, "%.1f°C", outlook.avgTemp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = String.format(Locale.ITALIAN, "%.0f mm", outlook.totalPrecipMm),
                    color = mycoColors.meteo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Barra di probabilità compatta
            Box(modifier = Modifier.weight(1f)) {
                ProbabilityBar(probability = outlook.probability, height = 4.dp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Percentuale con font Newsreader
            Text(
                text = "${outlook.probability}%",
                color = scoreColor,
                fontFamily = NewsreaderFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(36.dp)
            )
        }

        if (showDivider) {
            MycoDivider(subtle = true)
        }
    }
}
