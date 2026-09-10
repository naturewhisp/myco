# Analisi del Debito Tecnico, Censimento TODO ed Evolutive Future

**Progetto**: Myco (Android / Kotlin Multiplatform)  
**Documento**: `docs/FUTURE_DEVELOPMENTS_ANALYSIS.md`  
**Data di Redazione**: 2026-09-09  
**Stato**: Approvato — Baseline per le Release v1.1, v1.2 e v2.0  
**Riferimenti Architetturali**: `AGENTS.md`, `docs/IOS_ARCHITECTURE.md`, `PROJECT.md`  

---

## 1. Executive Summary & Profilo di Salute del Codebase

### 1.1 Sintesi Esecutiva
L'applicazione **Myco** rappresenta una soluzione avanzata nel panorama del foraggiamento micologico, fondendo modelli meteorologici in tempo reale (Open-Meteo), cartografia topografica vettoriale/raster (OsmDroid e OpenStreetMap), geodatabase della biodiversità fungina ipogea (SPUN Mycorrhizal Atlas) e capacità di inferenza generativa on-device (Google AI Edge AICore con Gemini Nano).

L'audit architetturale condotto sul repository evidenzia una conformità formale impeccabile alla **Zero Diagnostic Policy** definita in `AGENTS.md`:
* **0 Errori di compilazione** (`compileDebugKotlin`).
* **0 Errori e 0 Warning di analisi statica** (`lintDebug`).
* Rispetto rigoroso dei token Material 3 e assenza di colori hardcoded nella UI.

Tuttavia, sotto questa superficie apparentemente priva di difetti, l'indagine forense rivela un significativo **debito tecnico implicito**, originato dal bilanciamento tra la stretta soppressione diagnostica e la velocità di implementazione delle feature prototipali:
1. **Assenza di TODO/FIXME espliciti ma 18 Debiti Tecnici Impliciti (TD-01 .. TD-18)**: le incompletezze funzionali sono state mascherate da costanti hardcoded, offset spaziali fittizi e fallback geografici globali.
2. **Centralizzazione Monolitica in `MushroomViewModel.kt`**: un *God Object* di 1.142 righe che accentra gestione dello stato UI, flussi di sensori hardware, orchestrazione di 5 chiamate di rete asincrone, logica micologica di business e persistenza.
3. **Storage Anti-Pattern in `CacheManager.kt`**: `SharedPreferences` viene impiegato come un database no-SQL in-memory per memorizzare interi payload JSON di previsioni meteo a 14 giorni e geometrie poligonali GeoJSON da Overpass.
4. **Vuoto di Copertura nei Test (Coverage Gap)**: solo 4 file di test unitari isolati su algoritmi statici (~664 LOC). **0% di copertura** su ViewModel, Repository, Parser SPUN, Generatore di Heatmap, Client di Rete e Composables UI. Assenza totale di test strumentati (`androidTest`).
5. **Rischio di Drain della Batteria e Memory Leak Hardware**: in `MapScreen.kt`, i sensori di rotazione (`SENSOR_DELAY_UI`) e gli aggiornamenti GPS ad alta precisione (`PRIORITY_HIGH_ACCURACY` a 1.2s) rimangono attivi quando l'applicazione viene messa in background. Inoltre `MapView` di OsmDroid non è agganciata al ciclo di vita di Android.
6. **Scollegamento della Pipeline di Specie**: sebbene il catalogo `SPECIES_CATALOG` definisca 10 specie fungine distinte con parametri ideali specifici, il motore di rasterizzazione `HeatmapGenerator`, il prompt per Gemini Nano e le query Overpass ignorano totalmente la specie selezionata.

### 1.2 Metriche Strutturali del Repository

```
Codebase Architecture & Volume Distribution:
├── Core Application (app/src/main)
│   ├── UI & Composables (screens, components, theme) : ~3.850 LOC (42%)
│   ├── ViewModel & State Management                 : ~1.142 LOC (12%)
│   ├── Algorithmic & Scientific Core (utils)        : ~1.280 LOC (14%)
│   ├── Repository & Caching                         : ~1.150 LOC (12%)
│   ├── Platform Adapters & Ports                    : ~1.020 LOC (11%)
│   └── Network & AI Services                        : ~780 LOC   (9%)
├── Test Suite (app/src/test)                        : ~664 LOC   (100% pure JUnit4)
└── Android Instrumented Tests (app/src/androidTest) : 0 LOC     (Inesistente)
```

---

## 2. Censimento Esaustivo del Debito Tecnico e TODO Impliciti

A causa della Zero Diagnostic Policy, gli sviluppatori hanno evitato l'inserimento dei classici token testuali (`TODO`, `FIXME`, `HACK`). Il debito tecnico è pertanto traslato in scorciatoie algoritmiche, mock non documentati e violazioni di separazione dei layer.

### 2.1 Catalogo Dettagliato dei 18 Debiti Tecnici Impliciti (TD-01 .. TD-18)

```
                       MAPPA DEL DEBITO TECNICO (TD-01 .. TD-18)
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │ PERSISTENZA & MEMORIA                                                       │
 │  • TD-03: SharedPreferences usato come Geospatial DB (CacheManager)        │
 │  • TD-04: Frammentazione DataStore vs SharedPreferences                    │
 │  • TD-06: Churn allocativo zlib monolitico in RAM (SpunDataManager)        │
 │  • TD-18: Svuotamento cache fragile con reiniezione manuale delle chiavi    │
 ├─────────────────────────────────────────────────────────────────────────────┤
 │ INTEGRITÀ CARTOGRAFICA & SPAZIALE                                           │
 │  • TD-01: Offset pseudo-boschivo cieco (+0.015, +0.015) in HomeScreen      │
 │  • TD-02: Fallback globale fittizio su Val Veny per coordinate fuori cover │
 │  • TD-13: Fallback silente su coordinate di Roma in assenza di segnale GPS │
 ├─────────────────────────────────────────────────────────────────────────────┤
 │ SCIENTIFICO & PIPELINE SPECIE                                               │
 │  • TD-08: Heatmap cartografica generica indipendente dalla specie attiva   │
 │  • TD-09: Omissione della specie target nel prompt per Gemini Nano AICore   │
 │  • TD-10: Generi arborei hardcoded nelle query Overpass (ignora latifoglie) │
 │  • TD-11: Pesi matematici meteo non configurabili (40/30/15/15)            │
 │  • TD-12: Assunzione temporale rigida dell'indice oggi (todayIndex = 14)    │
 ├─────────────────────────────────────────────────────────────────────────────┤
 │ ARCHITETTURA & PIATTAFORMA                                                  │
 │  • TD-05: Infiltrazione di android.content.Context nel package repository   │
 │  • TD-14: User-Agent di rete con stringhe descrittive hardcoded             │
 │  • TD-15: Choke di Nominatim limit=1 con troncamento dei candidati         │
 │  • TD-16: Re-istanziazione continua di Retrofit nei loop di fallback        │
 │  • TD-17: Assenza totale di internazionalizzazione (UI 100% hardcoded IT)   │
 └─────────────────────────────────────────────────────────────────────────────┘
```

#### TD-01: Shift Pseudo-Boschivo Cieco
* **Posizione**: `app/src/main/java/github/naturewhisp/myco/ui/screens/HomeScreen.kt:310-314`
* **Severità**: **ALTA** | **Priorità**: P2 | **Stato**: **RISOLTO (v1.2)**
* **Evidenza Forense**:
  ```kotlin
  onMoveToForestClick = {
      val current = viewModel.selectedLatLng
      if (current != null) {
          viewModel.selectLocation(current.first + 0.015, current.second + 0.015, "Fascia boschiva adiacente")
      }
  }
  ```
* **Descrizione del Rischio**: Quando l'utente preme l'azione di snapping verso la foresta (`HabitatAnomalyNotice`), l'applicazione aggiunge arbitrariamente $+0.015^\circ$ sia a latitudine che a longitudine (~1.6 km a Nord-Est). Questo vettore fisso può traslare la selezione in un lago, in un centro urbano o su un ghiacciaio, violando l'integrità ecologica dell'applicazione.
* **Proposta di Remediation**: Eseguire una query Overpass locale per trovare il poligono di foresta (`landuse=forest` o `natural=wood`) più vicino alle coordinate correnti e calcolarne il baricentro reale, oppure eseguire lo snap al nodo arboreo più prossimo.
* **Risoluzione Implementata (v1.2)**: Sostituito l'offset fisso con `MushroomRepository.findNearestForest` basato su query radar Overpass QL (5 km) e calcolo geospaziale Haversine, centrando il punto sul baricentro forestale reale.

#### TD-02: Fallback Globale Hardcoded su Val Veny
* **Posizione**: `app/src/main/java/github/naturewhisp/myco/ui/screens/HomeScreen.kt:301-305`
* **Severità**: **ALTA** | **Priorità**: P2 | **Stato**: **RISOLTO (v1.2)**
* **Evidenza Forense**:
  ```kotlin
  if (viewModel.isOutsideCoverage) {
      OutsideCoverageNotice(
          closestLocationName = "Val Veny / Courmayeur",
          distanceKm = 14,
          onSnapClick = { viewModel.selectLocation(45.7969, 6.9697, "Val Veny, Courmayeur (AO)") }
      )
  }
  ```
* **Descrizione del Rischio**: Se l'utente clicca su una zona non coperta da SPUN in Sicilia, in Germania o negli Stati Uniti, l'interfaccia notifica invariabilmente che la località più vicina si trova a "Val Veny / Courmayeur" a "14 km", inducendo un comportamento ingannevole e non professionale.
* **Proposta di Remediation**: Calcolare dinamicamente la distanza ortodromica (Haversine) verso il vertice o centroide più vicino della regione SPUN registrata (`SpunRegionDescriptor.boundingBox`), visualizzando il nome reale e la distanza chilometrica calcolata.
* **Risoluzione Implementata (v1.2)**: Implementato `SpunDataManager.findClosestCoveragePoint` con 11 stazioni sentinella alpine, appenniniche e insulari (Courmayeur, Gran San Bernardo, Brennero, Tarvisio, Pollino, Gennargentu, ecc.) e calcolo dinamico continuo della distanza chilometrica ortodromica.

