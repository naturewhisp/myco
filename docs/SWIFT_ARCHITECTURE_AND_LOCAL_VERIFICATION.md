# Architettura MycoDataKit e Verifica Swift su Ambienti Non-macOS

Questo documento formalizza l'analisi, la progettazione architetturale e la roadmap di implementazione della **Soluzione C** ("Pure Swift Data Package"), finalizzata a consentire la compilazione, il type-checking e l'esecuzione dei test della logica Swift direttamente su macchine **Windows** (tramite Docker o toolchain Swift nativa), eliminando la dipendenza dal feedback remoto di GitHub Actions e azzerando le regressioni di compilazione cross-platform KMP-Swift.

---

## 1. Problema e Motivazione

Nello sviluppo combinato di **Kotlin Multiplatform (:core)** e **iOS (Swift)** su stazioni di lavoro Windows o Linux:
- **Asimmetria di feedback:** Mentre per Android e KMP JVM/Native i test e il linting girano localmente in 2-5 secondi (inclusa la verifica su dispositivo fisico Pixel collegato), il codice Swift non poteva essere compilato in locale a causa della natura monolitica del progetto Xcode (`iosApp/MycoIOS.xcodeproj`), che mescola client di rete, mapper di dominio e viste SwiftUI.
- **Latenza CI remota:** Ogni push comportava un'attesa di circa 8-9 minuti su runner macOS GitHub Actions per scoprire discrepanze di tipo o binding tra Kotlin e Swift (es. `if let` su proprietà non-opzionali).
- **Accoppiamento spurio:** Classi di networking e mapping come `OverpassClient.swift` o `SpunBundleService.swift` interagivano direttamente con i tipi generati da KMP e importavano `CoreLocation` unicamente per un singolo tipo (`CLLocationCoordinate2D`), impedendo a un compilatore Swift standard (Linux/Windows) di analizzarli.

---

## 2. Inventario delle Dipendenze in `iosApp`

Analizzando l'attuale codice sorgente in `iosApp/MycoIOS/`:

| Componente / File | Dipendenze Attuali | Dipendenza Reale Minima | Può essere Pure Swift? |
| :--- | :--- | :--- | :---: |
| `Data/APIClient.swift` | `Foundation` | `Foundation` | **SÌ** |
| `Data/OpenMeteoClient.swift` | `Foundation` | `Foundation` | **SÌ** |
| `Data/NominatimClient.swift` | `Foundation` | `Foundation` | **SÌ** |
| `Data/OverpassClient.swift` | `Foundation`, `CoreLocation`, `MycoCore` | `Foundation`, `GeoCoordinate` protocol | **SÌ** (con adapter) |
| `DomainAdapters/OpenMeteoDomainMapper.swift` | `Foundation`, `MycoCore` | `Foundation`, `MycoCore` types | **SÌ** |
| `DomainAdapters/NominatimDomainMapper.swift` | `Foundation` | `Foundation` | **SÌ** |
| `Platform/FoundationModelService.swift` | `Foundation`, `FoundationModels` (condizionale) | `Foundation` | **SÌ** |
| `Platform/PreferencesStore.swift` | `Foundation` | `Foundation` | **SÌ** |
| `Platform/SpunBundleService.swift` | `Foundation`, `MycoCore` | `Foundation`, `MycoCore` types | **SÌ** |
| `Platform/SavedPlacesStore.swift` | `Foundation` | `Foundation` | **SÌ** |
| `Platform/CoreLocationService.swift` | `CoreLocation`, `Combine` | Framework Apple hardware | NO (Host Apple) |
| `Platform/AppleMapsNavigator.swift` | `MapKit` | Framework Apple UI | NO (Host Apple) |
| `App/MycoViewModel.swift` | `Observation`, `Foundation`, `MycoCore` | Presentation layer | NO (Host Apple) |
| `Features/*.swift` (Views) | `SwiftUI`, `MapKit`, `Charts` | Framework Apple UI | NO (Host Apple) |

**Conclusione:** Oltre l'**80% del codice logico e dei test unitari** (client HTTP, decodifica JSON, algoritmi Overpass, sanitizzazione SPUN, mapper meteorologici) dipende unicamente da **`Foundation`** e dai modelli di dominio, e potrebbe essere compilato ed eseguito istantaneamente su qualsiasi sistema operativo dotato di Swift.

---

## 3. Architettura a 3 Livelli: `MycoDataKit`

L'architettura proposta disaccoppia il repository iOS in tre strati netti:

