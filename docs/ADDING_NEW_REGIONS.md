# Guida Operativa e Architetturale: Aggiunta e Gestione di Nuove Regioni Geografiche

**Progetto:** Myco (`github.naturewhisp.myco`)  
**Data:** 2026-09-10  
**Riferimenti:** `docs/TECHNICAL_DOCUMENTATION.md`, `docs/FUTURE_DEVELOPMENTS_ANALYSIS.md`, `AGENTS.md`

---

## Indice
1. [Panoramica delle Dipendenze Regionali](#1-panoramica-delle-dipendenze-regionali)
2. [Specifiche del Formato Binario SPUN (`.bin`)](#2-specifiche-del-formato-binario-spun-bin)
3. [Pipeline di Generazione con Script Python](#3-pipeline-di-generazione-con-script-python)
4. [Integrazione nel Core Applicativo (`SpunDataManager.kt`)](#4-integrazione-nel-core-applicativo-spundatamanagerkt)
5. [Rete delle Sentinelle e Prossimità Fuori Griglia](#5-rete-delle-sentinelle-e-prossimit-fuori-griglia)
6. [Calibrazione Micologica ed Ecologica Regionale](#6-calibrazione-micologica-ed-ecologica-regionale)
7. [Packaging: Asset Bundling vs On-Demand CDN](#7-packaging-asset-bundling-vs-on-demand-cdn)
8. [Checklist di Validazione e Zero Diagnostic Policy](#8-checklist-di-validazione-e-zero-diagnostic-policy)

---

## 1. Panoramica delle Dipendenze Regionali

L'architettura di **Myco** è concepita per essere modulare e geograficamente scalabile. Quando si introduce una nuova regione (es. *Penisola Iberica*, *Scandinavia*, *Balcani*, *Nord America*), i sottosistemi coinvolti sono i seguenti:

```mermaid
graph TD
    subgraph Sottosistemi Globali (Zero Modifiche)
        Meteo["Open-Meteo Weather API<br>(Copertura Globale WGS84)"]
        OSM["Overpass API / OpenStreetMap<br>(Copertura Globale Habitat/Boschi)"]
        DEM["DEM Altimetrico / Solare<br>(Modello Topografico a 5 Punti)"]
        MapTiles["OsmDroid Mapnik & OpenTopo<br>(Mattonelle Cartografiche Mondiali)"]
    end

    subgraph Componenti Specifici della Regione (Da Aggiornare)
        SPUN_BIN["1. Asset Binario SPUN (.bin)<br>Matrici EcM & Ife compresse zlib"]
        REG_DESC["2. SpunRegionDescriptor<br>Bounding Box & Asset FileName"]
        SENTINELS["3. Stazioni Sentinella<br>Landmark per calcolo prossimità Haversine"]
        ECOL_CALIB["4. Calibrazione Ecologica<br>Quote altimetriche & finestre fenologiche"]
    end

    SPUN_BIN --> SpunDataManager
    REG_DESC --> SpunDataManager
    SENTINELS --> SpunDataManager
    ECOL_CALIB --> MushroomAlgorithms
```

Come mostrato nello schema, le componenti di rete (Open-Meteo, Overpass OSM, DEM topografico e piastrelle cartografiche OsmDroid) operano già su scala planetaria. L'onere di implementazione di una nuova regione è circoscritto alla **pipeline dei dati micorrizici SPUN** e alla **configurazione ecologica e delle sentinelle**.

---

## 2. Specifiche del Formato Binario SPUN (`.bin`)

I dati del consorzio SPUN (*Society for the Protection of Underground Networks*) sono distribuiti originariamente come grandi raster GeoTIFF globali. Per garantire caricamenti istantanei in memoria RAM mobile (<15 ms) e limitare il consumo a pochi megabyte, Myco adotta un formato binario proprietario compresso.

### 2.1 Struttura dell'Header (Rigorosamente 32 Byte, Big-Endian)

L'header deve avere una lunghezza esatta di **32 byte** per essere letto correttamente da `ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN)`.

| Offset (Byte) | Dimensione | Tipo | Nome Campo | Descrizione ed Esempio |
|---|---|---|---|---|
| `0..3` | 4 Byte | ASCII | `magic` | Identificatore fisso: deve essere esattamente `b"SPUN"`. |
| `4..5` | 2 Byte | uint16 (BE) | `version` | Versione del formato (attualmente `1`). |
| `6..9` | 4 Byte | ASCII | `regionCode` | Codice univoco di 3-4 caratteri null-padded (es. `b"ALP\0"`, `b"IBE\0"`, `b"SCA\0"`). |
| `10..13` | 4 Byte | float32 (BE) | `minLat` | Latitudine minima (Sud) del bounding box in gradi decimali WGS84. |
| `14..17` | 4 Byte | float32 (BE) | `maxLat` | Latitudine massima (Nord) del bounding box in gradi decimali WGS84. |
| `18..21` | 4 Byte | float32 (BE) | `minLon` | Longitudine minima (Ovest) del bounding box in gradi decimali WGS84. |
| `22..25` | 4 Byte | float32 (BE) | `maxLon` | Longitudine massima (Est) del bounding box in gradi decimali WGS84. |
| `26..27` | 2 Byte | uint16 (BE) | `width` | Numero di colonne longitudinali della griglia (colonne = round((maxLon - minLon) / step)). |
| `28..29` | 2 Byte | uint16 (BE) | `height` | Numero di righe latitudinali della griglia (righe = round((maxLat - minLat) / step)). |
| `30..31` | 2 Byte | uint16 (BE) | `stepArcSec` | Risoluzione spaziale in secondi d'arco (standard: `30`, pari a 1/120° ≈ 0.92 km). |

> [!WARNING]
> **Invariante Critico sull'Allineamento dell'Header:**  
> In Python `struct.pack`, la stringa di formato corretta è **`">4sH4sffffHHH"`** (totale esatto: 32 byte). L'omissione del campo `regionCode` a 4 byte o l'uso di tipi di dimensione errata sposta i byte successivi, rendendo corrotti i valori `float32` delle coordinate. In Kotlin, `SpunDataManager` scarterebbe il file senza caricare i dati.

### 2.2 Struttura del Payload Decompresso (zlib DEFLATE, Level 9)

Il payload decompressa ha una dimensione fissa calcolata come:
DimensionePayload = width * height * 2 byte

È composto da due matrici sequenziali contigue:
1. **Matrice EcM Richness (`width * height` byte):**
   * Tipo: `uint8` (0 .. 255).
   * Rappresenta il numero atteso di specie fungine ectomicorriziche predette per cella.
   * I valori negativi o `NaN` dei raster originari devono essere convertiti a `0.0`.
2. **Matrice Hyphal Density (`width * height` byte):**
   * Tipo: `uint8` (0 .. 255).
   * Rappresenta la densità di ife sotterranee espressa in m/cm³ di suolo.
   * **Fattore di quantizzazione:** Il valore reale in m/cm³ viene moltiplicato per **`20.0`** e convertito in `uint8`. In questo modo la risoluzione è di 0.05 m/cm³ e il range coperto va da 0.0 a 12.75 m/cm³.

---

## 3. Pipeline di Generazione con Script Python

Per generare il file `.bin` per una nuova regione, utilizzare lo script parametrico descritto di seguito.

### 3.1 Prerequisito Esterno Obbligatorio: Dataset GeoTIFF Globali SPUN

> [!IMPORTANT]
> **I file GeoTIFF planetari NON sono presenti nel repository Git.**  
> A causa della risoluzione ad altissima densità, i due raster globali originari occupano complessivamente oltre **15–20 GB**, superando ampiamente i limiti di dimensione di Git e GitHub. Il repository traccia esclusivamente i pacchetti binari territoriali compressi generati (`.bin` di ~1.5–2 MB) situati in `app/src/main/assets/spun/`.  
> Il possesso di questi file sorgente è pertanto un **prerequisito esterno indispensabile** per chiunque debba compilare nuove regioni.

I due file GeoTIFF necessari sono:
1. **`EcM_Fungi_Richness_Predicted.tif`**:
   * *Contenuto:* Modello predittivo globale della ricchezza di specie fungine ectomicorriziche (EcM).
   * *Sorgente:* SPUN (*Society for the Protection of Underground Networks*) & Crowther Lab Global Soil Mycobiome initiative.
2. **`hyphal_density_m_cm3_Classified_mean.tif`**:
   * *Contenuto:* Stima globale della densità di ife miceliali per volume di suolo ($\text{m/cm}^3$).
   * *Sorgente:* Nature / SPUN Consortium Open Science Data Portal.

#### Gestione e Posizionamento dei File Sorgente
* **Non collocarli all'interno del repository:** Conservarli in una directory esterna dedicata sulla propria workstation (es. `~/Documents/Spun/` o disco esterno/volume dati GIS).
* Il file `.gitignore` del progetto include già `*.tif` e `*.tiff` per prevenire commit accidentali.
* I percorsi locali ai due file vengono forniti come argomenti CLI `--ecm-tif` e `--hyp-tif` allo script di compilazione.

### 3.2 Script CLI Riutilizzabile (`tools/generate_spun_region.py`)

```python
#!/usr/bin/env python3
"""
Pipeline per la generazione di file binari regionali SPUN compressi (.bin) per Myco.
Utilizzo:
  python generate_spun_region.py --code IBE --name "Iberia" \
    --min-lat 35.5 --max-lat 44.5 --min-lon -9.5 --max-lon 3.5 \
    --ecm-tif /path/to/EcM.tif --hyp-tif /path/to/Hyphal.tif \
    --output app/src/main/assets/spun/spun_iberia.bin
"""

import argparse
import struct
import zlib
import numpy as np
from PIL import Image

Image.MAX_IMAGE_PIXELS = None

def generate_region_bin(code, min_lat, max_lat, min_lon, max_lon, ecm_path, hyp_path, output_path):
    STEP = 1.0 / 120.0  # 30 arc-seconds (~0.008333 gradi WGS84)
    width = int(round((max_lon - min_lon) / STEP))
    height = int(round((max_lat - min_lat) / STEP))

    print(f"[*] Elaborazione Regione [{code}]: {width}x{height} ({width * height:,} celle)")

    # 1. Ritaglio e Ricampionamento EcM Richness
    print("[1/3] Estrazione matrice EcM...")
    im_ecm = Image.open(ecm_path)
    scale_x, scale_y = im_ecm.tag_v2[33550][0], im_ecm.tag_v2[33550][1]
    tie_x, tie_y = im_ecm.tag_v2[33922][3], im_ecm.tag_v2[33922][4]

    x0 = int((min_lon - tie_x) / scale_x)
    x1 = int((max_lon - tie_x) / scale_x)
    y0 = int((tie_y - max_lat) / scale_y)
    y1 = int((tie_y - min_lat) / scale_y)

    crop_ecm = im_ecm.crop((x0, y0, x1, y1)).resize((width, height), Image.Resampling.BILINEAR)
    arr_ecm = np.array(crop_ecm)
    arr_ecm = np.where(np.isnan(arr_ecm) | (arr_ecm < 0), 0.0, arr_ecm)
    u8_ecm = np.clip(np.round(arr_ecm), 0, 255).astype(np.uint8)

    # 2. Ritaglio e Ricampionamento Hyphal Density
    print("[2/3] Estrazione matrice Hyphal Density...")
    im_hyp = Image.open(hyp_path)
    scale_x2, scale_y2 = im_hyp.tag_v2[33550][0], im_hyp.tag_v2[33550][1]
    tie_x2, tie_y2 = im_hyp.tag_v2[33922][3], im_hyp.tag_v2[33922][4]

    x0_2 = int((min_lon - tie_x2) / scale_x2)
    x1_2 = int((max_lon - tie_x2) / scale_x2)
    y0_2 = int((tie_y2 - max_lat) / scale_y2)
    y1_2 = int((tie_y2 - min_lat) / scale_y2)

    crop_hyp = im_hyp.crop((x0_2, y0_2, x1_2, y0_2 + height))
    arr_hyp = np.array(crop_hyp)
    arr_hyp = np.where(np.isnan(arr_hyp) | (arr_hyp < 0), 0.0, arr_hyp)
    # Quantizzazione: moltiplicazione per 20.0 (risoluzione 0.05 m/cm3)
    u8_hyp = np.clip(np.round(arr_hyp * 20.0), 0, 255).astype(np.uint8)

    # 3. Assemblaggio Header (32 Byte) e Compressione Payload
    print("[3/3] Compressione e serializzazione...")
    code_bytes = (code[:3] + "\x00").encode("ascii") if len(code) < 4 else code[:4].encode("ascii")

    header = struct.pack(
        ">4sH4sffffHHH",
        b"SPUN",
        1,
        code_bytes,
        min_lat, max_lat, min_lon, max_lon,
        width, height,
        30
    )
    assert len(header) == 32, f"Errore: dimensione header {len(header)} != 32 byte"

    payload = u8_ecm.tobytes() + u8_hyp.tobytes()
    compressed_payload = zlib.compress(payload, level=9)

    with open(output_path, "wb") as f:
        f.write(header)
        f.write(compressed_payload)

    total_bytes = len(header) + len(compressed_payload)
    print(f"[+] Completato! File salvato in: {output_path}")
    print(f"    Dimensione: {total_bytes:,} byte ({total_bytes / 1024 / 1024:.2f} MB)")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generatore di Asset SPUN Regionali per Myco")
    parser.add_argument("--code", required=True, help="Codice regione di 3-4 caratteri (es. IBE, SCA, BAL)")
    parser.add_argument("--min-lat", type=float, required=True, help="Latitudine Minima (Sud)")
    parser.add_argument("--max-lat", type=float, required=True, help="Latitudine Massima (Nord)")
    parser.add_argument("--min-lon", type=float, required=True, help="Longitudine Minima (Ovest)")
    parser.add_argument("--max-lon", type=float, required=True, help="Longitudine Massima (Est)")
    parser.add_argument("--ecm-tif", required=True, help="Percorso del GeoTIFF EcM Richness")
    parser.add_argument("--hyp-tif", required=True, help="Percorso del GeoTIFF Hyphal Density")
    parser.add_argument("--output", required=True, help="File .bin di destinazione")
    args = parser.parse_args()

    generate_region_bin(
        args.code, args.min_lat, args.max_lat, args.min_lon, args.max_lon,
        args.ecm_tif, args.hyp_tif, args.output
    )
```

---

## 4. Integrazione nel Core Applicativo (`SpunDataManager.kt`)

Una volta copiato il nuovo file binario nella cartella `app/src/main/assets/spun/` (es. `spun_iberia.bin`), l'attivazione nel codice richiede due passaggi essenziali in `github.naturewhisp.myco.repository.SpunDataManager.kt`.

### 4.1 Registrazione in `availableRegions`
Aggiungere la nuova regione nella lista immutabile `availableRegions`:

```kotlin
val availableRegions = listOf(
    SpunRegionDescriptor(
        regionCode = "ALP",
        displayName = "Italia e Arco Alpino",
        assetFileName = "spun/spun_italy.bin",
        minLat = 35.0f,
        maxLat = 48.5f,
        minLon = 5.0f,
        maxLon = 19.0f
    ),
    SpunRegionDescriptor(
        regionCode = "IBE",
        displayName = "Penisola Iberica",
        assetFileName = "spun/spun_iberia.bin",
        minLat = 35.5f,
        maxLat = 44.5f,
        minLon = -9.5f,
        maxLon = 3.5f
    )
)
```

### 4.2 Switch Dinamico Trasparente
Il metodo `ensureRegionLoadedFor(lat, lon)` di `SpunDataManager` gestisce già il caricamento concorrente e thread-safe:
1. `findRegionFor(lat, lon)` cerca nella lista la regione contenente le coordinate (`contains(lat, lon)`).
2. Se la regione coincide con quella già in memoria RAM (`currentLoadedRegion`), ritorna istantaneamente.
3. Se l'utente si è spostato in un'altra regione, acquisisce il `Mutex`, rilascia la vecchia matrice e decomprime il nuovo asset binario in meno di 20 millisecondi.

---

## 5. Rete delle Sentinelle e Prossimità Fuori Griglia

Quando l'utente tocca un'area non coperta da alcuna griglia SPUN o in acque aperte, Myco calcola la distanza ortodromica Haversine verso il punto di interesse micologico monitorato più vicino mediante `findClosestCoveragePoint()`.

### 5.1 Aggiunta delle Sentinelle della Nuova Regione
In `SpunDataManager.kt`, estendere la lista `sentinelCoveragePoints` aggiungendo i massicci montuosi e le foreste principali della nuova area:

```kotlin
private val sentinelCoveragePoints = listOf(
    // Sentinelle Alpine & Italiane esistenti...
    ClosestCoverageResult("Val Veny / Courmayeur (AO)", 45.7969, 6.9697, 0),
    ClosestCoverageResult("Passo del Brennero (BZ)", 47.0061, 11.5058, 0),

    // Nuove Sentinelle per la Penisola Iberica (Esempio)
    ClosestCoverageResult("Picos de Europa (Asturie)", 43.1872, -4.8322, 0),
    ClosestCoverageResult("Parco Nazionale di Ordesa (Pirenei)", 42.6736, 0.0544, 0),
    ClosestCoverageResult("Sierra de Gredos (Castiglia)", 40.2500, -5.2500, 0),
    ClosestCoverageResult("Sierra Nevada (Andalusia)", 37.0536, -3.3114, 0)
)
```

---

## 6. Calibrazione Micologica ed Ecologica Regionale

Le condizioni per la fruttificazione dei funghi variano tra macro-regioni climatiche. Quando si supporta una nuova area geografica, verificare in `github.naturewhisp.myco.utils.MushroomAlgorithms.kt`:

### 6.1 Curve Altimetriche
* **Regione Mediterranea / Penisola Iberica:** La fascia ottimale dei porcini (*Boletus edulis* e *Boletus aereus*) sale di quota (1000 .. 1800 m), a causa delle elevate temperature estive nel fondovalle.
* **Regione Boreale / Scandinavia:** La fascia ottimale scende a quote collinari o costiere (50 .. 600 m).

### 6.2 Finestre Fenologiche Stagionali
In `MushroomAlgorithms.kt`, la funzione `calculateSeasonalScore(month, species)` modella il ciclo annuale:
* Nel Sud Europa il periodo favorevole si protrae fino a novembre o inizio dicembre.
* Nel Nord Europa o sulle Alpi continentali la stagione culmina a fine estate (agosto-settembre) e si interrompe precocemente con le prime gelate persistenti.

### 6.3 Alberi Simbionti in OpenStreetMap
Le query Overpass in `MushroomRepository.kt` estraggono boschi `natural=wood` e `landuse=forest`. Assicurarsi che le specie forestali tipiche della nuova regione siano supportate nel matching botanico:
* **Boschi Nordici:** *Pinus sylvestris*, *Picea abies*, *Betula pendula*.
* **Boschi Mediterranei/Iberici:** *Quercus suber* (sughera), *Quercus ilex* (leccio), *Castanea sativa*.

---

## 7. Architettura di Packaging: Tassellatura All-in-APK con Caricamento On-Demand in RAM

La strategia architetturale adottata da Myco per la copertura globale consiste nell'**inclusione di tutti i tasselli regionali direttamente negli asset dell'APK** (`app/src/main/assets/spun/`), delegando a `SpunDataManager` il **caricamento lazy on-demand in memoria RAM** esclusivamente per la regione in cui l'utente sta navigando.

### 7.1 Budget Dimensionale e Limiti Google Play
* **Limite Google Play (Android App Bundle - AAB):** Il limite per il modulo base è di **150 MB**.
* **Volume Totale Compresso dei Tasselli Globali:**
  L'insieme delle macro-regioni di reale interesse micologico mondiale (foreste temperate, montane e boreali) richiede circa **~65 MB** di file binari compressi zlib (livello 9).
* **Dimensione Finale dell'APK/AAB:**
  Sommando il codice dell'applicazione, le librerie AndroidX/Compose e le risorse grafiche (~30 MB), l'APK complessivo si attesta attorno a **~95 MB**, rimanendo **ampiamente al di sotto del limite di 150 MB** senza necessitare di download secondari o pacchetti di espansione.

### 7.2 Zero Spreco di RAM (Lazy Swapping Dinamico)
Nonostante la presenza di decine di file `.bin` nel pacchetto installato, **l'impatto sulla memoria RAM del dispositivo rimane costante a ~4–6 MB di heap**:
1. **Conservazione su Disco Flash:** I file in `assets/` non consumano RAM finché non vengono aperti da un `InputStream`.
2. **Singola Regione Attiva:** In qualsiasi momento, `SpunDataManager` mantiene decompressa in RAM un'unica istanza `currentLoadedRegion`.
3. **Swapping Concorrente Trasparente:** Quando l'utente seleziona una coordinata appartenente a un altro tassello geografico, il `Mutex` interno rilascia la vecchia matrice (immediatamente bonificata dal Garbage Collector) e decomprime il nuovo tassello in **meno di 15 millisecondi**.

### 7.3 I Vantaggi per il Foraggiatore da Campo
* **100% Offline Garantito in Tutto il Mondo:** Il raccoglitore può viaggiare all'estero (es. escursione nei Pirenei o nei boschi scandinavi) e consultare la biodiversità micorrizica e la nuvola termica anche in assenza totale di segnale cellulare, senza roaming e senza doversi ricordare di pre-scaricare mappe prima della partenza.
* **Zero Costi di Infrastruttura Cloud:** Nessun server backend, nessun bucket S3 o Google Cloud Storage da mantenere, e zero costi ricorrenti di banda in uscita (egress traffic).
* **Resilienza Totale:** Azzeramento dei punti di fallimento legati a timeout HTTP, download interrotti o errori di checksum su rete mobile instabile.

### 7.4 Catalogo Consigliato dei Tasselli Macro-Regionali

```
app/src/main/assets/spun/
├── spun_alp.bin           # Italia, Arco Alpino (Fr/Ch/At/Si), Appennini (~1.88 MB)
├── spun_iberia.bin        # Spagna, Portogallo, Pirenei (~1.65 MB)
├── spun_central_eu.bin    # Francia centro-nord, Germania, Austria, Rep. Ceca (~2.00 MB)
├── spun_scandinavia.bin   # Norvegia, Svezia, Finlandia (~2.20 MB)
├── spun_east_eu.bin       # Polonia, Slovacchia, Carpazi, Balcani (~2.10 MB)
├── spun_british_isles.bin # Regno Unito, Irlanda (~1.20 MB)
├── spun_us_west.bin       # Pacific Northwest, California, Montagne Rocciose (~3.50 MB)
├── spun_us_east.bin       # Appalachi, New England, Grandi Laghi (~3.20 MB)
├── spun_canada.bin        # Fascia boreale e montana canadese (~3.80 MB)
├── spun_asia_east.bin     # Giappone, Corea, Cina montana (~4.50 MB)
├── spun_south_america.bin # Ande, Cile, Patagonia (~2.50 MB)
└── spun_oceania.bin       # Australia sud-est, Tasmania, Nuova Zelanda (~2.20 MB)
```

---

## 8. Checklist di Validazione e Zero Diagnostic Policy

Prima di committare una nuova regione, completare la seguente checklist:

- [ ] **Verifica Lunghezza Header (32 Byte):**
  ```python
  with open("app/src/main/assets/spun/nuova_regione.bin", "rb") as f:
      magic = f.read(4)
      assert magic == b"SPUN", f"Magic errato: {magic}"
      f.seek(0)
      header = f.read(32)
      assert len(header) == 32, f"Lunghezza header errata: {len(header)} != 32 byte"
  ```
- [ ] **Zero Diagnostic Policy:**
  1. `.\gradlew.bat compileDebugKotlin` -> **BUILD SUCCESSFUL**
  2. `.\gradlew.bat lintDebug` -> **0 errors, 0 warnings**
  3. `.\gradlew.bat testDebugUnitTest` -> **BUILD SUCCESSFUL**
  4. `.\gradlew.bat assembleDebug` -> **BUILD SUCCESSFUL**
- [ ] **Verifica Funzionale su Dispositivo:**
  * Cercare una località nella nuova regione (es. *Madrid* o *Picos de Europa*).
  * Verificare che la nuvola di probabilità e le schede EcM / Rete Ifale mostrino valori coerenti.
  * Verificare il funzionamento del pulsante `[ 🧭 Naviga ]` verso il punto selezionato.
