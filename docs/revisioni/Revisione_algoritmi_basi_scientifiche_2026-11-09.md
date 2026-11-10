# Myco — Revisione degli algoritmi e delle basi scientifiche

**Data:** 9 novembre 2026 · **Commit esaminato:** `747fd44` (master, working tree pulito)
**Natura:** lettura interna al progetto eseguita da una sessione AI (Claude, con tre sotto-agenti usati solo per scaricare pagine web). Non è una revisione esterna né una peer review.
**Oggetto:** motore `:core` (`MycoAlgorithms`, `MycoAnalysisEngine`, `HabitatGeometry`, `HeatmapEngine`, `ParameterRegistry`, `SpeciesCatalog`) e i suoi ingressi Android. Nessun file sorgente è stato modificato.

**Aggiornamento 10 novembre 2026:** i rilievi **A1**, **A4** (sezione 7), **A2** e **B1** (sezioni 8–9) sono stati corretti dopo questa revisione; **A3** è mitigato con la normalizzazione per cella (sezione 10); i due limiti dichiarati in sezione 7 (soglie del gelo uguali per tutte le specie, testo di fase con piogge deboli) sono chiusi in sezione 11. Le sezioni 1–6 descrivono il commit `747fd44` e restano invariate.

## 1. Esito

Il motore è numericamente solido (nessun NaN, calendario per date reali, raccordi Hermite corretti dove dichiarati, qualità dei dati esplicita) e l'inquadramento «indice euristico, non probabilità» è onesto. I problemi che restano non sono di implementazione ma di **modello**: sei comportamenti alterano il punteggio in condizioni del tutto ordinarie, e tre dei quattro «gate avversari» dichiarati in `AGENTS.md` non sono rispettati o non sono esercitati da alcun test.

I sei più rilevanti, in ordine di effetto misurato:

| ID | Problema | Effetto misurato sul motore reale |
|---|---|---|
| A1 | Una pioggia in più **abbassa** l'indice | 40 mm a 11 gg: S = 69,7. Aggiungendo 40 mm oggi: S = 47,0 |
| A2 | Minime notturne confrontate con soglie di temperatura **media** | Media 14 °C, minima 8 °C: fattore notturno al pavimento 0,30; S = 58,5 contro 81,2 con minima 13 °C |
| A3 | Soglie assolute di umidità del suolo, dipendenti dalla tessitura della cella | θ da 0,14 a 0,22 porta S da 10 a 70. Abetone, 250 mm in 31 giorni: 17 giorni su 27 con penalità di siccità |
| A4 | Il gelo dopo l'innesco non abbatte l'indice e, a 7–9 giorni, lo **alza** | 3 notti a −2 °C a 7–9 gg: S = 73,1 contro 56,4 senza gelo |
| A5 | L'ipossia da suolo saturo vale al massimo ~14 punti | 180 mm in 3 giorni, suolo a 0,48: S = 53–61 a 7–11 gg |
| A6 | Habitat «sconosciuto» vale 0,50 e non è distinto da «niente bosco mappato» | Area senza bosco: S = 28 se OSM è vuoto, S = 0 se qualcuno ha mappato un prato |

## 2. Metodo, copertura e limiti

| Verifica | Come | Esito |
|---|---|---|
| Lettura integrale | `core/src/commonMain/.../*.kt` (11 file), ingressi in `MushroomViewModel`, `MushroomRepository`, `ApiServices`, `HeatmapGenerator` | Completata. Il ramo legacy di `utils/MushroomAlgorithms.kt` (config `DEFAULT`) è stato solo scorso |
| Suite esistente | `gradlew testDebugUnitTest :core:allTests` su `747fd44` | 248 test Android e 51 core: 0 fallimenti, 0 errori, 0 saltati. Test iOS simulator saltati (host Windows) |
| Sonde numeriche | Test JUnit temporaneo che chiama `MycoAnalysisEngine.analyze` e `MycoAlgorithms` reali; eseguito due volte e poi cancellato | Tutti i numeri della sezione 3 vengono da quelle esecuzioni. Serie sintetiche di 45 giorni, *B. edulis*, 1000 m, ottobre, faggeta 80 % con ospite, salvo diversa indicazione |
| Dati reali | Chiamata Open-Meteo con le stesse variabili dell'app (`past_days=28`), 6 località italiane, 9 nov 2026 | Tabella in A3 |
| Fonti | Vedi registro in sezione 6 | Letto direttamente il preprint «Predicting porcini» (HTML v2). De Frenne 2019, Zellweger 2020 e Balsamo 2009 **non si sono aperti** |

Limiti da tenere presenti:

- Il dispositivo `56271FDCH00BHR` risulta `unauthorized` ad ADB: non l'ho usato. La revisione non lo richiedeva; un collaudo richiede di accettare la richiesta di debug USB sul telefono.
- Gli scenari sono sintetici. Dimostrano come risponde il modello, non che cosa accade in bosco. Nessuna affermazione qui stabilisce il valore biologico corretto di una soglia.
- I dati reali coprono sei celle e un mese: bastano a mostrare la dipendenza dalla cella, non a stimarne la distribuzione.
- iOS non è stato esaminato.

Ogni rilievo riporta l'etichetta della prova: *numerico* (sonda eseguita), *repository* (lettura del codice), *ragionamento* (inferenza mia, nessuna fonte aperta), *letto direttamente* (fonte aperta in questa sessione).

## 3. Rilievi

### A — Alterano il risultato in condizioni ordinarie

#### A1. Pioggia aggiuntiva riduce l'indice; pioggia regolare è penalizzata
*numerico · repository* — `MycoAlgorithms.kt:299–322`

La fase è una media pesata dei moltiplicatori di tutti gli eventi nel periodo di osservazione, con peso `R·smoothstep(0,10,R)`. Un evento recente (moltiplicatore 0,35–0,50) diluisce quello maturo (1,00). Il vecchio reset a soglia è stato tolto, ma ne resta la versione continua.

| Scenario (*B. edulis*) | Fase | S |
|---|---|---|
| 40 mm a 11 giorni | 1,000 | 69,7 |
| + 10 mm oggi | 0,883 | 61,5 |
| + 40 mm oggi | 0,675 | 47,0 |
| + 40 mm a 5 giorni | 0,777 | 54,2 |
| 4 mm ogni giorno per 45 giorni | 0,625 | 43,6 |
| 8 mm ogni giorno per 45 giorni | 0,625 | 43,6 |
| 20 mm ogni settimana | 0,641 | 44,6 |

Nell'outlook lo stesso effetto fa scendere il punteggio da 68 a 47 nel giorno in cui sono previsti 30 mm. Che una pioggia successiva non annulli una fruttificazione già innescata è una mia inferenza; il preprint citato dal progetto modella l'effetto della precipitazione come lineare e crescente, senza soglia superiore (sezione 4).

**Correzione.** Combinare gli eventi in modo che l'aggiunta di pioggia non possa ridurre il risultato: ad esempio `Φ = 1 − Π(1 − aᵢ·mᵢ)` oppure una media di potenza a esponente alto. Aggiungere un test di monotonia: per ogni ritardo, aggiungere pioggia non deve abbassare S.

#### A2. Minime notturne giudicate con le soglie della temperatura media
*numerico · repository* — `MycoAlgorithms.kt:912–918`, uso a `531–532` e `177–178`

`nocturnalChillingInhibition` confronta il minimo delle minime su 5 giorni con `toleratedTempMin` e `idealTempMin`, che nel catalogo sono soglie di temperatura **media**. Con un'escursione di 10–12 °C, una media pari a `idealTempMin` implica minime sotto `toleratedTempMin`: il fattore contraddice l'intervallo ideale della specie stessa.

