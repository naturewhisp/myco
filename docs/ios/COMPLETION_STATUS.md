# Stato completamento Myco iOS nativo

Aggiornamento: 13 settembre 2026. Branch: `ios-native`.

## Implementazione consegnata

- Core Kotlin Multiplatform con dominio scientifico, 10 specie, fattori, previsioni, facade `MycoAnalysisEngine`, parser SPUN e raster heatmap.
- Client SwiftUI iOS 18+ con ricerca `MKLocalSearch`, GPS one-shot e tracking separati, heading validato, Registry, selettore specie, Forecast, MapKit, indicazioni Apple Maps e palette light/dark/system.
- Pipeline ambientale concorrente Open-Meteo/DEM/Overpass con cancellazione anti-stale, mapping verso il core, risultati parziali dichiarati e fallback su cache SwiftData.
- Asset SPUN condiviso col pacchetto Android, decompressione zlib/deflate Apple e bridge verso `KotlinByteArray`.
- Preferiti e ultimi luoghi in SwiftData; preferenze UserDefaults protette dal clear cache.
- Foundation Models opzionale con availability check e fallback deterministico; il modello non può modificare numeri, specie, tossicità o disclaimer.
- App icon, launch screen generata, stringa permesso posizione e `PrivacyInfo.xcprivacy`.
- GitHub Actions separate per Android, core KMP e iOS; scheme Xcode condiviso.

## Verifica automatizzata

- Test common KMP: formule, engine, specie, parser SPUN, hash raster e budget prestazionale.
- Test Android: golden master esistenti più confronto cross-platform completo della formula e della palette.
- XCTest: rete, DTO/mapper, TTL e fallback cache, preferenze, preferiti/recenti, cancellazione ricerca, fallback Foundation Models e lettura dell'atlante SPUN reale dal bundle.
- Le build Debug/Release e i gate Android sono comandi obbligatori prima del merge; i workflow replicano gli stessi controlli.

## Attività operative esterne al codice

La firma con un Apple Development Team, l'archiviazione `.ipa`, TestFlight/App Store e la prova energetica Instruments su iPhone fisico richiedono credenziali e hardware del titolare. Non sono sostituibili da un test di repository; prima della distribuzione vanno completati insieme agli smoke manuali VoiceOver, Dynamic Type, permesso posizione negato e rete assente.
