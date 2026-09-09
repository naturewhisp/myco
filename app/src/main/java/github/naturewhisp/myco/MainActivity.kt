package github.naturewhisp.myco

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import github.naturewhisp.myco.network.LocalAiService
import github.naturewhisp.myco.platform.android.AndroidLocationProvider
import github.naturewhisp.myco.platform.android.AndroidSensorOrientationProvider
import github.naturewhisp.myco.repository.CacheManager
import github.naturewhisp.myco.repository.MushroomRepository
import github.naturewhisp.myco.repository.SpunDataManager
import github.naturewhisp.myco.ui.screens.MushroomApp
import github.naturewhisp.myco.ui.theme.MycoTheme
import github.naturewhisp.myco.ui.theme.ThemeMode
import github.naturewhisp.myco.ui.theme.ThemePreference
import github.naturewhisp.myco.ui.viewmodel.MushroomViewModel
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var viewModel: MushroomViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            getCurrentLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize OsmDroid Configuration
        Configuration.getInstance().load(
            applicationContext,
            applicationContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

        val cacheManager = CacheManager(applicationContext)
        val spunDataManager = SpunDataManager(applicationContext)
        val repository = MushroomRepository(cacheManager, spunDataManager)
        val localAiService = LocalAiService(applicationContext)
        val themePreference = ThemePreference(applicationContext)
        val locationProvider = AndroidLocationProvider(applicationContext)
        val orientationProvider = AndroidSensorOrientationProvider(applicationContext)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MushroomViewModel(
                    repository = repository,
                    cacheManager = cacheManager,
                    localAiService = localAiService,
                    spunDataManager = spunDataManager,
                    themePreference = themePreference,
                    locationProvider = locationProvider,
                    orientationProvider = orientationProvider
                ) as T
            }
        })[MushroomViewModel::class.java]

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            val currentThemeMode by themePreference.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            MycoTheme(themeMode = currentThemeMode) {
                MushroomApp(
                    viewModel = viewModel,
                    onGeolocateClick = { checkLocationPermissions() }
                )
            }
        }
    }

    private fun checkLocationPermissions() {
        val finePermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarsePermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (finePermission == PackageManager.PERMISSION_GRANTED ||
            coarsePermission == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                viewModel.selectLocationFromGps(location.latitude, location.longitude)
            } else {
                // GPS non disponibile (emulatore, GPS spento) — fallback su Roma
                viewModel.selectLocation(41.8902, 12.4922, "Roma (GPS non disponibile)")
            }
        }.addOnFailureListener {
            viewModel.selectLocation(41.8902, 12.4922, "Roma (GPS fallito)")
        }
    }
}
