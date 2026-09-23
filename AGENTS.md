# AGENTS.md - Myco Developer & AI Agent Guidelines

This document defines the architectural guidelines, development workflows, and coding standards for AI coding agents (and human contributors) working on the **Myco** codebase.

---

## 1. Core Rule: Zero Diagnostic Policy (Strict)

- **0 Compiler Warnings, 0 Lint Warnings, 0 Diagnostic Errors**.
- Every modification must leave the codebase completely clean.
- Before considering any coding task complete, agents **must run and verify**:
  1. `./gradlew lintDebug` (Static code analysis must report `0 errors, 0 warnings`).
  2. `./gradlew compileDebugKotlin` (Kotlin compiler must report clean build).
  3. `./gradlew testDebugUnitTest` (All unit tests, invariants and benchmarks must pass 100%).
  4. `./gradlew assembleDebug` (Debug APK packaging must succeed).
  5. If an Android device or emulator is connected (`adb devices`), stream-install the APK (`adb install -r`), launch the app, check logcat for zero runtime crashes, and capture screenshot proof.

---

## 2. Project Overview & Tech Stack

**Myco** is an Android application for foragers, evaluating mushroom growth probabilities by aggregating real-time weather data, historical precipitation, soil humidity, and OSM habitat layers, enhanced with local on-device AI inference (Gemini Nano).

* **Platform & SDK**: Android (`minSdk 31`, `targetSdk 36`, `compileSdk 36`, Java 17).
* **Build System**: Gradle 9.7.1, Android Gradle Plugin 9.3.2 (AGP 9+ with native built-in Kotlin support).
* **UI**: 100% Jetpack Compose with Material 3 (No Android Fragments / XML layouts).
* **Networking**: Retrofit 2 + Gson, OkHttp 3.
* **Mapping**: OsmDroid (OpenStreetMap).
* **AI / ML**: Google AI Edge AICore for on-device Gemini Nano inference.
* **Architecture**: Modern Android Architecture (MVVM + Repository Pattern + Single Source of Truth).

---

## 3. Directory & Architecture Map

```
app/src/main/
├── AndroidManifest.xml
├── java/github/naturewhisp/myco/
│   ├── MainActivity.kt                # Single-activity Compose entrypoint (ComponentActivity)
│   ├── model/                         # Data classes, DTOs & HeatmapRaster
│   ├── platform/                      # Ports & Adapters (AssetProvider, KeyValueStorage, PlatformAiEngine, etc.)
│   ├── network/                       # Remote & local AI services
│   ├── repository/                    # Data aggregation & caching (CacheManager, SpunDataManager)
│   ├── ui/
│   │   ├── components/                # Modular Compose components (MapView, ScoreCards, etc.)
│   │   ├── screens/                   # MushroomApp composable & main screen workflows
│   │   ├── theme/                     # Compose Material3 theme, typography, color palettes
│   │   └── viewmodel/
│   │       └── MushroomViewModel.kt   # Central state, coroutines, settings & search logic
│   └── utils/
│       └── MushroomAlgorithms.kt      # Mathematical modeling of fungal fruiting probability & HeatmapGenerator
└── res/                               # Assets, mipmap adaptive icons, backup rules (no XML layouts)
docs/
└── IOS_ARCHITECTURE.md                # Hexagonal architecture blueprint & iOS porting roadmap
```

---

## 4. Coding Standards & Conventions

