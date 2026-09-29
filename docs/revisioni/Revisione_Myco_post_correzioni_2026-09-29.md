# Myco — revisione scientifica e tecnica dopo le correzioni

**Data:** 29 settembre 2026  
**Repository:** https://github.com/naturewhisp/myco  
**Versione esaminata:** `0dd3bdce3a46883d0878379e1a340c41084a9dfe` (`master` al momento dell'acquisizione).  
**Confronto:** revisione precedente riferita a `99bd52f8d2565aeb7f16978485d1e84498741a65`.  
**Oggetto:** verifica delle correzioni, anomalie residue e attività di sviluppo del percorso A. Il percorso B rimane un'evoluzione futura opzionale.

## 1. Giudizio complessivo

Le modifiche sono sostanziali e risolvono alcuni difetti reali: la precedente formula termica non utilizzabile è stata sostituita; il reset al rapporto 0,70 è stato raccordato; il clustering somma ora gli apporti degli eventi riconosciuti; la risposta del suolo profondo è più regolare; Android disattiva il bonus ifale AM nel meteo operativo; la modalità WEATHER_ONLY è più coerente; sono stati aggiunti test e metadati SPUN.

**Non è però ancora sostenibile dichiarare conclusa la correzione integrale dei rilievi F01–F20 o raggiunta la parità scientifica completa Android/iOS.** Diversi interventi correggono funzioni isolate, mentre i percorsi di produzione continuano a usare dati, impostazioni e algoritmi differenti. Altri problemi sono stati spostati: il reset a 0,70 è raccordato, ma una soglia precedente a 25 mm produce ancora un salto molto grande.

La raccomandazione è completare il consolidamento del **percorso A: indice euristico di idoneità micologica**, con definizione esplicita delle variabili, gestione dell'incertezza e verifica comparativa sul campo. Non è necessario implementare il percorso B per chiudere questa revisione.

### Decisione sul prodotto

| Aspetto | Percorso A attuale | Percorso B futuro opzionale |
|---|---|---|
| Output | Indice convenzionale 0–100 delle condizioni considerate dal modello | Probabilità empiricamente calibrata di un evento definito |
| Significato di 70 | Punteggio relativo secondo una versione dell'algoritmo | Frequenza attesa circa 70% in casi comparabili, se verificata |
| Evento di raccolta | Non stimato direttamente | Richiede specie, zona, periodo, durata/sforzo e criterio di successo |
| Requisito scientifico | Trasparenza, coerenza ecologica, robustezza e utilità comparativa | Anche campionamento adeguato, calibrazione e validazione esterna |
| Stato corretto | Prodotto autonomo da consolidare | Opzione di ricerca, senza obbligo di realizzazione |

**L'assenza di una probabilità calibrata non è un bug del percorso A. Presentare l'indice come probabilità lo è.** La normalizzazione con `tanh`, il vincolo 0–100 e il nome di una funzione non costituiscono calibrazione statistica. Anche una funzione chiamata `hurdleOccurrenceProbability` può essere usata come fattore euristico, purché non sia descritta come una probabilità stimata e validata.

## 2. Metodo e limiti della verifica

Sono stati confrontati codice Android, core Kotlin Multiplatform, adattatori e interfacce iOS, repository dati, test di regressione e documentazione. I riferimenti nel seguito sono bloccati al commit esaminato: aggiornamenti successivi non sono coperti dal giudizio.

La verifica comprende:

- analisi statica dei percorsi effettivamente chiamati dalle applicazioni;
- confronto delle formule e delle impostazioni predefinite;
- controesempi deterministici sulle soglie e sulla rappresentazione dei dati;
- controllo dello stato delle pipeline GitHub Actions del commit;
- confronto con i criteri della revisione precedente, senza considerarli automaticamente tutti risolti perché è presente un test con lo stesso identificatore.

Le tre pipeline **Android, KMP Core e iOS risultavano completate con successo**. Sono rispettivamente i run [36534506394](https://github.com/naturewhisp/myco/actions/runs/36534506394), [36534506443](https://github.com/naturewhisp/myco/actions/runs/36534506443) e [36534506500](https://github.com/naturewhisp/myco/actions/runs/36534506500). I workflow prevedono test Android, test del core e build/test iOS. Questo è un risultato positivo, ma non dimostra che i test coprano le anomalie descritte qui.

Non è stato possibile rieseguire localmente la suite Gradle: il wrapper non ha potuto scaricare Gradle 9.7.1 per indisponibilità della rete verso il server della distribuzione. **I valori numerici riportati come riproduzioni sono calcoli indipendenti delle formule lette nel sorgente, non risultati di una nuova esecuzione dell'app Kotlin.** Questa distinzione vale anche per il caso a 25 mm.

Non sono state svolte nuove osservazioni sul campo, una stima dei parametri o una validazione predittiva. La revisione precedente resta il riferimento per la discussione bibliografica generale; questo documento verifica soprattutto l'effettiva implementazione delle correzioni. La documentazione ufficiale Open-Meteo è stata ricontrollata per la semantica delle precipitazioni.

## 3. Stato dei precedenti rilievi

“Corretto localmente” significa che il difetto specifico è rimosso nella funzione esaminata; non implica validazione biologica né propagazione a tutte le piattaforme.

| Rilievo precedente | Esito della nuova verifica | Residuo principale |
|---|---|---|
| F01 — indice presentato come probabilità | Parziale | Documentazione orientata correttamente ad A; UI iOS e note del core restano probabilistiche, R02 |
| F02 — ramo CTMI non operativo | Difetto originario corretto localmente | Nuova curva utilizzabile; attribuzione e regolarità da descrivere precisamente, R11 |
| F03 — reset rigido della buttata | Parziale | Raccordato il rapporto 0,70; soglia di ammissibilità precedente ancora discontinua, R03 |
| F04 — pioggia persa nel clustering | Corretto per gli eventi candidati | Somma nel cluster presente; estrazione resta selettiva e a soglie, R03 |
| F05 — fasi e piogge persistenti | Parziale | Selezione degli eventi, confini delle fasi e incoerenza pioggia grezza/corretta da verificare globalmente |
| F06 — compensazione di condizioni limitanti | Parziale | Aggiunto gating termico Android; non è dimostrazione generale dei vincoli biologici, R11 |
| F07 — interpretazione fisica della risposta idrica | Migliorato | Funzione euristica, non legge idraulica stimata per il sito; parametri ancora da qualificare |
| F08 — OSM, copertura e area basimetrica | Non risolto sostanzialmente | Ottanti dei centroidi non misurano area o chiome; vuoto OSM classificato inadatto, R04 |
| F09 — ospiti e cache | Migliorato sul percorso Android | Cache include raggio e bonus usa generi confermati; resta necessaria verifica end-to-end fra piattaforme |
| F10 — SPUN e gilde | Parziale | Manifest e disattivazione meteo Android presenti; uso AM residuo in core e mappe, R05 |
| F11 — heatmap | Parziale | Formula finale migliorata e W=0 soppresso; variabili, fase e significato differenti, R06 |
| F12 — parità Android/iOS | Non risolto a livello applicativo | Test di componenti non equivalgono a parità del motore completo, R01 |
| F13 — WEATHER_ONLY | Corretto nella neutralizzazione Android | Habitat, quota, stagione e terreno neutralizzati anche negli outlook; resta scelta da documentare sulla fenologia |
| F14 — date, indici, mese e quota | Parziale | Android cerca la data; iOS usa ancora indice 14; outlook con mese fisso; salti altimetrici, R07 |
| F15 — dati mancanti e serie corte | Non risolto end-to-end | Zeri artificiali, copertura non esplicita e accesso a finestra con indice negativo, R08 |
| F16 — neve e disponibilità temporale | Parziale | Filtro euristico neve in alcuni rami, pioggia efficace ancora su totale, R09 |
| F17 — memoria e suolo profondo | Migliorato | Supporto fissato a 26 elementi e risposta profonda raccordata; chilling ancora booleano e date non garantite |
| F18 — canopy e parametri | Parziale | Canopy Android indipendente dalla specie e risposta termica più regolare; copertura fisica e parametri non validati, R04/R11 |
| F19 — natura delle prove | Parziale | Calendario corretto in nuova fixture; serie ancora sintetica, nessuna validazione esterna, R10 |
| F20 — precisione, robustezza, spiegazioni | Parziale | Output finale Double disponibile; meteo intermedio Int, NaN e spiegazioni da consolidare, R08/R11 |

## 4. Problemi residui e soluzioni implementabili

### R01 — Android e iOS non eseguono ancora lo stesso modello operativo

**Priorità: P1, alta.** È un problema di correttezza e riproducibilità del prodotto, indipendente dalla futura calibrazione.

**Evidenza.** In Android `MushroomViewModel.recalculateForSpecies()` invoca il meteo con `EcologicalWeightsConfig.PHENOLOGICAL`, applica il buffering della canopy, ricostruisce l'habitat da `HabitatEvidence`, calcola la fase e passa il relativo moltiplicatore alla formula finale. In iOS `MycoViewModel.swift` chiama `analysisEngine.analyze(input:)`. L'overload standard di `MycoAnalysisEngine` imposta `growthPhaseMultiplier = 1.0` e `useHurdle = false`. Il meteo del core mantiene proprie finestre e non è il motore fenologico Android. Inoltre il suo bonus ifale resta abilitato per default.

La presenza nel core delle funzioni CTMI, suolo e hurdle non significa che il percorso iOS le usi nella stessa combinazione. Il test `reg18_crossPlatformParityBetweenAndroidAndCore` confronta risposte elementari e formula finale con parametri impostati esplicitamente: non confronta le due pipeline applicative complete.

**Effetto.** A parità di luogo, specie e sorgenti meteo, i due sistemi possono produrre punteggi e previsioni differenti. La mancata applicazione della fase è particolarmente importante subito dopo una pioggia. Non viene qui quantificato un delta reale Android/iOS: manca una nuova esecuzione end-to-end delle due app.

**Soluzione.** Portare nel core l'intera catena di analisi A, incluse aggregazione/qualità, trasformazione microclimatica, risposta meteo, habitat, fenologia e outlook. Esporre un'unica configurazione operativa versionata. Evitare default legacy nelle API chiamabili dal prodotto; mantenerli, se servono, in un percorso di compatibilità separato. Android e iOS devono essere adattatori dello stesso motore.

**Accettazione.** Fixture JSON identica attraversa gli adattatori reali delle due piattaforme e produce stessi dati normalizzati, componenti, fase, punteggio non arrotondato e outlook. Includere pioggia appena caduta, freddo notturno, cambio mese, dati incompleti e variazione del layer AM. Il test deve fallire se una piattaforma torna all'overload con fase costante.

### R02 — Il percorso A continua a essere presentato come probabilità su iOS

**Priorità: P1.** La scelta documentale A/B è buona, ma non ancora applicata a tutti gli output.

**Evidenza.** `RegistryView.swift` contiene la sezione “Probabilità di fruttificazione”, valore percentuale e accessibilità in percento; `ForecastView.swift` usa “Probabilità”, asse percentuale e previsioni con `%`; `MapView.swift` mostra un valore percentuale. `MycoAnalysisEngine.deterministicNote()` produce “Probabilità stimata …%”.

**Soluzione.** Mostrare “Indice di idoneità” e `n/100` su scheda, mappa, grafico, cronologia, testo generato e accessibilità. Aggiungere una definizione breve: “Sintesi euristica delle condizioni considerate dal modello; non indica la probabilità di trovare funghi”. Rinominare le API pubbliche gradualmente, con compatibilità dove necessaria. Non occorre cambiare tutti i nomi interni in un solo rilascio, ma nessun nome legacy deve ricomparire come affermazione all'utente.

**Accettazione.** Test degli output visibili/accessibili e delle note. Vietare `Probabilità stimata` e `%` riferiti all'indice A; le vere percentuali meteorologiche o di copertura possono naturalmente conservarli. Non attribuire al punteggio una frequenza di successo.

### R03 — Nuova discontinuità importante nella selezione della pioggia precedente

**Priorità: P1, alta.** Il precedente raccordo non basta a rendere continua l'intera funzione.

**Evidenza.** In `evaluateGrowthPhase()` l'evento precedente è considerato solo se:

```kotlin
val isSaturatingRain = earlier.rainAmount >= max(25.0f, targetRain * 0.70f)
```

Se non supera questa soglia viene restituita la valutazione della pioggia più recente. Solo dopo la selezione viene applicato lo `smoothstep(0.50, 0.90, ratio)`.

**Controesempio deterministico.** Specie `boletus_edulis`, picco 11 giorni, alpha 4, pioggia isolata 11 giorni prima e nuova pioggia di 10 mm nel giorno target; nessun'altra pioggia, temperature tali da non attivare il filtro neve. I due eventi non appartengono allo stesso cluster.

| Pioggia 11 giorni prima | Ammessa come evento precedente? | Moltiplicatore della fase | Indice con W=80 e H=A=S=T=1 |
|---|---:|---:|---:|
| 24,99 mm | No | 0,35 | circa 26,78 |
| 25,01 mm | Sì | 1,00 | circa 76,32 |

Il salto è di circa **49,55 punti per 0,02 mm**, nella formula operativa con hurdle. W è mantenuto fisso per isolare la causa; non si tratta del risultato di una simulazione meteorologica completa. La pioggia efficace Android esclude il giorno target: il recente episodio può quindi modificare la fase prima di entrare nella convoluzione idrica.

Anche le soglie di estrazione a 10 mm giornalieri, 17,5 mm su tre giorni e 24 mm su cinque giorni richiedono verifica: la continuità di un raccordo interno non elimina le discontinuità della selezione degli eventi. Il vecchio `resolveActiveRainTrigger` conserva il reset rigido, ma non è la causa di questo controesempio: il percorso esaminato usa il nuovo blending.

**Soluzione preferita.** Separare la descrizione delle fasi dal moltiplicatore numerico; quest'ultimo deve derivare da contributi continui degli apporti liquidi datati, eventualmente saturati e combinati secondo una regola esplicita. Se si conserva un modello a eventi, rendere continua anche l'ammissibilità dell'evento precedente, con un peso che tende a zero alla soglia; il semplice spostamento della soglia non risolve il problema. Conservare il bilancio degli apporti e definire l'effetto di piogge sovrapposte o prolungate.

**Accettazione.** Regressione esatta 24,99/25,01; sweep attorno a tutte le soglie per tutte le specie, con controllo sul punteggio finale non arrotondato. Testare anche assenza di eventi, pioviggine persistente e attraversamento dei confini di fase. Un limite di 5 punti può essere un criterio ingegneristico provvisorio, non una costante biologica dimostrata.

### R04 — HabitatEvidence non contiene ancora una vera misura di copertura

**Priorità: P1.** Il nuovo tipo è utile, ma il contenuto non corrisponde alla sua interpretazione fisica.

**Evidenza.** `extractHabitatEvidence()` usa i centroidi/coordinati degli elementi OSM, divide l'intorno in otto settori e calcola `coveredForestSectors / 8.0`. Impone inoltre una copertura minima 0,75 se un centro è entro 50 m, e 0,50 entro 150 m. La query restituisce `out tags center`; il modello non conserva la geometria poligonale. Il numero degli elementi resta usato per la dominanza urbana.

Questa è una misura di presenza per direzione, non un'area forestale. Un solo piccolo poligono con centro vicino può generare copertura 75%; dividere un grande poligono in parti i cui centri occupano più settori cambia il risultato pur lasciando invariata l'area reale. Un punto dentro un bosco esteso non è necessariamente vicino al suo centro. Le soglie 50/150 m introducono altri salti artificiali.

Inoltre una risposta OSM vuota viene classificata `KNOWN_UNSUITABLE`, mentre prova soltanto che la query non ha restituito elementi corrispondenti. `UNKNOWN_HABITAT` assegna invece una copertura 0,50: tale valore entra anche nel buffering microclimatico, trasformando un'assenza di informazione in una proprietà fisica apparentemente conosciuta.

Infine, anche una vera frazione di superficie a bosco **non coincide** con copertura delle chiome o area basimetrica. Applicare `canopyCoverToBasalArea` a questi ottanti non rende il risultato una misura dendrometrica.

**Soluzione.** Distinguere almeno `forestLandFraction`, `canopyCoverFraction`, `distanceToForestBoundary`, evidenza degli ospiti e qualità/provenienza. Calcolare la prima con intersezione e unione di geometrie nell'area di analisi, trattando sovrapposizioni, buchi e multipoligoni; la distanza deve essere dalla geometria, e zero se il punto è interno. Per canopy e area basimetrica usare dati appropriati, oppure espliciti scenari euristici senza unità fisiche dichiarate come misurate. Una risposta vuota deve rimanere UNKNOWN salvo altra evidenza positiva di incompatibilità. Non applicare automaticamente un raffreddamento da bosco al dato ignoto.

**Accettazione.** Stesso poligono intero o suddiviso: identica copertura. Grande poligono con punto interno: distanza zero. Un piccolo boschetto vicino non occupa il 75% dell'area per definizione. Risposta vuota e timeout restano distinguibili da urbano/acqua confermati. Verificare anche disponibilità e costo computazionale: se la geometria non è disponibile, esporre un indicatore di presenza senza chiamarlo copertura.

### R05 — Il layer AM è ancora usato come proxy operativo non giustificato

**Priorità: P1.** La documentazione del dataset è migliorata, ma il codice non rispetta ovunque l'isolamento dichiarato.

**Evidenza.** `SPUN_MANIFEST.json` identifica `hyphal_density` come `ARBUSCULAR_MYCORRHIZAL` e dichiara l'isolamento dai modelli operativi EcM/saprotrofi. In Android PHENOLOGICAL il meteo non applica più quel bonus: correzione positiva. Tuttavia:

1. `core.MycoAlgorithms.weatherScore()` mantiene `applySpunHyphalBonus = true` e `MycoAnalysisEngine` non lo disabilita nella chiamata;
2. le heatmap Android e core usano ancora `hyphalRatio * 80 + 20` per i saprotrofi e `hyphalRatio * 70 + ecmRatio * 30` per i parassiti;
3. il parametro della heatmap che disabilita il bonus ifale riguarda il ramo EcM, non elimina quei due utilizzi.

Una misura AM non diventa biomassa dei funghi saprotrofi o parassiti per il fatto che tutti producono ife. Non è presentata una validazione che giustifichi queste relazioni operative. Anche la ricchezza EcM resta una covariata di comunità: non misura direttamente abbondanza o fruttificazione della specie bersaglio.

**Soluzione.** Disabilitare il contributo AM in tutti i rami operativi privi di giustificazione specifica, incluso il default del core. Mantenerlo eventualmente in un layer esplorativo dichiarato. Per saprotrofi e parassiti progettare covariate coerenti con substrato e ospiti; se non disponibili, dichiarare insufficienza dei dati invece di riutilizzare AM.

**Accettazione.** Cambiare solo AM, a parità di tutto il resto, non modifica indice o heatmap operativi di queste specie. Il test deve attraversare il motore effettivo, non soltanto `calculateWeatherScore(PHENOLOGICAL)` Android. Manifest, unità, trasformazioni e descrizioni devono corrispondere al codice.

### R06 — Heatmap e scheda hanno formula simile, ma non la stessa semantica

**Priorità: P2; P1 se si promette equivalenza puntuale.**

**Evidenza.** La nuova heatmap usa la formula di idoneità e rimuove il precedente potenziale residuo a W=0. Tuttavia imposta `growthPhaseMultiplier = 1.0`, `terrainModifier = 1.0` e sostituisce l'habitat con un potenziale SPUN. Meteo, quota e stagione sono parametri comuni del centro; non diventano automaticamente valori locali per ogni cella. La scheda Android usa habitat OSM, fase e terreno specifici.

**Effetto.** Il pixel centrale può divergere dalla scheda anche quando è corretta la formula finale. Un test della stessa formula con gli stessi numeri non dimostra equivalenza degli input reali. Su una mappa ampia la variabilità rappresentata può dipendere soprattutto dal layer SPUN, non da previsioni micologiche locali complete.

**Soluzione, scelta da esplicitare.** (a) Mappa dello stesso indice A: calcolare per cella i fattori necessari e la stessa pipeline, con risoluzione/costi dichiarati; oppure (b) layer di contesto ecologico: nome e legenda distinti, senza promessa di coincidenza con la scheda. La seconda opzione è compatibile con un prodotto A rigoroso e più economica da implementare.

**Accettazione.** Nel caso (a), confronto end-to-end pixel centrale/scheda su input coincidenti, includendo fase inferiore a 1 e terreno non neutro. Nel caso (b), test del contratto della mappa e testo chiaro su variabili spaziali e valori mantenuti costanti.

### R07 — Gestione del tempo e discontinuità altimetriche ancora incomplete

**Priorità: P1 per selezione del giorno; P2 per stagionalità e quota.**

**Evidenza.** Android `deriveTodayIndex()` cerca la data nel fuso fornito: miglioramento reale. Se non la trova sceglie però un indice di ripiego non legato alla data target. iOS continua a usare `min(14, days.count - 1)` e il mese del calendario del dispositivo. `calculateDailyOutlooks()` Android calcola `seasonScore` una sola volta dal parametro `month`, prima del ciclo: le previsioni oltre il cambio mese ereditano la stagione iniziale. Le finestre e le latenze operano sugli indici, senza garanzia che due elementi successivi siano due giorni consecutivi.

La quota resta discontinua: per B. edulis, appena sotto 300 m il fattore è 0,4; a 300 m è 0,6. Per un errore di quota arbitrariamente piccolo aumenta del 50% quel fattore, prima dell'ulteriore effetto hurdle.

**Soluzione.** Passare esplicitamente `targetDate`, `analysisAsOf` e fuso del luogo; nessun ripiego silenzioso su “giorno 14” se la data manca. Ordinare e validare una griglia giornaliera, mantenendo i giorni mancanti come mancanti. Calcolare stagione e ogni fattore temporale per la data di ciascun outlook. Raccordare la risposta altimetrica se si vuole una funzione continua, senza introdurre un ulteriore salto al bordo. Non aggiungere complessità stagionale non validata solo per ottenere una curva liscia: prima correggere la data utilizzata.

**Accettazione.** Cambiare quantità di storico non cambia la data di analisi; rimuovere un giorno non comprime il tempo; test cambio mese/anno e fusi differenti. Sweep ai limiti altimetrici per tutte le specie. La valutazione retrospettiva deve usare solo dati disponibili all'istante dichiarato.

### R08 — Missingness, serie corte e valori non finiti

**Priorità: P1.**

**Evidenza.** `processWeatherData()` Android aggiunge 0 per temperatura, pioggia e umidità se la lista è più corta di `hourly.time`. Il mapper iOS ignora alcuni valori mancanti, ma assegna comunque 0 se non esistono temperature/umidità e somma a 0 una lista di pioggia vuota. Non esiste così una distinzione affidabile fra “zero osservato” e “non disponibile”.

In `recalculateForSpecies()` la finestra umidità usa `subList(todayIndex - 3, ...)` dopo aver verificato solo che l'indice sia non negativo e nei limiti: una serie con oggi ai primi tre indici può generare un estremo iniziale negativo. È un percorso potenziale di eccezione, non un crash osservato durante questa revisione.

Le formule finali applicano `coerceIn`, che non costituisce una validazione di finitezza dei Double: NaN richiede una gestione esplicita. Restituire un Double dopo aver già arrotondato il meteo a Int non recupera la precisione persa.

**Soluzione.** Modello dati con valore, disponibilità, numerosità/copertura e provenienza; validazione di range e `isFinite()` all'ingresso. Definire soglie di copertura temporale per calcolare ciascun indicatore. Output tipizzato `Available/InsufficientData/InvalidInput`; evitare lo zero come codice d'errore. Finestre sicure e comportamento definito per serie corte. Mantenere Double fino alla sola presentazione.

**Accettazione.** Input vuoto, uno/due giorni, list length mismatch, null, NaN, infiniti e giorno parziale non devono produrre crash né un indice ordinario apparentemente affidabile. Testare il mapper e il viewmodel reale, oltre alle funzioni numeriche. Una pioggia mancante non deve essere indistinguibile da un giorno asciutto.

### R09 — Separazione pioggia/neve parziale e incoerente

**Priorità: P1 nelle aree con neve, P2 altrove.**

**Evidenza.** `ProcessedDay.liquidPrecip` restituisce zero per alcuni codici neve oppure temperatura media ≤0; altrimenti restituisce l'intera precipitazione. Gli eventi fenologici usano questa proprietà, mentre `calculateEffectiveRainfall()` continua a usare `totalPrecip`, dopo l'eventuale buffering. Ne consegue che la stessa neve può essere esclusa dall'innesco della fase e inclusa nel contributo idrico ritardato.

Open-Meteo distingue precipitazione totale, pioggia, rovesci e neve. Un codice giornaliero e una temperatura media non separano quantitativamente una giornata mista. Il filtro introdotto è una protezione euristica, non una misura della pioggia liquida.

**Soluzione.** Acquisire le componenti liquide appropriate al provider, mantenere separata la neve e usare una sola definizione di apporto liquido in convoluzione, eventi e spiegazioni. Gestire l'eventuale fusione con un modello dichiarato e verificabile oppure escluderla esplicitamente in questa versione. Non assimilare centimetri di neve a millimetri d'acqua. Esplicitare se ogni funzione usa precipitazione sopra chioma o apporto al suolo e applicare l'intercettazione una sola volta.

**Accettazione.** Giornata tutta neve, mista, pioggia con media vicina a 0 e disgelo: contributi coerenti in tutte le componenti e nelle due piattaforme. Nessuna falsa precisione quando il provider non fornisce la separazione.

### R10 — Test e documentazione sovrastimano il completamento scientifico

**Priorità: P1 per correggere le dichiarazioni; P2 per ampliare le prove.**

**Evidenza.** `FUTURE_DEVELOPMENTS_ANALYSIS.md` dichiara i quattro blocchi completati e, in più punti, parità o risoluzione integrale. I percorsi descritti sopra contraddicono tali affermazioni. La nuova fixture Mindino usa date di calendario corrette, ma costruisce temperature e umidità costanti e due piogge impostate nel codice. Il test verifica un comportamento atteso su una ricostruzione; non è un dataset osservativo indipendente.

Un caso reale che ha ispirato una fixture è utile per individuare un bug, ma non dimostra l'accuratezza del modello dopo che il modello è stato adattato proprio a quel caso. Analogamente, testare che una formula dia lo stesso risultato della sua copia non ne dimostra l'utilità sul campo.

**Soluzione.** Matrice requisito → test → percorso eseguito → stato. Distinguere test matematici, integrazione, regressioni sintetiche ispirate a casi reali e validazione esterna. Aggiornare gli stati a “parziale” dove necessario e rimuovere le promesse di parità totale finché mancano le prove end-to-end.

Per A pianificare una verifica su uscite registrate con specie, luogo, data, durata/sforzo e anche esiti negativi; confronto temporale/spaziale fuori campione contro baseline semplici, senza richiedere una probabilità calibrata. Metriche adatte includono concordanza di ranking, capacità di selezionare condizioni migliori e robustezza per specie/stagione; occorre definire prima l'uso e il disegno di confronto. Evitare che taratura e valutazione avvengano sugli stessi siti e periodi.

**Accettazione.** La CI verde è descritta come verifica software. I risultati sintetici non sono etichettati come validazione osservativa. La verifica di utilità di A è un'attività autonoma; non viene rinviata implicitamente al percorso B.

### R11 — Rigore delle formule: migliorare senza attribuire loro più evidenza di quella disponibile

**Priorità: P2.** Non tutti questi aspetti richiedono un nuovo modello; spesso richiedono contratti e descrizioni più precisi.

- **Curva termica.** La nuova formula normalizzata elimina il difetto del denominatore precedente e ha massimo nell'ottimo. Non è però globalmente C1 se è posta a zero fuori dai cardinali: nel ramo con `spanMin <= spanMax` il fattore x è lineare al limite inferiore e la derivata interna non è zero; nel ramo speculare accade al limite superiore. Continuità e Lipschitz non sono sinonimi di C1. Se C1 è requisito tecnico, scegliere una famiglia che lo soddisfi e testare i bordi; altrimenti correggere il requisito e descrivere la curva effettiva. Non chiamare una curva empiricamente validata solo perché ispirata a un modello pubblicato.
- **Gating termico.** Riduce la compensazione additiva in condizioni molto fredde/calde. La temperatura media recente resta però un proxy e il limite di 3 °C è un parametro di progetto, non un vincolo universale dimostrato per tutte le specie. Distinguere formazione di nuovi carpofori e persistenza di carpofori già presenti.
- **Suolo.** Soglie volumetriche uniformi non rappresentano automaticamente la stessa disponibilità idrica in terreni diversi. Mantenere la risposta come euristica; una parametrizzazione idraulica richiede dati pedologici pertinenti.
- **Memoria.** Fissare il supporto a 26 elementi migliora l'invarianza rispetto a storico più lungo, ma non garantisce 26 giorni reali né sufficiente memoria per tutte le specie. Il chilling resta attivato da un booleano `any` e sposta il picco: aggiungere test di sensibilità attorno alla sua soglia.
- **Habitat e hurdle.** Il prodotto H×A e la loro riutilizzazione nel fattore hurdle intensificano le penalizzazioni. È una scelta euristica da valutare con analisi di sensibilità, non una doppia informazione empirica indipendente.
- **Parametri.** Documentare origine, specie/regione, unità, range plausibile e versione di ogni parametro importante; distinguere stima da dati, trasferimento bibliografico e scelta progettuale. Le costanti di canopy e i picchi fenologici non diventano validati perché raccordati.

**Accettazione.** Un registro dei parametri e delle ipotesi, test di sensibilità, terminologia coerente e nessuna pretesa di probabilità derivante da queste sole formule. La complessità deve essere mantenuta solo quando produce utilità dimostrabile per A.

## 5. Ordine di sviluppo proposto

| Ticket | Intervento | Dipendenze | Criterio di chiusura |
|---|---|---|---|
| REV2-01 | Correggere semantica UI iOS/core e stati della documentazione | Nessuna | Nessuna probabilità dichiarata per A; stati aderenti al codice |
| REV2-02 | Eliminare usi operativi AM non giustificati e default legacy | Nessuna | Invarianza end-to-end rispetto al layer AM |
| REV2-03 | Correggere ammissibilità e aggregazione degli eventi fenologici | Nessuna, poi migrazione core | Caso 24,99/25,01 e sweep finali superati |
| REV2-04 | Unificare dati, qualità, date e motore A nel core | Disegno contratto dati | Parità completa tramite adattatori di produzione |
| REV2-05 | Correggere pioggia liquida, neve e convenzione temporale | Contratto REV2-04 | Stesso apporto in tutte le componenti, unità coerenti |
| REV2-06 | Sostituire copertura a settori con geometrie o proxy dichiarato | Scelta sorgente/spazio di analisi | Invarianza alla segmentazione, UNKNOWN corretto |
| REV2-07 | Definire e implementare contratto heatmap | REV2-02, scelta prodotto | Equivalenza verificata oppure layer distinto esplicito |
| REV2-08 | Sistemare outlook, quota, precisione e input invalidi | REV2-04 | Casi limite senza salto spurio/crash/zero artificiale |
| REV2-09 | Registro parametri e validazione comparativa di A | Versione A congelata | Protocollo e risultati separati dai test sintetici |

Le prime tre attività possono procedere senza attendere una riscrittura completa. La centralizzazione nel core evita però che ogni correzione debba essere replicata indefinitamente. Le priorità indicano impatto sul prodotto, non una stima dei giorni di lavoro.

### Condizioni per dichiarare consolidato il percorso A

1. Un solo significato dell'output e una sola versione operativa del motore su entrambe le piattaforme.
2. Assenza delle discontinuità gravi dimostrate; soglie residue documentate e testate sul risultato finale.
3. Dati mancanti, ignoti e incompatibili distinti; tempi e unità coerenti.
4. Nessuna misura fisica attribuita a proxy non corrispondenti; covariate SPUN usate coerentemente con la loro gilda.
5. Contratto della heatmap dichiarato e verificato.
6. Documentazione e test descrivono ciò che è provato, con protocollo separato per l'utilità sul campo.

Il punto 6 non richiede di bloccare ogni sperimentazione del prodotto finché esiste una grande base osservativa: richiede che il rilascio sperimentale sia presentato come tale e non come previsione scientificamente validata.

## 6. Percorso B: cosa lasciare nel piano, senza trasformarlo in requisito attuale

È corretto mantenerlo come evoluzione eventuale. Il suo avvio dovrebbe dipendere da una decisione di utilità per l'utente e dalla disponibilità di osservazioni sufficienti, non dalla sola possibilità di aggiungere una sigmoide o un calibratore.

Se verrà avviato, occorrerà definire l'evento: per esempio rilevare almeno un carpoforo della specie target durante una visita di durata e area definite. La probabilità di raccolta richiede inoltre di distinguere presenza, rilevabilità, sforzo ed eventuale disponibilità dopo il passaggio di altri raccoglitori. Avere meteo e habitat non basta a identificare queste quantità.

A e B dovrebbero avere output separati e versionati: `suitabilityIndex` per A; un eventuale `calibratedEncounterProbability` solo quando è stimato e validato sul bersaglio dichiarato, con dominio d'applicazione e incertezza. Non rinominare automaticamente l'indice storico come probabilità quando sarà introdotto B.

**Nessun ticket di questa revisione richiede implementare B.** Tutte le anomalie prioritarie riguardano la coerenza, robustezza e corretta comunicazione del percorso A.

## 7. Appendice riproducibile del salto fenologico

Il frammento Python seguente riproduce la parte matematica del controesempio, mantenendo fissi gli altri fattori. La selezione degli eventi nel sorgente garantisce due eventi separati per le piogge poste al giorno target e undici giorni prima.

```python
import math

# B. edulis: tauPeak=11, alpha=4, minRainAccumulation=35
# hydrationThreshold=4; il giorno 11 è nella finestra di fruttificazione.
weather = 80.0
recent_rain = 10.0
recent_phase = 0.35   # evento nel giorno target, età 0
older_phase = 1.0     # evento al picco, età 11
hurdle = 1.0 - math.exp(-(1.0 / 0.35) ** 2.5)

def smoothstep(a, b, x):
    t = max(0.0, min(1.0, (x-a)/(b-a)))
    return t*t*(3.0-2.0*t)

for older_rain in (24.99, 25.01):
    if older_rain < max(25.0, 35.0*0.70):
        phase = recent_phase
    else:
        weight = smoothstep(0.50, 0.90, recent_rain/older_rain)
        phase = (1.0-weight)*older_phase + weight*recent_phase
    raw = 100.0*(weather/100.0)**1.2*phase*hurdle
    score = 70.0+22.0*math.tanh((raw-70.0)/22.0) if raw > 70.0 else raw
    print(older_rain, phase, score)

# 24.99  0.35  26.777842755127132
# 25.01  1.00  76.3246947370918
```

È una dimostrazione della discontinuità algoritmica, non una previsione biologica e non una prova osservativa. Il test definitivo va inserito in Kotlin sul percorso di produzione con la serie completa.

## 8. Riferimenti verificabili

Tutti i collegamenti al codice seguente puntano alla versione esaminata; cercare i simboli indicati nel rapporto.

- [Algoritmi Android: aggregazione, pioggia efficace, fenologia, date, habitat e outlook](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt). Sezioni principali: righe 128–172, 424–496, 613–805, 815–1040, 1312–1328, 1615–1660, 1777–1924, 1993–2043, 2460–2508.
- [Viewmodel Android: percorso operativo](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/app/src/main/java/github/naturewhisp/myco/ui/viewmodel/MushroomViewModel.kt), `recalculateForSpecies`, righe 375–500.
- [Repository OSM: query, cache ed estrazione dell'habitat](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/app/src/main/java/github/naturewhisp/myco/repository/MushroomRepository.kt), righe 148–168 e 237–344.
- [Modello Overpass: coordinate/centri, senza geometrie](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/app/src/main/java/github/naturewhisp/myco/model/OverpassModel.kt).
- [HabitatEvidence e fallback UNKNOWN](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/app/src/main/java/github/naturewhisp/myco/model/HabitatEvidence.kt).
- [WeatherModel: liquidPrecip e isSnowDay](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/app/src/main/java/github/naturewhisp/myco/model/WeatherModel.kt), righe 79–86.
- [Configurazione ecologica Android](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/app/src/main/java/github/naturewhisp/myco/model/EcologicalWeightsConfig.kt).
- [Motore core: overload operativo, outlook e note](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/core/src/commonMain/kotlin/github/naturewhisp/myco/core/MycoAnalysisEngine.kt).
- [Algoritmi core: meteo e bonus AM, formula finale, CTMI](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/core/src/commonMain/kotlin/github/naturewhisp/myco/core/MycoAlgorithms.kt).
- [Catalogo specie condiviso](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/core/src/commonMain/kotlin/github/naturewhisp/myco/core/SpeciesCatalog.kt).
- [Heatmap Android](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/app/src/main/java/github/naturewhisp/myco/utils/HeatmapGenerator.kt), righe 110–143; [heatmap core](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/core/src/commonMain/kotlin/github/naturewhisp/myco/core/HeatmapEngine.kt), righe 53–77.
- [Manifest SPUN](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/app/src/main/assets/spun/SPUN_MANIFEST.json). L'audit verifica la coerenza fra dichiarazioni e uso nel codice, non certifica ex novo la provenienza dei raster sorgenti.
- [Viewmodel iOS](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/iosApp/MycoIOS/App/MycoViewModel.swift), righe 311–330; [mapper meteo iOS](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/iosApp/MycoIOS/DomainAdapters/OpenMeteoDomainMapper.swift).
- [Scheda iOS](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/iosApp/MycoIOS/Features/RegistryView.swift), righe 222–230; [previsioni iOS](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/iosApp/MycoIOS/Features/ForecastView.swift); [mappa iOS](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/iosApp/MycoIOS/Features/MapView.swift).
- [Test di regressione blocco 4](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/app/src/test/java/github/naturewhisp/myco/ScientificRegressionBlock4Test.kt), parità righe 322–409 e fixture Mindino righe 416–494.
- [Piano e dichiarazioni di completamento](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/docs/FUTURE_DEVELOPMENTS_ANALYSIS.md), in particolare stati dei ticket e sezioni 8.6/9.
- [Revisione precedente inclusa nel repository](https://github.com/naturewhisp/myco/blob/0dd3bdce3a46883d0878379e1a340c41084a9dfe/docs/Revisione_scientifica_algoritmi_Myco.md).
- [Open-Meteo, documentazione ufficiale delle variabili](https://open-meteo.com/en/docs), consultata il 29 settembre 2026: precipitazione totale e componenti liquide/nevose sono variabili distinte.

**Esito:** nuovo rapporto necessario. Le correzioni costituiscono un avanzamento reale, ma il consolidamento scientifico e tecnico del percorso A è ancora parziale. La priorità è chiudere le incoerenze operative dimostrate, mantenendo B come scelta futura facoltativa.
