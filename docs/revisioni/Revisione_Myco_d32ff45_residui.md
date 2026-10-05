# Myco — Revisione integrativa del commit d32ff45

**Data della verifica:** 1 ottobre 2026, UTC.  
**Commit esaminato:** `d32ff459008131f7d2a72bcb21e58a8739fa062c`.  
**Confronto:** `67b5362a78480ff647d8d3259c34cba35f17bfeb`, piano RES-01..09 e successiva revisione C01..C11.  
**Oggetto:** soli problemi residui o regressioni; istruzioni operative e criteri di accettazione.

## Esito della verifica

La bonifica è avanzata, ma **non è conclusa**. Sono confermati nel sorgente il recupero dello scope Swift nel mapper meteo, la chiamata Android a `MycoAnalysisEngine.analyze`, la rappresentazione dell'apporto pluviometrico tramite convoluzione, l'allineamento del chilling e l'applicazione del fattore idrico nei rami fenologici. Sono inoltre presenti la separazione numerica `/100`, la restrizione dell'AI iOS alla scelta di uno stile e gli stati di indisponibilità della mappa per guild non supportate.

Questi miglioramenti non dimostrano ancora la parità operativa delle piattaforme. La migrazione Android perde dati del terreno e cambia la preparazione dell'habitat. La CI iOS del commit fallisce effettivamente in compilazione: il successo dei test Kotlin non copre questo difetto.

**Evidenza verificata:** sorgenti del commit, differenze rispetto al precedente, suite di test e log GitHub Actions. Il resoconto Antigravity è stato confrontato con queste evidenze. Non ho eseguito un collaudo sul Pixel, non dispongo del database originale completo di Mindino e non ho verificato le immagini richiamate da percorsi Windows nel resoconto. Le prove sintetiche non sono una riproduzione certificata del caso reale né una validazione ecologica del modello.

Il prodotto resta correttamente nel **Percorso A: indice euristico di favorevolezza ambientale**. Il Percorso B, probabilità calibrata con osservazioni e sforzo di ricerca, rimane un'evoluzione opzionale. Correggere i problemi sotto riportati non trasforma l'indice in una probabilità.

## Rilievi aperti

| ID | Priorità | Problema | Accettazione essenziale |
|---|---|---|---|
| D01 | P0 | iOS non compila: campo raster inesistente | Build e XCTest iOS riusciti sul commit corretto |
| D02 | P1 | Android perde pendenza ed esposizione nella migrazione | Input DEM e risultato coerenti fra adattatori |
| D03 | P1 | Habitat ancora divergente; effetti EcM non vincolati alla guild; valore UI diverso dal proxy dichiarato | Valutazione condivisa, effetti giustificati per guild, fattori trasparenti |
| D04 | P1 | Giorni mancanti compressi e data target sostituita con un indice fisso | Calendario reale, copertura esplicita, nessun giorno sostitutivo implicito |
| D05 | P1 | Dati invalidi resi come idoneità 0/100 | Stato non calcolabile senza punteggio né fascia |
| D06 | P1 | Esaurimento temporale confuso con siccità; danno biologico dichiarato | Testi derivati separatamente da fase e diagnosi idrica |
| D07 | P2 | Android consente ancora all'AI di riscrivere l'analisi scientifica | Testo scientifico deterministico, output AI vincolato |
| D08 | P1 | Test ancora con date impossibili; Mindino non riprodotto con fixture originale | Calendario validato e replay tracciabile dei dati reali |

P0 indica un blocco della build della piattaforma, P1 un problema di risultato o interpretazione, P2 un miglioramento necessario di coerenza comunicativa. Le priorità non misurano la frequenza osservata del difetto.

## D01 — Errore di compilazione iOS sul contratto della mappa

**Evidenza.** `iosApp/MycoIOS/Platform/SpunBundleService.swift:114` legge `raster.status`. Il contratto KMP `HeatmapRaster`, in `core/.../Domain.kt:288–298`, espone invece `layerStatus` e `statusDescription`.