### 4.1 Kotlin & Android Best Practices
- **Android KTX Extensions**: Always use KTX extension functions (e.g., `prefs.edit { putString(...) }` instead of legacy chaining `.edit().putString(...).apply()`; `createBitmap(...)` from `androidx.core.graphics` instead of `Bitmap.createBitmap(...)`).
- **Platform-Agnostic Core**: Business logic, mycological algorithms, data parsers (SPUN), and models must remain 100% pure Kotlin with zero `android.*` imports. Operating system capabilities must be accessed exclusively through `github.naturewhisp.myco.platform` interfaces to ensure seamless compatibility with iOS.
- **Raster & Pixel Buffering**: Algorithmic raster engines must produce raw 32-bit ARGB pixel arrays (`HeatmapRaster`) via bitwise operations rather than directly allocating or drawing onto platform graphics objects (`android.graphics.Bitmap`, `Color`).
- **Time & Durations**: Use typed Kotlin `Duration` (e.g. `import kotlin.time.Duration.Companion.milliseconds` -> `delay(10.milliseconds)`) rather than raw `Long` millisecond overloads.
- **Explicit Locales**: Never call `String.format(...)` without an explicit `Locale`. Use `Locale.getDefault()` for UI strings, or `Locale.US` for coordinate numbers, query keys, or serialization.
- **Unused Exception Syntax**: Catch blocks with intentionally unused exceptions must use `catch (_: Exception)`.
- **Pure Compose Activity**: `MainActivity` inherits from `ComponentActivity`. Do not introduce legacy Fragment dependencies or FragmentActivity workarounds.
- **KMP `:core` & Android Algorithmic Lockstep**: Any update to the mycological algorithm, response curves (e.g. nocturnal chilling, DTR, hurdle model), asymptotic calibrations (`growthProbability`), or taxonomic catalogs (`SPECIES_CATALOG`) must be co-evolved immediately in `core/src/commonMain/kotlin/github/naturewhisp/myco/core/`. Parity tests (`ScientificParityTest`, `FullAnalysisResultParityTest`, `CrossPlatformScientificParityTest`) must remain 100% synchronized with zero tolerance for drift.

### 4.2 Formatting & Style (`.editorconfig`)
- Indentation: 4 spaces for Kotlin/Java/XML/Gradle; 2 spaces for JSON/YAML.
- Trailing commas: Enabled on multi-line parameter lists for clean git diffs.
- No wildcard imports: Explicitly import individual symbols (`ij_kotlin_name_count_to_use_star_import = 999`).

### 4.3 Code Analysis & Linter Configuration
- Rules are defined in `app/lint.xml` and enforced via `app/build.gradle` (`lint { ... }`).
- Code analysis produces SARIF reports (`lint-results-debug.sarif`) compatible with GitHub Code Scanning and Visual Studio Code Analysis.
- Elevated severities: `DefaultLocale`, `UnusedResources`, `ObsoleteSdkInt`, `MonochromeLauncherIcon`, `UseKtx`, and `Security` issues are treated as build-blocking errors.

### 4.4 Jetpack Compose Tabular Layouts & Data Contracts
- **Prevent Horizontal Starvation**: In horizontal `Row` layouts featuring a weighted left column and a right-aligned value/badge:
  - The left `Column` must specify `Modifier.weight(1f, fill = true)`.
  - The right `Text` must set `textAlign = TextAlign.End` and must receive strictly compact, atomic tokens (e.g., `"Sud"`, `"1422 m"`, `"20.0°C"`).
  - Extended descriptions or narrative explanations must **never** be passed into compact value slots; they belong strictly in dedicated subtitle/detail fields.
  - Rows must enforce consistent vertical rhythm (e.g., `Modifier.heightIn(min = 48.dp)` and uniform vertical padding).
- **Prevent Header & Action Collisions**: In horizontal card headers or rows pairing a label with an action control/button:
  - The label must specify `Modifier.weight(1f, fill = false)` or `Modifier.weight(1f)` to guarantee responsive space allocation.
  - Action buttons inside compact headers must use bounded internal padding (e.g. `contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)`) and bounded height (`Modifier.heightIn(min = 32.dp)`).
  - An explicit horizontal `Spacer(Modifier.width(8.dp))` must separate label and action button to guarantee zero overlap across all screen widths.

### 4.5 Design Tokens, Iconography & Resource Cleanliness
- **Zero Hardcoded Colors**: `Color(0x...)` definitions must reside exclusively in `ui/theme/Color.kt`. Composable functions must access colors via `MaterialTheme.colorScheme` or custom theme attributes (`HerbariumTheme`).
- **Zero Emojis in Kotlin**: Do not use Unicode emojis in `.kt` source files. Use Material Icons M3 (`Icons.Outlined.*`, `Icons.Filled.*`) or typographic glyphs (`✳`).
- **Strict Resource Hygiene**: With `UnusedResources` elevated to a fatal lint error, never add speculative entries to `res/values/strings.xml`. Every declared resource must be actively referenced.