```mermaid
graph TD
    subgraph "KMP Module (:core)"
        KMP[Domain.kt / MycoAlgorithms.kt]
    end

    subgraph "MycoDataKit (Pure Swift Package - Cross-Platform)"
        direction TB
        ACL[KmpBridgeAdapter.swift\nAnti-Corruption Layer]
        GEO[GeoCoordinate.swift\nProtocol & Value Type]
        HTTP[APIClient / OpenMeteoClient / NominatimClient]
        OVER[OverpassClient.swift]
        SPUN[SpunBundleService.swift]
        MAPPER[OpenMeteoDomainMapper.swift]
        ACL --> KMP
        OVER --> GEO
        OVER --> ACL
        MAPPER --> ACL
    end

    subgraph "Local Non-macOS Tooling (Windows/Linux/Docker)"
        SWIFT_WIN[Swift.Toolchain nativa su Windows 11]
        DOCKER[Docker: swift:6.0 container]
        LINT[Docker: SwiftLint static analysis]
        PY[check_swift_contracts.py local gate]
    end

    subgraph "iOS Application (Xcode Project - macOS Only)"
        APP[MycoIOSApp.swift]
        VM[MycoViewModel.swift]
        VIEWS[MapView / ForecastView / RegistryView]
        LOC[CoreLocationService.swift]
        NAV[AppleMapsNavigator.swift]
        VIEWS --> VM
        VM --> MycoDataKit
        LOC --> MycoDataKit
    end

    MycoDataKit -.->|Compilabile e testabile localmente con| Local Non-macOS Tooling
```

### 3.1 Value Object di Dominio Condiviso (`GeoCoordinates` da `:core`)
Anziché creare un protocollo fittizio solo lato Swift, la soluzione adottata ed effettivamente integrata nel commit `a9133ec` ha introdotto il tipo di dominio ufficiale cross-platform direttamente in Kotlin Multiplatform `:core`:

```kotlin
// core/src/commonMain/kotlin/github/naturewhisp/myco/core/Domain.kt
data class GeoCoordinates(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitudine non valida: $latitude" }
        require(longitude in -180.0..180.0) { "Longitudine non valida: $longitude" }
    }

    fun distanceToMeters(other: GeoCoordinates): Double { ... }
    fun distanceToKm(other: GeoCoordinates): Double = distanceToMeters(other) / 1000.0
}
```

Esportato verso Swift come `MycoCore.GeoCoordinates`, questo Value Object:
1. Sostituisce completamente `CLLocationCoordinate2D` in `OpenMeteoClient`, `OverpassClient`, `NominatimClient`, `LocationSearchService`, `MycoViewModel` e in tutte le suite di test.
2. Isola `CoreLocation` rigidamente nel solo platform bridge (`Platform/GeoCoordinates+CoreLocation.swift`), nel sensore GPS (`CoreLocationService.swift`) e nella mappa UI (`MapView.swift`).
3. È coperto da conformance a Swift 6:
   ```swift
   // iosApp/MycoIOS/Platform/GeoCoordinates+CoreLocation.swift
   extension GeoCoordinates: @retroactive @unchecked Sendable {}
   ```
4. È verificato e blindato dalla **Regola #14** di `check_swift_contracts.py` (divieto tassativo di `CoreLocation` e `CLLocationCoordinate2D` al di fuori degli adapter platform).

### 3.2 Anti-Corruption Layer (`KmpBridgeAdapter.swift`)
Per evitare che discrepanze di esportazione KMP (es. tipi non-opzionali esportati da Kotlin come `String` e non `String?`) si propaghino nelle viste:
1. `KmpBridgeAdapter` funge da barriera unica tra KMP e Swift.
2. Traduce i tipi generati da KMP in struct Swift native e immutabili (`HabitatEvaluationModel`, `AnalysisResultModel`, `RasterSnapshot`).
3. Qualsiasi evoluzione del core Kotlin viene assorbita in **un solo file sorgente**.

---

## 4. Pipeline di Verifica Locale su Windows (Zero CI Latency)