La CI iOS del commit registra precisamente:

```text
SpunBundleService.swift:114:28: error: value of type 'HeatmapRaster' has no member 'status'
Testing cancelled because the build failed.
```

**Soluzione.** Adeguare il mapper Swift al contratto esportato: usare `layerStatus` e trasferire anche `statusDescription` se serve al banner. Se il modello Swift conserva un campo `status`, definire esplicitamente la conversione di tipo; non aggiungere un secondo stato KMP solo per mascherare il disallineamento.

**Test.** Build del framework e dell'app iOS; XCTest del mapper per raster disponibile e `UNAVAILABLE_GUILD_NOT_SUPPORTED`; verifica del banner e dei pixel trasparenti. I test annullati da una build fallita non possono essere dichiarati passanti.

## D02 — Regressione del terreno nell'adattatore Android

**Evidenza.** `MushroomViewModel.kt:451` costruisce `elevationSamples = listOf(lastElevation.toDouble())`. `MycoAlgorithms.terrain`, riga 773 e seguenti, con meno di cinque campioni restituisce pendenza zero, esposizione zero, testo «Pianeggiante» e modificatore 1. La chiamata operativa perde quindi il terreno già acquisito in `lastTerrainData`. iOS passa invece la lista delle quote disponibili.

**Conseguenza.** Un versante può essere rappresentato come pianeggiante e perdere il modificatore previsto dal modello. L'AI Android può contemporaneamente descrivere il terreno conservato in `lastTerrainData`: testo e calcolo usano informazioni diverse. La quota centrale resta disponibile; il difetto riguarda soprattutto pendenza ed esposizione.

**Soluzione.** Conservare e passare i cinque campioni DEM originali con ordine e distanza documentati, oppure introdurre un input terreno tipizzato condiviso con quota, pendenza, esposizione, provenienza e qualità. L'assenza dei campioni deve produrre «pendenza/esposizione non disponibili», non un'osservazione di terreno pianeggiante.

**Test.** Fixture con cinque quote non uniformi; attraversamento dell'adattatore Android realmente chiamato dal ViewModel; confronto con l'adattatore iOS e con il core. Verificare pendenza, esposizione e modificatore prima del punteggio arrotondato. Separatamente provare quota singola e indisponibilità DEM. Non fissare un bonus come scientificamente corretto solo perché le due piattaforme concordano.

## D03 — Habitat: unificazione incompleta e semantica inesatta

**Evidenza.** Nel nuovo percorso Android, `MushroomViewModel.kt:416–427` ricava il punteggio grezzo dalla frazione forestale per tutte le guild, senza usare `meadowFraction`. La precedente `evaluateSpeciesHabitat` contiene invece una distinzione per i saprotrofi, con prati/pascoli favorevoli. La migrazione non conserva questa logica.

iOS, in `OverpassClient.habitat`, mantiene soglie basate sul **numero di elementi OSM** (16+, 5+, 1+) e valori di chioma discreti 0,85/0,70/0,45. Android utilizza evidenza per settori e una risposta di densità del popolamento. Usare lo stesso motore finale non elimina queste differenze negli input.

Nel core operativo, `MycoAnalysisEngine.kt:76–92`, l'effetto di ricchezza **EcM** viene applicato senza controllo della categoria ecologica. Anche saprotrofi e parassiti possono pertanto ricevere il bonus/penalità EcM nel punteggio puntuale, benché il raster sia stato correttamente dichiarato non supportato per tali guild. Il bonus per ospite e quello EcM possono inoltre moltiplicarsi: da H=0,50 si può arrivare a 0,50×1,15×1,15=0,66125. L'indipendenza dei due segnali non è dimostrata dalla sola implementazione.

