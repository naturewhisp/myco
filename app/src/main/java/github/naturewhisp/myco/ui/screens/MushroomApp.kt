package github.naturewhisp.myco.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import github.naturewhisp.myco.ui.components.*
import github.naturewhisp.myco.ui.viewmodel.MushroomViewModel
import github.naturewhisp.myco.utils.MushroomAlgorithms

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushroomApp(
    viewModel: MushroomViewModel,
    onGeolocateClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

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
                            viewModel.searchLocation()
                        }),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            onGeolocateClick()
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
                        onClick = { viewModel.setShowSettingsDialog(true) },
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
                Text(
                    text = "Tocca sulla mappa per impostare le coordinate precise.",
                    color = Color(0xFF34D399),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                val latLng = viewModel.selectedLatLng!!
                MapViewContainer(
                    latitude = latLng.first,
                    longitude = latLng.second,
                    mapStyle = viewModel.mapStyle,
                    onMapClick = { lat, lon ->
                        viewModel.selectLocation(lat, lon, "Punto selezionato")
                    }
                )
            }

            // Analysis Results
            if (viewModel.selectedLatLng != null && !viewModel.isLoading && viewModel.errorMessage == null) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = viewModel.locationName,
                    color = Color(0xFF34D399),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
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
                            
                            // Row 4: Versante (Full width)
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

                // Simple Summary Card
                if (viewModel.summaryText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Analisi in parole semplici",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = viewModel.summaryText,
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
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
                                Text(
                                    text = viewModel.aiStatusText,
                                    color = if (viewModel.aiStatusText.contains("pronto")) Color(0xFF34D399) else Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
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