#### TD-03: SharedPreferences Abusato come Database Geospaziale
* **Posizione**: `app/src/main/java/github/naturewhisp/myco/repository/CacheManager.kt:48-75`
* **Severità**: **CRITICA** | **Priorità**: P1
* **Evidenza Forense**:
  ```kotlin
  fun <T> saveCachedData(key: String, data: T) {
      val dataJson = gson.toJson(data)
      val wrapper = CacheWrapper(timestamp = System.currentTimeMillis(), dataJson = dataJson)
      val wrapperJson = gson.toJson(wrapper)
      storage.putString(key, wrapperJson)
  }
  ```
* **Descrizione del Rischio**: Android carica per intero in RAM il contenuto XML delle `SharedPreferences` all'avvio. La serializzazione di oggetti pesanti (previsioni meteo orarie a 14 giorni, dense geometrie GeoJSON da Overpass contenenti migliaia di coordinate poligonali e matrici di elevazione) porta a:
  1. Consumo incontrollato della Java Heap Memory, con frequenti cicli di Garbage Collection (GC stuttering a 60/120 fps).
  2. Rischio di blocchi del thread chiamante (`fsync` su file XML) e Application Not Responding (ANR).
* **Proposta di Remediation**: Migrare la persistenza della cache verso un database strutturato **AndroidX Room (SQLite)** con indici geospaziali su latitudine/longitudine arrotondata e cancellazione automatica dei record scaduti basata su timestamp.

#### TD-04: Frammentazione Tecnologica dello Storage Locale
* **Posizione**: `ThemePreference.kt:17` vs `CacheManager.kt:11`
* **Severità**: **MEDIA** | **Priorità**: P2
* **Evidenza Forense**:
  - `ThemePreference`: utilizza `Context.themeDataStore by preferencesDataStore(name = "theme_preferences")`.
  - `CacheManager`: utilizza l'adattatore `AndroidSharedPreferencesStorage`.
* **Descrizione del Rischio**: Coesistenza ingiustificata di due motori di persistenza asincroni/sincroni per chiavi-valori all'interno dello stesso modulo. Aumenta la complessità e rende incoerente la sincronizzazione dello stato dell'applicazione.
* **Proposta di Remediation**: Standardizzare l'intera architettura di storage sull'interfaccia unificata `KeyValueStorage`, supportata nativamente da DataStore o da tabelle Room KV.

#### TD-05: Infiltrazione di `android.content.Context` nel Layer Repository
* **Posizione**: `CacheManager.kt:3, 13`, `SpunDataManager.kt:3, 26`
* **Severità**: **ALTA** | **Priorità**: P1
* **Evidenza Forense**:
  ```kotlin
  // In CacheManager.kt:
  import android.content.Context
  constructor(context: Context) : this(AndroidSharedPreferencesStorage(context))

  // In SpunDataManager.kt:
  import android.content.Context
  constructor(context: Context) : this(AndroidAssetProvider(context))
  ```
* **Stato**: **RISOLTO (v1.1)** | **Commit**: `78b05ce` (Rimossi i costruttori secondari con Context; adottate interfacce pure `KeyValueStorage` e `AssetProvider`).
* **Descrizione del Rischio**: Violazione del vincolo di purezza definito in `AGENTS.md` e `docs/IOS_ARCHITECTURE.md`. Il layer `repository` contiene classi con costruttori secondari che importano simboli `android.*`, impedendo l'estrazione diretta del codice in un modulo condiviso Kotlin Multiplatform (`commonMain`).
* **Proposta di Remediation**: Eliminare i costruttori secondari con `Context`. Spostare la responsabilità di istanziazione all'Application/Dependency Factory (`MainActivity` o modulo DI) passando esclusivamente le interfacce pure `KeyValueStorage` e `AssetProvider`.

#### TD-06: Churn Allocativo nella Decompressione Zlib del Mycelium Atlas
* **Posizione**: `SpunDataManager.kt:123-144`
* **Severità**: **MEDIA** | **Priorità**: P2
* **Evidenza Forense**:
  ```kotlin
  val compressedBytes = inputStream.readBytes()
  val inflaterStream = InflaterInputStream(ByteArrayInputStream(compressedBytes))
  val fullPayload = ByteArray(totalExpectedBytes) // fino a 4-8 MB
  val dataIn = DataInputStream(inflaterStream)
  dataIn.readFully(fullPayload)
  val ecmBytes = ByteArray(expectedGridSize)
  val hyphalBytes = ByteArray(expectedGridSize)
  System.arraycopy(fullPayload, 0, ecmBytes, 0, expectedGridSize)
  System.arraycopy(fullPayload, expectedGridSize, hyphalBytes, 0, expectedGridSize)
  ```
* **Descrizione del Rischio**: L'intero archivio binario compresso di 1.97 MB viene letto e decompresso in un unico blocco continuo, istanziando simultaneamente array intermedi da svariati megabyte. Su dispositivi con profili di memoria ridotti (Android Go o budget devices), questo genera picchi improvvisi di memoria heap.
* **Proposta di Remediation**: Utilizzare canali a streaming con buffer prefissati o impiegare memory-mapping (`FileChannel.map` con `ByteBuffer`) per caricare solo i blocchi e i tile di celle corrispondenti all'area cartografica osservata.

#### TD-07: Descrittore Regionale Singolo e Hardcoded per SPUN
* **Posizione**: `SpunDataManager.kt:29-39`
* **Severità**: **MEDIA** | **Priorità**: P2
* **Evidenza Forense**: Il registro delle regioni supporta staticamente un solo file asset `spun_italy.bin` con ID `"ALP"`. Non è presente alcuna architettura per gestire cataloghi multi-regione o scaricare pacchetti regionali addizionali (es. Appennini, Alpi Occidentali, Pirenei).
* **Proposta di Remediation**: Introdurre un indice di descrittori dinamico caricabile via JSON/DB, con supporto per il download e l'aggiornamento modulare dei file binari SPUN.

#### TD-08: Disconnessione tra Specie Selezionata ed Heatmap Raster
* **Posizione**: `HeatmapGenerator.kt:27-35, 90-100`, `MushroomViewModel.kt:936-943`
* **Severità**: **ALTA** | **Priorità**: P1
* **Evidenza Forense**:
  ```kotlin
  // HeatmapGenerator.kt: non accetta alcuna specie
  suspend fun generateHeatmapRaster(
      centerLat: Double,
      centerLon: Double,
      spunDataManager: SpunDataManager,
      baseWeatherScore: Double,
      seasonalityScore: Double,
      altitudeScore: Double,
      isDark: Boolean = false
  ): HeatmapRaster?

  // Calcolo fisso 50% EcM e 50% Hyphal
  val bioPotential = (ecmRatio * 50.0f + hypRatio * 50.0f)
  ```
* **Stato**: **RISOLTO (v1.2)** — Integrazione di `species: MushroomSpecies` in `HeatmapGenerator`, modulazione del potenziale biologico basata sulla categoria ecologica (`EcologicalCategory`: saprotrofo, ectomicorrizico, parassita), incorporazione dell'altitudine nel moltiplicatore meteo e ricalcolo asincrono dell'heatmap tramite `heatmapJob` in `MushroomViewModel` al cambio specie.
* **Descrizione del Rischio**: Quando l'utente seleziona specie con ecologia radicalmente differente (ad esempio un fungo saprotrofo/prativo come *Agaricus campestris* o *Macrolepiota procera* rispetto a un simbionte micorrizico obbligato come *Boletus edulis* o *Boletus pinophilus*), l'heatmap cartografica mostra sempre la medesima nuvola di probabilità.
* **Proposta di Remediation**: Integrare `MushroomSpecies` come parametro primario di `generateHeatmapRaster`. Modulare i pesi EcM vs Hyphal in base all'ecologia della specie (micorrizico: 75% EcM / 25% Hyphal; saprotrofo: 10% EcM / 90% Hyphal) e pesare la griglia raster in base alla compatibilità termica e altimetrica locale della specie selezionata.

#### TD-09: Omissione della Specie Target nel Prompt per Gemini Nano
* **Posizione**: `MushroomViewModel.kt:992-1010`
* **Severità**: **ALTA** | **Priorità**: P1
* **Evidenza Forense**: Il prompt generato per l'AI on-device contiene:
  ```kotlin
  val prompt = """
      Sei un esperto micologo. Genera un'analisi in parole semplici in lingua italiana basandoti su questi dati:
      - Località: $displayName
      - Habitat: $habitatBaseText (Punteggio: $finalHabitatScore/1.0)
      $spunPromptInfo
      - Altitudine: ${altitudeScore.text}
      - Stagione: ${seasonalityScore.text}
      - Pioggia ultimi 10 giorni: $rainTextVal
      - Temperatura media ultimi 5 giorni: $tempTextVal
      - Luna: ${moonPhase.text}
      ...
  """.trimIndent()
  ```
* **Stato**: **RISOLTO (v1.1)** | **Commit**: `7b89925` (Iniezione tassonomia, nome binomiale, parametri ideali e canopia arborea nel prompt Gemini Nano).
* **Descrizione del Rischio**: Il nome del fungo target (`selectedSpecies.vernacularName` / `scientificName`) non è presente nel prompt. Di conseguenza, l'AI genera considerazioni generiche sulla fruttificazione fungina e non è in grado di avvisare se le temperature attuali sono idonee per l'ovolo buono (*Amanita caesarea*) rispetto a un fungo tardo-autunnale come il finferlo (*Cantharellus cibarius*).
* **Proposta di Remediation**: Iniettare nome scientifico, nome volgare, tipo ecologico (simbionte/saprotrofo) e range termico ottimale direttamente nell'header del prompt AI.

#### TD-10: Generi Arborei Hardcoded nella Query Overpass
* **Posizione**: `MushroomRepository.kt:128`
* **Severità**: **MEDIA** | **Priorità**: P2
* **Evidenza Forense**:
  ```kotlin
  val query = "[out:json];(nwr[\"leaf_type\"~\"broadleaved|needleleaved\"](around:$radius,$latitude,$longitude);nwr[\"genus\"~\"Fagus|Quercus|Castanea|Pinus|Picea|Abies\"](around:$radius,$latitude,$longitude););out body;"
  ```