| *B. edulis* (ideale 13–20, tollerata 9–24), media/min/max | Minima sottochioma | Fattore notturno | S |
|---|---|---|---|
| 14 / 13 / 15 | 13,8 | 1,000 | 81,2 |
| 14 / 8 / 20 | 9,0 | 0,300 | 58,5 |
| 11,5 / 6,5 / 17 | 7,6 | 0,300 | 55,3 |
| 16,5 / 11 / 22 | 12,0 | 0,883 | 76,3 |

Per il profilo «general» il fattore è pieno solo con minime sottochioma ≥ 14 °C. Lo stesso confronto governa il «trauma da chilling» che allunga la latenza: per *B. edulis* scatta per intero con una sola notte a 6 °C.

**Correzione.** Soglie notturne proprie per specie (`nightMinIdeal`, `nightMinTolerated`), registrate come `EXPERT_PRIOR`, oppure sospendere il fattore finché non è calibrato. Il preprint non usa la temperatura minima come predittore.

#### A3. Soglie assolute di umidità del suolo
*numerico · repository · dati reali* — `MycoAlgorithms.kt:218–273`, `949–979`

`Φ_soil` è la leva più forte del modello: a parità di tutto il resto, S passa da 10,4 (θ = 0,14) a 39,8 (0,18) a 69,7 (0,22). Le soglie 0,14 / 0,22 / 0,38 / 0,44 m³/m³ sono assolute, ma il contenuto volumetrico di un modello meteorologico dipende dalla tessitura assegnata alla cella.

Open-Meteo, stesse variabili dell'app, 12 ott – 9 nov 2026:

| Località (cella) | Pioggia | θ 0–7 cm min / media / max | Giorni con Φ_soil < 1 | < 0,5 | Giorni con media 3 gg > 0,38 |
|---|---|---|---|---|---|
| Abetone 1387 m | 250 mm | 0,126 / 0,211 / 0,365 | 17 su 27 | 6 | 0 |
| Pianura Padana 20 m | 38 mm | 0,316 / 0,388 / 0,516 | 0 | 0 | 14 |
| Cansiglio 1003 m | 135 mm | 0,340 / 0,380 / 0,439 | 0 | 0 | 10 |
| Mindino 902 m | 175 mm | 0,283 / 0,352 / 0,445 | 0 | 0 | 8 |
| Sila 1529 m | 31 mm | 0,239 / 0,305 / 0,422 | 0 | 0 | 0 |
| Firenze 62 m | 42 mm | 0,134 / 0,269 / 0,428 | 6 | 2 | 0 |

La cella dell'Abetone riceve oltre sei volte la pioggia della Pianura Padana e risulta «in stress idrico» per metà del mese; la Pianura con 38 mm sfiora la soglia di «anossia» per metà del mese. Le soglie misurano soprattutto il tipo di suolo del modello. Che la causa sia la tessitura è una mia inferenza: non sono riuscito ad aprire la tabella ECMWF dei parametri per classe.

