package github.naturewhisp.myco.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import github.naturewhisp.myco.platform.MapOrientationMode
import github.naturewhisp.myco.ui.components.CompassRoseDial
import github.naturewhisp.myco.ui.components.MapViewContainer
import github.naturewhisp.myco.ui.components.NavigationModeIcon
import github.naturewhisp.myco.ui.theme.MycoTheme
import github.naturewhisp.myco.ui.theme.NewsreaderFontFamily
import github.naturewhisp.myco.ui.viewmodel.MushroomViewModel
import github.naturewhisp.myco.utils.NavigationHelper

// Schermata cartografica interattiva con sovraimpressione della nuvola termica miceliare e navigazione da campo
@Composable
fun MapScreen(
    viewModel: MushroomViewModel,
    onGeolocateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mycoColors = MycoTheme.colors
    val selected = viewModel.selectedLatLng
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Aggancia il tracking continuo di bussola e GPS al ciclo di vita (attivo solo in ON_RESUME, spento in ON_PAUSE)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startLocationAndOrientationTracking()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopLocationAndOrientationTracking()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopLocationAndOrientationTracking()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Mappa OsmDroid con supporto orientamento dinamico e fascio direzionale utente
        MapViewContainer(
            latitude = selected?.first,
            longitude = selected?.second,
            mapStyle = viewModel.mapStyle,
            locationName = viewModel.placeName?.primary ?: viewModel.locationName,
            onMapClick = { lat, lon -> viewModel.selectLocationFromMap(lat, lon) },
            heatmapData = viewModel.heatmapData,
            showHeatmap = viewModel.showHeatmap,
            userLocation = viewModel.userLocation,
            deviceHeading = viewModel.deviceHeading,
            mapOrientationDegrees = viewModel.mapRotationDegrees,
            isMapCenteredOnUser = viewModel.isMapCenteredOnUser,
            centerOnPointTrigger = viewModel.centerOnPointTrigger,
            onMapDragged = { viewModel.onMapDraggedByUser() },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay superiore: Card informativo e, subito sotto a destra, la Rosa dei Venti
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.End
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, mycoColors.ruleHairline, RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Consuma il tocco per impedire la propagazione accidentale alla mappa sottostante
                    }
            ) {
                if (viewModel.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .align(Alignment.TopCenter),
                        color = mycoColors.favorable,
                        trackColor = mycoColors.favorable.copy(alpha = 0.15f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    // Riga 1: Categoria superiore (sinistra) + Pulsanti Centra & Naviga (destra)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selected != null) "PUNTO CARTOGRAFICO" else "ESPLORAZIONE CARTOGRAFICA",
                            color = mycoColors.favorable,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        )

                        if (selected != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Pulsante per centrare la vista sul punto selezionato
                                OutlinedButton(
                                    onClick = { viewModel.centerMapOnSelectedPoint() },
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 28.dp),
                                    border = BorderStroke(1.dp, mycoColors.ruleHairline),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterCenterFocus,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Centra",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Pulsante per avviare navigazione esterna (Google Maps / Outdooractive)
                                Button(
                                    onClick = {
                                        NavigationHelper.navigateTo(
                                            context = context,
                                            latitude = selected.first,
                                            longitude = selected.second,
                                            label = viewModel.placeName?.primary ?: viewModel.locationName
                                        )
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 28.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = mycoColors.favorable,
                                        contentColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Naviga",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Riga 2: Nome località principale con font Newsreader serif ed eventuali azioni preferiti
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = viewModel.placeName?.primary ?: viewModel.locationName.ifEmpty { "Tocca la mappa per posizionare il punto" },
                            fontFamily = NewsreaderFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (selected != null) {
                            val isFav = viewModel.currentLocationIsFavorite
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.toggleCurrentLocationFavorite() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = if (isFav) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                                        tint = if (isFav) mycoColors.scale2 else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                if (isFav) {
                                    IconButton(
                                        onClick = { viewModel.startEditingCurrentFavorite() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = "Rinomina preferito",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sottotitolo geografico (regione o coordinate GPS formattate)
                    val subtitle = when {
                        viewModel.placeName != null -> {
                            val admin = viewModel.placeName?.administrative
                            if (!admin.isNullOrEmpty() && admin != "Coordinate geografiche") {
                                "$admin • ${viewModel.placeName?.coordinatesFormatted}"
                            } else {
                                viewModel.placeName?.coordinatesFormatted
                            }
                        }
                        selected != null -> String.format(Locale.US, "%.4f° N, %.4f° E (WGS84)", selected.first, selected.second)
                        else -> null
                    }
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Riga 3: Divisorio sottile hairline
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(mycoColors.ruleSubtle)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Riga 4: Metadati micologici (Specie target + Badge probabilità colorato)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = "Specie: ",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Normal
                            )
                            Text(
                                text = viewModel.selectedSpecies.vernacularName,
                                fontSize = 12.sp,
                                fontFamily = NewsreaderFontFamily,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Badge probabilità con feedback di caricamento o tavolozza semantica botanica Herbarium
                        if (viewModel.isLoading) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(mycoColors.favorable.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    color = mycoColors.favorable,
                                    strokeWidth = 1.5.dp
                                )
                                Text(
                                    text = "Analisi...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = mycoColors.favorable
                                )
                            }
                        } else {
                            val prob = viewModel.todayProbability
                            val probTierColor = mycoColors.scaleForProbability(prob)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (prob == 0) mycoColors.ruleSubtle.copy(alpha = 0.5f) else probTierColor)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Indice $prob%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (prob == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quadrante Rosa dei Venti posizionato con certezza sotto il card informativo
            CompassRoseDial(
                rotationDegrees = (360f - viewModel.mapRotationDegrees) % 360f,
                onClick = { viewModel.resetToNorthUp() }
            )
        }

        // Overlay inferiore: FAB di orientamento/centratura e Legenda tassonomica
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.End
        ) {
            val isCentered = viewModel.isMapCenteredOnUser
            val isHeadingUp = viewModel.mapOrientationMode == MapOrientationMode.HEADING_UP
            val hasUserLocation = viewModel.userLocation != null

            // Barra di controllo sopra la legenda: Toggle stile mappa (Toponimi vs Rilievi) e FAB navigazione
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isStandard = viewModel.mapStyle == "standard"
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isStandard) mycoColors.favorable else MaterialTheme.colorScheme.surface,
                    contentColor = if (isStandard) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, if (isStandard) mycoColors.favorable else mycoColors.ruleHairline),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .height(38.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.toggleMapStyle()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isStandard) Icons.Default.Map else Icons.Default.Terrain,
                            contentDescription = if (isStandard) "Mappa toponomastica attiva: tocca per visualizzare i rilievi" else "Mappa rilievi attiva: tocca per visualizzare i toponimi",
                            modifier = Modifier.size(16.dp),
                            tint = if (isStandard) MaterialTheme.colorScheme.surface else mycoColors.favorable
                        )
                        Text(
                            text = if (isStandard) "Toponimi (OSM)" else "Rilievi (Topo)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // FAB Navigazione a 3 stati ancorato sopra la legenda
                FloatingActionButton(
                    onClick = {
                        if (!hasUserLocation) {
                            onGeolocateClick()
                        } else if (!isCentered) {
                            viewModel.centerMapOnUser()
                        } else {
                            viewModel.toggleMapOrientationMode()
                        }
                    },
                    shape = CircleShape,
                    containerColor = when {
                        isCentered && isHeadingUp -> mycoColors.scale3
                        isCentered -> mycoColors.favorable
                        else -> MaterialTheme.colorScheme.surface
                    },
                    contentColor = when {
                        isCentered -> MaterialTheme.colorScheme.surface
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    NavigationModeIcon(
                        isCentered = isCentered,
                        isHeadingUp = isHeadingUp,
                        tint = when {
                            isCentered -> MaterialTheme.colorScheme.surface
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legenda tassonomica
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, mycoColors.ruleHairline, RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Consuma il tocco per impedire la propagazione accidentale alla mappa sottostante
                    }
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LEGENDA SPUN",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Pulsante toggle Heatmap compatto
                        Button(
                            onClick = { viewModel.toggleHeatmap() },
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.showHeatmap) mycoColors.favorable else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (viewModel.showHeatmap) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (viewModel.showHeatmap) "Heatmap Attiva" else "Heatmap Nascosta",
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Scala cromatica Herbarium
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendPip(color = mycoColors.scale1, label = "Innesco")
                        LegendPip(color = mycoColors.scale2, label = "Moderato")
                        LegendPip(color = mycoColors.scale3, label = "Propizio")
                        LegendPip(color = mycoColors.scale4, label = "Culmine")
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendPip(
    color: androidx.compose.ui.graphics.Color,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
