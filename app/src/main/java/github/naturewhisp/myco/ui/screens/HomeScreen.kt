package github.naturewhisp.myco.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.naturewhisp.myco.ui.components.BotanicalBreak
import github.naturewhisp.myco.ui.components.DayRow
import github.naturewhisp.myco.ui.components.EmptyState
import github.naturewhisp.myco.ui.components.FactorRow
import github.naturewhisp.myco.ui.components.FieldNote
import github.naturewhisp.myco.ui.components.HabitatAnomalyNotice
import github.naturewhisp.myco.ui.components.LocationChip
import github.naturewhisp.myco.ui.components.MycoDivider
import github.naturewhisp.myco.ui.components.OfflineCacheNotice
import github.naturewhisp.myco.ui.components.OutsideCoverageNotice
import github.naturewhisp.myco.ui.components.ProbabilityHeadline
import github.naturewhisp.myco.ui.theme.CodeTech
import github.naturewhisp.myco.ui.theme.MycoTheme
import github.naturewhisp.myco.ui.theme.NewsreaderFontFamily
import github.naturewhisp.myco.ui.viewmodel.MushroomViewModel
import github.naturewhisp.myco.utils.NavigationHelper

// Schermata principale "Registro & Tavola Micologica" nello stile Herbarium
@Composable
fun HomeScreen(
    viewModel: MushroomViewModel,
    onGeolocateClick: () -> Unit,
    onOpenMapTab: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenForecastTab: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val mycoColors = MycoTheme.colors
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        // Intestazione con Monogramma, Titolo e Pulsante Impostazioni
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Monogramma "M" Herbarium
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(mycoColors.favorable),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "M",
                        fontFamily = NewsreaderFontFamily,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.surface
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "MYCO • HERBARIUM",
                        color = mycoColors.favorable,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = viewModel.placeName?.primary ?: "Registro Micologico",
                        fontFamily = NewsreaderFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pulsante preferito per la località corrente
                IconButton(onClick = { viewModel.toggleCurrentLocationFavorite() }) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = if (viewModel.currentLocationIsFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                        tint = if (viewModel.currentLocationIsFavorite) mycoColors.scale2 else MaterialTheme.colorScheme.outline
                    )
                }

                // Pulsante per rinominare il preferito se la località corrente è salvata
                if (viewModel.currentLocationIsFavorite) {
                    IconButton(
                        onClick = { viewModel.startEditingCurrentFavorite() }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Rinomina preferito",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Impostazioni",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Selettore Specie Bersaglio (Bottone ad apertura sheet)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, mycoColors.ruleHairline, RoundedCornerShape(4.dp))
                .clickable { viewModel.setTargetSpeciesSheetVisibility(true) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SPECIE BERSAGLIO ATTIVA",
                    color = mycoColors.favorable,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${viewModel.selectedSpecies.vernacularName} (${viewModel.selectedSpecies.binomialName})",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = NewsreaderFontFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }

            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Cambia specie",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Barra di ricerca toponomastica a riga singola (Zero-Shadow)
        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            placeholder = {
                Text(
                    text = "Cerca comune, valle o bosco...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Cerca",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (viewModel.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancella",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onGeolocateClick) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Geolocalizza",
                            tint = mycoColors.favorable
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focusManager.clearFocus()
                viewModel.searchLocation()
            }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedIndicatorColor = mycoColors.favorable,
                unfocusedIndicatorColor = mycoColors.ruleHairline
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Chip località preferite e recenti
        val allChips = viewModel.favoriteLocations + viewModel.recentLocations.filterNot { it.isFavorite }
        if (allChips.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allChips.take(8).forEach { loc ->
                    LocationChip(
                        loc = loc,
                        onClick = { viewModel.selectSavedLocation(loc) },
                        onLongClick = if (loc.isFavorite) {
                            { viewModel.startEditingFavorite(loc) }
                        } else null,
                        onRemoveClick = if (!loc.isFavorite) {
                            { viewModel.removeRecentLocation(loc) }
                        } else null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Avvisi di stato anomalo (Habitat urbano, Fuori copertura, Cache offline)
        if (viewModel.isFromCache && viewModel.cacheAgeText != null) {
            OfflineCacheNotice(
                cacheAgeText = viewModel.cacheAgeText ?: "",
                isFieldOffline = viewModel.isOfflineFieldMode
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (viewModel.isOutsideCoverage) {
            OutsideCoverageNotice(
                closestLocationName = viewModel.closestCoverageName,
                distanceKm = viewModel.closestCoverageDistanceKm,
                onSnapClick = { viewModel.snapToClosestCoverage() }
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else if (viewModel.isOutsideHabitat) {
            HabitatAnomalyNotice(
                onMoveToForestClick = { viewModel.snapToNearestForest() }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Stato di caricamento
        if (viewModel.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = mycoColors.favorable,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = viewModel.loadingText.ifEmpty { "Interrogazione sensori ambientali in corso..." },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
            return
        }

        // Se nessuna località è caricata
        if (viewModel.selectedLatLng == null) {
            EmptyState(
                title = "Nessun Rilevamento Attivo",
                description = "Cerca un comune, seleziona una località salvata o attiva la geolocalizzazione per calcolare il potenziale di crescita miceliare.",
                action = {
                    Button(
                        onClick = onGeolocateClick,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = mycoColors.favorable,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Rileva Posizione GPS", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
            return
        }

        // Dati di posizione e coordinate WGS84
        val place = viewModel.placeName
        if (place != null) {
            Text(
                text = "${place.coordinatesFormatted} • ${place.elevationFormatted}",
                style = CodeTech,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        // Testata di probabilità con barra a 5 segmenti
        ProbabilityHeadline(
            probability = viewModel.todayProbability,
            speciesVernacular = viewModel.selectedSpecies.vernacularName
        )

        Spacer(modifier = Modifier.height(20.dp))
        BotanicalBreak()
        Spacer(modifier = Modifier.height(12.dp))

        // Fattori Ecologici e Ambientali Tabellari
        Text(
            text = "FATTORI ECOLOGICI & AMBIENTALI",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (viewModel.factors.isNotEmpty()) {
            viewModel.factors.forEach { factor ->
                FactorRow(factor = factor)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Nota di campo / Sintesi AI on-device
        if (viewModel.summaryText.isNotEmpty()) {
            FieldNote(
                text = viewModel.summaryText,
                title = "OSSERVAZIONI DI CAMPO & MODELLO BIOLOGICO"
            )
            Spacer(modifier = Modifier.height(16.dp))
        } else if (viewModel.isAiLoading) {
            Text(
                text = "Elaborazione sintesi analitica on-device...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Anteprima Previsioni Prossimi Giorni
        if (viewModel.dailyOutlooks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ANDAMENTO PROSSIMI 7 GIORNI",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Vedi tutti →",
                    color = mycoColors.favorable,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onOpenForecastTab() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            viewModel.dailyOutlooks.take(3).forEach { outlook ->
                DayRow(outlook = outlook, onClick = { onOpenForecastTab() })
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        val context = LocalContext.current

        // Pulsanti rapidi: Mappa e Navigazione al punto
        if (viewModel.selectedLatLng != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenMapTab,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mycoColors.favorable,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Icon(imageVector = Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Mappa",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = {
                        val latLng = viewModel.selectedLatLng ?: return@OutlinedButton
                        NavigationHelper.navigateTo(
                            context = context,
                            latitude = latLng.first,
                            longitude = latLng.second,
                            label = viewModel.placeName?.primary ?: viewModel.locationName
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Naviga al punto",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            Button(
                onClick = onOpenMapTab,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = mycoColors.favorable,
                    contentColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(imageVector = Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Esplora Mappa & Heatmap Miceliare",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
