# Myco iOS portability audit

**Data audit:** 2026-09-13

**Repository baseline:** `app/src/main` Android, nessun source set `commonMain`/`iosMain` rilevato

**Scope:** classi e contratti che determinano il porting iOS. Questo documento è un audit dello stato osservato, non una dichiarazione che il porting sia già compilabile.

## Legenda

* **GREEN** — deterministico e senza API di sistema; candidato diretto a `commonMain` (dopo l’eventuale rimozione di dipendenze JVM di supporto).
* **YELLOW** — dominio utile e riusabile, ma con serializzazione, I/O, tempo, coroutine o rete legati alla JVM/Android; richiede estrazione di contratti o sostituzione dell’implementazione.
* **RED** — UI o adapter Android/Google/OsmDroid; da reimplementare nativamente su iOS.

## Matrice di audit

| Area / classi principali | Stato | Evidenza osservata | Destinazione iOS |
|---|---|---|---|
| `model/ProbabilityTier.kt`, `Factor.kt`, `MushroomSpecies.kt`, `EcologicalWeightsConfig.kt`, `TerrainAspectConfig.kt`, `HeatmapRenderConfig.kt`, `HeatmapModel.kt`, `SpunModel.kt` | **GREEN** | Data class, enum e configurazioni senza import Android o framework UI; `HeatmapRaster` è un buffer ARGB + bounding box. | `commonMain`/KMP. Il rendering del buffer resta in SwiftUI/MapKit adapter. |
| `platform/UserLocation.kt` | **GREEN** | Coordinate, heading e modalità mappa sono tipi Kotlin agnostici. | `commonMain`; bridge da `CLLocation`/`CLHeading`. |
| `utils/MushroomAlgorithms.kt` | **YELLOW** | Algoritmi deterministici, ma usa `java.util.Calendar`, `java.util.Date` e `java.util.Locale`. | Portare il calcolo in KMP sostituendo tempo/locale con tipi `kotlinx-datetime` o input già normalizzati. |
| DTO `model/WeatherModel.kt`, `TerrainModel.kt`, `OverpassModel.kt`, `GeocodingModel.kt` | **YELLOW** | Annotazioni `com.google.gson.annotations.SerializedName`; il modello concettuale è condivisibile, il binding non lo è. | DTO/domain in `commonMain`; `Codable`/Foundation in iOS e Gson/adapter Android, oppure serializzazione KMP esplicita. |
| `model/DailyOutlook.kt`, `SavedLocation.kt`, `PlaceName.kt` | **YELLOW** | Formattazione e locale dipendono da `java.text`/`java.util`. | Separare valori domain da formattazione; UI SwiftUI usa `Foundation.DateFormatter`/locale. |
| `platform/AssetProvider.kt` | **YELLOW** | Il contratto espone `java.io.InputStream`. | Contratto a `ByteArray`/reader KMP; implementazione iOS con `Foundation.NSBundle`/`NSData`. |
| `platform/KeyValueStorage.kt`, `PlatformCacheStore.kt`, `PlatformNavigator.kt`, `PlatformLocationProvider.kt`, `PlatformOrientationProvider.kt`, `PlatformAiEngine.kt` | **YELLOW** | Porte corrette concettualmente, ma usano `Flow`/`StateFlow`, payload JSON e primitive temporali; sono ancora sotto `app/src/main` Android. | Trasferire i contratti KMP; adapter iOS con `UserDefaults`, `URLSession`, `CLLocationManager`, heading e motore AI Apple. |
| `platform/InMemoryCacheStore.kt` | **YELLOW** | Implementazione testabile, ma usa `ConcurrentHashMap` e clock JVM (`System.currentTimeMillis`). | Riscrivere con primitive KMP e clock iniettato; utile come fake comune. |
| `repository/CacheManager.kt` | **YELLOW** | Policy riutilizzabile, ma serializzazione Gson e storage concreti passano da contratti JVM/Android. | Conservare la policy; spostare codec e persistenza negli adapter. |
| `repository/SpunDataManager.kt` | **YELLOW** | Parser deterministico, ma dipende da `InputStream`, `DataInputStream`, `ByteBuffer`, `InflaterInputStream` e `Locale`. | Portare il parser su `ByteArray`/zlib KMP o isolare un parser nativo; `NSBundle` fornisce i bytes. |
| `repository/MushroomRepository.kt`, `network/NetworkClient.kt`, `network/ApiServices.kt` | **YELLOW** | Aggregazione di rete e Gson/Retrofit; non è UI ma non è core deterministico. | Mantenere use case/domain in KMP; implementare trasporto con `URLSession` + Foundation su iOS e Retrofit su Android. |
| `utils/HeatmapGenerator.kt` | **YELLOW** | Il generatore raster è quasi interamente puro, ma il file importa anche `platform.android.HeatmapData`, `toHeatmapData`, coroutine e `SpunDataManager`. | Separare il kernel che produce `HeatmapRaster` dagli adapter/repository; conversione raster/overlay in MapKit (`MKOverlay`/renderer). |
| `ui/viewmodel/MushroomViewModel.kt` | **RED** | Importa Compose, Android lifecycle e `platform.android.HeatmapData`; contiene stato di presentazione Android. | Nuovo `ObservableObject` SwiftUI; eventuale orchestrazione condivisa deve essere un use case KMP privo di Compose. |
| `MainActivity.kt`, `ui/screens/*`, `ui/components/*`, `ui/theme/*` | **RED** | Jetpack Compose, Android lifecycle, `Context`, OsmDroid e Material 3. | App entry point SwiftUI, navigation/state SwiftUI, design system SwiftUI. |
| `platform/android/*` | **RED** | `Context`, Fused Location, `SensorManager`, `SharedPreferences`, SQLiteOpenHelper e `Bitmap`. | Adapter nativi iOS: CoreLocation, `CLHeading`, `UserDefaults`, SQLite/SQLDelight, `CGImage`/MapKit. |
| `network/LocalAiService.kt` | **RED** | Android AICore/Gemini Nano e `Context` importati direttamente. | Adapter separato per Core ML/Apple Intelligence; fallback deterministico resta nel core. |
| `utils/NavigationHelper.kt` | **RED** | `Context` e `AndroidPlatformNavigator` importati direttamente. | Usare esclusivamente `PlatformNavigator`; adapter iOS con `MKMapItem`/Apple Maps. |

