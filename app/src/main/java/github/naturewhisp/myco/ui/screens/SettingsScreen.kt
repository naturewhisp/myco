package github.naturewhisp.myco.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.naturewhisp.myco.ui.components.BotanicalBreak
import github.naturewhisp.myco.ui.components.FieldNote
import github.naturewhisp.myco.ui.components.MycoDivider
import github.naturewhisp.myco.ui.theme.MycoTheme
import github.naturewhisp.myco.ui.theme.NewsreaderFontFamily
import github.naturewhisp.myco.ui.theme.ThemeMode
import github.naturewhisp.myco.ui.viewmodel.MushroomViewModel
import kotlinx.coroutines.launch

// Schermata delle impostazioni e preferenze del sistema Herbarium
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun SettingsScreen(
    viewModel: MushroomViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mycoColors = MycoTheme.colors
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val currentThemeMode by (viewModel.themePreference?.themeMode?.collectAsState(initial = ThemeMode.SYSTEM)
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ThemeMode.SYSTEM) })

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        // Intestazione con tasto Indietro
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Indietro",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = "IMPOSTAZIONI",
                    color = mycoColors.favorable,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Configurazione & Diagnostica",
                    fontFamily = NewsreaderFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SEZIONE 1: TEMA GRAFICO
        Text(
            text = "TEMA DELL'INTERFACCIA",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeModeSelectorButton(
                title = "Sistema",
                subtitle = "Automatico",
                isSelected = currentThemeMode == ThemeMode.SYSTEM,
                onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f)
            )

            ThemeModeSelectorButton(
                title = "Naturalist",
                subtitle = "Pergamena",
                isSelected = currentThemeMode == ThemeMode.LIGHT,
                onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )

            ThemeModeSelectorButton(
                title = "Nocturne",
                subtitle = "Terra d'ombra",
                isSelected = currentThemeMode == ThemeMode.DARK,
                onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        MycoDivider(subtle = true)
        Spacer(modifier = Modifier.height(20.dp))

        // SEZIONE 2: MODALITÀ DI CALCOLO
        Text(
            text = "MODELLO DI CALCOLO",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        CalculationModeRow(
            title = "Modello Unificato (Raccomandato)",
            description = "Aggrega meteo Open-Meteo, densità ifale SPUN, altimetria e copertura forestale OSM.",
            isSelected = viewModel.calculationMode == "UNIFIED",
            onClick = { viewModel.updateCalculationMode("UNIFIED") }
        )

        Spacer(modifier = Modifier.height(10.dp))

        CalculationModeRow(
            title = "Solo Dati Meteorologici",
            description = "Valuta esclusivamente pioggia cumulata, temperatura e umidità atmosferica, escludendo i vincoli di habitat.",
            isSelected = viewModel.calculationMode == "WEATHER_ONLY",
            onClick = { viewModel.updateCalculationMode("WEATHER_ONLY") }
        )

        Spacer(modifier = Modifier.height(24.dp))
        MycoDivider(subtle = true)
        Spacer(modifier = Modifier.height(20.dp))

        // SEZIONE 3: BASE CARTOGRAFICA PREDEFINITA
        Text(
            text = "BASE CARTOGRAFICA PREDEFINITA",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        CalculationModeRow(
            title = "Toponomastica & Sentieri (Consigliata)",
            description = "Carta standard OpenStreetMap con nomi dei luoghi, borghi, frazioni, vette e sentieri chiaramente indicati.",
            isSelected = viewModel.mapStyle == "standard",
            onClick = { viewModel.updateMapStyle("standard") }
        )

        Spacer(modifier = Modifier.height(10.dp))

        CalculationModeRow(
            title = "Topografica (Rilievi & Isoipse)",
            description = "OpenTopoMap con curve di livello e ombreggiatura orografica dei versanti. Ottimale per pendenze ed altimetria.",
            isSelected = viewModel.mapStyle != "standard",
            onClick = { viewModel.updateMapStyle("topo") }
        )

        Spacer(modifier = Modifier.height(24.dp))
        MycoDivider(subtle = true)
        Spacer(modifier = Modifier.height(20.dp))

        // SEZIONE 4: DIAGNOSTICA ARCHIVIO E STORAGE
        Text(
            text = "ARCHIVIO E DIAGNOSTICA LOCALE",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                .border(1.dp, mycoColors.ruleHairline, RoundedCornerShape(6.dp))
                .padding(14.dp)
        ) {
            Column {
                DiagnosticRow(label = "Dimensione cache locale", value = viewModel.cacheSize)
                DiagnosticRow(label = "Archivio SPUN Italia", value = "spun_italy.bin (Verificato)")
                DiagnosticRow(label = "Integrità SHA-256", value = "Conforme (Asset interno)")
                DiagnosticRow(label = "Motore AI on-device", value = viewModel.aiStatusText)

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.clearCache()
                        viewModel.updateCacheSize()
                    },
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Svuota Cache Cartografica e Previsioni", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        BotanicalBreak()
        Spacer(modifier = Modifier.height(16.dp))

        // SEZIONE 4: AVVERTENZA SANITARIA E LEGALE ASL
        FieldNote(
            text = "Questa applicazione fornisce esclusivamente stime probabilistiche teoriche a scopi escursionistici, scientifici e di studio ecologico. Non garantisce la reale presenza né la commestibilità dei funghi. Prima del consumo alimentare, è obbligatorio per legge sottoporre il raccolto al controllo gratuito di un Ispettorato Micologico dell'ASL.",
            title = "AVVERTENZA SANITARIA OBBLIGATORIA (ASL)",
            isCaution = true
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ThemeModeSelectorButton(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mycoColors = MycoTheme.colors
    val border = if (isSelected) mycoColors.favorable else mycoColors.ruleHairline
    val background = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(1.5.dp, border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) mycoColors.favorable else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CalculationModeRow(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mycoColors = MycoTheme.colors
    val border = if (isSelected) mycoColors.favorable else mycoColors.ruleHairline

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, if (isSelected) mycoColors.favorable else mycoColors.ruleHairline, CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(mycoColors.favorable))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}
