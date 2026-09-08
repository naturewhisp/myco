package github.naturewhisp.myco.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.naturewhisp.myco.model.DailyOutlook
import github.naturewhisp.myco.ui.components.BotanicalBreak
import github.naturewhisp.myco.ui.components.DayRow
import github.naturewhisp.myco.ui.components.EmptyState
import github.naturewhisp.myco.ui.components.FieldNote
import github.naturewhisp.myco.ui.components.MycoDivider
import github.naturewhisp.myco.ui.components.TrendCurve
import github.naturewhisp.myco.ui.theme.MycoTheme
import github.naturewhisp.myco.ui.theme.NewsreaderFontFamily
import github.naturewhisp.myco.ui.viewmodel.MushroomViewModel
import java.util.Locale

// Schermata dettagliata delle previsioni settimanali Herbarium
@Composable
fun ForecastScreen(
    viewModel: MushroomViewModel,
    modifier: Modifier = Modifier
) {
    val mycoColors = MycoTheme.colors
    val scrollState = rememberScrollState()
    var selectedOutlook by remember { mutableStateOf<DailyOutlook?>(null) }

    val outlooks = viewModel.dailyOutlooks

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        // Testata
        Text(
            text = "PROSPETTO PREVISIONALE",
            color = mycoColors.favorable,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )

        Text(
            text = "Andamento Fenologico a 7 Giorni",
            fontFamily = NewsreaderFontFamily,
            fontSize = 24.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "${viewModel.placeName?.primary ?: viewModel.locationName} • Specie: ${viewModel.selectedSpecies.vernacularName}",
            fontFamily = NewsreaderFontFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (outlooks.isEmpty()) {
            EmptyState(
                title = "Dati Previsionali Non Disponibili",
                description = "Seleziona una località sulla mappa o cerca un comune per calcolare il trend temporale."
            )
            return
        }

        // Grafico del trend continuo a 7 giorni
        TrendCurve(days = outlooks)

        Spacer(modifier = Modifier.height(20.dp))
        BotanicalBreak()
        Spacer(modifier = Modifier.height(12.dp))

        // Dettaglio del giorno selezionato
        val currentDay = selectedOutlook ?: outlooks.firstOrNull()
        if (currentDay != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${currentDay.dayOfWeek} ${currentDay.dayOfMonth}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = NewsreaderFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "${currentDay.tierLabel.uppercase(Locale.getDefault())} (${currentDay.probability}%)",
                            color = if (currentDay.tier == 0) MaterialTheme.colorScheme.onSurfaceVariant else mycoColors.scaleForTier(currentDay.tier),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    MycoDivider(subtle = true)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "TEMPERATURA", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(text = String.format(Locale.ITALIAN, "%.1f°C", currentDay.avgTemp), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Column {
                            Text(text = "PRECIPITAZIONI", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(text = String.format(Locale.ITALIAN, "%.1f mm", currentDay.totalPrecipMm), color = mycoColors.meteo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Column {
                            Text(text = "UMIDITÀ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(text = String.format(Locale.ITALIAN, "%.0f%%", currentDay.avgHumidityPercent), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Tabella giorni
        Text(
            text = "DETTAGLIO GIORNALIERO",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        outlooks.forEach { day ->
            DayRow(
                outlook = day,
                isSelected = day.dateIso == currentDay?.dateIso,
                onClick = { selectedOutlook = day }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        FieldNote(
            text = "La risposta del micelio sotterraneo alle precipitazioni ha un'inerzia biologica tipica di 7-12 giorni. Le piogge attuali concorrono all'innesco della buttata per la settimana successiva.",
            title = "CINETICA DELLA FRUTTIFICAZIONE"
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}