## Risultato sintetico

Il porting non è ancora in stato GREEN end-to-end: il repository è un modulo Android JVM e non contiene una configurazione KMP. Il nucleo scientifico è un buon candidato, ma l’audit rileva tre blocchi prima di dichiarare una condivisione reale:

1. eliminare `java.*`/Gson/stream JVM dai contratti e dai calcoli candidati al core;
2. separare il kernel `HeatmapRaster` dal percorso `HeatmapData` Android oggi presente in `HeatmapGenerator` e nel ViewModel;
3. sostituire l’intera superficie UI/mapper/sensori con SwiftUI, MapKit e CoreLocation.

## Tracciabilità delle evidenze

* `settings.gradle` e `app/build.gradle`: progetto Android/Gradle senza target KMP osservato.
* `app/src/main/java/github/naturewhisp/myco/MainActivity.kt`: entry point `ComponentActivity` e dipendenze Android/Compose.
* `app/src/main/java/github/naturewhisp/myco/utils/HeatmapGenerator.kt`: dipendenza diretta da `platform.android.HeatmapData`.
* `app/src/main/java/github/naturewhisp/myco/ui/viewmodel/MushroomViewModel.kt`: Compose/ViewModel e `HeatmapData` Android.
* `app/src/main/java/github/naturewhisp/myco/ui/components/MapViewContainer.kt`: OsmDroid `MapView`/overlay.
* `app/src/main/java/github/naturewhisp/myco/platform/AssetProvider.kt` e `repository/SpunDataManager.kt`: stream e decompressione JVM.
* `app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt`: calcolo condivisibile, ma con `java.util`.

## Rischi residui

* Divergenza numerica tra Kotlin/Native e Swift se il parser SPUN, le date o il raster vengono riscritti senza fixture binarie e golden test.
* `HeatmapRaster` usa ARGB int: va definito un contratto esplicito di byte order/alpha prima del bridge a `CGImage`.
* Permessi e ciclo di vita CoreLocation/heading devono essere gestiti dalla scena SwiftUI, non dal core.
* Il motore AI iOS non è equivalente garantito a Gemini Nano: il fallback deterministico deve rimanere sempre disponibile.
* La compatibilità offline dipende da un adapter cache/asset iOS verificato con asset SPUN incluso nel bundle.
