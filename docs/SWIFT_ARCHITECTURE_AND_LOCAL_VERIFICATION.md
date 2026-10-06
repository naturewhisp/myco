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

### 3.1 Il Protocollo `GeoCoordinate` (Disaccoppiamento da CoreLocation)
Attualmente `OverpassClient` richiede `CLLocationCoordinate2D` solo per leggere `.latitude` e `.longitude`.
Nel package puro `MycoDataKit`:
```swift
public protocol GeoCoordinateRepresentable: Sendable {
    var latitude: Double { get }
    var longitude: Double { get }
}

public struct GeoCoordinate: GeoCoordinateRepresentable, Hashable, Codable, Sendable {
    public let latitude: Double
    public let longitude: Double
    
    public init(latitude: Double, longitude: Double) {
        self.latitude = latitude
        self.longitude = longitude
    }
}

#if canImport(CoreLocation)
import CoreLocation
extension CLLocationCoordinate2D: GeoCoordinateRepresentable {}
#endif
```
In questo modo, su Windows/Linux il package usa `GeoCoordinate`, mentre nell'app iOS su macOS `CLLocationCoordinate2D` vi aderisce trasparentemente.

### 3.2 Anti-Corruption Layer (`KmpBridgeAdapter.swift`)
Per evitare che discrepanze di esportazione KMP (es. tipi non-opzionali esportati da Kotlin come `String` e non `String?`) si propaghino nelle viste:
1. `KmpBridgeAdapter` funge da barriera unica tra KMP e Swift.
2. Traduce i tipi generati da KMP in struct Swift native e immutabili (`HabitatEvaluationModel`, `AnalysisResultModel`, `RasterSnapshot`).
3. Qualsiasi evoluzione del core Kotlin viene assorbita in **un solo file sorgente**.

---

## 4. Pipeline di Verifica Locale su Windows (Zero CI Latency)

### 4.1 Livello 1: Pre-Commit Hook & Static Contract Checker (0.15s)
Già attivo e configurato nel repository:
- File: [`scripts/check_swift_contracts.py`](file:///c:/Users/dendo/Documents/GitHub/myco/scripts/check_swift_contracts.py)
- Hook Git: `scripts/hooks/pre-commit` (attivato con `git config core.hooksPath scripts/hooks`)
- Integrazione Gradle: Task `:app:checkSwiftContracts` agganciato a `:app:preBuild`
- **Regole verificate:**
  1. Integrità e completezza delle proprietà di `AnalysisResult` e `HeatmapRaster`.
  2. Firme e parametri obbligatori dei costruttori KMP (`AnalysisInputs`).
  3. **Regola 12 (Cruciale):** Divieto assoluto di conditional binding (`if let` / `guard let`) su proprietà Kotlin non-opzionali (`bonusText`, `baseText`, `score`, `probability`, `tier`, ecc.).
  4. Swift 6 Concurrency: divieto di mutazioni di variabili catturate in closure `@Sendable`.
  5. Sintassi illegali cross-platform.

### 4.2 Livello 2: Analisi Statica SwiftLint via Docker (1.5s)
Eseguibile localmente in qualsiasi momento prima del commit:
```bash
docker run --rm -v "%cd%:/work" -w /work ghcr.io/realm/swiftlint:latest swiftlint lint iosApp/
```
Garantisce conformità formale, assenza di force casts o errori di sintassi grezzi.

### 4.3 Livello 3: Compilazione & Unit Test del Package tramite Docker
Per eseguire la build e i test del package Swift puro:
```bash
docker run --rm -v "%cd%/iosApp/MycoDataKit:/src" -w /src swift:6.0 swift test
```
Tutti i test di parsing, serializzazione e mapping vengono eseguiti dal vero compilatore Swift di Apple (versione Linux), garantendo zero warning e zero errori prima del push.

---

## 5. Roadmap di Implementazione

1. **Fase 1 (Completata):**
   - Implementazione della Regola #12 in `check_swift_contracts.py`.
   - Aggancio del task `:app:checkSwiftContracts` a Gradle `preBuild`.
   - Creazione del Git pre-commit hook tracciato in `scripts/hooks/pre-commit`.
2. **Fase 2 (Strutturazione KmpBridgeAdapter):**
   - Consolidamento delle chiamate `MycoAlgorithms` e `SpeciesCatalog` dentro un adapter Swift unico in `iosApp/MycoIOS/Platform/KmpBridgeAdapter.swift`.
3. **Fase 3 (Package `MycoDataKit`):**
   - Creazione di `Package.swift` in `iosApp/MycoDataKit`.
   - Spostamento di `Data/` e `DomainAdapters/` nel package.
   - Integrazione come local package in `iosApp/MycoIOS.xcodeproj`.
4. **Fase 4 (GitHub Actions Fast-Fail):**
   - Aggiunta di un job rapido su runner Ubuntu Linux in `.github/workflows/ios.yml` che esegue `swift test` su `MycoDataKit` in 25 secondi prima di avviare il runner macOS da 8 minuti.