Infine `forestProximityIndex` è presente nell'input, ma la riga UI «Indice di prossimità forestale» mostra il fattore habitat elaborato. Per i saprotrofi il fattore mostrato ha persino un minimo di 0,85, mentre il punteggio può usare un habitat inferiore. Il testo «settori a 8 spicchi» viene generato anche per i dati iOS ottenuti contando elementi.

**Soluzione.**

1. Trasferire nel core una valutazione habitat condivisa e specifica per guild, alimentata da evidenze tipizzate: forestali, praticole, ospiti, substrato e qualità. Gli adattatori devono acquisire evidenze, senza duplicare soglie o bonus.
2. Limitare qualsiasi effetto EcM alle categorie per cui è pertinente e documentato. La presenza di un layer regionale EcM non è una prova di idoneità per un saprotrofo praticolo o di substrato per un lignicolo.
3. Esplicitare la regola di combinazione degli indizi correlati: ad esempio un solo modificatore habitat prudenziale, finché non esiste giustificazione per cumularli. Questa resta una scelta euristica da registrare, non una nuova legge biologica.
4. Mostrare separatamente **prossimità forestale**, **compatibilità ecologica della specie** e **fattore habitat effettivamente usato**. Eliminare il minimo UI di 0,85 che nasconde il fattore numerico reale.
5. Trattare la chioma inferita da conteggi/settori come proxy, con qualità esplicita. Non presentarla come copertura misurata. La separazione dal punteggio habitat, già iniziata, non rende da sola il proxy idoneo a una trasformazione fisica del microclima.

**Test.** Stessa evidenza nei due adattatori; specie EcM, praticola e lignicola; prato con poco bosco; habitat ignoto; ospite presente/assente; ricchezza EcM alta/bassa. Variare EcM nelle guild non pertinenti deve lasciare invariato il punteggio. La riga UI deve riportare il fattore realmente applicato.

## D04 — Calendario e completezza meteo non ancora affidabili

**Evidenza.** `OpenMeteoDomainMapper.swift:30–34` elimina una giornata se manca interamente uno dei gruppi temperatura, umidità o precipitazione. La rimozione evita uno zero inventato, ma comprime il calendario. Il core calcola età e latenza tramite differenza fra indici: una pioggia di dodici giorni prima può diventare di undici giorni prima se un giorno intermedio scompare.

Una giornata con pochissimi valori orari può invece restare nella serie senza un indicatore di copertura: la somma della precipitazione parziale può essere scambiata per il totale giornaliero. Non esiste qui una verifica delle ore attese né della distinzione fra osservato, modellato e previsto.

Persistono inoltre i fallback se manca oggi: `MycoViewModel.swift:312` usa `min(28, days.count - 1)` e Android `deriveTodayIndex` usa un ripiego posizionale. Il core limita un indice fuori intervallo invece di rifiutare la data target non rappresentata. `Dictionary(uniqueKeysWithValues:)` può infine interrompere l'esecuzione con date giornaliere duplicate.

**Soluzione.** Usare date gregoriane reali e una `targetDate` esplicita. Conservare le lacune come qualità/stato del giorno, oppure calcolare tutti i ritardi dalle date anziché dalle posizioni. Vietare la sostituzione implicita di oggi con un altro giorno. Aggiungere conteggi e copertura per variabile, timestamp di acquisizione e provenienza dei dati; gestire duplicati con una politica esplicita. Le soglie di copertura devono essere dichiarate e testate, senza trasformare dati assenti in assenza di pioggia.

Per la pioggia liquida resta da verificare anche la separazione dei componenti meteo: la query chiede la precipitazione complessiva e il modello impiega una classificazione semplificata. Per pioggia/neve mista occorre un contratto di unità e componenti coerente con il provider, evitando di usare una sola classificazione giornaliera come misura della frazione liquida.

**Test.** Rimuovere un giorno tra la pioggia del 17 e il 29 settembre; la sua età deve restare 12 giorni. Provare oggi assente, date duplicate/impossibili, una sola ora di pioggia disponibile, finestre incomplete e giorno locale al cambio di data. Non rendere invalida l'analisi odierna per una giornata futura estranea alla sua finestra senza distinguere la qualità dei due risultati.

