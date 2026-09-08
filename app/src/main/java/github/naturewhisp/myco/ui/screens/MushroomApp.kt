package github.naturewhisp.myco.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.naturewhisp.myco.ui.components.SpeciesSelectionSheet
import github.naturewhisp.myco.ui.theme.MycoTheme
import github.naturewhisp.myco.ui.viewmodel.MushroomViewModel

// Coordinatore dell'applicazione Myco secondo l'architettura Herbarium
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushroomApp(
    viewModel: MushroomViewModel,
    onGeolocateClick: () -> Unit
) {
    val mycoColors = MycoTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Gestione del tasto indietro per ritornare al registro principale dai tab secondari
    if (!viewModel.showSettings && viewModel.currentScreenTab != 0) {
        BackHandler {
            viewModel.setScreenTab(0)
        }
    }

    // Gestione della schermata delle impostazioni come overlay
    if (viewModel.showSettings) {
        BackHandler {
            viewModel.showSettings = false
        }
        SettingsScreen(
            viewModel = viewModel,
            onBackClick = { viewModel.showSettings = false }
        )
    } else {
        // Interfaccia principale a tre sezioni con barra di navigazione Herbarium
        Scaffold(
            bottomBar = {
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = mycoColors.ruleSubtle
                        ),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    val tabs = listOf(
                        Triple(0, "Registro", Icons.AutoMirrored.Filled.MenuBook),
                        Triple(1, "Previsioni", Icons.Default.CalendarMonth),
                        Triple(2, "Mappa", Icons.Default.Map)
                    )

                    tabs.forEach { (index, label, icon) ->
                        val selected = viewModel.currentScreenTab == index
                        NavigationBarItem(
                            selected = selected,
                            onClick = { viewModel.setScreenTab(index) },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = mycoColors.favorable,
                                selectedTextColor = mycoColors.favorable,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (viewModel.currentScreenTab) {
                    0 -> HomeScreen(
                        viewModel = viewModel,
                        onGeolocateClick = onGeolocateClick,
                        onOpenMapTab = { viewModel.setScreenTab(2) },
                        onOpenForecastTab = { viewModel.setScreenTab(1) },
                        onOpenSettings = { viewModel.showSettings = true }
                    )
                    1 -> ForecastScreen(
                        viewModel = viewModel
                    )
                    2 -> MapScreen(
                        viewModel = viewModel,
                        onGeolocateClick = onGeolocateClick
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet per la selezione della specie fungina
    if (viewModel.targetSpeciesSheetOpen) {
        SpeciesSelectionSheet(
            selectedSpecies = viewModel.selectedSpecies,
            onSpeciesSelected = { species ->
                viewModel.selectSpecies(species)
                viewModel.setTargetSpeciesSheetVisibility(false)
            },
            onDismissRequest = {
                viewModel.setTargetSpeciesSheetVisibility(false)
            },
            sheetState = sheetState
        )
    }
}