### 4.6 Mapping & OsmDroid Invariants
- **Disable Vertical Repetition**: Always set `isVerticalMapRepetitionEnabled = false` on `MapView` instances to prevent vertical world-tiling on tall mobile displays.
- **Enforce Zoom Clamping**: Always specify bounded zoom limits (e.g., `minZoomLevel = 4.0`, `maxZoomLevel = 20.0`).
- **Sane Geographic Initialization**: When coordinates are null/unselected, initialize the viewport to a sensible regional centroid (e.g. Central Italy `GeoPoint(42.5, 12.5)` at zoom `6.0`) rather than leaving OsmDroid at zoom `0.0`.
- **Cartographic Heatmap Balance (Legibility vs Design)**:
  - **Balanced Progressive Opacity**: Overlays placed atop dense topographic maps (contours, elevation relief, roads) must never use flat low opacity (e.g. 50% washes out) nor heavy opacity (>80% blinds underlying topography). Opacity must scale dynamically across probability tiers within a balanced window ($115 \dots 180$, $\sim 45\% \dots 70\%$).
  - **Chromatic Anchor Separation**: Multi-tier palettes must span distinct, readable spectral anchors (botanical green $\to$ golden amber $\to$ cinnabar terracotta $\to$ crimson garnet) rather than clustering in narrow monochromatic brown/pastel hues that camouflage against mountain terrain.
  - **Smoothstep Zonal Delineation**: To make probability zones identifiable without pixelation or stair-stepping, each tier must maintain a distinct core color plateau with smooth $C^1$ smoothstep transitions around boundary thresholds.

### 4.7 Scientific & Ecological Modeling Standards (Revisione v1.3 & Percorso A/B)
- **Continuous Biological Curves**: Model environmental variables (temperature, soil moisture, precipitation, elevation) with continuous normalized response functions ($0.0 \dots 1.0$) rather than discrete step functions. Small continuous parameter perturbations must never create abrupt step jumps.
- **Normalized Suitability Calibration (Indice Euristico, 0–100 — Percorso A)**:
  - The primary output of Myco is an **heuristic environmental suitability score** ($0 \dots 100$, `suitabilityScore`) designed for relative comparison across sites and dates.
  - It must **NEVER** be presented as a frequentist probability of foraging success ("7 uscite su 10") without empirical ground-truth calibration (reserved for Percorso B post-Citizen Science).
  - Raw score is computed as:
    $$S_{\text{raw}} = 100 \times (W / 100)^{1.2} \times H \times A \times S \times T \times p_{\text{hurdle}} \times \Phi_{\text{phase}}$$
    $$S_{\text{calibrated}} = \begin{cases} S_{\text{raw}} & \text{if } S_{\text{raw}} \le 70.0 \\ 70.0 + 22.0 \cdot \tanh\left(\frac{S_{\text{raw}} - 70.0}{22.0}\right) & \text{if } S_{\text{raw}} > 70.0 \end{cases}$$
- **Phenological Inertia & Continuous Rainfall Maturation**:
  - Precipitation must be weighted using a continuous unimodal phenological kernel:
    $$f_{\text{species}}(\tau) = \left(\frac{\tau}{\tau_{\text{peak}}}\right)^\alpha \exp\left(-\alpha\left(\frac{\tau}{\tau_{\text{peak}}} - 1\right)\right)$$
  - Mass conservation must be strictly preserved across rainfall clusters ($R_1 + R_2 = R_{\text{total}}$); clustering must never drop real rainfall volume.
  - Multi-event transitions must be continuous ($C^1$ smoothness); **never apply hard discontinuous threshold resets** (e.g., $R_{\text{recent}} \ge 0.70 \cdot R_{\text{earlier}}$) that induce artificial double-digit point drops for infinitesimal variations ($\Delta R \approx 0.02\text{ mm}$).
- **Soil Moisture Modeling & Aeration**:
  - Model soil moisture response continuously. When volumetric water content approaches saturation ($\theta > 0.38\text{ m}^3/\text{m}^3$), apply continuous damping representing oxygen depletion, falling toward the biological floor ($0.15 \dots 0.20$) in acute waterlogging ($\theta > 0.44\text{ m}^3/\text{m}^3$). Distinguish empirical smoothstep functions from full van Genuchten hydraulic retention parameters.
- **Nocturnal Chilling, Thermal Range & Cardinal Models**:
  - Apply continuous nocturnal chilling inhibition via smoothstep when $T_{\min}$ falls below species tolerance. Modulate phenological recovery stasis upon chilling trauma. Apply continuous DTR damping ($12^\circ\text{C} \dots 18^\circ\text{C}$).
  - Temperature response curves (Rosso cardinal or Yan & Hunt) must be continuously differentiable across their entire domain ($T_{\min} < T_{\text{opt}} < T_{\max}$) without internal singularities, undefined ratios, or unreachable rational branches.
