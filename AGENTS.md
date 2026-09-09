# AGENTS.md - Myco Developer & AI Agent Guidelines

This document defines the architectural guidelines, development workflows, and coding standards for AI coding agents (and human contributors) working on the **Myco** codebase.

---

## 1. Core Rule: Zero Diagnostic Policy (Strict)

- **0 Compiler Warnings, 0 Lint Warnings, 0 Diagnostic Errors**.
- Every modification must leave the codebase completely clean.
- Before considering any coding task complete, agents **must run and verify**:
  1. `./gradlew lintDebug` (Static code analysis must report `0 errors, 0 warnings`).
  2. `./gradlew compileDebugKotlin` (Kotlin compiler must report clean build).
  3. `./gradlew assembleDebug` (Debug APK packaging must succeed).

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
└── MACOS_ARCHITECTURE.md              # Hexagonal architecture blueprint & macOS porting roadmap
```

---

## 4. Coding Standards & Conventions

### 4.1 Kotlin & Android Best Practices
- **Android KTX Extensions**: Always use KTX extension functions (e.g., `prefs.edit { putString(...) }` instead of legacy chaining `.edit().putString(...).apply()`; `createBitmap(...)` from `androidx.core.graphics` instead of `Bitmap.createBitmap(...)`).
- **Platform-Agnostic Core**: Business logic, mycological algorithms, data parsers (SPUN), and models must remain 100% pure Kotlin with zero `android.*` imports. Operating system capabilities must be accessed exclusively through `github.naturewhisp.myco.platform` interfaces to ensure seamless compatibility with macOS/Desktop.
- **Raster & Pixel Buffering**: Algorithmic raster engines must produce raw 32-bit ARGB pixel arrays (`HeatmapRaster`) via bitwise operations rather than directly allocating or drawing onto platform graphics objects (`android.graphics.Bitmap`, `Color`).
- **Time & Durations**: Use typed Kotlin `Duration` (e.g. `import kotlin.time.Duration.Companion.milliseconds` -> `delay(10.milliseconds)`) rather than raw `Long` millisecond overloads.
- **Explicit Locales**: Never call `String.format(...)` without an explicit `Locale`. Use `Locale.getDefault()` for UI strings, or `Locale.US` for coordinate numbers, query keys, or serialization.
- **Unused Exception Syntax**: Catch blocks with intentionally unused exceptions must use `catch (_: Exception)`.
- **Pure Compose Activity**: `MainActivity` inherits from `ComponentActivity`. Do not introduce legacy Fragment dependencies or FragmentActivity workarounds.

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

### 4.7 Scientific & Ecological Modeling Standards
- **Continuous Biological Curves**: Model environmental variables (temperature, soil moisture, precipitation, elevation) with continuous normalized response functions ($0.0 \dots 1.0$) rather than discrete step functions.
- **Unified Probability Calibration**: Aggregate probability scores must adhere to the standardized formula:
  $$P = 100 \times (W / 100)^{1.2} \times H \times A \times S \times T$$
  where $W$ is weather score, $H$ is habitat score, $A$ is altitude score, $S$ is seasonality score, and $T$ is the continuous terrain aspect modifier ($0.50 \dots 1.10$).

---

## 5. Verification Commands for Agents

Run these commands after making changes:

```pwsh
# 1. Run full static code analysis (Lint + SARIF report)
.\gradlew.bat lintDebug

# 2. Compile Kotlin sources
.\gradlew.bat compileDebugKotlin

# 3. Assemble complete debug APK
.\gradlew.bat assembleDebug

# 4. (Optional / On-Device) Install and verify on connected physical device or emulator
adb -s <DEVICE_ID> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <DEVICE_ID> shell am force-stop github.naturewhisp.myco
adb -s <DEVICE_ID> logcat -c
adb -s <DEVICE_ID> shell am start -n github.naturewhisp.myco/.MainActivity
adb -s <DEVICE_ID> logcat -d -e "AndroidRuntime|FATAL|naturewhisp"
adb -s <DEVICE_ID> shell screencap -p /sdcard/verify.png
adb -s <DEVICE_ID> pull /sdcard/verify.png
```