## D05 — Non calcolabile non significa idoneità nulla

**Evidenza.** Il controllo preventivo degli input meteo è migliorato, ma `emptyResult` restituisce `probability = 0` e `VERY_LOW`. Android passa quel numero a `ProbabilityHeadline`; iOS `RegistryView.swift:224` mostra sempre `analysis.probability/100`. La nota «non calcolabile» può quindi convivere con un numero dall'apparenza valida.

**Soluzione.** Distinguere nel dominio `Computed` e `Unavailable`, oppure rendere il punteggio opzionale con uno stato di validità non ambiguo. Dati insufficienti e dati anomali richiedono una spiegazione, ma nessun numero, fascia o graduatoria. Estendere la validazione agli input non meteo — habitat, campioni DEM, chioma e SPUN — e gestire valori non finiti, intervalli e indisponibilità prima dell'aritmetica. Le soglie plausibili vanno centralizzate e motivate, non confuse con soglie biologiche universali.

**Test.** Serie vuota, NaN, input habitat/DEM non finito e target assente: entrambe le UI devono mostrare «Indice non calcolabile» senza 0/100. Un vero punteggio zero, calcolato con input validi, deve restare distinguibile.

## D06 — Fase temporale ancora scambiata per disseccamento

**Evidenza.** `evaluateGrowthFromTrigger` può produrre `WANING` per semplice superamento della finestra temporale, indipendentemente dall'umidità. Tuttavia il core etichetta ogni `WANING` come «Disseccamento» e `deterministicNote`, riga 473, genera «severo deficit idrico superficiale (sviluppo primordi compromesso)» se la fase è `WANING` **oppure** se `phiSoil ≤ 0,50`.

La prima condizione può diagnosticare siccità su una serie umida. La seconda non autorizza a dichiarare osservato un danno ai primordi: un fattore euristico di stress non è una misura del loro stato biologico. Con suolo mancante, la nota può inoltre segnalare dati pedologici non disponibili e concludere «Tutte le fonti ambientali sono disponibili», perché l'elenco delle fonti mancanti è separato.

**Soluzione.** Separare fase temporale potenziale, diagnosi idrica modellata e qualità. Usare «Finestra temporale in esaurimento» per `WANING` dovuto all'età. Descrivere lo stress sulla base dell'evidenza idrica e della sua qualità: per esempio «Condizioni superficiali modellate sfavorevoli; sviluppo potenzialmente limitato». Evitare «primordi compromessi», «aborto» o altri esiti biologici non osservati. Costruire il testo di completezza dalla stessa struttura di qualità usata nel risultato.

**Test.** `WANING` con suolo umido non deve produrre una diagnosi di deficit; fase temporalmente favorevole con suolo secco deve segnalare il limite idrico; suolo assente non deve risultare copertura completa. Confrontare testo, `phiBase`, `phiSoil`, `phiFinal` e diagnosi strutturata.

## D07 — Android: AI ancora libera e alimentata da dati misti

**Evidenza.** Il prompt Android usa valori locali del percorso precedente — fra cui `finalHabitatScore`, `rainTextVal` e dettagli di terreno — insieme alla nuova diagnosi del core. `generateAdvancedSummary` può poi sostituire la nota deterministica. `cleanAiResponse` elimina formattazione e preamboli: non verifica affermazioni scientifiche o coerenza numerica.

La soluzione iOS attuale è più solida: l'AI sceglie soltanto un token di stile, mentre la nota scientifica viene composta localmente. Questo rilievo riguarda quindi **Android**, senza riaprire la correzione iOS già presente.

**Soluzione.** Applicare la stessa scelta di stile vincolata anche ad Android. Se si conserva una generazione più ampia, usarla solo come contenuto accessorio chiaramente separato, senza sostituire diagnosi o introdurre probabilità. Ogni testo e valore deve provenire da un unico risultato versionato. Associare i risultati asincroni a località, specie, data target e ID analisi, verificando che siano ancora correnti prima della pubblicazione.