**Correzione.** Normalizzare θ per cella: indice relativo tra punto di appassimento e capacità di campo se la tessitura è nota, altrimenti posizione di θ rispetto ai percentili della serie della stessa cella (l'API consente `past_days` fino a 92). Nel frattempo ridurre il peso del fattore e segnalarlo come qualità ridotta.

#### A4. Gate del gelo non rispettato; il gelo può aumentare l'indice
*numerico · repository* — `MycoAlgorithms.kt:592–621`

`AGENTS.md` richiede S ≤ 20 con notti post-innesco a T_min ≤ −2 °C. Regime mite (media 12, minima 8), 40 mm a 11 giorni:

| Scenario | W | S |
|---|---|---|
| Nessun gelo | 62 | 56,4 |
| 1 notte a −2 °C (2 giorni fa) | 61 | 55,3 |
| 3 notti a −2 °C (1–3 giorni fa) | 58 | 52,0 |
| 3 notti a −5 °C (1–3 giorni fa) | 42 | 35,3 |
| 5 notti a −2 °C (1–5 giorni fa) | 6 | 3,4 |
| 3 notti a −2 °C (7–9 giorni fa) | 77 | **73,1** |

L'indice crolla solo quando la media su 5 giorni scende sotto la tollerata, cioè per la temperatura media, non per il gelo. Non c'è memoria oltre 5 giorni, e una singola notte a −2 o a −4 °C dà risultati quasi identici (differenza massima 1,1 punti nelle prove). Le giornate fredde generano inoltre il bonus di «shock termico» (calo > 3 °C), che vale fino a 15 punti: il gelo di una settimana prima alza S di 17 punti.

**Correzione.** Escludere dallo shock i cali che portano sotto `toleratedTempMin` o sotto zero; introdurre un danno da gelo con memoria sulla finestra di latenza; aggiungere un test che lo eserciti (oggi non esiste, vedi C3).

#### A5. Gate «Diluvio»: l'ipossia non limita l'indice
*numerico · repository*

La saturazione agisce solo sulla componente umidità (15 punti su 100, per il 60 %) e sulla compensazione del suolo profondo. Con 40 mm a 11 giorni, θ = 0,30 dà S = 69,7; θ = 0,48 dà 59,6; θ = 0,52 dà 55,3. Con 180 mm in 3 giorni e suolo fermo a 0,48:

| Giorni dall'ultima pioggia | 0 | 2 | 3 | 5 | 7 | 9 | 11 | 15 |
|---|---|---|---|---|---|---|---|---|
| S | 9,0 | 18,9 | 31,6 | 42,5 | 53,2 | 59,8 | 60,9 | 52,2 |

Il limite S ≤ 35 vale solo nei primi 3 giorni, e per la latenza, non per l'ipossia. Nessun test del repository esercita questo gate.

**Correzione.** Decidere se il gate è un requisito. Se sì, serve un fattore di saturazione persistente che moltiplichi l'indice; se no, va tolto da `AGENTS.md`. In entrambi i casi dipende da A3.

#### A6. Habitat: sconosciuto non è neutro, e l'evidenza non è monotona
*numerico · repository* — `HabitatGeometry.kt:114–118`, `MycoAlgorithms.kt:773–775`, `MushroomRepository.kt:190`

| Evidenza (*B. edulis*) | H | Hurdle | S |
|---|---|---|---|
| Bosco 80 % + ospite | 1,000 | 1,000 | 69,7 |
| `UNKNOWN` | 0,500 | 0,913 | 28,2 |
| Prato 5 %, bosco 0 % | 0,098 | 0,040 | 0,2 |
| Bosco 2 % | 0,131 | 0,083 | 0,7 |

Tre problemi distinti:

1. **`UNKNOWN` dimezza l'indice.** In un prodotto moltiplicativo 0,50 non è neutro: offline il punteggio scende a meno della metà.
2. **Risposta vuota = sconosciuto.** La query chiede solo bosco, prato, brughiera, zone edificate e `genus`. Seminativi, vigneti, acqua, roccia non vengono richiesti: lì la risposta è vuota e l'habitat risulta `UNKNOWN` (0,50) anziché inadatto. Un singolo poligono di prato lo porta a 0,098; un poligono residenziale sotto il 50 % del disco lo lascia a 0,50 (verificato con `HabitatGeometry.extract`).
3. **Frazione di paesaggio usata come chioma.** `forestCoverFraction` è la quota di bosco OSM nel disco di 1500 m. Viene usata come chiusura della chioma **sul punto** per il microclima e l'intercettazione, e convertita in area basimetrica (`50·c^1,15`). Un punto in radura circondata da bosco riceve il microclima di sottobosco; un punto in un bosco isolato no. L'effetto numerico sulla densità è piccolo (fattore 0,83–1,00), quello concettuale no.

**Correzione.** Distinguere «query riuscita e vuota» da «query fallita»; richiedere anche le coperture che provano l'assenza di bosco; con habitat sconosciuto escludere il fattore e degradare la qualità invece di applicare 0,50; usare il test punto-in-poligono (già calcolato per la distanza) per decidere se applicare il microclima.

### B — Discontinuità e incoerenze rispetto agli standard del progetto

#### B1. Troncamento del kernel a 26 giorni
*numerico · repository* — `MycoAlgorithms.kt:192`

Il kernel viene azzerato oltre 26 giorni anche quando vale ancora molto. Pioggia efficace di un evento da 40 mm a 26 e a 27 giorni:

| Specie (τ picco) | 26 gg | 27 gg |
|---|---|---|
| *Cantharellus cibarius* (14) | 17,4 mm | 0 |
| *Hydnum repandum* (13) | 11,7 mm | 0 |
| *B. pinophilus*, *L. deliciosus* (12) | 8,3 mm | 0 |
| *B. edulis* (11) | 5,3 mm | 0 |

Con un evento da 60 mm il finferlo passa da S = 20,3 a 8,0 e W da 50 a 26 in un giorno. Viola il vincolo di continuità (|ΔS| ≤ 5 %).

**Correzione.** Raccordare il kernel a zero con una finestra liscia, oppure estendere il supporto fino a f < 0,01 (circa 3,2·τ picco) portando `past_days` a 45.

#### B2. Stagionalità a gradini mensili
*numerico · repository* — `MycoAlgorithms.kt:1001–1004`

Valori 1,0 / 0,6 / 0,1 per mese. A meteo identico, *B. edulis* dà S = 7,0 a giugno, 41,8 a luglio, 69,7 ad agosto: tra il 30 giugno e il 1° luglio l'indice si moltiplica per sei. Il calendario è inoltre fisso per quota e latitudine, e duplica in parte l'informazione della temperatura.

**Correzione.** Funzione continua del giorno dell'anno con raccordi di 2–3 settimane, oppure affidare la stagione alla sola risposta termica.

#### B3. Esposizione a gradini
*numerico · repository* — `MycoAlgorithms.kt:1028–1064`

| Variazione | Moltiplicatore |
|---|---|
| Pendenza 2,9° → 3,1° (nord, ottobre) | 1,00 → 0,90 |
| Esposizione 45,0° → 45,1° | 0,90 → 1,00 |
| Esposizione 134,9° → 135,1° | 1,00 → 1,05 |
| Temperatura 20,9 → 21,1 °C (sud, maggio) | 1,02 → 0,90 |
| 30 settembre → 1° ottobre (nord, 16 °C) | 1,00 → 0,90 |
| Pendenza 37,9° → 38,1° | 1,05 → 0,92 |

I versanti ovest (225–315°) valgono sempre 1,00. La temperatura usata è quella del solo giorno corrente.

**Correzione.** Forma continua, ad esempio `1 + k(stagione)·cos(esposizione − esposizione₀)·smoothstep(pendenza)`.

#### B4. Neve: interruttore binario e nessuna fusione
*numerico · repository* — `Domain.kt:244–245, 270–273`

40 mm con media giornaliera +0,1 °C contano 40 mm; a −0,1 °C contano 0. Un giorno con codice WMO nevoso e media +4 °C conta 0. La precipitazione nevosa non rientra mai come acqua di fusione, il che pesa sulle specie primaverili in quota.

**Correzione.** Chiedere a Open-Meteo `rain`, `showers` e `snowfall` orari invece di dedurre la fase dal codice giornaliero.

#### B5. Curve non C¹ e due curve termiche in disaccordo
*numerico · repository* — `MycoAlgorithms.kt:894–939`

- `temperatureResponse` (75 % del termine termico) è un trapezio: derivata 0,25 → 0 a `idealTempMin`. Stesso spigolo in `rainResponse` e `humidityResponse`. `AGENTS.md` chiede risposte termiche C¹.
- Per *B. edulis* il trapezio vale 1,0 da 13 a 20 °C, mentre `ctmi` (25 %) vale 0,42 a 18 °C e 0,12 a 20 °C: le due curve descrivono specie diverse.
- La funzione `ctmi` non implementa il modello cardinale di Rosso: è una funzione beta normalizzata elevata al quadrato. Ho verificato algebricamente e numericamente che ha massimo 1 in T_opt e derivata nulla ai limiti; l'attribuzione a una forma tipo Yan e Hunt è da memoria, non da fonte riletta. Va rinominata o documentata per quello che è.

#### B6. Stessa informazione contata due volte
*numerico · ragionamento*

- **Ritardo dalla pioggia:** entra nel kernel (dentro W) e nella fase (Φ). Il prodotto restringe la finestra con S ≥ 60 a 5 giorni (9–13), mentre W da solo resta ≥ 70 da 8 a 14 giorni.
- **Habitat e quota:** entrano linearmente e di nuovo nell'hurdle.
- **Stagione e temperatura:** vedi B2.

Non è un errore in sé, ma ogni curva va letta sapendo che l'effetto finale è il prodotto di due.

### C — Provenienza dei parametri e potere dei test

#### C1. Etichette `FITTED` e `MEASURED` non sostenute; registro scollegato
*repository* — `ParameterRegistry.kt:102–188`, `docs/TECHNICAL_DOCUMENTATION.md:757–774`

- Nel repository non esistono dati né programmi di calibrazione (`tools/` contiene solo `build_spun_asset.py`). Cinque parametri sono però `FITTED`: ginocchio 70, scala 22, esponente 1,2, β e σ dell'hurdle, questi ultimi attribuiti a de-Miguel et al. 2014. Vanno riclassificati `EXPERT_PRIOR`.
- 10 parametri su 23 non sono letti da alcuna formula (tutti i `FITTED`, più `CANOPY_WARMING_BASE`, `THROUGHFALL_INTERCEPTION_BASE`, `SOIL_SATURATION_ANOXIA_THRESHOLD`, `HABITAT_RESPONSE_PRIORS`, `ECM_RESPONSE_PRIORS`): il codice usa letterali. Modificare il registro non cambia il calcolo.
- La tabella in `TECHNICAL_DOCUMENTATION.md` non corrisponde al codice: «esponente α = 2,5 del kernel» (il kernel usa 3,5–4,5; 2,5 è il β dell'hurdle), «capacità di ritenzione 1,2 mm» (inesistente), soglie del suolo e DTR dichiarate `MEASURED`, memoria a 26 giorni dichiarata `FITTED`.
- `CANOPY_COOLING_MAX = 4,0` è descritto come raffreddamento **massimo**. Il comunicato CNRS sullo studio parla di massime mediamente inferiori di 4 °C: un valore medio, non un tetto (letto solo nel riassunto del motore di ricerca; l'articolo non si è aperto).
- La citazione «Zellweger et al. 2020 / De Frenne 2021» per l'intercettazione del 15 % non è stata verificata: entrambe le pagine erano bloccate.
- Non sono registrate, tra le altre: pesi 40/30/15/15, miscela 75/25, soglie DTR, pavimento notturno 0,30, soglie dello shock, nodi delle risposte di suolo e umidità, livelli di stagionalità e quota, moltiplicatori di esposizione, soglie delle fasi.
- `Φ_phase` è documentato in [0,35; 1,00]; il codice arriva a 0,05 (assenza di pioggia e suolo secco).

#### C2. I «26 giorni» e «20 giorni» vengono da un modello diverso
*letto direttamente (HTML v2: abstract, risultati, metodi; tabelle e figure non lette)*

Il preprint «Predicting porcini» (Brejon Lamartiniere e Hoffman, bioRxiv 2025.12.12.693895) analizza i conteggi giornalieri 2015–2024 di una popolazione presso Bielefeld con un GLMM binomiale negativo. Seleziona per AIC la lunghezza di finestre mobili allineate a destra sulle quali temperatura e precipitazione sono «averaged over right-aligned sliding windows»: 20 giorni per la temperatura, 26 per la precipitazione (26–32 per sito). La temperatura ha risposta quadratica con ottimo intorno a 13 °C; la precipitazione è lineare e crescente.

Conseguenze per Myco:

- 26 è la lunghezza di una **media uniforme**, non il supporto di un kernel unimodale con picco a 8–14 giorni. La forma del kernel di Myco non è stimata da quella fonte.
- Myco usa una finestra termica di 5 giorni (75 %) più 20→5 (25 %) e un plateau ideale 13–20 °C. Il preprint colloca la fruttificazione tra 10 e 15 °C di media su 20 giorni.
- Minime notturne, gelo, escursione termica, shock termico e umidità del suolo **non sono predittori** in quello studio. Le fonti esaminate non forniscono quindi una relazione quantitativa per A2, A4 e la penalità DTR.
- È un preprint, un solo sito di pianura nella Germania nord-occidentale, una sola specie. Non autorizza il trasferimento dei coefficienti all'Appennino o alle altre undici specie.

#### C3. Test che non possono fallire per la ragione dichiarata
*numerico · repository* — `PhenologicalInvariantsTest.kt:522–547`

`benchmark06_carniaPersistentFloodAndFreezing` asserisce S ≤ 20 per «allagamento e gelo». Con media 3 °C il punteggio meteo è 0 per la sola temperatura media: **anche senza pioggia e senza gelo** (0 mm, minima +1 °C) W resta 0. I fattori statici del test (0,90·0,70·0,50·0,90) limitano comunque S a 28,0 con W = 100. Il test passa qualunque cosa faccia il codice su allagamento e gelo; la serie non contiene nemmeno dati di suolo.

- Non esiste alcun test per il gate Diluvio né per il gate del gelo in regime mite (A4, A5).
- I «benchmark» portano nomi di luoghi reali (Carnia, Val di Taro, Garfagnana) ma sono serie sintetiche: `AGENTS.md` chiede di tenerle separate dai riscontri empirici.

**Correzione.** Per ogni gate, uno scenario in cui la sola causa dichiarata è attiva e un controllo in cui è assente e il test deve dare esito opposto.

### D — Minori

- **Outlook:** mostra i valori già trasformati dal modello di sottobosco come se fossero la previsione. 30 mm previsti diventano 26,3 mm; anche temperatura e umidità sono sottochioma (`MycoAnalysisEngine.kt:203–208`).
- **Troncamento:** `growthProbability` usa `toInt()` (69,68 → 69), mentre il punteggio meteo usa `roundToInt()`.
- **Controllo di qualità:** `rainAvailableDays < 8` si riferisce alla finestra legacy `[t−10, t−3]`; la convoluzione ne usa 26. Con soli 11 giorni di storico l'analisi risulta `OPTIMAL` (`MycoAnalysisEngine.kt:91`).
- **Mappa:** per tutte le specie EcM è la stessa ricolorazione della ricchezza EcM di SPUN. Ricchezza della comunità non è presenza di una specie; l'etichetta «N specie» nella scheda è impropria.
- **D02 residuo:** `utils/MushroomAlgorithms.calculateTerrainAspect` restituisce ancora «Pianeggiante» con meno di 5 quote (`:1063–1074`). Non raggiungibile in produzione, ma contrario alla regola.
- **Dati meteo:** `past_days` restituisce previsioni archiviate, non osservazioni. I temporali estivi localizzati, che nel modello hanno la leva maggiore, sono la grandezza meno affidabile. Le variabili `soil_moisture_0_to_7cm` e `7_to_28cm` non compaiono nella documentazione della Forecast API (riferito dal sotto-agente), ma la chiamata reale le restituisce complete (744 ore su 744): modello e risoluzione di provenienza restano non documentati.

## 4. Che cosa regge

- Raccordi Hermite del suolo, della compensazione profonda, del buffering e della saturazione habitat: continui e C¹ dove dichiarato.
- Convoluzione della pioggia senza clustering né soglie: il volume è conservato entro la finestra.
- Calendario per date reali, giorni mancanti non sostituiti, stato «non calcolabile» distinto dallo zero.
- Ordine di grandezza del microclima di sottobosco plausibile (a 24 °C e copertura 0,8: massima −2,2 °C, minima +1,0 °C).
- Luna solo informativa; densità ifale AM esclusa dal calcolo; guild non supportate dichiarate sulla mappa.
- Tetto pratico coerente: condizioni ideali danno S = 80 senza calo termico e 87 con.

## 5. Ordine di intervento proposto

1. **A1** (monotonia rispetto alla pioggia) e **A4** (shock che premia il gelo): difetti di segno, correggibili nel solo `:core` con test dedicati.
2. **A2** (soglie notturne) e **B1** (troncamento del kernel): poche righe, effetto ampio.
3. **A3** (normalizzazione di θ per cella): richiede più storico dall'API; fino ad allora ridurre il peso di `Φ_soil`.
4. **A6** (habitat sconosciuto e query): tocca repository e core.
5. **B2–B5** (stagione, esposizione, neve, curve C¹).
6. **C1, C3** (riclassificare i parametri, collegare il registro, rigenerare la tabella della documentazione, aggiungere i controlli di fallimento) e allineare `AGENTS.md` ai gate realmente garantiti (A5).

Ogni modifica a `:core` richiede l'aggiornamento dei test di parità e, dove cambia il comportamento, dei valori attesi nei golden master.

## 6. Registro delle fonti e delle ricerche

| Fonte | Accesso | Esito |
|---|---|---|
| «Predicting porcini», bioRxiv v2, pagina HTML completa | Letto direttamente (abstract, risultati, metodi) | Usato in C2. Tabella S1 e figure non lette |
| De Frenne et al. 2019, nature.com e core.ac.uk | **Bloccato** (redirect a login; HTTP 403) | Nessun valore verificato. Solo il riassunto di ricerca del comunicato CNRS («in media 4 °C in meno») |
| Zellweger et al. 2020, science.org e PubMed | **Bloccato** (HTTP 403; muro cookie) | Citazione sull'intercettazione non verificata |
| Balsamo et al. 2009 (parametri HTESSEL per tessitura) | **Non leggibile** (PDF scaricato, nessun estrattore di testo disponibile) | La dipendenza dalla tessitura in A3 resta un'inferenza; il riassunto di ricerca conferma solo che dal ciclo 32r3 punto di appassimento e capacità di campo variano con la tessitura |
| Documentazione Forecast API di Open-Meteo | Riferito da sotto-agente (pagina letta) | Strati documentati 0–1 … 27–81 cm; `past_days` descritto come previsioni archiviate |
| Open-Meteo, chiamata reale | Dati scaricati il 9 nov 2026 | Tabella in A3 |

Ricerche eseguite (9 nov 2026): offset De Frenne 2019; costanti idrauliche ECMWF per tessitura; preprint «Predicting porcini». Nessuna ricerca è stata fatta sulla letteratura delle singole specie, sulle soglie di gelo, sull'escursione termica o sull'area basimetrica: su questi punti il rapporto non afferma né nega l'esistenza di basi pubblicate.

**Non affermato.** Che i valori proposti dalle correzioni siano biologicamente corretti; che il comportamento osservato nelle sei celle valga altrove; che i rilievi delle revisioni precedenti siano tutti chiusi (ho verificato solo quelli che ricadono nei punti sopra).

## 7. Correzioni applicate il 10 novembre 2026 (A1, A4)

Modifiche in `:core` (`MycoAlgorithms.kt`, `ParameterRegistry.kt`) e, per allineamento, nel ramo Android non delegato (`utils/MushroomAlgorithms.kt`). Firme pubbliche esportate invariate; aggiunte le sole funzioni `frostDamage` e `shockColdViability`.

**A1.** La media pesata degli eventi è sostituita da un ensemble a livelli: per ogni livello di maturità conta il peso cumulato degli eventi almeno così maturi. Aggiungere pioggia non può più abbassare la fase; un evento isolato dà lo stesso risultato di prima.

**A4.** Tre interventi: danno da gelo `1 − smoothstep(−2, +1, T_min)` sulla minima vista dal modello; il danno delle notti successive a una pioggia riduce sia la maturità di quell'evento sia il suo peso nella memoria pluviometrica; lo shock termico non conta i cali che portano la media sotto la tollerata della specie (credito pieno solo dalla soglia ideale in su). Le due soglie del gelo sono registrate come `EXPERT_PRIOR`, uguali per tutte le specie.

Stessi scenari della sezione 3, motore dopo le correzioni (*numerico*, sonda temporanea poi cancellata):

| Scenario (*B. edulis*) | S prima | S dopo |
|---|---|---|
| 40 mm a 11 giorni | 69,7 | 69,7 |
| + 10 mm oggi | 61,5 | 69,7 |
| + 40 mm oggi | 47,0 | 69,7 |
| 4 mm ogni giorno | 43,6 | 59,5 |
| 8 mm ogni giorno | 43,6 | 68,8 |
| 20 mm ogni settimana | 44,6 | 69,7 |
| 40 mm solo oggi | 9,6 | 9,6 |
| Regime mite senza gelo | 56,4 | 56,4 |
| 1 notte a −2 °C, 2 giorni fa (sottochioma −0,6 °C) | 55,3 | 17,9 |
| 3 notti a −2 °C, 1–3 giorni fa | 52,0 | 4,5 |
| 3 notti a −2 °C, 7–9 giorni fa | 73,1 | 5,9 |
| 3 giorni freddi senza gelo (media 6 °C, minima +3), 7–9 giorni fa | non misurato | 56,4 |
| Gelo a −2 °C due giorni **prima** della pioggia | non misurato | 56,4 |
| Calo da 19 a 14,5 °C dopo 60 mm, entro tolleranza (69,7 senza calo) | non misurato | 76,3 |

Nell'outlook con 30 mm previsti fra due giorni il punteggio passa da 68 a 64 (prima: da 68 a 47); il calo residuo viene dalla curva di latenza dell'evento precedente, non dalla nuova pioggia.

**Test.** `RainMonotonicityAndFrostTest` (11 test, core) e `FrostMemoryLockstepTest` (2 test, Android). Undici dei tredici sono stati visti fallire sul codice precedente; due sono guardie che passavano già (evento isolato limitato dal proprio stadio; calo termico entro tolleranza ancora premiato). Il test del gate del gelo include il controllo senza gelo; quello dell'ondata fredda copre campo aperto e tre livelli di copertura. Suite completa: 250 test Android e 62 core, 0 fallimenti.

**Attese modificate in test preesistenti.** Cinque asserzioni sull'etichetta di fase negli scenari Mindino a due piogge (`MushroomAlgorithmsTest`, `PhenologicalInvariantsTest` ×3, `ScientificRegressionBlock4Test`) codificavano il vecchio comportamento: evento rappresentativo = pioggia più recente e più abbondante («deve resettare a MYCELIAL_HYDRATION»). Ora l'evento rappresentativo è il più maturo attivato. Le soglie numeriche di quegli stessi test (indice ≤ 45 il 18 settembre, 20–50 il 21 settembre, gate Mindino ≤ 35) sono rimaste invariate e passano.

**Limiti.**
- Le soglie del gelo sono uguali per tutte le specie. Per specie di stagione fredda (*Morchella*, *Hydnum*) possono essere troppo severe; il danno è già del 26 % a 0 °C.
- Il gate del gelo è verificato su una griglia (3 specie, 3 volumi di pioggia, gelo da 1 a 10 giorni prima) senza pioggia successiva al gelo. Non è una dimostrazione per ogni scenario.
- Quando nessun livello è attivato a metà (piogge deboli ripetute) lo stadio è «in attesa» ma il testo descrive ancora l'evento più maturo: incoerenza preesistente, non corretta qui.
- XCTest iOS non eseguiti (host Windows).

**Verifica del 10 novembre 2026.** `testDebugUnitTest :core:allTests --rerun-tasks`: 250 + 62 test, 0 fallimenti. `lintDebug`: 0 errori, 0 warning (21 hint informativi). `compileDebugKotlin` e `assembleDebug` riusciti senza warning del compilatore. Controllo dei contratti Swift: 6 test e analisi statica passati. APK debug SHA256 `192bf700ff7eec1e7146fd6165748a1ca51ae1b878bb1e2558a7da4abb47d015`.

**Collaudo sul Pixel `56271FDCH00BHR`.** Installazione stream riuscita, avvio dopo pulizia del logcat, nessuna riga `AndroidRuntime`/`FATAL`. Aperto il preferito Mindino con il modello polifito: indice 0/100, temperatura media 6,4 °C (tollerata 10–25 °C), apporto ponderato 69 mm, fase «Incubazione primordi (6 gg dall'innesco)». Lo zero deriva dalla temperatura media sotto la tollerata, comportamento preesistente e non toccato da queste correzioni. Con i dati reali del giorno non c'era gelo sottochioma né un secondo evento dopo quello maturo: il collaudo conferma l'assenza di regressioni a runtime, non esercita i due rami corretti, che restano coperti dai test automatici. Schermate e log in `build/audit/2026-11-10/` (cartella non versionata).

## 8. Correzioni applicate il 10 novembre 2026 (A2, B1)

Modifiche in `:core` e, per allineamento, nel ramo Android non delegato. Firme esportate invariate; aggiunta la funzione `rainMemoryTaper` e due parametri `EXPERT_PRIOR` nel registro.

**A2.** Le minime notturne sono confrontate con soglie notturne: le soglie di temperatura media della specie meno 6 °C (`NOCTURNAL_MIN_OFFSET_C`). Lo scarto è metà dell'escursione di 12 °C oltre la quale il modello stesso inizia a penalizzare il DTR, così una giornata accettata per media ed escursione non viene penalizzata una seconda volta per la minima. Vale sia per il fattore notturno sia per l'allungamento di latenza da freddo. È una regola di coerenza interna, non una stima fisiologica.

| Media / minima / massima (pioggia 40 mm a 11 gg, copertura 0,8) | *B. edulis* prima | dopo | «general» prima | dopo |
|---|---|---|---|---|
| 14 / 13 / 15 | 81,2 | 81,2 | 78,4 | 78,4 |
| 14 / 8 / 20 | 58,5 | 81,2 | 57,4 | 76,3 |
| 11,5 / 6,5 / 17 | 55,3 | 71,9 | 52,0 | 59,6 |
| 16,5 / 11 / 22 | 76,3 | 79,3 | 69,7 | 80,3 |
| 14 / 2 / 20 (notte realmente fredda) | non misurato | 57,4 | non misurato | 55,3 |

**B1.** Il peso del kernel è moltiplicato per una finestra che vale 1 fino a 20 giorni e scende a zero a 27 con raccordo Hermite. Pioggia efficace di un evento da 40 mm:

| Specie (τ picco) | 26 gg prima | 26 gg dopo | 27 gg |
|---|---|---|---|
| *Cantharellus cibarius* (14) | 17,4 mm | 0,96 mm | 0 |
| *Hydnum repandum* (13) | 11,7 mm | 0,65 mm | 0 |
| *B. pinophilus*, *L. deliciosus* (12) | 8,3 mm | 0,46 mm | 0 |
| *B. edulis* (11) | 5,3 mm | 0,30 mm | 0 |

Con un evento da 60 mm il finferlo passa da S = 16,9 a 14,1 tra 26 e 27 giorni (prima: da 20,3 a 8,0).

**Test.** `NightThresholdsAndKernelTaperTest` (8 test, core) e `NightAndMemoryLockstepTest` (3 test, Android). Nove degli undici sono stati visti fallire sul codice precedente; due sono guardie che passavano già (pavimento e monotonia del fattore notturno; kernel invariato fino a 20 giorni). Il test delle notti ordinarie ha un controllo con notte realmente fredda. Suite completa: 253 test Android e 70 core, 0 fallimenti.

**Scenari di test preesistenti adattati.**
- `testChillingWindow_ColdNightOutside5DaysDoesNotTriggerStasis`: la notte fredda dello scenario passa da 4 °C a 1 °C. Con le nuove soglie 4 °C non è più «freddo» per *B. edulis*; 1 °C lo è e resta sopra la soglia del gelo, quindi il test continua a verificare il solo confine della finestra di 5 giorni.
- Nei due test sulla memoria cancellata dal gelo scritti per A4, la notte di controllo è spostata da 5 a 7 giorni, fuori dalla finestra che allunga la latenza, per isolare il solo danno da gelo.

**Limiti.**
- Lo scarto di 6 °C è unico per tutte le specie e si applica alla minima vista dal modello (sottochioma). Soglie notturne proprie per specie richiederebbero nuovi campi nel catalogo, esportato verso Swift.
- La finestra elimina il gradino ma concentra la discesa negli ultimi sei giorni. Per le specie a latenza lunga la pendenza resta alta: nello scenario da 60 mm il finferlo perde 12 punti tra 22 e 23 giorni. La soluzione piena è estendere lo storico (`past_days` ≥ 45) e con esso il supporto del kernel, cambiamento che tocca anche il client iOS.
- Il fattore notturno entra anche nel ramo `DEFAULT` usato dai test «oracolo»: le fixture esistenti non ne sono toccate, ma la formula di quel ramo non è più quella storica per minime comprese tra le vecchie e le nuove soglie.

**Verifica (A2, B1).** `testDebugUnitTest :core:allTests --rerun-tasks`: 253 + 70 test, 0 fallimenti. `lintDebug`: 0 errori, 0 warning (21 hint informativi). `compileDebugKotlin`, `assembleDebug` e controllo dei contratti Swift riusciti. APK debug SHA256 `4e4c42cfc9a68e042c060e43d20818d1c35eeee53dee414b2ab9108b249d6873`.

**Collaudo sul Pixel `56271FDCH00BHR`.** Installazione stream riuscita, nessuna riga `AndroidRuntime`/`FATAL`. Preferito Mindino, modello polifito, stessi dati in archivio locale del collaudo precedente: indice ancora 0/100 per la temperatura media di 6,4 °C; l'apporto ponderato passa da 69 a 63 mm, effetto atteso della finestra di estinzione sulle piogge di oltre 20 giorni. Le soglie notturne non sono osservabili con questi dati perché il punteggio meteo è già azzerato dalla media. Schermate in `build/audit/2026-11-10/device-a2b1/` (cartella non versionata).

## 9. Storico a 60 giorni e memoria per specie

Completamento di B1 in `:core` e nel ramo Android non delegato. Firme esportate ed etichette Swift invariate; aggiunte la funzione `rainMemoryDays` e due parametri `EXPERT_PRIOR`.

**Che cosa cambia.**
- Android chiede 60 giorni di archivio (`past_days = 60` in `ApiServices.kt`, prima 28).
- La memoria pluviometrica dipende dalla specie: $M = \text{clamp}(\lceil 3\,\tau_{\text{peak}} \rceil, 26, 56)$ (`PHENOLOGY_MEMORY_PEAK_MULTIPLE = 3`, `PHENOLOGY_MEMORY_MAX_DAYS = 56`, `PHENOLOGY_MEMORY_DAYS = 26` diventa il minimo). Valori: *Cantharellus* 42, *Hydnum* 39, *B. pinophilus* e *L. deliciosus* 36, *B. edulis* e «general» 33, *Armillaria* 30, *B. aereus* 27, le altre 26. Dipende solo dalla specie, non dal picco allungato dal freddo né dalla lunghezza dello storico.
- Il raccordo di estinzione resta di 6 giorni, ora riferito a $M$: peso intatto fino a $M - 6$, nullo a $M + 1$. La compensazione del suolo profondo (7–28 cm) resta mediata su 26 giorni.
- Il motore valida almeno $\max(28, M)$ giorni e aggiunge il motivo «storico pioggia parziale» a `qualityReasons` e `missingSources` (e ai motivi di ogni giorno di prospettiva) se manca un giorno di $[t - M, t - 1]$. `DataQualityStatus` non cambia.
- Il ramo Android `calculateEffectiveRainfall` usa per default la memoria della specie del `:core`. `formatLastSignificantRain` non riceve la specie e resta a 26 giorni.

**Pioggia efficace di un evento da 40 mm** (notti a 16 °C, nessun freddo né gelo; scenario SINTETICO):

| Specie | 22 gg | 26 gg | 30 gg | 36 gg | 42 gg |
|---|---|---|---|---|---|
| *Cantharellus cibarius*, prima | 21,11 mm | 0,96 mm | 0 | 0 | 0 |
| *Cantharellus cibarius*, dopo | 26,33 mm | 17,38 mm | 10,55 mm | 4,46 mm | 0,09 mm |
| *B. edulis*, prima | 9,40 mm | 0,30 mm | 0 | 0 | 0 |
| *B. edulis*, dopo | 11,72 mm | 5,34 mm | 1,34 mm | 0 | 0 |

Calo giornaliero massimo nel raccordo di estinzione: 3,9 % del volume dell'evento (*B. aereus*), contro il 16,5 % del vecchio raccordo fisso 20–27 giorni per il finferlo. Sull'intera curva il calo massimo resta sotto il 12 % per tutte le specie con memoria oltre 26 giorni (massimo 10,1 %, *B. aereus*). Per tre specie a latenza breve con memoria minima il kernel puro, non toccato dal raccordo, scende più del 12 % in un giorno: *Macrolepiota* 14,9 % a 9 giorni, *Morchella* 13,0 % a 10, *Amanita caesarea* 12,2 % a 11. È la forma del kernel, uguale a prima; il test del 12 % per queste specie copre il solo raccordo.

**Test aggiunti.** `SpeciesRainMemoryTest` (9 test, core): valori di `rainMemoryDays`; registrazione dei parametri; kernel puro fino a $M - 6$, ≤ 5 % a $M$, zero a $M + 1$; calo giornaliero ≤ 12 %; finferlo a 22 giorni pari al kernel puro; invarianza tra serie di 45 e 60 giorni; finestra del suolo profondo a 26 giorni; motivo «storico pioggia parziale» con 28 e con 60 giorni di storico (stato di qualità invariato) e al confine esatto della memoria. Visti fallire sul codice precedente: valori di memoria, calo ≤ 12 % (finferlo), finferlo a 22 giorni, i due test sul motivo di qualità. `NightAndMemoryLockstepTest`: nuovo `legacyKeepsRainBeyondTwentySixDaysForLongLatencySpecies`, visto fallire insieme al test di lockstep riscritto.

**Test modificati.**
- `NightThresholdsAndKernelTaperTest` (core): le soglie «≤ 5 % a 26 gg, zero a 27, intatto fino a 20» diventano relative alla memoria della specie; il test del salto d'indice usa $M$ e $M + 1$. Serie di 45 giorni sufficienti.
- `NightAndMemoryLockstepTest` (Android): serie da 45 a 60 giorni; lockstep core/Android anche a 30–43 giorni e ai confini della memoria.
- `ScientificRegressionBlock3Test.reg17_fixedMemoryWindowInvariance26Days` diventa `reg17_speciesMemoryWindowInvariance`: codificava il vecchio taglio fisso a 26 giorni con una base di 28 giorni, che non copre più i 33 di *B. edulis*, e usava date inesistenti («2026-08-35»). Ora la base è di 40 giorni su date reali e l'invarianza è verificata oltre la memoria della specie.
- Nessun test esistente controllava `qualityReasons` vuoti o il testo «Tutte le fonti ambientali sono disponibili» con serie corte: nessuna fixture allungata per questo motivo.

**Limiti.**
- Il multiplo 3 e il tetto di 56 giorni sono scelte di forma `EXPERT_PRIOR`, non calibrazioni di campo; le fonti esaminate non danno una durata di memoria per specie.
- Il client iOS chiede ancora il proprio `past_days`: finché non è allineato a 60 giorni, sulle specie con memoria oltre 28 giorni le analisi iOS riportano «storico pioggia parziale» e pesano meno pioggia di Android.
- Le risposte meteo già in cache con 28 giorni di storico, se riusate dopo l'aggiornamento, producono il motivo di storico parziale per le specie con memoria oltre 28 giorni (comportamento della cache non verificato sul dispositivo).
- Verifica XCTest su macOS non eseguita; controllo statico dei contratti Swift riuscito.

## 10. Umidità del suolo normalizzata per cella (A3)

Mitigazione di A3 nel solo motore `:core` (`MycoAnalysisEngine`): Android e iOS la ereditano senza modifiche agli adattatori. Firme esportate e nomi dei parametri invariati; aggiunte la funzione pubblica `MycoAlgorithms.soilCellScale(values, rainMm)` e dieci parametri `EXPERT_PRIOR`.

**Che cosa cambia.**
- Per ciascun orizzonte (0–7 e 7–28 cm) il motore calcola la scala di cella $k = \text{P90}/0.35$ (`SOIL_REFERENCE_CAPACITY`, `SOIL_SCALE_PERCENTILE`) sui valori giornalieri dei 56 giorni di calendario precedenti il target, target escluso (`SOIL_SCALE_WINDOW_DAYS`). Il percentile interpola linearmente fra statistiche d'ordine ed è continuo nei dati.
- $k$ è saturata in $[0.60, 1.50]$ (`SOIL_SCALE_MIN`, `SOIL_SCALE_MAX`) con raccordo quadratico $C^1$ di semiampiezza 0,05 (`SOIL_SCALE_BLEND_HALF_WIDTH`): identità in $[0.65, 1.45]$, costante fuori da $[0.55, 1.55]$, nessun angolo.
- Una scala > 1 (cella fine) vale con almeno 30 valori dell'orizzonte (`SOIL_SCALE_MIN_DAYS`) e almeno 30 mm di pioggia liquida grezza, prima della chioma, nella finestra (`SOIL_SCALE_MIN_RAIN_MM`). Una scala < 1 (soglie abbassate) richiede inoltre una finestra piovosa: è moltiplicata per un raccordo smoothstep della pioggia tra 80 e 120 mm (`SOIL_SCALE_DOWN_MIN_RAIN_MM = 100`, `SOIL_SCALE_DOWN_RAIN_BLEND_MM = 20`), perché dopo un periodo secco un P90 basso non indica un suolo grossolano. Sotto i minimi, o con raccordo sotto 0,5, il motore aggiunge «suolo non normalizzato» a `qualityReasons`, `missingSources` e ai motivi dei giorni di prospettiva; `DataQualityStatus` non cambia. Il motivo non compare per un orizzonte del tutto assente dalla serie.
- La validazione (0–0,60 m³/m³) resta sui valori grezzi. Dopo la chioma ogni θ è diviso per la scala del proprio orizzonte nella serie passata a `MycoAlgorithms`: equivale a moltiplicare per $k$ tutte le soglie volumetriche (0,14/0,22, 0,38/0,44, nodi profondi 0,12–0,52) senza cambiare firme. La stessa coppia di scale, calcolata per il target, vale per tutti i giorni di prospettiva.
- Restano grezzi il valore del fattore SOIL_MOISTURE, `waterDiagnosis` e i `DailyOutlook`. Il dettaglio del fattore diventa «Orizzonti 0-7 e 7-28 cm • normalizzata sulla cella (×0.66)» (due valori se gli orizzonti differiscono).
- Le funzioni Android non delegate di `MushroomAlgorithms.kt` non cambiano: ricevono serie già preparate e la normalizzazione è un passo del motore.

**Scenario di invarianza** (SINTETICO: *B. edulis*, ottobre, solo meteo, 60 giorni passati, cinque piogge da 25 mm, 125 mm nella finestra, traccia con P90 0,355 m³/m³ su entrambi gli orizzonti, suolo moltiplicato per c):

| c | Indice prima | Φ_soil prima | Indice dopo | Φ_soil dopo |
|---|---|---|---|---|
| 0,65 | 40 | 0,56 | 73 | 1,00 |
| 1,00 | 73 | 1,00 | 73 | 1,00 |
| 1,40 | 71 | 1,00 | 73 | 1,00 |

Controlli direzionali (stessa traccia, SINTETICI): cella grossolana (c = 0,6) in periodo umido 26 → 73 (Φ_soil 0,37 → 1,00); stessa cella con gli ultimi tre giorni a 0,10/0,12 m³/m³ 12 → 24 (Φ_soil 0,20 → 0,35, resta siccità). Cella fine (c = 1,3) al proprio P90: fattore suolo da sfavorevole (indice 66) a favorevole (73); al 125 % del proprio P90 resta non favorevole (indice 70, punteggio meteo 82 contro 85). Traccia «tipo Mindino» (livello umido 0,25–0,28, poi 0,16 per 11 giorni): con 47,6 mm nella finestra scala 1,0, indice 25 e Φ_soil 0,32 come prima, motivo presente; con 125 mm la scala scende a 0,71 e l'indice sale a 79 (Φ_soil 1,00).

**Gate Mindino e ordinamento delle stazioni.** Le due fixture esistenti passano senza modifiche. `EndToEndOperationalParityTest.mindinoGate_droughtDecayAbortsFruitingInCoreAndEngine` (47,6 mm nella finestra, P90 0,28): scala 1,0, indice **23** come prima (≤ 35), con il motivo «suolo non normalizzato». Con la prima versione della regola, senza soglia asimmetrica, la stessa fixture dava 67. `ComparativeValidationTest.rankingConcordanceAcrossStations` (35 mm): stazioni 76 > 43 > 23 > 0, invariate. Il replay empirico (`MindinoEmpiricalReplayTest`, 24 giorni) ricade sulle soglie assolute ed è invariato.

**Test aggiunti.** `SoilCellNormalizationTest` (15 test, core): percentile e scala con valori calcolati a mano; non calcolabilità sotto 30 giorni o 30 mm; raccordo pluviometrico della scala < 1 (1,0 fino a 80 mm, 0,90 a 100, 0,80 da 120, continuo e monotono; scala > 1 già con 30 mm); registrazione dei parametri; saturazione e continuità $C^1$ ai limiti; invarianza alla tessitura (c = 0,65, 0,8, 1,25, 1,4; indice entro 1 punto, Φ_soil e punteggio meteo uguali, anche nei giorni di prospettiva); cella grossolana nei due sensi; cella fine nei due sensi; traccia tipo Mindino con circa 50 mm (scala 1,0, Φ_soil ≤ 0,5, motivo presente) e con 125 mm (scala < 1 applicata); cella argillosa (P90 0,45) con 40 mm (scala 1,29 applicata); ripiego con pochi giorni di suolo e con poca pioggia; invarianza fra serie di 60 e 90 giorni; valori mostrati grezzi. Visti fallire sul codice senza normalizzazione: i test della funzione, invarianza (c = 0,65: 40 contro 73), cella grossolana (Φ_soil 0,37), cella fine (fattore sfavorevole a 0,46 m³/m³), ripieghi e valori mostrati. Visti fallire sulla prima versione senza regola asimmetrica: raccordo pluviometrico, traccia tipo Mindino a 50 mm (motivo assente) e confronto 50/125 mm (Φ_soil 1,0 in entrambi), oltre alle due fixture esistenti. Il test 60/90 giorni e quello della cella argillosa passavano già.

**Test esistenti adattati.** Nessuna fixture esistente modificata e nessuna soglia numerica allentata.

**Limiti.**
- La scala è un'euristica ricavata da 56 giorni della stessa cella, non una capacità di campo misurata né un parametro idraulico; 0,35 m³/m³, il P90 e la soglia di 100 mm sono scelte `EXPERT_PRIOR`.
- Regola asimmetrica: una cella sabbiosa in un periodo con meno di 100 mm di pioggia nei 56 giorni resta giudicata con le soglie assolute (riduzione piena solo da 120 mm), quindi con la penalità di siccità di A3; sotto 30 mm vale lo stesso anche per le celle argillose.
- Una cella persistentemente satura alza il proprio P90 e attenua lo smorzamento per saturazione già da 30 mm: il gate Diluvio (A5) dipende ora dal livello umido della cella.
- Analisi e prospettiva condividono una sola coppia di scale, calcolata per il target.
- Verifica XCTest su macOS non eseguita; controllo statico dei contratti Swift riuscito.

## 11. Resistenza al gelo per specie e testo di attesa

Chiude due limiti dichiarati in sezione 7. Modifiche in `:core` (`Domain.kt`, `SpeciesCatalog.kt`, `MycoAlgorithms.kt`, `ParameterRegistry.kt`) e, per allineamento, nel modello e nel ramo Android non delegato (`model/MushroomSpecies.kt`, `utils/MushroomAlgorithms.kt`). Firme esistenti e nomi dei parametri invariati: `frostHardinessC: Double = 0.0` è aggiunto come ultimo parametro, con valore di default, ai due `MushroomSpecies`; il codice Swift (`iosApp/`, sola lettura) non costruisce `MushroomSpecies` e il controllo statico dei contratti passa. Aggiunti l'overload `frostDamage(minTemp, species)` e il parametro `FROST_HARDINESS_BY_SPECIES` (`EXPERT_PRIOR`).

**Che cosa cambia.**
- Danno da gelo della specie: $d = 1 - \text{smoothstep}(-2 - h, 1 - h, T_{\min})$ con $h$ = `frostHardinessC` limitato in $[0, 4]$ °C. Con $h = 0$ coincide con `frostDamage(minTemp)`, che resta invariata. L'overload è usato da `frostSurvivalSince` (quindi da `calculateEffectiveRainfall` ed `evaluateGrowthPhase`) e dal `calculateEffectiveRainfall` Android non delegato.
- Il Nocturnal Frost Gate (`AGENTS.md`) vale alla minima di danno completo della specie, $-2\,°\text{C} - h$.
- In `evaluateGrowthPhase`, se esistono eventi ma nessun livello è attivato almeno a metà e il ramo del gelo non si applica, il testo diventa «Fase temporale potenziale: Piogge deboli, innesco non consolidato (in attesa di precipitazioni più consistenti).» con `daysSinceTrigger = null`; moltiplicatore e stadio (`WAITING_FOR_RAIN`) invariati. Il fattore MYCELIAL_PHASE mostra «In attesa» con questo testo; nessun codice Android o Compose legge `daysSinceTrigger` dal risultato del motore.

| Specie | h (°C) | Innesco del danno | Danno completo |
|---|---|---|---|
| *Hydnum repandum*, *Morchella esculenta* | 2 | −1 °C | −4 °C |
| *Armillaria mellea*, *Lactarius deliciosus*, *Boletus pinophilus* | 1 | 0 °C | −3 °C |
| Tutte le altre | 0 | +1 °C | −2 °C |

I valori sono letti dalle descrizioni del catalogo («resistente alle prime brinate», autunno inoltrato, primavera precoce): stime esperte, non misure.

**Test aggiunti.** `SpeciesFrostHardinessTest` (10 test, core, scenari SINTETICI su 62 giorni reali, 15 agosto – 15 ottobre 2026): valori del catalogo e del registro; `frostDamage(t, species)` per *Hydnum* (0 a −1, 0,5 a −2,5, 7/27 a −2, 1 a −4), identità con la funzione a un argomento per *B. edulis*, $C^1$ ai due estremi, limitazione di $h$ in $[0, 4]$; stessa notte a −2 °C 7 giorni dopo 40 mm: memoria azzerata per *B. edulis*, conservata per 20/27 ≈ 0,741 per *Hydnum* ($u = 2/3$, $3u^2 - 2u^3 = 20/27$), azzerata per *Hydnum* a −4 °C; gate per specie (sei specie, 40 e 80 mm, gelo da 1 a 10 giorni: indice ≤ 20 alla minima di danno completo, controllo a innesco + 2 °C almeno 15 punti sopra); testo di attesa con pioggerella quotidiana e con 3 mm isolati, «Culmine teorico» con 11 giorni per 40 mm a 11 giorni, precedenza del testo del gelo, fattore MYCELIAL_PHASE senza conteggio di giorni. `SpeciesFrostHardinessLockstepTest` (1 test, Android): parità core/Android per *Hydnum* a sette minime tra +3 e −4 °C. `CanonicalSpeciesSafetyParityTest` confronta anche `frostHardinessC` e la mappatura di `toCore()` per una specie fuori catalogo.

Visti fallire prima dell'implementazione: con l'API esistente, la memoria di *Hydnum* (0 contro 30,03 mm attesi), il testo con piogge deboli (3 mm isolati e fattore MYCELIAL_PHASE: «Culmine teorico della finestra (11 gg dall'innesco)» al posto del nuovo testo; la prima versione del test della pioggerella, su *B. edulis*, falliva per lo stadio `WANING`, vedi sotto, e la versione riscritta è stata vista fallire ripristinando temporaneamente il vecchio testo) e il lockstep Android (0 contro 28,10 mm); i test che usano `frostHardinessC` e l'overload non compilavano. Passavano già: «Culmine teorico» con 40 mm e precedenza del testo del gelo (guardie).

**Scostamento dalla consegna.** Con 2 mm al giorno *B. edulis* non resta «in attesa»: su 29 eventi nella finestra di ricerca il peso cumulato (≈ 6 mm) supera metà attivazione e la fase segue l'evento rappresentativo (`WANING`, 24 giorni). Il test della pioggerella usa le cinque specie a latenza breve per cui lo stadio è `WAITING_FOR_RAIN` (*Macrolepiota*, *Morchella*, *A. caesarea*, *B. reticulatus*, *B. aereus*) e verifica per tutte le specie che `WAITING_FOR_RAIN` non porti giorni; il caso *B. edulis* è coperto da 3 mm isolati a 11 giorni.

**Attese modificate in test preesistenti.** `RainMonotonicityAndFrostTest.aFrostNightAfterTheTriggerKeepsTheIndexAtOrBelowTwenty`: la notte di gelo passa da −2 °C fisso alla minima di danno completo della specie (−4 °C per *Hydnum*, −2 °C per *B. edulis* e «general») e il controllo da +3 °C fisso a innesco + 2 °C (+1 °C per *Hydnum*, +3 °C per le altre). La soglia dell'indice (≤ 20) e lo scarto del controllo (≥ 15 punti) sono invariati. Nessun'altra asserzione cambiata.

**Limiti.**
- I valori di $h$ sono stime esperte da rivedere da parte del responsabile del progetto; nessuna fonte esaminata fornisce soglie di gelo per specie.
- Lo spostamento è unico per innesco e danno completo; la larghezza del raccordo (3 °C) resta comune.
- Lo scarto notturno di 6 °C (`NOCTURNAL_MIN_OFFSET_C`) resta deliberatamente comune a tutte le specie: non è stata trovata una base per differenziarlo.
- Con piogge deboli ma numerose le specie a latenza lunga possono superare metà attivazione e mostrare uno stadio maturo con il conteggio dell'evento rappresentativo: comportamento dell'ensemble, non modificato qui.
- Verifica XCTest su macOS non eseguita; controllo statico dei contratti Swift riuscito.

**Verifica (§11).** `testDebugUnitTest :core:allTests --rerun-tasks`: 255 test Android e 104 core, 0 fallimenti. `lintDebug`: 0 errori, 0 warning (21 hint informativi). `compileDebugKotlin` e `assembleDebug` riusciti senza warning del compilatore. Controllo dei contratti Swift: 6 test e analisi statica passati. Nessun dispositivo usato.
