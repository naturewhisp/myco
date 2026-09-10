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
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.naturewhisp.myco.R
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
                    contentDescription = stringResource(R.string.settings_back_desc),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = stringResource(R.string.settings_tag),
                    color = mycoColors.favorable,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = stringResource(R.string.settings_title),
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
            text = stringResource(R.string.settings_section_theme),
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
                title = stringResource(R.string.settings_theme_system),
                subtitle = stringResource(R.string.settings_theme_system_desc),
                isSelected = currentThemeMode == ThemeMode.SYSTEM,
                onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f)
            )

            ThemeModeSelectorButton(
                title = stringResource(R.string.settings_theme_light),
                subtitle = stringResource(R.string.settings_theme_light_desc),
                isSelected = currentThemeMode == ThemeMode.LIGHT,
                onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )

            ThemeModeSelectorButton(
                title = stringResource(R.string.settings_theme_dark),
                subtitle = stringResource(R.string.settings_theme_dark_desc),
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
            text = stringResource(R.string.settings_section_calculation),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        CalculationModeRow(
            title = stringResource(R.string.settings_calc_unified_title),
            description = stringResource(R.string.settings_calc_unified_desc),
            isSelected = viewModel.calculationMode == "UNIFIED",
            onClick = { viewModel.updateCalculationMode("UNIFIED") }
        )

        Spacer(modifier = Modifier.height(10.dp))

        CalculationModeRow(
            title = stringResource(R.string.settings_calc_weather_title),
            description = stringResource(R.string.settings_calc_weather_desc),
            isSelected = viewModel.calculationMode == "WEATHER_ONLY",
            onClick = { viewModel.updateCalculationMode("WEATHER_ONLY") }
        )

        Spacer(modifier = Modifier.height(24.dp))
        MycoDivider(subtle = true)
        Spacer(modifier = Modifier.height(20.dp))

        // SEZIONE 3: BASE CARTOGRAFICA PREDEFINITA
        Text(
            text = stringResource(R.string.settings_section_map_style),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        CalculationModeRow(
            title = stringResource(R.string.settings_map_standard_title),
            description = stringResource(R.string.settings_map_standard_desc),
            isSelected = viewModel.mapStyle == "standard",
            onClick = { viewModel.updateMapStyle("standard") }
        )

        Spacer(modifier = Modifier.height(10.dp))

        CalculationModeRow(
            title = stringResource(R.string.settings_map_topo_title),
            description = stringResource(R.string.settings_map_topo_desc),
            isSelected = viewModel.mapStyle != "standard",
            onClick = { viewModel.updateMapStyle("topo") }
        )

        Spacer(modifier = Modifier.height(24.dp))
        MycoDivider(subtle = true)
        Spacer(modifier = Modifier.height(20.dp))

        // SEZIONE 4: DIAGNOSTICA ARCHIVIO E STORAGE
        Text(
            text = stringResource(R.string.settings_section_storage),
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
                DiagnosticRow(label = stringResource(R.string.settings_diag_cache_size), value = viewModel.cacheSize)
                DiagnosticRow(label = stringResource(R.string.settings_diag_spun_archive), value = stringResource(R.string.settings_diag_spun_archive_val))
                DiagnosticRow(label = stringResource(R.string.settings_diag_integrity), value = stringResource(R.string.settings_diag_integrity_val))
                DiagnosticRow(label = stringResource(R.string.settings_diag_ai_engine), value = viewModel.aiStatusText)

                Spacer(modifier = Modifier.height(12.dp))

                var syncCount by remember { mutableStateOf<Int?>(null) }

                Button(
                    onClick = {
                        viewModel.prefetchForOfflineUse { count ->
                            syncCount = count
                        }
                    },
                    shape = RoundedCornerShape(4.dp),
                    enabled = !viewModel.isPrefetchingOffline,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mycoColors.favorable,
                        contentColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (viewModel.isPrefetchingOffline) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.settings_btn_syncing_offline), fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val buttonText = syncCount?.let { count ->
                            pluralStringResource(R.plurals.settings_sync_completed, count, count)
                        } ?: stringResource(R.string.settings_btn_sync_offline)
                        Text(
                            text = buttonText,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.clearCache()
                        viewModel.updateCacheSize()
                    },
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.settings_btn_clear_cache), fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        BotanicalBreak()
        Spacer(modifier = Modifier.height(16.dp))

        // SEZIONE 4: AVVERTENZA SANITARIA E LEGALE ASL
        FieldNote(
            text = stringResource(R.string.settings_safety_note_body),
            title = stringResource(R.string.settings_safety_note_title),
            isCaution = true
        )

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = { viewModel.openSafetyDisclaimer() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_btn_read_safety),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

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