- **Forest Canopy Microclimate Buffering (De Frenne Offset)**:
  - Macroclimatic 2m open-air data must be modulated under forest canopies using continuous De Frenne buffering ($C_f \in [0.0, 1.0]$). Vegetative cooling for $T_{\max}$, radiative blanket insulation for $T_{\min}$, and species-appropriate throughfall interception.
- **Two-Stage Hurdle Model & Liebig Growth Phase Gating**:
  - Zero-inflation hurdle $p_{\text{hurdle}}$ via Weibull CDF gates obligate ectomycorrhizal taxa on unsuitable open land while permitting meadow saprotrophs to fruit.
  - The phenological growth phase multiplier $\Phi_{\text{phase}} \in [0.35, 1.0]$ prevents premature false positives during mycelial hydration and primordiation, with smooth transitions between biological stages.
- **Stand Basal Area ($G$) & Species-Aware Habitat**:
  - Unimodal CTFC density response curves centered on species-specific $G_{\text{opt}}$.
  - Habitat scoring (`evaluateSpeciesHabitat`) must dynamically adapt to active species ecology, distinguishing ectomycorrhizal symbioses, meadow saprotrophs, and wood-decay taxa.
- **Geospatial & SPUN Data Integrity**:
  - Habitat evaluation must rely on actual polygon geometries/surfaces and distance-based intersections, rather than discrete OSM node counts.
  - SPUN assets must carry explicit provenance, version, and SHA256 checksums. Arbuscular mycorrhizal (AM) hyphal density must NOT be conflated with ectomycorrhizal or saprotrophic mushroom biomass. Bilinear resampling must mask `NoData` before interpolation.
- **KMP Engine Parity & Outlook Coherence**:
  - Calculations across Android, iOS, and Heatmap must use the unified KMP engine in `:core` (`commonMain`). All prospective outlook days and raster pixels must share identical environmental inputs, formulas, and timezone-aware dates.
- **Testing & Verification Policy (REG-01..20)**:
  - All modifications must satisfy the regression test suite REG-01..20, verifying Lipschitz continuity ($|\Delta S| \le 5\%$ for small perturbations), mass conservation, cardinal temperature validity, absence of NaN/infinite values, and real-world calendar correctness (no fictitious dates).
  - Synthetic scenario tests must be strictly segregated from empirical historical observation benchmarks.
- **Runtime Phenological Configuration Parity**: All production runtime invocations of algorithmic entrypoints in `MushroomViewModel`, background tasks, or summary generators MUST explicitly supply `config = EcologicalWeightsConfig.PHENOLOGICAL`. Silent fallback to `EcologicalWeightsConfig.DEFAULT` is strictly reserved for legacy oracle regression tests.

### 4.8 Lifecycle, Hardware Sensors & Concurrency Invariants
- **Lifecycle-Bound Hardware Sensors**: Do NOT use naked `DisposableEffect(Unit)` for battery-intensive hardware listeners (GPS updates, rotation vector compass, barometer). Sensors must be bound to `LocalLifecycleOwner.current` via `LifecycleEventObserver`, starting exclusively on `Lifecycle.Event.ON_RESUME` (or `ON_START`) and stopping immediately on `Lifecycle.Event.ON_PAUSE` (or `ON_STOP`).
- **Geospatial Hardware Sensor Isolation**: Physical device hardware sensors (rotation vector compass, orientation, gyroscope, barometer) must ONLY be queried or applied if:
  $$\text{haversineDistance}(lat_{\text{user}}, lon_{\text{user}}, lat_{\text{target}}, lon_{\text{target}}) \le 50\text{ meters}$$
  or if inspecting the user's current GPS position. When inspecting remote map coordinates ($\Delta d > 50\text{ m}$), heading-up rotation and device compass cones must be strictly disabled/neutralized.
