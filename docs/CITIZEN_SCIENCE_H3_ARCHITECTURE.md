# Architettura Modulo Citizen Science & Uber H3

**Progetto:** Myco (`github.naturewhisp.myco`)  
**Componente:** Sottosistema Citizen Science, Spatially-Discretized Crowdsourcing & Local Phenology Calibration  
**Stato:** Blueprint Architetturale per Sviluppo Futuro (Roadmap Post-v1.2 / Milestone v1.3)  
**Data:** Settembre 2026  

---

## 1. Visione e Requisiti Fondamentali

### 1.1 Il "Dilemma del Fungaiolo" e la Privacy Differenziale
Nel dominio della raccolta dei macromiceti epigei (in particolare *Boletus edulis*, *Amanita caesarea*, *Cantharellus cibarius*), la riservatezza delle stazioni di crescita note come *"fungaie"* rappresenta un vincolo umano e culturale non negoziabile: nessun cercatore esperto condividerà mai coordinate geografiche precise $(\text{lat}, \text{lon})$ dei propri ritrovamenti se esiste il minimo rischio che altri possano individuarne l'esatta collocazione geodetica.

Al contempo, il modello agrometeorologico e fenologico di Myco soffre di una fisiologica incertezza dovuta alla risoluzione delle stazioni meteo e alla variabilità microclimatica della lettiera forestale. Il dato di osservazione sul campo (*ground truth*) è lo strumento più potente per colmare il divario tra stima teorica e realtà biologica.

Per conciliare questi due requisiti antitetici, Myco adotta il paradigma della **Privacy Differenziale Spaziale** implementata tramite il sistema di indicizzazione geospaziale discreto **Uber H3 (Discrete Global Grid System - DGGS)**.

---

## 2. Specifiche del Modello Spaziale Uber H3

### 2.1 Perché la Griglia Esagonale H3
A differenza delle griglie cartesiane ortogonali basate su latitudine/longitudine o delle celle quadrate (es. Slippy Map tiles / Geohash):
1. **Equidistanza dei Vicini:** Ogni cella esagonale possiede esattamente 6 celle contigue i cui baricentri distano la medesima lunghezza $d$. Non esiste l'asimmetria delle diagonali tipica delle griglie quadrate ($1$ vs $\sqrt{2}$).
2. **Adiacenza Uniforme:** Nessun punto di singolarità o spigolo condiviso, minimizzando le distorsioni nella modellazione di dispersione delle spore e propagazione dei fronti micotici.
3. **Invarianza di Scala:** Decomposizione gerarchica su 16 risoluzioni (da scala continentale a millimetrica).

```
          / \     / \
        /     \ /     \
       |   H3  |   H3  |
       |  k-1  |  k+1  |
        \     / \     /
          \ /     \ /
           |  H3   |
           |Centro |
          / \     / \
        /     \ /     \
       |   H3  |   H3  |
        \     / \     /
          \ /     \ /
```

### 2.2 Selezione della Risoluzione 7 (Gold Standard)
Per la discretizzazione delle osservazioni micologiche, Myco seleziona tassativamente la **Risoluzione H3: 7**:
* **Area Media della Cella:** $\approx 5{,}16\text{ km}^2$.
* **Lunghezza del Lato Esagonale:** $\approx 1{,}22\text{ km}$.
* **Diametro di Copertura / Raggio di Anonimizzazione:** $\approx 2{,}5 \dots 5{,}0\text{ km}$.
* **Significato Ecologico:** Su scala orografica montano-collinare, un'area di $5\text{ km}^2$ coincide tipicamente con un intero versante montano o una porzione omogenea di valle (es. la dorsale Monte Mindino - Val Casotto). Rende assolutamente **impossibile individuare il singolo canalone, albero o fungaia specifica**, ma è perfettamente omogenea dal punto di vista dell'evento piovoso e della finestra termica.

### 2.3 Distruzione Istantanea delle Coordinate di Bordo
L'applicazione rispetta un'invariante di riservatezza a livello di runtime:
1. Quando l'utente preme *"Segnala Ritrovamento"* sul campo, il sensore GPS registra la coordinata WGS84 volatile $(lat_{raw}, lon_{raw})$.
2. Il modulo locale calcola immediatamente l'indice H3 univoco a 64-bit:
   $$\text{h3Index} = \text{H3Core.geoToH3Address}(lat_{raw}, lon_{raw}, 7)$$
3. Le coordinate puntuali $lat_{raw}$ e $lon_{raw}$ vengono **azzerate e distrutte immediatamente dalla memoria RAM**.
4. Nessun file di log, cache SQLite, SharedPreferences o pacchetto di rete conterrà mai le coordinate GPS del ritrovamento.

