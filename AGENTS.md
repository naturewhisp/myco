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
│   ├── model/                         # Data classes & DTOs
│   │   ├── GeocodingModel.kt          # Nominatim OSM geocoding responses
│   │   ├── OverpassModel.kt           # Overpass OSM habitat & tree queries
│   │   ├── SavedLocation.kt           # User bookmarks and recent locations
│   │   └── WeatherModel.kt            # Open-Meteo current & forecast data
│   ├── network/                       # Remote & local AI services
│   │   ├── ApiServices.kt             # Open-Meteo & Overpass API interfaces
│   │   ├── LocalAiService.kt          # Gemini Nano (AICore) client & prompt generator
│   │   └── NetworkClient.kt           # Retrofit & OkHttp singleton configuration
│   ├── repository/                    # Data aggregation & caching
│   │   ├── CacheManager.kt            # SharedPreferences caching with KTX extensions
│   │   └── MushroomRepository.kt      # Orchestrates weather + OSM calls
│   ├── ui/
│   │   ├── components/                # Modular Compose components (MapView, ScoreCards, etc.)
│   │   ├── screens/                   # MushroomApp composable & main screen workflows
│   │   ├── theme/                     # Compose Material3 theme, typography, color palettes
│   │   └── viewmodel/
│   │       └── MushroomViewModel.kt   # Central state, coroutines, settings & search logic
│   └── utils/
│       └── MushroomAlgorithms.kt      # Mathematical modeling of fungal fruiting probability
└── res/                               # Assets, mipmap adaptive icons, backup rules (no XML layouts)
```

---

## 4. Coding Standards & Conventions

### 4.1 Kotlin & Android Best Practices
- **Android KTX Extensions**: Always use KTX extension functions (e.g., `prefs.edit { putString(...) }` instead of legacy chaining `.edit().putString(...).apply()`).
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
```