### 4.1 Livello 1: Pre-Commit Hook & Static Contract Checker (0.15s - Operativo)
Attivo e configurato nel repository come guardiano primario:
- File: [`scripts/check_swift_contracts.py`](file:///c:/Users/dendo/Documents/GitHub/myco/scripts/check_swift_contracts.py)
- Hook Git: `scripts/hooks/pre-commit` (attivato con `git config core.hooksPath scripts/hooks`)
- Integrazione Gradle: Task `:app:checkSwiftContracts` agganciato a `:app:preBuild`
- **15 Regole verificate in tempo reale:**
  1. Integrità e completezza delle 9 proprietà di `HeatmapRaster`.
  2. Completezza delle 13 proprietà di `AnalysisResult`.
  3. Presenza di `AnalysisInputsBuilder` in `Domain.kt`.
  4. Firme e parametri obbligatori dei costruttori Swift di `AnalysisInputs`.
  5. Binding di `statusDescription` del raster in `MapView.swift`.
  6. Iniezione del `clock` deterministico in `MycoViewModel.swift`.
  7. Presenza del test per guild non supportate in `SpunBundleServiceTests.swift`.
  8. Allineamento dei punteggi praticoli saprotrofi (0.95) in `OverpassClient.swift`.
  9. Rilevamento di sintassi illegali cross-platform (es. `{ _ in ... $0 }`).
  10. Obbligo di `import MycoCore` per file che referenziano tipi di dominio.
  11. Swift 6 Concurrency: divieto di mutazioni di variabili catturate in closure `@Sendable`.
  12. **Regola Cruciale:** Divieto assoluto di conditional binding (`if let` / `guard let`) su proprietà Kotlin non-opzionali (25 proprietà tracciate).
  13. Presenza di `GeoCoordinates` con metodi di distanza Haversine in `Domain.kt`.
  14. **Isolamento Architetturale di `CoreLocation`:** Divieto tassativo di `import CoreLocation` o tipi `CLLocationCoordinate2D` al di fuori dei soli 6 file platform/mappa autorizzati.
  15. Conformità a Swift 6 Sendable per estensioni KMP (`@retroactive @unchecked Sendable` e `@preconcurrency`).

### 4.2 Livello 2: Opzioni di Compilazione Locale su Ambienti Non-macOS (WSL, Docker, Windows Nativo)
Qualora si desideri eseguire un ciclo di compilazione binaria locale prima di inviare a GitHub Actions:

1. **WSL (Ubuntu) & Antigravity Remote WSL:**
   - La macchina dispone di una distribuzione `Ubuntu` (WSL 2). Antigravity supporta la connessione nativa *"Connect to WSL > Ubuntu"* dalla tray icon.
   - Su Ubuntu Linux, la toolchain Swift ufficiale non dipende da Visual Studio o UCRT: usa direttamente `clang`/`glibc`.
   - È possibile eseguire direttamente i test del package senza avviare container pesanti:
     ```bash
     wsl -d Ubuntu swift test --package-path /mnt/c/Users/dendo/Documents/GitHub/myco/iosApp/MycoDataKit
     ```
2. **SwiftLint Static Analysis via Docker (1.5s):**
   ```bash
   docker run --rm -v "%cd%:/work" -w /work ghcr.io/realm/swiftlint:latest swiftlint lint iosApp/
   ```
3. **Container Swift Docker:**
   ```bash
   docker run --rm -v "%cd%/iosApp/MycoDataKit:/src" -w /src swift:6.0 swift test
   ```
4. **Swift Toolchain Nativo Windows (Swift 6.4):**
   - Disponibile sul sistema (`swift.exe`), subordinata all'allineamento dei path MSVC/UCRT del compiler C++ di Visual Studio.

---

## 5. Stato di Avanzamento e Roadmap

1. **Fase 1 (Completata - Commit `a9133ec`):**
   - Introduzione del Value Object `GeoCoordinates` in `:core`.
   - Bonifica totale di `CoreLocation` da 10 file client e test in `iosApp`.
   - Conformance Swift 6 `@retroactive @unchecked Sendable`.
   - Implementazione e blindatura delle 15 regole in `check_swift_contracts.py`.
   - **Risultato:** CI GitHub Actions 100% Green su tutti i job (Android, KMP `:core`, iOS con 15 suite e 53 unit test superati).
2. **Fase 2 (Strutturazione Anti-Corruption Layer):**
   - Consolidamento delle chiamate `MycoAlgorithms` e `SpeciesCatalog` dentro un adapter Swift unico in `iosApp/MycoIOS/Platform/KmpBridgeAdapter.swift`.
3. **Fase 3 (Package `MycoDataKit`):**
   - Isolamento formale di `Data/` e `DomainAdapters/` in `iosApp/MycoDataKit/Package.swift`.
   - Integrazione come local package in `iosApp/MycoIOS.xcodeproj`.
4. **Fase 4 (GitHub Actions Fast-Fail):**
   - Aggiunta di un job rapido su runner Ubuntu Linux in `.github/workflows/ios.yml` che esegue `swift test` su `MycoDataKit` in 25 secondi prima di avviare il runner macOS da 8 minuti.