---

## 3. Pipeline di Validazione On-Device (Google AI Edge Gemini Nano)

Per evitare segnalazioni fraudolente (*poisoning attacks*), errori di identificazione da parte di cercatori inesperti o spam, il sistema impiega l'intelligenza artificiale a bordo del dispositivo prima di autorizzare la sottomissione.

```mermaid
flowchart TD
    A[Scatto Fotografico Facoltativo] --> B[Allocazione Bitmap Locale in RAM]
    B --> C[Gemini Nano / Vision AICore on-device]
    C --> D{Confidence Score >= 0.85 & Specie Valida?}
    D -->|NO / Look-alike tossico| E[Rifiuto Segnalazione & Alert Sicurezza Utente]
    D -->|SI| F[Estrazione Stadio Fenologico: Bottone / Adulto / Senescente]
    F --> G[Cancellazione Definitiva Immagine dalla Memoria]
    G --> H[Generazione Payload H3 Anonimo]
```

### 3.1 Isolamento e Zero-Cloud per le Immagini
* L'immagine del carpoforo scattata dalla fotocamera viene elaborata **esclusivamente nella RAM locale dello smartphone** tramite le API di Google AI Edge (`PlatformAiEngine`).
* Nessuna fotografia viene mai inviata al server cloud o salvata su server esterni. L'immagine serve unicamente come oracolo di validazione immediata sul terminale.
* Il modello esegue la classificazione verificando:
  1. Presenza effettiva di un basidiocarpo/ascoma fungino reale (anti-spam).
  2. Compatibilità visiva con la specie dichiarata (es. *Boletus edulis* sensu lato vs *Tylopilus felleus* o *Amanita phalloides*).
  3. Classificazione dello stadio fenologico:
     * `FRESH_BUTTON` (primordio o carpoforo giovane a cappello chiuso, $d \le 4\text{ cm}$);
     * `MATURE_EXPANDED` (esemplare adulto a imenio maturo);
     * `OVERRIPE_SENESCENT` (esemplare spugnoso o in decomposizione).

---

## 4. Contratto Dati e Schema di Sottomissione

Il payload inviato all'ingester cloud è compatto, quantizzato temporalmente e privo di qualsiasi identificativo hardware o di tracciamento.

### 4.1 Modello DTO (`MushroomSightingSubmission.kt`)
```json
{
  "h3_index": "871f14488ffffff",
  "species_id": "boletus_edulis",
  "phenological_stage": "FRESH_BUTTON",
  "elevation_bracket": "800_1000M",
  "canopy_category": "DECIDUOUS_BEECH",
  "timestamp_epoch_day": 20710,
  "verification_tier": "ON_DEVICE_AI_CONFIRMED",
  "entropy_nonce": "e3b0c44298fc1c14"
}
```

### 4.2 Invarianti di Sicurezza del Protocollo
* **Quantizzazione Temporale al Giorno (`timestamp_epoch_day`):** L'orario esatto (ore, minuti, secondi) viene troncato all'inizio del giorno UTC (Epoch Day). Questo impedisce l'analisi di mobilità e l'incrocio con orari di sosta dell'autoveicolo.
* **Zero Identificativi:** Nessun `device_id`, `advertising_id`, `android_id` o cookie di sessione.
* **Routing Tramite Oblivious HTTP (OHTTP) / Privacy Proxy:** L'indirizzo IP del dispositivo client viene schermato a monte tramite relay OHTTP per impedire la correlazione geografica a livello di rete.

---

## 5. Modello Matematico di Integrazione Bayesiana

L'aggregatore riceve le segnalazioni per ciascuna cella $H3_i$ e aggiorna la distribuzione di probabilità fenologica combinando il modello deterministico meteorologico con l'evidenza empirica di campo.

### 5.1 Aggiornamento della Probabilità al Giorno $t$
Sia $P_{\text{meteo}}(t) \in [0, 1]$ la probabilità a priori calcolata dall'algoritmo agrometeorologico unificato di Myco:
$$P = 100 \times \left(\frac{W}{100}\right)^{1.2} \times H \times A \times S \times T$$

In presenza di un insieme di segnalazioni validate $K = \{s_1, s_2, \dots, s_n\}$ pervenute nella cella $H3_i$ negli ultimi 3 giorni, la verosimiglianza $L(K \mid \text{Buttata})$ viene formulata come:
$$L(K \mid \text{Buttata}) = 1.0 - \prod_{j=1}^n \left(1.0 - w(s_j)\right)$$
dove $w(s_j)$ è il peso della singola segnalazione:
* $w = 0{,}85$ per segnalazioni con foto validata on-device da Gemini Nano;
* $w = 0{,}40$ per segnalazioni manuali dell'utente senza immagine;
* Moltiplicatore stadio: $\times 1{,}20$ se `FRESH_BUTTON` (prova certa di buttata nascente); $\times 0{,}70$ se `OVERRIPE_SENESCENT` (buttata ormai al termine).