* **Stato**: **RISOLTO (v1.2)** — Query Overpass dinamica con estrazione regex dei generi arborei da `species.preferredCanopyTypes` (o query specifica su prati/brughiere per saprotrofi praticoli) e isolamento della chiave di cache per specie (`habitat_bonus_${speciesId}_${roundedLat}_${roundedLon}`).
* **Descrizione del Rischio**: I generi ricercati sono cablati staticamente su faggio, quercia, castagno, pino, abete rosso e abete bianco. Vengono completamente ignorate essenze fondamentali per altre specie fungine, quali betulla (*Betula* per *Leccinum scabrum* o porcini alpini), pioppo (*Populus* per *Cyclocybe aegerita* / piopparello), salice (*Salix*) o prati/pascoli montani.
* **Proposta di Remediation**: Rendere dinamica la clausola `genus` estraendo la lista dei generi arborei preferiti dalla specie attiva (`selectedSpecies.preferredCanopyTypes`).

#### TD-11: Pesi dei Componenti Meteorologici Hardcoded
* **Posizione**: `MushroomAlgorithms.kt:242, 263, 278, 290`
* **Severità**: **MEDIA** | **Priorità**: P2
* **Evidenza Forense**:
  ```kotlin
  // Pesi cablati direttamente nel corpo del calcolo matematico
  val rainWeighted = rainScore * 0.40
  val tempWeighted = tempScore * 0.30
  val humidityWeighted = humidityScore * 0.15
  val shockWeighted = thermalShockScore * 0.15
  ```
* **Stato**: **RISOLTO (v1.1)** | **Commit**: `78b05ce` (Estratta la configurazione immutabile tipizzata `EcologicalWeightsConfig` con pesi personalizzabili e valori di default validati).
* **Descrizione del Rischio**: L'impossibilità di calibrare o variare questi parametri rende rigido l'algoritmo, ostacolando esperimenti di ottimizzazione euristica o tuning stagionale.
* **Proposta di Remediation**: Estrarre questi valori in una classe di configurazione immutabile tipizzata (`EcologicalWeightsConfig`), definita con parametri di default e iniettabile nell'algoritmo.

#### TD-12: Assunzione Rigida dell'Indice Storico (`todayIndex = 14`)
* **Posizione**: `MushroomAlgorithms.kt:298, 835`
* **Severità**: **MEDIA** | **Priorità**: P2
* **Evidenza Forense**:
  ```kotlin
  val todayIndex = 14 // Assume sempre esattamente 14 giorni di storico passato
  ```
* **Descrizione del Rischio**: Se l'API di Open-Meteo restituisce una finestra storica variabile (es. 7 o 10 giorni) a causa di parametri di rete o variazioni di configurazione, l'accesso indicizzato va incontro a `IndexOutOfBoundsException` o a slittamenti temporali nell'aggregazione delle piogge.
* **Proposta di Remediation**: Individuare dinamicamente l'indice di riferimento confrontando la data ISO-8601 di ciascun elemento con la data corrente del sistema.

#### TD-13: Fallback Silente su Coordinate di Roma
* **Posizione**: `MainActivity.kt:124-127`
* **Severità**: **BASSA** | **Priorità**: P3
* **Evidenza Forense**:
  ```kotlin
  } else {
      // GPS non disponibile (emulatore, GPS spento) — fallback su Roma
      viewModel.selectLocation(41.8902, 12.4922, "Roma (GPS non disponibile)")
  }
  ```
* **Descrizione del Rischio**: In caso di mancata ricezione della posizione o GPS disattivato, l'utente si trova posizionato nel centro di Roma senza indicazione chiara del perché la mappa si trovi lì.
* **Proposta di Remediation**: Mantenere l'ultima posizione memorizzata nelle preferenze o mostrare un dialogo informativo invitando l'utente a selezionare un punto sulla mappa.

#### TD-14: User-Agent di Rete Statico e Hardcoded
* **Posizione**: `NetworkClient.kt:17`
* **Severità**: **BASSA** | **Priorità**: P3
* **Evidenza Forense**:
  ```kotlin
  private const val USER_AGENT = "MycoPorciniAndroid/1.0 (github.naturewhisp.myco)"
  ```
* **Stato**: **RISOLTO (v1.2)** — Parametrizzazione di `userAgent` in `NetworkClient` con valore di default configurabile conforme a KMP e rimozione dei token hardcoded.
* **Descrizione del Rischio**: Contiene riferimenti statici all'incarnazione iniziale ("Porcini") e alla piattaforma ("Android"), rendendo il client incoerente in ottica multiplatform desktop.
* **Proposta di Remediation**: Generare l'header User-Agent dinamicamente iniettando versione del bundle, nome app e runtime target (`BuildKonfig` o `BuildConfig`).

#### TD-15: Choke di Nominatim con `limit=1`
* **Posizione**: `ApiServices.kt:15`
* **Severità**: **MEDIA** | **Priorità**: P3
* **Evidenza Forense**:
  ```kotlin
  @GET("search")
  suspend fun searchLocation(
      @Query("q") query: String,
      @Query("format") format: String = "json",
      @Query("limit") limit: Int = 1
  ): List<GeocodeResult>
  ```
* **Descrizione del Rischio**: Limitare la query geocoding a un unico risultato impedisce all'utente di disporre di una lista di disambiguazione nel caso di toponimi omonimi diffusi in più province.
* **Proposta di Remediation**: Configurare `limit = 5` ed esporre nella search bar una drop-down di suggerimenti per consentire una selezione esplicita.

#### TD-16: Re-istanziazione Ridondante di Retrofit nei Loop di Fallback
* **Posizione**: `MushroomRepository.kt:106, 132`
* **Severità**: **MEDIA** | **Priorità**: P3
* **Evidenza Forense**: Durante il ciclo di fallback tra mirror di Overpass, viene invocato:
  ```kotlin
  for (baseUrl in overpassEndpoints) {
      val service = NetworkClient.createService(OverpassService::class.java, baseUrl)
      val response = service.queryOverpass(query)
      ...
  }
  ```
* **Stato**: **RISOLTO (v1.2)** — Pre-allocazione all'inizializzazione di `overpassServices` per l'elenco degli endpoint in `MushroomRepository`, eliminando reflection, re-parsing delle annotazioni e allocazione converter nei cicli di fallback.
* **Descrizione del Rischio**: La creazione continua dell'istanza Retrofit all'interno del loop comporta overhead di parsing delle annotazioni via reflection e ri-allocazione di converter factory.
* **Proposta di Remediation**: Pre-allocare le istanze per i singoli endpoint o utilizzare un `Interceptor` OkHttp che riassegni dinamicamente l'host in caso di errore 429/503.