**Test.** Risposta AI che inventa «buttata certa», assenza di stress o effetto lunare: nessuna modifica della nota scientifica. Cambio di specie/località mentre la risposta è in corso: nessuna sovrascrittura con testo obsoleto. Test con AI non disponibile e output non riconosciuto.

## D08 — Prove ancora insufficienti per chiudere Mindino e la parità

**Evidenza.** In `EndToEndOperationalParityTest.mindinoGate_droughtDecayAbortsFruitingInCoreAndEngine`, la lista di 36 elementi costruisce ancora `2026-09-${index + 1}`: produce anche **31–36 settembre**. Il target 35 non rappresenta il 29 settembre. Il fatto che questi test passino mostra che il calendario non è validato dal percorso testato.

Le fixture Android hanno date corrette in diversi punti, ma assegnano temperature, umidità e andamento idrico attraverso formule e valori fissi. Riprendere alcuni eventi pluviometrici reali non rende misurata l'intera serie. Confrontare due funzioni con input già preparati manualmente non esercita la catena API/cache → mapper → ViewModel → core → UI.

**Soluzione e test.** Generare tutte le date con `LocalDate.plusDays`; validarle prima del motore. Rinominare le fixture sintetiche come tali e distinguere gli scenari controllati dai replay reali. Aggiungere prove agli adattatori operativi per terreno, habitat, giorni mancanti e stato non calcolabile. La parità richiede confronto di input intermedi e fattori, non solo del punteggio intero.

### Procedura di replay Mindino da eseguire con Antigravity

1. Esportare dal dispositivo i record cache originali e la risposta meteo completa associati a Mindino e al 29 settembre 2026; registrare coordinate, timezone, orario di acquisizione, specie, modalità, versioni e provenienza. Conservare una fixture anonimizzata immutabile con hash. Se i dati non sono più disponibili, dichiarare la ricostruzione incompleta.
2. Conservare piogge **grezze** e trasformate, suolo superficiale/profondo, ET0, temperature min/media/max, umidità e copertura oraria. Annotare se ciascun dato è osservazione, modello o previsione. Non sostituire dati idrici mancanti con curve costruite per ottenere un punteggio atteso.
3. Registrare per ogni stadio: canopy proxy e buffering, precipitazione liquida, contributo di ogni pioggia alla convoluzione, trigger e sua data, `phiBase`, `phiSoil`, `phiFinal`, meteo, habitat prima/dopo bonus, quota/terreno/stagione, prodotto finale prima dell'arrotondamento, qualità e testi UI.
4. Confrontare tre condizioni: dati originali; stessi eventi di pioggia con suolo umido; stessi eventi con suolo secco. È un test di sensibilità del modello, non una stima di resa reale. Aggiungere pioggia debole senza ricarica, ricarica persistente, suolo mancante e una lacuna nel calendario.
5. Eseguire la stessa fixture attraverso i due adattatori. Spiegare ogni divergenza prima del punteggio arrotondato. Archiviare risultati e build esatta; collaudare sul Pixel e su iOS le righe pioggia, stress, fase, qualità e indisponibilità.

**Lettura corretta dei 35 mm.** Una memoria pluviometrica ponderata può restare elevata dodici giorni dopo la pioggia: ciò non dimostra una riserva idrica residua. La convoluzione fenologica e il fattore di umidità del suolo rappresentano quantità diverse. La verifica deve accertare che il secondo limiti davvero l'indice e che l'interfaccia spieghi questa distinzione. Non sottrarre meccanicamente ET0 dalla convoluzione: ET0 non è evaporazione effettiva della lettiera e quel valore ponderato non è un serbatoio idrologico. Un eventuale bilancio idrico esplicito richiede stati, unità, intercettazione, infiltrazione, drenaggio ed evaporazione effettiva coerenti e una validazione dedicata.

