package github.naturewhisp.myco.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.naturewhisp.myco.ui.components.MapViewContainer
import github.naturewhisp.myco.ui.theme.MycoTheme
import github.naturewhisp.myco.ui.theme.NewsreaderFontFamily
import github.naturewhisp.myco.ui.viewmodel.MushroomViewModel

// Schermata cartografica interattiva con sovraimpressione della nuvola termica miceliare
@Composable
fun MapScreen(
    viewModel: MushroomViewModel,
    onGeolocateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mycoColors = MycoTheme.colors
    val selected = viewModel.selectedLatLng

    Box(modifier = modifier.fillMaxSize()) {
        // Mappa OsmDroid
        MapViewContainer(
            latitude = selected?.first,
            longitude = selected?.second,
            mapStyle = viewModel.mapStyle,
            onMapClick = { lat, lon -> viewModel.selectLocation(lat, lon, "Punto cartografico") },
            heatmapData = viewModel.heatmapData,
            showHeatmap = viewModel.showHeatmap,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay superiore informativo (Località e Specie attiva)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, mycoColors.ruleHairline, RoundedCornerShape(6.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = viewModel.placeName?.primary ?: viewModel.locationName.ifEmpty { "Tocca la mappa per posizionare il punto" },
                            fontFamily = NewsreaderFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Specie: ${viewModel.selectedSpecies.vernacularName} • Indice: ${viewModel.todayProbability}%",
                            color = mycoColors.favorable,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    // Pulsante per commutare stile mappa (topo / dark)
                    OutlinedButton(
                        onClick = {
                            val nextStyle = if (viewModel.mapStyle == "topo") "dark" else "topo"
                            viewModel.updateMapStyle(nextStyle)
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (viewModel.mapStyle == "topo") "Scura" else "Topo",
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Overlay inferiore: FAB geolocalizzazione e Legenda tassonomica
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.End
        ) {
            // FAB Geolocalizzazione ancorato sopra la legenda
            FloatingActionButton(
                onClick = onGeolocateClick,
                shape = CircleShape,
                containerColor = mycoColors.favorable,
                contentColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Rileva GPS")
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legenda tassonomica
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, mycoColors.ruleHairline, RoundedCornerShape(6.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LEGENDA PROBABILITÀ SPUN",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        // Pulsante toggle Heatmap
                        Button(
                            onClick = { viewModel.toggleHeatmap() },
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.showHeatmap) mycoColors.favorable else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (viewModel.showHeatmap) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (viewModel.showHeatmap) "Heatmap Attiva" else "Heatmap Nascosta",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
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
