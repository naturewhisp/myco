# ADR-001 — Architettura iOS nativa per Myco

*Stato:* Accepted  
*Data:* 2026-09-13  
*Decisione:* UI e integrazioni iOS native; KMP limitato al core deterministico.

## Contesto

Myco oggi è un’app Android Compose con un layer `platform` che anticipa alcune porte agnostiche. `docs/IOS_ARCHITECTURE.md` lascia aperta la scelta tra Compose Multiplatform iOS e SwiftUI + MapKit. L’audit di portabilità mostra inoltre che il repository non ha source set KMP e che alcune classi dichiarate pure importano JVM o adapter Android.

Il porting deve conservare il comportamento scientifico (modello probabilistico, catalogo specie, parser SPUN e raster) senza trascinare nel core lifecycle, permessi, rendering o SDK di un sistema operativo.

## Decisione

La superficie iOS sarà nativa:

* **SwiftUI** per schermate, navigazione, stato di presentazione, dialoghi, accessibilità e design system;
* **MapKit** per mappa, camera, marker, overlay e rendering della superficie di probabilità;
* **CoreLocation** per posizione, autorizzazioni e heading/magnetometro;
* **Foundation** per `URLSession`, `UserDefaults`, date/locale, bundle resources, codifica JSON e integrazione delle primitive di persistenza;
* **Foundation Models**, dietro `PlatformAiEngine`, come adapter opzionale con controllo di disponibilità a runtime e fallback deterministico.

Il solo codice condiviso tramite Kotlin Multiplatform è il core deterministico e testabile: modelli domain privi di framework, algoritmo probabilistico, validazione/normalizzazione, logica di classificazione, parser SPUN portato a input di bytes agnostico e generazione di `HeatmapRaster`. Nessuna API SwiftUI, MapKit, CoreLocation, Foundation, Android, Compose o OsmDroid entra in `commonMain`.

## Confini di responsabilità

| Contratto/domain | `commonMain` KMP | Implementazione iOS |
|---|---|---|
| Probabilità, fattori, specie, tiers | Algoritmi e tipi immutabili | View model SwiftUI osserva risultati |
| SPUN | Parser da bytes + fixture/golden tests | `Bundle`/Foundation legge `spun_italy.bin` |
| `HeatmapRaster` | Pixel ARGB e bounding box | Convertitore a `CGImage` e overlay/renderer MapKit |
| Location/heading | Porte e tipi `UserLocation`/`DeviceHeading` | `CLLocationManager`, permessi e lifecycle scena |
| Cache/preferenze | Policy e contratti, senza DB/API di sistema | `UserDefaults` + SwiftData o SQLite3 di sistema |
| Rete/geocoding/meteo | Use case e DTO serializzabili | `URLSession`/Foundation; Retrofit resta Android |
| Navigazione esterna | `PlatformNavigator` | `MKMapItem`/Apple Maps |
| AI locale | Contratto e fallback deterministico | Foundation Models, se disponibile a runtime |

## Regole di implementazione

1. I tipi scambiati tra Swift e KMP devono essere piccoli, immutabili e privi di oggetti framework; le date arrivano come valori normalizzati (epoch/ISO) o tramite un clock iniettato.
2. Il core non deve esporre `InputStream`, `Bitmap`, `CGImage`, `MapView`, `Context`, `CLLocation` o `StateFlow` legato alla UI. Per gli asset preferire `ByteArray`/reader astratti e adapter di piattaforma.
3. Il rendering della mappa è esclusivamente MapKit. OsmDroid e `platform.android.HeatmapData` non sono dipendenze iOS né contratti condivisi.
4. SwiftUI governa lifecycle e cancellazione delle sottoscrizioni CoreLocation; il core riceve eventi e restituisce stato, senza possedere sensori o permessi.
5. Ogni formula scientifica modificata deve conservare fixture cross-platform e tolleranze numeriche documentate.

## Piano di migrazione incrementale

1. Creare `commonMain` e spostare i tipi **GREEN** e i test deterministici.
2. Estrarre da `MushroomAlgorithms` tempo/locale e da `SpunDataManager` I/O/decompressione JVM; aggiungere fixture binarie.
3. Rendere `HeatmapGenerator` un produttore solo di `HeatmapRaster`, rimuovendo `HeatmapData` Android dal core/ViewModel.
4. Implementare adapter iOS Foundation/CoreLocation/MapKit e un `ObservableObject` SwiftUI.
5. Aggiungere test di parità Android–iOS per probabilità, parser SPUN, bounding box e pixel raster; quindi validare permessi, offline cache e lifecycle su device.

## Alternative considerate

* **Compose Multiplatform iOS:** scartata per la superficie iOS di questo progetto; avrebbe massimizzato il riuso UI ma avrebbe mantenuto un layer UI cross-platform in contrasto con la decisione di usare controlli e lifecycle nativi Apple.
* **Porting completo Swift senza KMP:** scartato perché duplica il codice scientifico e aumenta il rischio di divergenza numerica.
* **OsmDroid su iOS:** scartato; non è un adapter nativo e non soddisfa il contratto MapKit deciso per mappa e overlay.

## Conseguenze

**Positive:** UX/accessibilità e lifecycle coerenti con iOS; responsabilità nette; core scientifico verificabile una sola volta; sostituzione indipendente di rete, cache, sensori e AI.

**Negative:** due superfici UI da mantenere; costo iniziale di estrazione KMP e bridge raster; parità grafica non automatica tra Material 3 e SwiftUI.

## Criteri di accettazione

L’ADR è soddisfatto quando:

* un target KMP compila il core senza import Android/Java/Gson/Compose;
* l’app iOS avvia SwiftUI e mostra MapKit senza OsmDroid;
* posizione e heading passano da CoreLocation con permessi e lifecycle verificati;
* test golden dimostrano parità di probabilità, parsing SPUN e raster entro tolleranza;
* cache offline, disclaimer di sicurezza e fallback AI sono coperti da test iOS;
* le verifiche Android richieste da `AGENTS.md` restano verdi dopo l’estrazione.