- **Unambiguous Environmental Telemetry Labels**: Never label remote agrometeorological or satellite reanalysis data (Open-Meteo, ERA5-Land, DEM) as "Sensori" or "Sensori del dispositivo". The UI and documentation must strictly and unambiguously distinguish on-board hardware sensors from remote meteorological models.
- **OsmDroid Lifecycle Hygiene**: All `MapView` instances hosted in `AndroidView` must explicitly receive `onResume()` on `ON_RESUME`, `onPause()` on `ON_PAUSE`, and `onRelease = { it.onDetach() }` to terminate background tile download workers and prevent Activity context leaks.
- **Interactive ViewModel Concurrency (Anti-Stale Overrides)**: When user actions (e.g. map tapping, location search) trigger asynchronous data fetches, the ViewModel must maintain an explicit `Job?` reference (e.g. `dataFetchJob`). Any in-flight job must be deterministically cancelled (`dataFetchJob?.cancel()`) prior to launching a new request. All active coroutine jobs must be explicitly cancelled in `onCleared()`.

### 4.9 Mycological Safety & Toxic Look-Alikes Standards
- **Mandatory Safety Metadata**: Every entry in the species catalog (`MushroomSpecies`) must document potential toxic look-alikes (`toxicLookAlikes: List<String>`) and, where applicable, culinary/health precautions (`edibilityWarning: String?`).
- **Visual Warning Visibility**: Whenever a selected species possesses toxic look-alikes, the UI must display a high-visibility warning badge (`Icons.Outlined.Warning` with warning tint) and list the confusion species.
- **Persistent Safety Disclaimer**: A blocking legal/health disclaimer modal (`SafetyDisclaimerDialog`) must be presented on first launch, requiring explicit user acknowledgment before dashboard interaction. The preference must be stored in `KeyValueStorage`.

### 4.10 Documentation & Storage Synchronization Protocol
- **Documentation Co-Evolution**: Any modification that resolves, mitigates, or introduces a technical debt or roadmap milestone must immediately update:
  1. The status column and release plan in `docs/FUTURE_DEVELOPMENTS_ANALYSIS.md`.
  2. The package/component inventory in `docs/TECHNICAL_DOCUMENTATION.md`.
- **Cache Isolation & Preference Protection**: When adding new user preferences to `KeyValueStorage`, verify that `CacheManager.clearCache()` preserves them and clears strictly ephemeral API response caches. Never invoke blanket `.clear()` without protecting user settings.

### 4.11 iOS & Swift Concurrency Testing Invariants
- **XCTest Asynchronous Polling Timeout**: In XCTest suites testing asynchronous state transitions or actor-hopping workflows (`@MainActor`, `Task.detached`), polling helper functions (`waitUntil`) must configure a minimum timeout of **5.0 seconds** (`Duration.seconds(5)`). This guarantees resilience against thread-scheduling and CPU latency spikes on virtualized macOS CI runners, while preserving sub-millisecond execution when conditions are met immediately.

### 4.12 CI / GitHub Actions SDK Configuration Standard
- **Android SDK Setup Action (`setup-android@v3`)**: Whenever `android-actions/setup-android@v3` is referenced in `.github/workflows/*.yml`, agents must explicitly set `with: packages: ''` to prevent fatal failures caused by Google's permanent removal of the deprecated legacy `tools` package from `dl.google.com`. Required SDK components (`platforms`, `build-tools`, `cmdline-tools`) must be installed explicitly via subsequent `sdkmanager` steps.

---

## 5. Verification Commands for Agents

Run these commands after making changes:

```pwsh
# 1. Run full unit test suite, invariants and ground truth benchmarks
.\gradlew.bat testDebugUnitTest --rerun-tasks

# 2. Run full static code analysis (Lint + SARIF report)
.\gradlew.bat lintDebug

# 3. Compile Kotlin sources
.\gradlew.bat compileDebugKotlin

# 4. Assemble complete debug APK
.\gradlew.bat assembleDebug

# 5. On-Device Verification (MANDATORY whenever an ADB device is connected):
# Check connected device: adb devices
adb -s <DEVICE_ID> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <DEVICE_ID> shell am force-stop github.naturewhisp.myco
adb -s <DEVICE_ID> logcat -c
adb -s <DEVICE_ID> shell am start -n github.naturewhisp.myco/.MainActivity
adb -s <DEVICE_ID> logcat -d -e "AndroidRuntime|FATAL|naturewhisp"
adb -s <DEVICE_ID> shell screencap -p /sdcard/verify.png
adb -s <DEVICE_ID> pull /sdcard/verify.png
```