Non imporre «Mindino deve dare zero» o una soglia 35/100 come verità ecologica in assenza di osservazioni adeguate. Anche dopo tutte le correzioni, un valore come 29/100 può essere un indice euristico; il problema è presentarlo come probabilità o prova di innesco reale. La rugiada non deve essere introdotta come spiegazione compensativa né come equivalenza automatica in millimetri di pioggia: questa revisione non aggiunge nuove evidenze quantitative che lo giustifichino.

## Ordine di sviluppo e chiusura

Correggere prima D01. Poi D02–D06, perché riguardano input, risultato e interpretazione. Completare D07 e D08 nello stesso ciclo di verifica. Ogni ticket va chiuso con riferimenti alla modifica e alla prova effettivamente eseguita; una nota di implementazione o il passaggio dei soli test core non bastano a certificare iOS, gli adattatori o Mindino reale.

La verifica indipendente della CI ha rilevato: **KMP Core riuscito**, **Android riuscito**, **iOS fallito**. Il resoconto del collaudo locale Android resta un'evidenza separata. Non è certificata qui la Zero Diagnostic Policy sull'intero progetto.

## Riferimenti verificabili

I percorsi riportati nel testo sono relativi al repository; `core/...` abbrevia `core/src/commonMain/kotlin/github/naturewhisp/myco/core` e i file Android citati appartengono a `app/src/main/java/github/naturewhisp/myco`.

- [Commit esaminato e diff](https://github.com/naturewhisp/myco/commit/d32ff459008131f7d2a72bcb21e58a8739fa062c).
- [Core: motore operativo](https://github.com/naturewhisp/myco/blob/d32ff459008131f7d2a72bcb21e58a8739fa062c/core/src/commonMain/kotlin/github/naturewhisp/myco/core/MycoAnalysisEngine.kt).
- [Core: algoritmi e calendario per indici](https://github.com/naturewhisp/myco/blob/d32ff459008131f7d2a72bcb21e58a8739fa062c/core/src/commonMain/kotlin/github/naturewhisp/myco/core/MycoAlgorithms.kt).
- [Android: adattatore e AI nel ViewModel](https://github.com/naturewhisp/myco/blob/d32ff459008131f7d2a72bcb21e58a8739fa062c/app/src/main/java/github/naturewhisp/myco/ui/viewmodel/MushroomViewModel.kt).
- [iOS: mapper meteo](https://github.com/naturewhisp/myco/blob/d32ff459008131f7d2a72bcb21e58a8739fa062c/iosApp/MycoIOS/DomainAdapters/OpenMeteoDomainMapper.swift).
- [iOS: habitat Overpass](https://github.com/naturewhisp/myco/blob/d32ff459008131f7d2a72bcb21e58a8739fa062c/iosApp/MycoIOS/Data/OverpassClient.swift).
- [Test core con calendario non valido](https://github.com/naturewhisp/myco/blob/d32ff459008131f7d2a72bcb21e58a8739fa062c/core/src/commonTest/kotlin/github/naturewhisp/myco/core/EndToEndOperationalParityTest.kt).
- [CI iOS fallita](https://github.com/naturewhisp/myco/actions/runs/36931987392), job `110603291974`, errore di compilazione e test annullati.
- [CI core riuscita](https://github.com/naturewhisp/myco/actions/runs/36931987457).
- [CI Android](https://github.com/naturewhisp/myco/actions/runs/36931987368).
- [FAO, definizione di ET0](https://www.fao.org/4/X0490E/x0490e05.htm) e [distinzione fra ET0, coefficienti e stress idrico](https://www.fao.org/4/x0490e/x0490e0e.htm): supportano la distinzione fra evapotraspirazione di riferimento ed effettiva. Non costituiscono una calibrazione per funghi o lettiera forestale.

Questa integrazione non modifica il repository e non sostituisce una validazione sul campo o una calibrazione probabilistica.
