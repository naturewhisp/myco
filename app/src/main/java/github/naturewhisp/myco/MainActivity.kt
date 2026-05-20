package github.naturewhisp.myco

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import github.naturewhisp.myco.repository.CacheManager
import github.naturewhisp.myco.repository.MushroomRepository
import github.naturewhisp.myco.ui.screens.MushroomApp
import github.naturewhisp.myco.ui.theme.MycoTheme
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
        super.onCreate(savedInstanceState)

        // Initialize OsmDroid Configuration
        Configuration.getInstance().load(
            applicationContext,
            applicationContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

        val cacheManager = CacheManager(applicationContext)
        val repository = MushroomRepository(cacheManager)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MushroomViewModel(repository, cacheManager) as T
            }
        })[MushroomViewModel::class.java]

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            MycoTheme {
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
        viewModel.selectLocation(45.0, 9.0, "Caricamento posizione...") // temporary placeholder
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                viewModel.selectLocation(
                    location.latitude,
                    location.longitude,
                    "La tua posizione"
                )
            } else {
                // Default location when GPS coordinates are unavailable (e.g. emulator, GPS disabled)
                viewModel.selectLocation(41.8902, 12.4922, "Roma (GPS non disponibile)")
            }
        }.addOnFailureListener {
            viewModel.selectLocation(41.8902, 12.4922, "Roma (GPS fallito)")
        }
    }
}
