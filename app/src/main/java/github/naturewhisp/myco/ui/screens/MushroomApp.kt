package github.naturewhisp.myco.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.time.Duration.Companion.milliseconds
import github.naturewhisp.myco.ui.components.*
import github.naturewhisp.myco.ui.viewmodel.MushroomViewModel
import github.naturewhisp.myco.utils.MushroomAlgorithms
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import github.naturewhisp.myco.utils.NavigationHelper
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushroomApp(
    viewModel: MushroomViewModel,
    onGeolocateClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isSearchFocused by remember { mutableStateOf(false) }
    var isFavoritesExpanded by remember { mutableStateOf(false) }
    var isMapFullscreen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D2115), Color(0xFF040810)),
                    startY = 0f,
                    endY = 1500f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Title
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .background(Color(0x3334D399), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x3334D399), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "MYCO PORCINI",
                    color = Color(0xFF34D399),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Previsione Crescita Funghi",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Previsione scientifica basata su microclima e vegetazione locale.",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Search Bar & Suggestions Container
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Search Bar Card
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = viewModel.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            singleLine = true,
                            placeholder = { Text("Digita una località...", color = Color(0xFF94A3B8), fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cerca", tint = Color(0xFF94A3B8)) },
                            trailingIcon = {
                                if (viewModel.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Cancella testo",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0x330D1423),
                                unfocusedContainerColor = Color(0x330D1423),
                                disabledContainerColor = Color(0x330D1423),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                viewModel.searchLocation()
                                isSearchFocused = false
                            }),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
                                .onFocusChanged { focusState ->
                                    isSearchFocused = focusState.isFocused
                                    if (focusState.isFocused) {
                                        keyboardController?.show()
                                    }
                                }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onGeolocateClick()
                                isSearchFocused = false
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = Color(0xFF34D399)
                            ),
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Usa posizione")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { 
                                isSearchFocused = false
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                viewModel.setShowSettingsDialog(true) 
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = Color(0xFF94A3B8)
                            ),
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
                        }
                    }
                }

                // Dropdown Suggestions (In-layout AnimatedVisibility: Zero window interference, soft keyboard always stays visible)
                AnimatedVisibility(
                    visible = isSearchFocused && viewModel.recentLocations.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .shadow(12.dp, RoundedCornerShape(16.dp))
                            .background(Color(0xF20F172A), RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(16.dp))
                            .padding(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ricerche Recenti",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Chiudi",
                                    color = Color(0xFF34D399),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        isSearchFocused = false
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    }
                                )
                            }
                            viewModel.recentLocations.take(5).forEach { loc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            viewModel.selectSavedLocation(loc)
                                            isSearchFocused = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = loc.shortName,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (loc.displayName.isNotEmpty() && loc.displayName != loc.shortName) {
                                            Text(
                                                text = loc.displayName,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Favorites collapsible section
            if (viewModel.favoriteLocations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x0DFFFFFF))
                        .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    // Header Row (Clickable)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFavoritesExpanded = !isFavoritesExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "I tuoi Preferiti (${viewModel.favoriteLocations.size})",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Rotatable Arrow Icon
                        val rotationAngle by animateFloatAsState(
                            targetValue = if (isFavoritesExpanded) 180f else 0f,
                            label = "arrowRotation"
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = if (isFavoritesExpanded) "Riduci" else "Espandi",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer(rotationZ = rotationAngle)
                        )
                    }

                    // Collapsible Content
                    AnimatedVisibility(
                        visible = isFavoritesExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                viewModel.favoriteLocations.forEach { loc ->
                                    LocationChip(
                                        loc = loc,
                                        onClick = {
                                            focusManager.clearFocus()
                                            viewModel.selectSavedLocation(loc)
                                        },
                                        onRemoveClick = {
                                            viewModel.removeFavoriteLocation(loc)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Error Message
            if (viewModel.errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33EF4444), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0x80EF4444), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = viewModel.errorMessage ?: "",
                        color = Color(0xFFF87171),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Interactive Map Container
            if (viewModel.showMap && viewModel.selectedLatLng != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tocca la mappa per scegliere il punto esatto.",
                        color = Color(0xFF34D399),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))

                val latLng = viewModel.selectedLatLng!!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    MapViewContainer(
                        latitude = latLng.first,
                        longitude = latLng.second,
                        mapStyle = viewModel.mapStyle,
                        onMapClick = { lat, lon ->
                            viewModel.selectLocationFromMap(lat, lon)
                        },
                        heatmapData = viewModel.heatmapData,
                        showHeatmap = viewModel.showHeatmap,
                        modifier = Modifier.fillMaxSize()
                    )

                    // 1. Floating Cloud Layer Toggle in Top-Start (Top-Left) corner
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .zIndex(2f)
                            .padding(10.dp)
                            .shadow(8.dp, RoundedCornerShape(12.dp))
                            .background(
                                if (viewModel.showHeatmap) Color(0xEE065F46) else Color(0xD90F172A),
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                if (viewModel.showHeatmap) Color(0xFF34D399) else Color(0x33FFFFFF),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.toggleHeatmap() }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(text = "☁️", fontSize = 13.sp)
                        Text(
                            text = if (viewModel.showHeatmap) "Nuvola ON" else "Nuvola OFF",
                            color = if (viewModel.showHeatmap) Color(0xFF34D399) else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 2. Floating Fullscreen expand button in Top-End (Top-Right) corner
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .zIndex(2f)
                            .padding(10.dp)
                            .size(38.dp)
                            .shadow(8.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xD90F172A), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                            .clickable { isMapFullscreen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⛶",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Floating Mini-Legend in compact map (when heatmap is enabled)
                    if (viewModel.showHeatmap && viewModel.heatmapData != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .zIndex(2f)
                                .padding(10.dp)
                                .shadow(6.dp, RoundedCornerShape(8.dp))
                                .background(Color(0xD90F172A), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = "Nuvola: ", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                Box(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color(0xFF00C8FF),
                                                    Color(0xFF10B981),
                                                    Color(0xFFFBBF24),
                                                    Color(0xFFEF4444)
                                                )
                                            )
                                        )
                                )
                                Text(text = "Probabilità", color = Color(0xFF34D399), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Analysis Results
            if (viewModel.selectedLatLng != null && !viewModel.isLoading && viewModel.errorMessage == null) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = viewModel.locationName,
                        color = Color(0xFF34D399),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )

                    // Pulsante Naviga (Outdooractive, Komoot, Google Maps, ecc.)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x1F34D399))
                            .border(1.dp, Color(0x4D34D399), RoundedCornerShape(10.dp))
                            .clickable {
                                val latLng = viewModel.selectedLatLng ?: return@clickable
                                NavigationHelper.navigateTo(
                                    context = context,
                                    latitude = latLng.first,
                                    longitude = latLng.second,
                                    label = viewModel.locationName
                                )
                            }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "🧭", fontSize = 13.sp)
                        Text(
                            text = "Naviga",
                            color = Color(0xFF34D399),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Stella preferito
                    val starScale by animateFloatAsState(
                        targetValue = if (viewModel.currentLocationIsFavorite) 1.2f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "starScale"
                    )
                    IconButton(
                        onClick = {
                            viewModel.toggleCurrentFavorite()
                            if (viewModel.currentLocationIsFavorite) {
                                isFavoritesExpanded = true
                            }
                        },
                        modifier = Modifier.size(36.dp).scale(starScale)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = if (viewModel.currentLocationIsFavorite)
                                "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                            tint = if (viewModel.currentLocationIsFavorite)
                                Color(0xFFFBBF24) else Color(0xFF475569),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Badge cache età
                    if (viewModel.isFromCache && viewModel.cacheAgeText != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1F34D399))
                                .border(1.dp, Color(0x6634D399), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Dati da cache locale",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = viewModel.cacheAgeText ?: "",
                                    color = Color(0xFF34D399),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Probability & Metrics Card
                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Top Section: Radial Progress & Growth Status Box
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                RadialProgress(
                                    probability = viewModel.todayProbability,
                                    threshold = viewModel.highlightThreshold
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            GrowthPhaseBadge(
                                phaseText = viewModel.growthPhase,
                                modifier = Modifier.weight(1.2f)
                            )
                        }
                        
                        // Bonus Banner (if available)
                        if (viewModel.habitatBonusText.isNotEmpty()) {
                            BonusBanner(
                                bonusText = viewModel.habitatBonusText,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        HorizontalDivider(
                            color = Color(0x14FFFFFF),
                            thickness = 1.dp
                        )
                        
                        // Parameters Grid: 2 columns using Row + weight
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row 1: Habitat & Altitudine
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricCard(
                                    metricText = viewModel.habitatText,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    metricText = viewModel.altitudeText,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            // Row 2: Pioggia & Temp. Media
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricCard(
                                    metricText = viewModel.rainText,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    metricText = viewModel.tempText,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            // Row 3: Stagione & Luna
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricCard(
                                    metricText = viewModel.seasonText,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    metricText = viewModel.moonPhaseText,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Row 4: SPUN Micorrize (Simbiosi EcM & Rete Ifale)
                            if (viewModel.spunDataAvailable) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MetricCard(
                                        metricText = viewModel.spunEcmText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricCard(
                                        metricText = viewModel.spunHyphalText,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            
                            // Row 5: Versante (Full width)
                            if (viewModel.slopeText.isNotEmpty()) {
                                MetricCard(
                                    metricText = viewModel.slopeText,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // 5-Day Forecast Grid
                if (viewModel.forecastDays.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Tendenza dei prossimi giorni",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.forecastDays.forEach { day ->
                            // Roughly estimate a future probability based on daily average weather
                            val weatherScore = MushroomAlgorithms.getRainStatus(day.totalPrecip.toDouble()).score + 
                                               MushroomAlgorithms.getTempStatus(day.avgTemp.toDouble()).score + 
                                               MushroomAlgorithms.getHumidityScore(day.avgHumidity.toDouble())
                            
                            val rawProb = (100.0 * Math.pow(weatherScore / 100.0, 1.2)).toInt()
                            val finalProb = maxOf(5, minOf(95, (rawProb * 0.8).toInt()))

                            ForecastGridItem(
                                dateStr = day.date,
                                weatherCode = day.weatherCode,
                                avgTemp = day.avgTemp,
                                probability = finalProb,
                                threshold = viewModel.highlightThreshold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Summary Card (AI or Fallback)
                if (viewModel.summaryText.isNotEmpty() || viewModel.isAiLoading) {
                    Spacer(modifier = Modifier.height(24.dp))
                    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Analisi in parole semplici",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            // Gemini Nano Badge – minimal chip
                            if (viewModel.useLocalAi && viewModel.localAiService.isAvailable()) {
                                val geminiGradient = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF9BC5FF),
                                        Color(0xFFD3B7FF),
                                        Color(0xFFFF9B9B)
                                    )
                                )
                                val infiniteTransition = rememberInfiniteTransition(label = "geminiSparkleAnim")
                                val sparkleAlpha by infiniteTransition.animateFloat(
                                    initialValue = 0.6f,
                                    targetValue = 1.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1600, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "sparkleAlpha"
                                )

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(0x1A9BC5FF))
                                        .border(
                                            width = 0.6.dp,
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF9BC5FF).copy(alpha = 0.5f),
                                                    Color(0xFFD3B7FF).copy(alpha = 0.5f)
                                                )
                                            ),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    // 4-pointed sparkle star
                                    Canvas(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .graphicsLayer(alpha = sparkleAlpha)
                                    ) {
                                        val w = size.width
                                        val h = size.height
                                        val path = Path().apply {
                                            moveTo(w / 2f, 0f)
                                            quadraticBezierTo(w * 0.5f, h * 0.5f, w, h * 0.5f)
                                            quadraticBezierTo(w * 0.5f, h * 0.5f, w / 2f, h)
                                            quadraticBezierTo(w * 0.5f, h * 0.5f, 0f, h * 0.5f)
                                            quadraticBezierTo(w * 0.5f, h * 0.5f, w / 2f, 0f)
                                            close()
                                        }
                                        drawPath(path = path, brush = geminiGradient)
                                    }
                                    Text(
                                        text = "Nano",
                                        style = TextStyle(
                                            brush = geminiGradient,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 0.3.sp
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (viewModel.isAiLoading) {
                            ShimmerSummaryLoader()
                        } else {
                            TypewriterText(
                                text = viewModel.summaryText,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Nota bene: Questa è una stima algoritmica. La crescita fungina è un fenomeno biologico complesso, influenzato da microclimi locali. Usa queste informazioni come indicatore e non come certezza.",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        // Loading Overlay
        AnimatedVisibility(
            visible = viewModel.isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC040810)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF34D399),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = viewModel.loadingText,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Fullscreen Map View Overlay
        if (isMapFullscreen && viewModel.selectedLatLng != null) {
            BackHandler {
                isMapFullscreen = false
            }

            val latLng = viewModel.selectedLatLng!!

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF040810))
            ) {
                // 1. Fullscreen Map
                MapViewContainer(
                    latitude = latLng.first,
                    longitude = latLng.second,
                    mapStyle = viewModel.mapStyle,
                    onMapClick = { lat, lon ->
                        viewModel.selectLocationFromMap(lat, lon)
                    },
                    heatmapData = viewModel.heatmapData,
                    showHeatmap = viewModel.showHeatmap,
                    modifier = Modifier.fillMaxSize()
                )

                // 2. Top Header Bar (Floating Glassmorphic)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(20.dp))
                        .background(Color(0xF20D1423), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { isMapFullscreen = false },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x26FFFFFF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Chiudi tutto schermo",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = viewModel.locationName.ifEmpty { "Punto selezionato" },
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = String.format(Locale.US, "Lat: %.4f • Lon: %.4f", latLng.first, latLng.second),
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Controls: Heatmap Toggle (Layer Complementare) & Map Style Selector
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Toggle Livello Nuvola Sovrapposto
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (viewModel.showHeatmap) Color(0x3310B981) else Color(0x14FFFFFF))
                                    .border(
                                        1.dp,
                                        if (viewModel.showHeatmap) Color(0xFF34D399) else Color(0x26FFFFFF),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.toggleHeatmap() }
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(text = "☁️", fontSize = 12.sp)
                                Text(
                                    text = if (viewModel.showHeatmap) "Nuvola ON" else "Nuvola OFF",
                                    color = if (viewModel.showHeatmap) Color(0xFF34D399) else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Separatore visivo
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(Color(0x26FFFFFF))
                            )

                            // Selettore Stile Mappa di Base
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(
                                    Pair("topo", "🏔️"),
                                    Pair("dark", "🕶️"),
                                    Pair("standard", "🗺️")
                                ).forEach { (styleKey, icon) ->
                                    val isSelected = viewModel.mapStyle == styleKey
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(if (isSelected) Color(0xFF34D399) else Color(0x1AFFFFFF))
                                            .clickable {
                                                viewModel.saveSettings(
                                                    style = styleKey,
                                                    radius = viewModel.searchRadius,
                                                    threshold = viewModel.highlightThreshold,
                                                    cacheActive = viewModel.cacheEnabled,
                                                    useLocalAiActive = viewModel.useLocalAi
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = icon, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Floating Bottom Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Floating GPS button on the right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { onGeolocateClick() },
                            modifier = Modifier
                                .size(48.dp)
                                .shadow(10.dp, CircleShape)
                                .background(Color(0xFF1E293B), CircleShape)
                                .border(1.dp, Color(0x3334D399), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Posizione GPS",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Floating Heatmap Legend in Fullscreen mode
                    if (viewModel.showHeatmap && viewModel.heatmapData != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(12.dp, RoundedCornerShape(14.dp))
                                .background(Color(0xF20F172A), RoundedCornerShape(14.dp))
                                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Bassa (25%)",
                                    color = Color(0xFF00C8FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .padding(horizontal = 10.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color(0xFF00C8FF),
                                                    Color(0xFF10B981),
                                                    Color(0xFFFBBF24),
                                                    Color(0xFFF97316),
                                                    Color(0xFFEF4444)
                                                )
                                            )
                                        )
                                )
                                Text(
                                    text = "Hotspot (>80%)",
                                    color = Color(0xFFEF4444),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Bottom Confirmation Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(16.dp, RoundedCornerShape(20.dp))
                            .background(Color(0xF20F172A), RoundedCornerShape(20.dp))
                            .border(1.dp, Color(0x3334D399), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tocca la mappa per spostare il punto",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Aggiornamento previsione in tempo reale",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            OutlinedButton(
                                onClick = {
                                    NavigationHelper.navigateTo(
                                        context = context,
                                        latitude = latLng.first,
                                        longitude = latLng.second,
                                        label = viewModel.locationName
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF34D399)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34D399)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "🧭 Naviga",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = { isMapFullscreen = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF34D399),
                                    contentColor = Color(0xFF0F172A)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Conferma",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Settings Dialog Overlay
        if (viewModel.showSettings) {
            SettingsDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.setShowSettingsDialog(false) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: MushroomViewModel,
    onDismiss: () -> Unit
) {
    var style by remember { mutableStateOf(viewModel.mapStyle) }
    var radius by remember { mutableStateOf(viewModel.searchRadius.toFloat()) }
    var threshold by remember { mutableStateOf(viewModel.highlightThreshold.toFloat()) }
    var cacheActive by remember { mutableStateOf(viewModel.cacheEnabled) }
    var useLocalAiActive by remember { mutableStateOf(viewModel.useLocalAi) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .shadow(24.dp, RoundedCornerShape(28.dp))
                .background(Color(0xFF0F172A), RoundedCornerShape(28.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.width(IntrinsicSize.Max)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Impostazioni",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Map Style Select
                Text(
                    text = "Stile della Mappa",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("topo", "🏔️", "Topo"),
                        Triple("dark", "🕶️", "Scuro"),
                        Triple("standard", "🗺️", "Standard")
                    ).forEach { (key, emoji, name) ->
                        val selected = style == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Color(0xFF34D399) else Color(0xFF1E293B))
                                .clickable { style = key }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(emoji, fontSize = 20.sp)
                                Text(
                                    text = name,
                                    color = if (selected) Color(0xFF040810) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Radius Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Raggio Ricerca Habitat",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${radius.toInt()}m",
                        color = Color(0xFF34D399),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 500f..3000f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFF34D399),
                        thumbColor = Color(0xFF34D399)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Threshold Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Soglia Alta Probabilità",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${threshold.toInt()}%",
                        color = Color(0xFF34D399),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = threshold,
                    onValueChange = { threshold = it },
                    valueRange = 30f..85f,
                    steps = 10,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFF34D399),
                        thumbColor = Color(0xFF34D399)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Local Cache Info Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x330F172A), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Abilita Cache locale",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Salva dati per uso offline",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = cacheActive,
                                onCheckedChange = { cacheActive = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF34D399),
                                    checkedTrackColor = Color(0xFF065F46)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0x14FFFFFF))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Usa IA locale (Gemini Nano)",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StatusLed(statusText = viewModel.aiStatusText)
                                    Text(
                                        text = viewModel.aiStatusText,
                                        color = if (viewModel.aiStatusText.contains("pronto")) Color(0xFF34D399) else Color(0xFF94A3B8),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Switch(
                                checked = useLocalAiActive,
                                onCheckedChange = { useLocalAiActive = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF34D399),
                                    checkedTrackColor = Color(0xFF065F46)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0x14FFFFFF))
                        Spacer(modifier = Modifier.height(12.dp))

                        // SPUN Regional Network Indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Rete Micorrizica SPUN",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (viewModel.spunRegionName != null) {
                                        "Regione attiva: ${viewModel.spunRegionName} (1.5 MB)"
                                    } else {
                                        "Auto-rilevamento regionale (Italia pronta)"
                                    },
                                    color = Color(0xFF34D399),
                                    fontSize = 10.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x1434D399)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "🍄", fontSize = 14.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0x14FFFFFF))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Spazio Utilizzato",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = viewModel.cacheSize,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = { viewModel.clearCache() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0x33EF4444),
                                    contentColor = Color(0xFFF87171)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Svuota Cache", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0x14FFFFFF))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Cronologia Ricerche",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = if (viewModel.recentLocations.isEmpty()) "Vuota" else "${viewModel.recentLocations.size} località",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = { viewModel.clearRecentLocations() },
                                enabled = viewModel.recentLocations.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0x33EF4444),
                                    contentColor = Color(0xFFF87171),
                                    disabledContainerColor = Color(0x0DFFFFFF),
                                    disabledContentColor = Color(0x40FFFFFF)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Cancella", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Apply Button
                Button(
                    onClick = {
                        viewModel.saveSettings(style, radius.toInt(), threshold.toInt(), cacheActive, useLocalAiActive)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34D399)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Applica Impostazioni",
                        color = Color(0xFF040810),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun StatusLed(statusText: String) {
    val isReady = statusText.contains("pronto")
    val isDownloading = statusText.contains("corso")

    val color = when {
        isReady -> Color(0xFF34D399) // Green
        isDownloading -> Color(0xFFF59E0B) // Orange
        else -> Color(0xFF64748B) // Gray
    }

    if (isReady) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val alpha by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )

        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha))
                .border(1.dp, color, CircleShape)
        )
    } else if (isDownloading) {
        val transition = rememberInfiniteTransition(label = "blink")
        val visible by transition.animateFloat(
            initialValue = 0.0f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "blinkVisibility"
        )

        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (visible > 0.5f) color else Color.Transparent)
                .border(1.dp, color, CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
        )
    }
}

@Composable
fun ShimmerSummaryLoader() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslation"
    )

    val shimmerColors = listOf(
        Color(0xFF1E293B),
        Color(0xFF334155),
        Color(0xFF1E293B)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 300f, translateAnim - 300f),
        end = Offset(translateAnim, translateAnim)
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
    }
}

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier
) {
    var textToDisplay by remember { mutableStateOf("") }

    LaunchedEffect(text) {
        textToDisplay = ""
        for (i in 1..text.length) {
            textToDisplay = text.substring(0, i)
            kotlinx.coroutines.delay(10.milliseconds)
        }
    }

    Text(
        text = textToDisplay,
        color = Color(0xFFCBD5E1),
        fontSize = 14.sp,
        lineHeight = 22.sp,
        modifier = modifier
    )
}