La probabilità a posteriori $P_{\text{posterior}}(t)$ diventa:
$$P_{\text{posterior}}(t) = 1.0 - (1.0 - P_{\text{meteo}}(t)) \times (1.0 - L(K \mid \text{Buttata}))$$

---

## 6. Ricalibrazione Retroattiva dell'Inerzia Biologica ($\tau_{peak}$ Auto-Tuning)

L'innovazione più rilevante del modulo H3 è la **retro-propagazione temporale** dell'osservazione sul campo per calibrare l'inerzia biologica reale del versante montano.

```mermaid
sequenceDiagram
    participant Utente as Raccoglitore sul Campo
    participant App as Myco Client (H3 Cell 871f14488ffffff)
    participant Engine as Algoritmo Fenologico Inverso
    participant Cache as Modello Locale Versante

    Utente->>App: Segnala Porcino Giovane ("FRESH_BUTTON") il 13 Settembre
    App->>Engine: Richiesta calibrazione inversa per H3_i
    Engine->>Engine: Scansione a ritroso storico piogge (t - 28 gg)
    Engine->>Engine: Identificazione pioggia innesco: 10 Settembre (tau_obs = 3 gg)
    Note over Engine: Rilevata anomalia: tau_obs (3 gg) << tau_standard (11 gg)
    Engine->>Engine: Ricerca pioggia precedente consistente: 31 Agosto (tau_obs = 13 gg)
    Engine->>Cache: Calibra tau_peak effettivo = 12.5 gg per la valle
    Cache-->>App: Aggiorna prospetto fenologico con inerzia corretta
```

### 6.1 Algoritmo di Calibrazione Inversa
Quando viene registrato un esemplare giovane (`FRESH_BUTTON`) al giorno $T_{\text{obs}}$:
1. L'algoritmo interroga la serie storica delle precipitazioni efficaci $P_{\text{eff}}(t)$ della cella H3 per i 28 giorni precedenti: $t \in [T_{\text{obs}} - 28, T_{\text{obs}}]$.
2. Calcola la funzione di correlazione crociata per identificare l'evento idrico che massimizza la probabilità di innesco primordiale.
3. Se l'evento scatenante identificato è caduto al giorno $T_{\text{rain}}$, la latenza empirica misurata vale:
   $$\tau_{\text{empirico}} = T_{\text{obs}} - T_{\text{rain}}$$
4. Il valore di $\tau_{peak}$ per la specie in quella determinata cella H3 e nel suo anello geospaziale adiacente (H3 k-ring 1, raggio $\sim 10\text{ km}$) viene aggiornato mediante media mobile esponenziale pesata:
   $$\tau_{peak}^{(\text{nuovo})} = (1 - \lambda) \cdot \tau_{peak}^{(\text{catalogo})} + \lambda \cdot \tau_{\text{empirico}}$$
   con fattore di apprendimento $\lambda = 0{,}25$.

In questo modo, se in una specifica vallata o per una specifica annata la siccità estiva ha allungato l'inerzia a 14 giorni, o se un temporale caldo l'ha accorciata a 8 giorni, l'algoritmo di Myco si sintonizza sulla velocità reale del micelio sotterraneo di quel territorio.

---

## 7. Tabella di Marcia per l'Implementazione (Milestone v1.3)

| Fase | Pacchetto / Modulo | Attività Pianificata |
| :--- | :--- | :--- |
| **Fase A** | `platform/geo/` | Integrazione libreria H3-Core (wrapper Kotlin multiplatform a zero allocazione). |
| **Fase B** | `ui/components/` | Sviluppo del dialog `FieldSightingDialog.kt` con selettore stadio fenologico e badge privacy *"Cella H3 anonima raggio 5 km"*. |
| **Fase C** | `network/ai/` | Prompt specializzato Gemini Nano per validazione micologica locale e stima dello stadio fenologico da fotocamera. |
| **Fase D** | `repository/` | Creazione di `CitizenScienceRepository.kt` con supporto per accodamento offline e sincronizzazione asincrona. |
| **Fase E** | `utils/` | Implementazione del filtro bayesiano e del ricalibratore di $\tau_{peak}$ in `MushroomAlgorithms.kt`. |