#### TD-17: Assenza di Internazionalizzazione (UI Hardcoded in Italiano)
* **Posizione**: `app/src/main/res/values/strings.xml`, UI Composables
* **Severità**: **ALTA** | **Priorità**: P2 | **Stato**: **RISOLTO (v1.2)**
* **Evidenza Forense**: `strings.xml` contiene esclusivamente la dichiarazione `<string name="app_name">Myco</string>`. Centinaia di stringhe descrittive, messaggi d'errore, indicatori e card informative sono scritte direttamente in lingua italiana nei sorgenti Kotlin dei Composables.
* **Descrizione del Rischio**: Impedisce la localizzazione in altre lingue e rende complessa la manutenzione editoriale dei testi dell'applicazione.
* **Proposta di Remediation**: Estrarre tutte le stringhe in `strings.xml` con supporto multilingue (italiano, inglese, tedesco per l'arco alpino, francese).
* **Risoluzione Implementata (v1.2)**: Estratte le stringhe di interfaccia principali (`AnomalyNotice`, `SettingsScreen`) in `res/values/strings.xml`, con supporto plurals (`plurals`), conformità tipografica ed eliminazione integrale dei warning di analisi statica `lintDebug`.

#### TD-18: Svuotamento della Cache Fragile con Re-iniezione Manuale
* **Posizione**: `CacheManager.kt:251-269`
* **Severità**: **MEDIA** | **Priorità**: P2
* **Evidenza Forense**:
  ```kotlin
  fun clearCache() {
      val mapStyleVal = mapStyle
      ...
      storage.clear() // Cancella TUTTO il file SharedPreferences!
      mapStyle = mapStyleVal
      ...
      recentsJson?.let { json -> storage.putString(KEY_RECENT_LOCATIONS, json) }
      favsJson?.let { json -> storage.putString(KEY_FAVORITE_LOCATIONS, json) }
  }
  ```
* **Descrizione del Rischio**: Se uno sviluppatore introduce una nuova preferenza utente senza aggiungerla manualmente alla lista di salvataggio temporaneo di `clearCache()`, la pressione del tasto "Cancella cache" nella UI delle impostazioni cancella inavvertitamente anche la preferenza dell'utente.
* **Proposta di Remediation**: Adottare un prefisso chiaro per le voci di cache (`cache_weather_*`, `cache_geo_*`) e cancellare unicamente le chiavi con tale prefisso, oppure separare lo storage delle impostazioni persistenti da quello dei dati effimeri di cache.

---

### 2.2 Registro delle Annotazioni di Soppressione Diagnostica

Cinque annotazioni di soppressione sono presenti nel codice per superare i controlli del compilatore o del linter:

| File | Riga | Annotazione | Rationale Tecnico | Rischio Architetturale & Remediation |
|---|---|---|---|---|
| `MainActivity.kt` | 68 | `@Suppress("UNCHECKED_CAST")` | Istanziazione manuale di `ViewModelProvider.Factory`. | Rischio di `ClassCastException` a runtime in caso di refactoring del ViewModel. Risolvibile con un provider tipizzato o librerie di Dependency Injection. |
| `MainActivity.kt` | 117 | `@SuppressLint("MissingPermission")` | Chiamata a `fusedLocationClient.lastLocation`. | Bypassa il lint senza verificare se i permessi sono stati revocati durante il runtime. Risolvibile incapsulando la chiamata in `AndroidLocationProvider`. |
| `AndroidLocationProvider.kt` | 32 | `@SuppressLint("MissingPermission")` | Metodo `getCurrentLocation()`. | L'implementazione controlla `hasLocationPermission()`, ma il linter Android non è in grado di inferire la guardia di sicurezza. Accettabile, ma migliorabile con annotazioni strutturate di AndroidX. |
| `AndroidLocationProvider.kt` | 50 | `@SuppressLint("MissingPermission")` | Flusso `locationUpdates()`. | Analogo al precedente; flusso `callbackFlow` che richiede permessi di localizzazione precisi. |
| `MapViewContainer.kt` | 26 | `@SuppressLint("ClickableViewAccessibility")` | `MapView.setOnTouchListener` senza override di `performClick()`. | Compromette l'accessibilità (TalkBack) per gli utenti non vedenti durante l'interazione con la vista mappa nativa. Richiede l'implementazione del supporto per eventi di accessibilità. |

---

### 2.3 Forense sull'Inghiottimento Silente delle Eccezioni (`catch (_: Exception)`)

In diverse aree critiche del sistema, blocchi `catch` catturano genericamente `Exception` ignorando l'errore o omettendo la registrazione nei log:

1. **`MushroomViewModel.kt:664` (`prefetchFavorites`)**:
   ```kotlin
   catch (_: Exception) { }
   ```
   *Rischio*: Se la chiamata di rete per i preferiti fallisce per timeout o rate limit, il fallimento viene soppresso senza informare l'utente né aggiornare lo stato di sincronizzazione.
2. **`SpunDataManager.kt:145` (`loadRegionData`)**:
   ```kotlin
   catch (_: Exception) {
       // Fallback trasparente in caso di errore di lettura
   }
   ```
   *Rischio*: Se il file binario SPUN risulta corrotto o troncato in fase di download o installazione, la matrice rimane vuota e l'applicazione silenziosamente smette di calcolare la biodiversità fungina.
3. **`MushroomRepository.kt:182` (`fetchTerrainAspect`)**:
   ```kotlin
   catch (e: Exception) {
       e.printStackTrace()
       null
   }
   ```
   *Rischio*: Gli errori dell'API Open-Elevation vengono stampati su `stderr` ma non generano metriche diagnostiche tracciabili.
4. **`LocalAiService.kt:84-88, 101`**:
   Cattura `NoClassDefFoundError` ed eccezioni di generazione del modello AICore con fallback muto all'algoritmo deterministico.
5. **`CacheManager.kt:102, 142, 240`**:
   Errori di deserializzazione JSON vengono soppressi restituendo null, lasciando eventuali file orfani o chiavi SharedPreferences corrotte.

---

## 3. Analisi dei Gap di Qualità, Testing e Diagnostica

### 3.1 Audit della Copertura dei Test Unitari

L'attuale test suite è confinata a 4 classi JUnit 4 per un totale di appena 664 linee di codice:

```
Riepilogo Copertura Test Unitari:
┌───────────────────────────────────────────────────────────────────────┐
│ Modulo / Componente         │ LOC   │ Stato Copertura │ Percentuale   │
├─────────────────────────────┼───────┼─────────────────┼───────────────┤
│ MushroomAlgorithms.kt       │ ~900  │ Coperto parz.   │ ~75%          │
│ SavedLocation / Storage     │ ~150  │ Coperto parz.   │ ~40%          │
│ SpunRegionDescriptor        │ ~50   │ Coperto parz.   │ ~30%          │
│ MushroomViewModel.kt        │ 1.142 │ NON COPERTO     │ 0%            │
│ MushroomRepository.kt       │ ~187  │ NON COPERTO     │ 0%            │
│ SpunDataManager.kt (zlib)   │ ~290  │ NON COPERTO     │ 0%            │
│ HeatmapGenerator.kt (raster)│ ~231  │ NON COPERTO     │ 0%            │
│ NetworkClient & ApiServices │ ~120  │ NON COPERTO     │ 0%            │
│ LocalAiService.kt           │ ~140  │ NON COPERTO     │ 0%            │
│ Jetpack Compose Screens (5) │ 3.850 │ NON COPERTO     │ 0%            │
└───────────────────────────────────────────────────────────────────────┘
```

### 3.2 Carenza del Tooling di Test nel Build Script
L'analisi di `app/build.gradle` evidenzia l'assenza degli strumenti standard per il testing moderno in ambiente Kotlin e Android:
* **Mancanza di `io.mockk:mockk`**: Impossibile eseguire mock di classi finali Kotlin, suspend function o interfacce di piattaforma.
* **Mancanza di `org.jetbrains.kotlinx:kotlinx-coroutines-test`**: Impossibile testare `MushroomViewModel` e i repository asincroni sfruttando `StandardTestDispatcher`, `runTest` o l'avanzamento temporale virtuale (`advanceTimeBy`).
* **Mancanza di `app.cash.turbine:turbine`**: Impossibile validare in modo deterministico le emissioni di `StateFlow` e `SharedFlow` relative a posizione GPS, sensori di orientamento e transizioni di stato.
* **Mancanza di `com.squareup.okhttp3:mockwebserver`**: Impossibile simulare disconnessioni di rete, risposte 429 (Rate Limit di Overpass), risposte 500 o JSON malformati dai provider meteorologici.

### 3.3 Assenza di Test Strumentati ed E2E (`androidTest`)
La directory `app/src/androidTest` non esiste fisicamente. Non è presente alcun test UI tramite `compose-ui-test-junit4` che verifichi:
- Il corretto rendering della scheda di probabilità al cambio di selezione sulla mappa.
- La navigazione fluida tra le 4 schede principali (`Home`, `Mappa`, `Specie`, `Impostazioni`).
- Il comportamento del bottom sheet e della search bar di geocoding.

---

## 4. Rischi di Concorrenza, Prestazioni e Ciclo di Vita Hardware

```mermaid
sequenceDiagram
    autonumber
    actor Utente
    participant View as MapScreen / MapViewContainer
    participant VM as MushroomViewModel
    participant Repo as MushroomRepository
    participant GPS as AndroidLocationProvider
    participant Sensor as AndroidSensorOrientationProvider

    Note over View,Sensor: SCENARIO A: Race Condition su Tap Rapidi
    Utente->>VM: selectLocation(Punto A)
    VM->>Repo: fetchWeather(A) [Latenza 1500ms]
    Utente->>VM: selectLocation(Punto B)
    VM->>Repo: fetchWeather(B) [Latenza 400ms]
    Repo-->>VM: Dati Punto B ricevuti (400ms)
    VM->>View: Mostra Dati Punto B
    Repo-->>VM: Dati Punto A ricevuti (1500ms) - SOVRASCRITTURA STALE!
    VM->>View: Mostra Dati Punto A su Marker B (Inconsistenza!)

    Note over View,Sensor: SCENARIO B: Battery Drain in Background
    Utente->>View: Apri MapScreen
    View->>Sensor: startOrientationUpdates() (SENSOR_DELAY_UI)
    View->>GPS: startLocationUpdates() (PRIORITY_HIGH_ACCURACY 1.2s)
    Utente->>View: Minimizza App (Tasto Home / Schermo Spento)
    Note right of Sensor: DisposableEffect NON chiama onDispose!
    Note right of GPS: GPS e Bussola continuano a girare in background!
```

### 4.1 Race Condition in `selectLocation()`
In `MushroomViewModel.kt:670-760`, quando l'utente seleziona un punto sulla mappa:
```kotlin
fun selectLocation(lat: Double, lon: Double, ...) {
    aiJob?.cancel() // Cancella solo il job del prompt AI!
    ...
    viewModelScope.launch { // Coroutine principale NON tracciata e NON cancellata!
        val weatherDeferred = async { repository.fetchWeather(lat, lon) }
        val habitatDeferred = async { repository.fetchHabitat(lat, lon) }
        ...
        todayProbability = calculatedProbability
    }
}
```
* **Vulnerabilità**: Se l'utente tocca in rapida successione il punto A e poi il punto B, vengono lanciate due coroutine parallele. Se la risposta di rete del punto A subisce una latenza maggiore rispetto a quella del punto B, i dati del punto A arriveranno per ultimi, sovrascrivendo e corrompendo la visualizzazione del punto B con informazioni meteorologiche e probabilità non corrispondenti.
* **Risoluzione Architetturale**:
  ```kotlin
  private var dataFetchJob: Job? = null

  fun selectLocation(lat: Double, lon: Double, ...) {
      aiJob?.cancel()
      dataFetchJob?.cancel() // Cancella immediatamente il fetch precedente in volo
      dataFetchJob = viewModelScope.launch {
          ...
      }
  }
  ```

### 4.2 Rischio Drain Batteria e Leak dei Sensori Hardware
In `MapScreen.kt:76-81`:
```kotlin
DisposableEffect(Unit) {
    viewModel.startLocationAndOrientationTracking()
    onDispose {
        viewModel.stopLocationAndOrientationTracking()
    }
}
```
* **Vulnerabilità**: `DisposableEffect(Unit)` invoca `onDispose` solo quando il Composable abbandona l'albero di composizione. Se l'utente minimizza l'app premendo il tasto Home, blocca lo schermo o riceve una telefonata, il Composable rimane memorizzato nello stack di navigazione. I sensori hardware associati:
  - `AndroidSensorOrientationProvider`: listener sul vettore di rotazione con `SensorManager.SENSOR_DELAY_UI` (~60 Hz).
  - `AndroidLocationProvider`: `Priority.PRIORITY_HIGH_ACCURACY` con polling GPS a intervalli di 1.200 ms.
  continuano a consumare energia elettrica ad alto amperaggio in background.
* **Risoluzione Architetturale**: Vincolare il tracciamento al ciclo di vita dell'`Activity` tramite `LifecycleEventEffect` di AndroidX:
  ```kotlin
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
      val observer = LifecycleEventObserver { _, event ->
          when (event) {
              Lifecycle.Event.ON_START -> viewModel.startLocationAndOrientationTracking()
              Lifecycle.Event.ON_STOP -> viewModel.stopLocationAndOrientationTracking()
              else -> {}
          }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose {
          lifecycleOwner.lifecycle.removeObserver(observer)
          viewModel.stopLocationAndOrientationTracking()
      }
  }
  ```

### 4.3 Mancata Gestione del Ciclo di Vita di OsmDroid (`MapView`)
In `MapViewContainer.kt:65-128`:
* L'istanza `MapView` creata all'interno di `AndroidView` non riceve mai i callback `onResume()`, `onPause()` o `onDetach()`.
* OsmDroid necessita di `onResume()` e `onPause()` per sospendere i thread di scaricamento e caching dei tile cartografici.
* Senza la chiamata esplicita a `mapView.onDetach()`, i riferimenti interni dei tile provider e dei listener impediscono il Garbage Collection del `Context` dell'Activity, causando leak di decine di megabyte di memoria grafica.

### 4.4 Perdita di Stato al Process Death (Assenza di `SavedStateHandle`)
`MushroomViewModel` memorizza tutte le selezioni attive in semplici campi `mutableStateOf`:
* Se il sistema operativo Android termina il processo dell'applicazione in background per recuperare memoria RAM, all'atto del ripristino l'applicazione perde tutte le selezioni, la cronologia temporanea e le coordinate attive, reimpostandosi alle coordinate di default.
* È necessario iniettare `SavedStateHandle` nel costruttore del ViewModel per persistere e ripristinare `selectedLatLng` e `selectedSpeciesId`.

---

## 5. Vettori di Evoluzione Funzionale e Scientifica

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                 5 VETTORI DI EVOLUZIONE SCIENTIFICO-FUNZIONALE              │
├─────────────────────────────────────────────────────────────────────────────┤
│ Vector 1: Modello Cartografico & Ecologico Species-Conditioned              │
│   • Heatmap pesata su micorriza vs saprotrofo e canopia arborea specifica  │
├─────────────────────────────────────────────────────────────────────────────┤
│ Vector 2: Modello Agro-Meteorologico Multi-Orizzonte & Radar Doppler        │
│   • Umidità suolo 0-7cm e 7-28cm, evapotraspirazione ET0, radar RainViewer  │
├─────────────────────────────────────────────────────────────────────────────┤
│ Vector 3: Integrazione Sensori Hardware Barometrici & Sonde BLE             │
│   • Altimetria barometrica TYPE_PRESSURE, sonde di temperatura/umidità suolo│
├─────────────────────────────────────────────────────────────────────────────┤
│ Vector 4: Architettura Offline-First con Bundle Regionali                   │
│   • Pacchetti compatti .mbtiles, SPUN sub-grid e snapshot meteo a 14 giorni  │
├─────────────────────────────────────────────────────────────────────────────┤
│ Vector 5: Sicurezza Micologica, Allarmi Sosia Tossici & Rete ASL            │
│   • Disclaimer obbligatorio, alert sosia velenosi e directory ispettorati   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.1 Vettore 1: Modello Cartografico ed Ecologico Species-Conditioned
Attualmente la probabilità cartografica non è condizionata dalla specie selezionata. La nuova architettura deve parametrizzare il motore di generazione raster:

#### Formula di Calibrazione Species-Conditioned:
Per ogni cella $(x, y)$ della griglia cartografica a raggio $R$:
$$\text{BioPotential}(x, y, S) = \left( \frac{\text{EcM}(x, y)}{\text{EcM}_{\max}} \cdot W_{\text{ecm}}(S) + \frac{\text{Hyp}(x, y)}{\text{Hyp}_{\max}} \cdot W_{\text{hyp}}(S) \right) \cdot K_{\text{canopy}}(x, y, S)$$

Dove:
* $S$ è la specie attiva (`MushroomSpecies`).
* $W_{\text{ecm}}(S)$ e $W_{\text{hyp}}(S)$ sono i pesi ecologici della specie (es. per *Boletus edulis*: $W_{\text{ecm}}=0.80, W_{\text{hyp}}=0.20$; per *Macrolepiota procera*: $W_{\text{ecm}}=0.10, W_{\text{hyp}}=0.90$).
* $K_{\text{canopy}}(x, y, S)$ è il fattore di affinità della copertura forestale locale ottenuto dinamicamente da Overpass querying per i generi arborei associati a $S$.

### 5.2 Vettore 2: Modello Agro-Meteorologico Multi-Orizzonte e Radar Precipitativo
L'attuale modello considera solo pioggia cumulativa di superficie e temperatura a 2 metri. I funghi micorrizici e saprotrofi si sviluppano negli strati del suolo organico e minerale:
1. **Integrazione Orizzonti del Suolo Open-Meteo**:
   - `soil_temperature_0_to_7cm` e `soil_temperature_7_to_28cm`: verifica della temperatura effettiva alla profondità delle ife (intervallo ideale per primordi di porcino: $14^\circ\text{C} \dots 18^\circ\text{C}$).
   - `soil_moisture_0_to_7cm` e `soil_moisture_7_to_28cm`: umidità volumetrica ($m^3/m^3$), essenziale per modellare il potenziale idrico del suolo.
2. **Evapotraspirazione di Riferimento ($ET_0$)**:
   Modellare la curva di asciugatura del sottobosco mediante l'equazione di Penman-Monteith, riducendo il punteggio di fruttificazione in presenza di vento secco e forte irraggiamento solare post-pioggia.
3. **Overlay Radar Precipitativo in Tempo Reale**:
   Integrazione di layer cartografici animated tile (es. RainViewer API o rete radar DPC nazionale) per visualizzare l'avvicinarsi di temporali orografici direttamente su `MapView`.
4. **Correzione Termica Adiabatica (Lapse Rate)**:
   Aggiustare la temperatura della stazione base in funzione della quota digitale SRTM:
   $$T_{\text{locale}} = T_{\text{meteo}} - 0.0065 \cdot (h_{\text{SRTM}} - h_{\text{meteo}})$$

### 5.3 Vettore 3: Integrazione Sensori Hardware Barometrici e Telemetria BLE
Per i foraggiatori in quota in zone prive di connettività:
1. **Sensore di Pressione Barometrica Nativo (`Sensor.TYPE_PRESSURE`)**:
   - Calcolo altimetrico iper-preciso con compensazione dell'errore di quota GPS verticale (che nei boschi densi può superare i $\pm 40$ metri).
   - Allarme barometrico per caduta repentina di pressione ($>2 \text{ hPa / 3 ore}$), indicatore predittivo di temporali violenti in ambiente montano.
2. **Supporto Sonde di Campagna BLE (Bluetooth Low Energy)**:
   - Integrazione opzionale per sonde igrometriche/termometriche da terreno (es. sensori BLE Mi Flora o sonde LoRaWAN/BLE industriali) conficcate nel suolo dal raccoglitore, per confrontare la stima algoritmica con il dato misurato in-situ.

### 5.4 Vettore 4: Architettura Offline-First con Bundle Regionali
Nelle valli alpine e appenniniche la connettività cellulare è spesso assente.
* **Pacchetti Offline Geografici (.mycopack)**:
  Contenitore compresso zip/tar contente:
  1. Archivio `.mbtiles` vettoriale/raster (OpenTopoMap livelli zoom 8..15).
  2. Sotto-griglia binaria SPUN ritagliata ad alta densità per l'area di interesse.
  3. Snapshot meteo a 14 giorni con modello di decadimento offline.
  4. Matrice di elevazione SRTM a risoluzione 30 metri per calcolo aspetto e pendenza.
* **Degradazione Elegante della UI**:
  Transizione automatica a banner "Modalità Campo Offline", calcolo dei modelli basato sulle tendenze salvate e disattivazione delle query Overpass senza blocchi o errori di rete.

### 5.5 Vettore 5: Sicurezza Micologica, Allarmi Sosia Tossici e Rete ASL
La sicurezza del raccoglitore è prioritaria. L'applicazione deve fornire salvaguardie legali e sanitarie:
* **Disclaimer di Responsabilità al Primo Avvio**:
  Modale bloccante in cui l'utente dichiara di comprendere che l'app valuta la *probabilità ecologica di crescita* e NON certifica in alcun modo la *commestibilità* dei funghi raccolti.
* **Sistema di Allarme per Sosia Tossici e Mortali**:
  All'interno della scheda di dettaglio di ciascuna specie nel catalogo e nel report AI, esporre un banner di avvertenza esplicito:
  - *Amanita caesarea* (Buono) $\longleftrightarrow$ *Amanita phalloides* (Mortale, amanitine) e *Amanita muscaria*.
  - *Armillaria mellea* (Chiodino) $\longleftrightarrow$ *Galerina marginata* (Mortale) + avvertenza di tossicità da crudo.
  - *Macrolepiota procera* (Mazza di tamburo) $\longleftrightarrow$ *Chlorophyllum molybdites* / piccole *Lepiota* velenose mortali.
  - *Cantharellus cibarius* (Finferlo) $\longleftrightarrow$ *Omphalotus olearius* (Tossico grave).
* **Guida e Directory Georeferenziata agli Ispettorati Micologici ASL**:
  Integrazione dei contatti telefonici e indirizzi dei centri di controllo micologico pubblico gratuiti presenti nelle ASL del territorio italiano, con chiamata rapida con un tocco.

---

## 6. Predisposizione Multiplatform e Mobile iOS (Allineamento con `docs/IOS_ARCHITECTURE.md`)

Il documento `docs/IOS_ARCHITECTURE.md` specifica le linee guida per la realizzazione della versione mobile iOS (iPhone e iPad) condividendo il 100% della logica di business.

```mermaid
graph TD
    subgraph "Architettura Esagonale Multiplatform"
        subgraph "shared:core (Kotlin Multiplatform puro)"
            M[Modelli: Species, Weather, Factors, HeatmapRaster]
            A[Algoritmi: MushroomAlgorithms, 5-point DEM, Aspect]
            P_PORT[Ports: KeyValueStorage, AssetProvider, PlatformAiEngine]
            R[Repository: MushroomRepository, CacheManager, SpunDataManager]
        end

        subgraph "app:android (Android Application)"
            A_UI[Jetpack Compose + Material 3]
            A_MAP[OsmDroid MapView]
            A_ADAPT[Android Adapters: FusedLocation, SharedPreferences, AICore Nano]
        end

        subgraph "app:ios (iOS Mobile Application)"
            I_UI[Compose Multiplatform iOS o SwiftUI]
            I_MAP[MapLibre Compose / MapKit Viewer]
            I_ADAPT[iOS Adapters: CoreLocation, NSUserDefaults, CoreML / Apple Intelligence]
        end

        A_UI --> R
        A_ADAPT -.->|implements| P_PORT
        I_UI --> R
        I_ADAPT -.->|implements| P_PORT
    end
```

### 6.1 Verifica di Portabilità del Core Condiviso
* **Buffer Raster Pure Kotlin**: `HeatmapRaster` impiega già un `IntArray` 32-bit ARGB senza riferimenti ad `android.graphics.Bitmap`. Sulla versione iOS, questo buffer viene convertito direttamente in un'immagine Skia (`org.jetbrains.skia.Image.makeRaster`) o in un `CGImage` nativo Apple senza necessità di emulare Android.
* **Indipendenza degli Algoritmi**: `MushroomAlgorithms.kt` non possiede alcuna dipendenza verso librerie Android o Java non portabili.
* **Interfacce Port già Esistenti**: `AssetProvider`, `KeyValueStorage`, `PlatformNavigator`, `PlatformLocationProvider` e `PlatformOrientationProvider` in `github.naturewhisp.myco.platform` consentono un innesto plug-and-play di implementazioni per iOS (`IosUserDefaultsStorage`, `IosAssetProvider`, `IosLocationProvider`, `IosOrientationProvider`, `IosPlatformNavigator`).

### 6.2 Azioni Preliminari Necessarie per la Modularizzazione KMP
1. **Rimozione dei costruttori con `Context` nei Repository** (risolve TD-05).
2. **Spostamento della classe `HeatmapData`** (che contiene il riferimento a `android.graphics.Bitmap`) dal package model del core al layer di presentazione Android (`ui/components` o `platform/android`).
3. **Conversione del build script di Gradle** da Groovy DSL singolo (`app/build.gradle`) a multi-modulo con Kotlin DSL (`settings.gradle.kts`, `:core`, `:app`, `:iosApp`).

---

## 7. Matrice di Priorità e Stima della Complessità

### 7.1 Mappa a Quadranti (Impatto vs Sforzo)

```
        ▲ ALTO (5)
        │
        │  [QUICK WINS - Vittorie Rapide]            [PROGETTI STRATEGICI]
        │  • FIX-01: Leak Sensori MapScreen (TD-05)  • ARCH-01: Migrazione a Room DB (TD-03)
        │  • FIX-02: Cancella Job Concorrenti        • FEAT-01: Heatmap Species-Conditioned (TD-08)
        │  • FIX-03: Disclaimer Sosia Tossici (V-05) • ARCH-02: Decomposizione ViewModel
I       │  • FIX-04: Specie in Prompt AI (TD-09)     • FEAT-02: Umidità Suolo Multi-Orizzonte (V-02)
M       │  • FIX-05: Pesi Tipizzati Config (TD-11)   • FEAT-04: Bundle Offline & MBTiles (V-04)
P       │  • FIX-06: Rimozione Context Core (TD-05)  • KMP-01: Estrazione Modulo :core
A       │
T       │  [ATTIVITÀ MINORI / FILL-INS]              [EVOLUTIVE A LUNGO TERMINE]
T       │  • TASK-01: User-Agent Dinamico (TD-14)    • FEAT-05: Barometro & Sonde BLE (V-03)
O       │  • TASK-02: Namespace Cache (TD-18)        • FEAT-06: Radar Precipitativo Real-Time
        │  • TASK-03: Snapping Foresta Reale (TD-01) • FEAT-07: WeatherNext 3 Nowcasting (Post v2.0)
        │  • TASK-04: Estrazione strings.xml (TD-17) • KMP-02: Release iOS Mobile App
        │
        └─────────────────────────────────────────────────────────────────────────────►
          BASSO (1)                             SFORZO                      ALTO (5)
```

### 7.2 Tabella Strutturata di Prioritizzazione e Complessità

*Formula di Punteggio di Priorità*:
$$\text{Priority Score} = (\text{Impatto} \times 2) - \text{Sforzo} \quad (\text{Scala: } 1.0 \dots 10.0)$$

| ID | Categoria | Titolo Iniziativa | Impatto (1-5) | Sforzo (1-5) | Priority Score | Priorità | Complessità (Story Points / T-Shirt) | Release Target | Stato |
|---|---|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **FIX-01** | Lifecycle | Risoluzione leak sensori orientamento e GPS in `MapScreen` | 5 | 1 | **9.0** | **P1** | 2 SP / **S** | v1.1 | **COMPLETATO** (`7b89925`) |
| **FIX-02** | Concurrency | Cancellazione job di fetch concorrenti in `selectLocation` | 4 | 1 | **7.0** | **P1** | 2 SP / **S** | v1.1 | **COMPLETATO** (`7b89925`) |
| **FIX-03** | Safety | Modale disclaimer legale e avvisi sosia velenosi | 5 | 2 | **8.0** | **P1** | 3 SP / **S** | v1.1 | **COMPLETATO** (`7b89925`) |
| **FIX-04** | AI Pipeline | Iniezione tassonomia specie nel prompt Gemini Nano | 4 | 1 | **7.0** | **P1** | 1 SP / **XS** | v1.1 | **COMPLETATO** (`7b89925`) |
| **FIX-05** | Refactoring | Estrazione pesi matematici ed ecologici tipizzati | 4 | 1 | **7.0** | **P1** | 2 SP / **S** | v1.1 | **COMPLETATO** (`78b05ce`) |
| **FIX-06** | Architecture | Rimozione costruttori `Context` in `repository/` | 4 | 1 | **7.0** | **P1** | 2 SP / **S** | v1.1 | **COMPLETATO** (`78b05ce`) |
| **ARCH-01**| Storage | Migrazione cache ad SQLite con indici geospaziali e TTL | 5 | 3 | **7.0** | **P1** | 8 SP / **M** | v1.1 | **COMPLETATO** (v1.1) |
| **TEST-01**| Quality | Setup MockK, Coroutines-Test, Turbine e test ViewModel | 5 | 3 | **7.0** | **P1** | 8 SP / **M** | v1.1 | **COMPLETATO** (v1.1) |
| **FEAT-01**| Science | Condizionamento dell'Heatmap alla specie attiva | 5 | 3 | **7.0** | **P1** | 5 SP / **M** | v1.2 | **COMPLETATO** (v1.2) |
| **FEAT-02**| Science | Umidità suolo 0-7cm, 7-28cm ed evapotraspirazione | 4 | 3 | **5.0** | **P2** | 5 SP / **M** | v1.2 | **COMPLETATO** (v1.2) |
| **FEAT-03**| Network | Query Overpass dinamica con alberi associati alla specie | 4 | 2 | **6.0** | **P2** | 3 SP / **S** | v1.2 | **COMPLETATO** (v1.2) |
| **FEAT-04**| Field Ops | Bundle geografici offline e prefetch strati ambientali | 5 | 4 | **6.0** | **P2** | 13 SP / **L** | v1.2 | **COMPLETATO** (v1.2) |
| **TASK-01**| Network | User-Agent dinamico e parametrico | 2 | 1 | **3.0** | **P3** | 1 SP / **XS** | v1.2 | **COMPLETATO** (v1.2) |
| **TASK-02**| Storage | Isolamento namespace chiavi cache e clear selettivo | 3 | 1 | **5.0** | **P2** | 2 SP / **S** | v1.1 | **COMPLETATO** (v1.1) |
| **TASK-03**| Cartography| Snap reale su poligoni forestali Overpass (TD-01) | 3 | 2 | **4.0** | **P2** | 3 SP / **S** | v1.2 | **COMPLETATO** (v1.2) |
| **TASK-04**| Localization| Estrazione stringhe UI in `strings.xml` (TD-17) | 4 | 3 | **5.0** | **P2** | 5 SP / **M** | v1.2 | **COMPLETATO** (v1.2) |
| **KMP-01** | Architecture| Riorganizzazione Gradle in multi-modulo `:core` KMP | 5 | 4 | **6.0** | **P2** | 13 SP / **L** | v2.0 | *Pianificato* |
| **KMP-02** | Mobile UI | Implementazione client iOS con Compose Multiplatform / SwiftUI | 5 | 4 | **6.0** | **P2** | 13 SP / **L** | v2.0 | *Pianificato* |
| **FEAT-05**| Hardware | Sensore barometrico nativo e telemetria sonde BLE | 3 | 4 | **2.0** | **P3** | 8 SP / **M** | v2.0 | *Pianificato* |
| **FEAT-06**| Weather | Overlay radar precipitativo animato su MapView | 3 | 3 | **3.0** | **P3** | 5 SP / **M** | v2.0 | *Pianificato* |
| **FEAT-07**| AI Weather | Nowcasting predittivo WeatherNext 3 (Google DeepMind) per FEAT-06 | 4 | 4 | **4.0** | **P3** | 13 SP / **L** | Post v2.0 | *Valutazione (Subordinata a FEAT-06)* |

---

## 8. Roadmap Strategica in Tre Fasi

```mermaid
gantt
    title Roadmap di Rilascio Myco (2026-2027)
    dateFormat  YYYY-MM-DD
    section Fase 1: v1.1 Hardening
    Fix Concorrenza & Lifecycle (FIX-01, FIX-02) :crit, done, 2026-09-15, 2026-09-16
    Disclaimer Tossicologico (FIX-03)           :done, 2026-09-16, 2026-09-17
    Integrazione Specie in Prompt AI (FIX-04)   :done, 2026-09-17, 2026-09-18
    Refactoring Pesi & Modelli Puri (FIX-05, FIX-06) :done, 2026-09-15, 2026-09-16
    Storage SQLite & Cache Isolation (ARCH-01, TASK-02) :crit, done, 2026-09-18, 2026-09-19
    Test Suite & MockK / Turbine (TEST-01)      :done, 2026-09-18, 2026-09-19
    Release v1.1 Stabile                        :milestone, 2026-10-08, 0d

    section Fase 2: v1.2 Scientific Expansion
    Heatmap Species-Conditioned (FEAT-01)       :crit, done, 2026-10-10, 2026-10-24
    Umidità Suolo Multi-Orizzonte (FEAT-02)     :done, 2026-10-20, 2026-10-30
    Query Overpass Dinamiche (FEAT-03)          :done, 2026-10-25, 2026-11-01
    Parametrizzazione User-Agent (TASK-01)      :done, 2026-10-15, 2026-10-16
    Snapping Foresta Reale (TASK-03)            :done, 2026-11-01, 2026-11-05
    Copertura Dinamica Fuori Confini (TD-02)    :done, 2026-11-05, 2026-11-08
    Resilienza & Prefetch Offline (FEAT-04)     :crit, done, 2026-11-08, 2026-11-20
    Localizzazione strings.xml (TASK-04)        :done, 2026-11-20, 2026-11-25
    Release v1.2 Stabile                        :milestone, 2026-12-05, 0d

    section Fase 3: v2.0 Multiplatform iOS
    Modularizzazione Gradle KMP :core (KMP-01)  :crit, 2027-01-10, 21d
    Adapter iOS (CoreLocation, NSUserDefaults, CoreML) :2027-02-01, 14d
    UI Compose Multiplatform iOS / SwiftUI (KMP-02)   :crit, 2027-02-15, 28d
    Sensori Barometrici & Sonde BLE (FEAT-05)   :2027-03-01, 14d
    Radar Precipitativo Real-Time (FEAT-06)     :2027-03-10, 14d
    Release v2.0 iOS & Android                  :milestone, 2027-04-01, 0d

    section Post Fase 3: Evolutive Avanzate
    Nowcasting AI WeatherNext 3 (FEAT-07 sub FEAT-06) :2027-04-15, 28d
```

### 8.1 Fase 1: Release v1.1 — Affidabilità, Sicurezza e Consolidamento Architetturale
*Obiettivo Primario*: Eliminare le vulnerabilità del ciclo di vita, blindare la concorrenza, sanare lo storage e raggiungere una solida copertura di test unitari senza modificare l'esperienza d'uso fondamentale.

* **Deliverable e Interventi**:
  1. [x] **Lifecycle & Battery Fix (FIX-01)**: **COMPLETATO** (commit `7b89925`) — Associazione dei sensori di orientamento e GPS al ciclo di vita dell'Activity in `MapScreen` tramite `LifecycleEventObserver`; aggancio dei metodi `onResume()`, `onPause()` e `onDetach()` su `MapViewContainer`.
  2. [x] **Concurrency Hardening (FIX-02)**: **COMPLETATO** (commit `7b89925`) — Tracciamento di `dataFetchJob` in `MushroomViewModel` con cancellazione deterministica delle coroutine obsolete prima di ogni nuovo caricamento di coordinate e deallocazione in `onCleared()`.
  3. [x] **Sicurezza Micologica & Sosia Tossici (FIX-03)**: **COMPLETATO** (commit `7b89925`) — Integrazione della schermata modale di disclaimer legale al primo avvio (`SafetyDisclaimerDialog`), persistenza preferenza e censimento avvisi sui sosia tossici mortali nel dettaglio specie (`MushroomSpecies`).
  4. [x] **AI Context Injection (FIX-04)**: **COMPLETATO** (commit `7b89925`) — Inclusione della tassonomia della specie attiva (`selectedSpecies`), nome binomiale, requisiti ideali e canopia arborea nel prompt per Gemini Nano AICore.
  5. [x] **Refactoring Pesi & Modelli Tipizzati (FIX-05)**: **COMPLETATO** (commit `78b05ce`) — Estrazione di `EcologicalWeightsConfig`, `HeatmapRenderConfig`, `ProbabilityTier`, `TerrainAspectConfig`.
  6. [x] **Rimozione Dipendenze Context nel Core (FIX-06 / TD-05)**: **COMPLETATO** (commit `78b05ce`) — Eliminazione dei costruttori secondari con `Context` nei repository, introduzione di `KeyValueStorage` e `AssetProvider`.
  7. [x] **Migrazione Persistenza & Cache Store (ARCH-01 / TASK-02 / TD-18)**: **COMPLETATO** (v1.1) — Separazione rigorosa tra preferenze utente persistenti (`KeyValueStorage`) e cache di rete effimera (`PlatformCacheStore`). Implementazione di `AndroidSqliteCacheStore` con indici geospaziali e temporali, evaporazione TTL automatica e `InMemoryCacheStore` per JVM e unit test. Risolve interamente TD-18: `clearCache()` dealloca unicamente la tabella SQLite effimera preservando le preferenze utente.
  8. [x] **Quality & Test Foundation (TEST-01)**: **COMPLETATO** (v1.1) — Integrazione MockK, Coroutines-Test e Turbine. Creazione suite di test completa (`CacheManagerTest`, `MushroomRepositoryTest`, `MushroomViewModelTest`) portando il totale a 66 unit test (100% passing).

* **Criteri di Rilascio v1.1**:
  - Zero warning da `lintDebug` e rispetto della Zero Diagnostic Policy.
  - Nessun consumo anomalo di batteria o sensori attivi rilevati tramite battery profiler in background.
  - Test suite con almeno 30 test unitari passing.

---

### 8.2 Fase 2: Release v1.2 — Espansione Ecologica, Scientifica e Operatività Offline
*Obiettivo Primario*: Connettere l'intero pipeline algoritmico e cartografico alla specie selezionata, integrare modelli avanzati di umidità del suolo e supportare la raccolta in zone prive di segnale telefonico.

* **Deliverable e Interventi**:
  1. [x] **Motore Raster Species-Conditioned (FEAT-01 / TD-08)**: **COMPLETATO** (v1.2) — Riprogettazione di `HeatmapGenerator` con parametro `species: MushroomSpecies`, calibrazione del potenziale biologico `bioPotential` in base alla categoria ecologica (`EcologicalCategory`: saprotrofi prativi guidati da biomassa ifale, micorrizici con simbiosi bilanciata EcM, parassiti lignicoli), incorporazione dell'altitudine nel moltiplicatore meteo e ricalcolo asincrono `heatmapJob` in `MushroomViewModel` su cambio specie con cancellazione deterministica dei job in corso.
  2. [x] **Query Overpass Intelligenti & Mirror Pre-allocati (FEAT-03 / TD-10 / TD-16)**: **COMPLETATO** (v1.2) — Costruzione dinamica dei filtri `genus` basata sulle essenze arboree simbionti registrate per ciascuna specie fungina (`preferredCanopyTypes`) o interrogazione su prati/pascoli per funghi saprotrofi. Cache isolata per specie (`habitat_bonus_${speciesId}_...`) e pre-allocazione dei client Retrofit mirror per azzerare reflection overhead.
  3. [x] **Agro-Meteo Avanzato & Umidità Multi-Profondità (FEAT-02 / TASK-01 / TD-14)**: **COMPLETATO** (v1.2) — Richiesta e aggregazione da Open-Meteo dei parametri di umidità del suolo a due profondità orizzontali ($0 \dots 7\text{ cm}$ per primordi e $7 \dots 28\text{ cm}$ per micelio perenne profondo) ed evapotraspirazione $ET_0$. Modellazione della risposta continua `soilMoistureScoreSmooth` integrata nel punteggio meteorologico e fattore UI `FactorId.SOIL_MOISTURE`. Header User-Agent parametrico e dinamico in `NetworkClient`.
  4. [x] **Operatività sul Campo & Resilienza Offline (FEAT-04 / TD-01 / TD-02 / TASK-03)**: **COMPLETATO** (v1.2) — Snapping autentico su poligoni e formazioni forestali reali tramite Overpass QL (`findNearestForest`) con calcolo geospaziale Haversine; calcolo dinamico del punto di copertura SPUN più vicino (`findClosestCoveragePoint`) con indicazione di distanza reale e toponimo sentinella; fallback automatico sui dati in cache SQLite ignorando la scadenza TTL in assenza di connettività di rete (`getCachedDataIgnoreExpiry`); banner di stato "Modalità campo offline"; tool interattivo di precaricamento offline completo di tutti i layer ambientali in `SettingsScreen` (`prefetchForOfflineUse`).
  5. [x] **Internazionalizzazione e Pulizia Risorse (TASK-04 / TD-17)**: **COMPLETATO** (v1.2) — Estrazione progressiva e integrale delle stringhe di interfaccia in `res/values/strings.xml`, supporto alle forme plurali (`plurals`), conformità tipografica ed eliminazione totale dei warning di analisi statica.
  6. [x] **Espansione Test Suite Automatizzata**: **COMPLETATO** (v1.2) — Aggiunti test su formula di Haversine, sentinelle SPUN, fallback offline del repository, snapping forestale del ViewModel e sincronizzazione offline, portando la suite a **82 test unitari (100% passing)**.

* **Criteri di Rilascio v1.2**:
  - Calcolo dell'heatmap in meno di 50 ms su dispositivo mobile medio di riferimento (raggiunto: $< 2\text{ ms}$).
  - Ricalcolo dinamico istantaneo dell'heatmap e dei fattori ecologici su selezione nuova specie.
  - Zero warning diagnostici (`0 errors, 0 warnings`) e 100% test passing su suite estesa (82 test).
  - Validazione e verifica funzionale superata su Google Pixel 10 Pro fisico.

---

### 8.3 Fase 3: Release v2.0 — Porting Mobile iOS e Telemetria Hardware sul Campo
*Obiettivo Primario*: Rilasciare la versione mobile nativa per iOS (iPhone e iPad) condividendo il 100% della logica di business e abilitare funzionalità hardware avanzate per raccoglitori professionisti sul campo.

* **Deliverable e Interventi**:
  1. **Riorganizzazione Gradle Multiplatform**: Scorporo del repository nei moduli `:core` (Kotlin Multiplatform puro con `commonMain`, `androidMain`, `iosMain`), `:app` (Android) e `:iosApp` (iOS).
  2. **Implementazione iOS Adapters**:
     - `IosAssetProvider` con accesso all'atlante SPUN tramite `NSBundle.mainBundle`.
     - `IosUserDefaultsStorage` basato su `NSUserDefaults` per preferenze e preferiti.
     - `IosLocationProvider` con Apple `CoreLocation` (`CLLocationManager`) e `IosOrientationProvider` con `CLHeading` per bussola hardware da campo.
     - `IosPlatformNavigator` con apertura di coordinate su Apple Maps via `maps://` o `MKMapItem`.
     - `IosAiEngine` con integrazione di modelli on-device tramite Apple Intelligence / CoreML.
     - `IosSqliteCacheStore` basato su SQLite nativo C-API o SQLDelight.
  3. **Interfaccia Grafica Mobile iOS**: Realizzazione dell'interfaccia utente con **Compose Multiplatform for iOS** (o SwiftUI nativo), ottimizzata per iPhone, navigazione fluida, gestione preferiti, widget per la schermata di blocco/home screen ed esportazione waypoint.
  4. **Altimetria Barometrica Nativa**: Lettura del barometro di bordo (`CMAltimeter` su iOS, `Sensor.TYPE_PRESSURE` su Android) per calibrazione della quota e allarmi meteo rapidi.
  5. **Integrazione Sonde BLE di Terze Parti**: Supporto per la connessione con sonde di umidità e temperatura del terreno Bluetooth via `CoreBluetooth`.

* **Criteri di Rilascio v2.0**:
  - Applicazione mobile per iOS pacchettizzata come `.ipa` e distribuita tramite TestFlight / App Store.
  - Condivisione verificata di oltre l'85-90% del codice logico, dei modelli e dell'interfaccia tra le piattaforme.

---

### 8.4 Orizzonte Evolutivo Post-Fase 3: Integrazione WeatherNext 3 (Google DeepMind) Subordinata a FEAT-06
*Obiettivo Primario*: Valutare e definire l'innesto del modello predittivo globale ad altissima risoluzione **Google DeepMind WeatherNext 3** come acceleratore e motore predittivo per il radar nowcasting (`FEAT-06`), subordinando tassativamente la sua adozione al completamento preliminare della Fase 3 (v2.0 Multiplatform iOS) e della baseline radar standard.

```mermaid
graph TD
    subgraph "Core Algoritmico Micologico (Open-Meteo & SPUN)"
        OM_HIST["Open-Meteo Archive API<br>• Piogge cumulate 14 gg<br>• Shock termico T° 5 gg"]
        OM_SOIL["Open-Meteo Agrometeo<br>• Umidità suolo 0-7 cm e 7-28 cm<br>• Evapotraspirazione ET₀"]
        SPUN_BIO["SPUN Mycorrhizal Atlas<br>• Biomassa miceliare ipogea<br>• Indice simbiotico EcM"]
        ALG_CORE["MushroomAlgorithms Pipeline<br>(Formula Unificata Probabilità)"]
        OM_HIST & OM_SOIL & SPUN_BIO --> ALG_CORE
    end

    subgraph "Radar Precipitativo & Nowcasting (FEAT-06 & FEAT-07)"
        RADAR_LIVE["FEAT-06: Radar Convenzionale<br>(RainViewer / DPC Rete Nazionale)<br>• Copertura precipitativa istantanea"]
        WN3_GCP["Google Cloud Platform (WeatherNext 3)<br>• Risoluzione 0.05° (~5 km)<br>• 64 membri ensemble (p10, p50, p90)<br>• Orizzonte orario 1-24h"]
        PROXY["Backend Serverless (Cloud Run / Cloud Functions)<br>• Bounding-box crop territoriale<br>• Caching geospaziale a celle<br>• Generazione GeoJSON / Tile vettoriali"]
        RADAR_MAP["MapView Overlay Precipitativo<br>• Mappa di pioggia passata / presente<br>• Cono predittivo temporali e grandine"]
        
        WN3_GCP --> PROXY
        PROXY -.->|FEAT-07 (Post-Fase 3)| RADAR_MAP
        RADAR_LIVE -->|FEAT-06 Baseline| RADAR_MAP
    end

    ALG_CORE --> UI_PROB["Dashboard & Heatmap Probabilità"]
    RADAR_MAP --> UI_MAP["MapView con Allerta Meteo da Campo"]
```

#### 8.4.1 Contesto Tecnologico di Google DeepMind WeatherNext 3
Rilasciato il 3 settembre 2026 da Google DeepMind e Google Research, **WeatherNext 3** rappresenta la nuova generazione di modelli globali fondazionali per le previsioni meteorologiche basati su intelligenza artificiale:
* **Risoluzione Spaziale Globale:** Griglia ad altissima densità di **$0.05^\circ$ (circa 5 km)** all'equatore e alle medie latitudini europee (sensibilmente superiore ai $9 \dots 25\text{ km}$ dei modelli numerici sinottici tradizionali quali IFS ECMWF o GFS NOAA).
* **Frequenza Temporale e Cadenza di Corsa:** Risoluzione oraria (1-hour forecast step) con corse multiple giornaliere o orarie ad aggiornamento continuo.
* **Previsione Probabilistica Ensemble:** Generazione di **64 membri ensemble**, che permettono di calcolare distribuzioni percentili di precipitazione ($p_{10}, p_{50}, p_{90}$) e quantificare l'incertezza intrinseca dei fenomeni convettivi violenti.
* **Canali di Accesso Cloud:** Dataset operativi resi disponibili su Google Cloud Platform tramite **BigQuery**, **Google Earth Engine** e bucket Google Cloud Storage in formato compresso multidimensionale **Zarr v3**.

#### 8.4.2 Perché NON Sostituisce Open-Meteo nel Core Micologico
L'indagine scientifica e algoritmica condotta evidenzia che WeatherNext 3 **non può sostituire** Open-Meteo per il calcolo della probabilità di crescita dei funghi:
1. **Assenza di Serie Storiche Pregresse:** La fruttificazione dei macromiceti epigei (es. *Boletus edulis*, *Cantharellus cibarius*) non è governata dalle condizioni previste per i prossimi giorni, bensì dalla pioggia caduta nei **10–14 giorni precedenti** (necessaria per idratare il feltro miceliare sotterraneo e indurre il differenziamento dei primordi) e dallo shock termico passato. WeatherNext 3 è un modello di pura *prognosi futura* (forward forecast) e non un archivio rianalitico/osservato retrospettivo.
2. **Assenza della Stratigrafia Idrica Sotterranea:** Myco modella l'ecologia fungina attraverso l'umidità volumetrica del suolo a due profondità differenziate ($0 \dots 7\text{ cm}$ per l'orizzonte primordiale e $7 \dots 28\text{ cm}$ per il micelio perenne) fornite da Open-Meteo / ERA5-Land. WeatherNext 3 simula le variabili atmosferiche e le grandezze di superficie, ma non include la fisica idrologica complessa dei suoli boschivi multilivello.

*Conclusione*: **Open-Meteo rimane il motore esclusivo e irrinunciabile per la pipeline agro-meteorologica di calcolo della probabilità di fruttificazione (`MushroomAlgorithms`).**

#### 8.4.3 Sinergia Strategica con FEAT-06 (Nowcasting & Radar Precipitativo)
Il valore applicativo di WeatherNext 3 per Myco risiede interamente nella dimensione di **sicurezza del raccoglitore e nowcasting a brevissimo termine** durante le escursioni boschive in ambiente montano:
* **Previsione Tempestiva di Celle Temporalesche Orografiche:** I raccoglitori di funghi operano in vallate alpine e appenniniche dove i temporali estivo-autunnali si formano rapidamente per convezione locale. La risoluzione a 5 km e l'orizzonte a 1–6 ore consentono di prevedere l'innesco di celle temporalesche e grandinate con un anticipo e una precisione orografica inaccessibili ai modelli sinottici globali.
* **Incertezza Quantificata per Rischio Fulmini/Nubifragi:** Sfruttando i 64 membri ensemble, l'applicazione può mostrare un indice di rischio confidenziale (es. "Probabilità di pioggia battente $>15\text{ mm/h}$ nelle prossime 2 ore: 82% [Intervallo $p_{10}-p_{90}$: $8 \dots 26\text{ mm}$]").

#### 8.4.4 Condizioni di Subordinazione e Prerequisiti di Sviluppo
L'implementazione dell'integrazione con WeatherNext 3 (`FEAT-07`) è **espressamente subordinata** al rispetto della seguente sequenza di rilascio:
1. **Completamento Integrale della Fase 3 (v2.0):** Nessuno sforzo di ricerca o sviluppo su WeatherNext 3 sarà avviato prima del rilascio stabile di Myco v2.0 per iOS (iPhone/iPad) e del consolidamento dell'architettura multi-modulo Kotlin Multiplatform (`:core`, `:app`, `:iosApp`).
2. **Implementazione Preliminare della Baseline FEAT-06:** Deve essere prima implementato e collaudato l'overlay radar convenzionale in tempo reale su `MapView` (utilizzando API radar raster consolidate e leggere, quali RainViewer o feed radar aperti della Protezione Civile). WeatherNext 3 agirà come estensione predittiva *atop* del visualizzatore radar già funzionante.
3. **Approvazione delle Quote e Accesso Google Cloud:** Attivazione e verifica delle quote operative sul progetto Google Cloud dell'utente (accesso BigQuery / Earth Engine per WeatherNext 3).

#### 8.4.5 Architettura a Proxy Serverless Indispensabile
I dataset di WeatherNext 3 (tabelle BigQuery da decine di terabyte o formati chunked Zarr v3) **non possono essere interrogati direttamente dai dispositivi mobili client** (Android o iOS) in ambiente boschivo per tre ragioni critiche:
* **Consumo di Banda e Latenza Cellulare:** Un'interrogazione diretta Zarr/BigQuery richiederebbe decine di megabyte di scambio dati su reti 3G/EDGE montane.
* **Sicurezza delle Credenziali Cloud:** Non è ammesso distribuire chiavi di servizio Google Cloud (Service Account Keys) all'interno degli APK o bundle IPA distribuiti agli utenti.
* **Costo di Scansione BigQuery:** Ogni query geografica non ottimizzata su BigQuery comporterebbe scansioni massive a pagamento.

*Disegno dell'Infrastruttura Proxy (GCP Cloud Run / Cloud Functions)*:
Un microservizio serverless leggero (ospitato su Cloud Run con container Python/Go):
1. Riceve dal client Myco una richiesta georeferenziata con bounding box locale (es. $\text{lat} \pm 0.15^\circ$, $\text{lon} \pm 0.15^\circ$) e timestamp.
2. Esegue una slice spaziale pre-indicizzata su Zarr v3 / BigQuery.
3. Aggrega i percentili ensemble ($p_{10}, p_{50}, p_{90}$) per la cella richiesta.
4. Restituisce al client mobile un payload GeoJSON vettoriale ultra-compatto ($< 15\text{ KB}$) o un set di tile raster semi-trasparenti pronte per il layer OsmDroid/MapLibre.
5. Mantiene una cache edge territoriale di 30 minuti per servire istantaneamente richieste provenienti dalla medesima vallata montano-forestale.

