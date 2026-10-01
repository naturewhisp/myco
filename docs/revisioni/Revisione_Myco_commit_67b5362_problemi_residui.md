# Myco — verifica del commit 67b5362: problemi residui e soluzioni

**Data:** 1 ottobre 2026.  
**Commit esaminato:** `67b5362a78480ff647d8d3259c34cba35f17bfeb`.  
**Confronto:** `d2822246d988e125d98892e57320d88e016a1014`.  
**Oggetto:** verifica delle correzioni RES-01..09 e della dichiarazione di completamento allegata dall'utente.

## Esito e limiti

**La chiusura integrale dei rilievi non è confermata.** Il commit contiene correzioni reali: fasce numeriche neutrali, /100 nelle viste Android modificate, rimozione del vecchio reset idrico a 1 mm, curva del suolo smoothstep, correzione del doppio buffering operativo core e della gerarchia termica su input validi. Questo documento riporta soltanto difetti residui e criteri per chiuderli.

Revisione del sorgente e dei test, senza modificare il repository. Il log Antigravity allegato registra comandi e dichiarazioni, ma non fornisce gli output completi, gli screenshot né la fixture originale: non equivale a una verifica indipendente dei risultati. La fixture `mindino_2026-09-29_case.json` non è presente nel checkout di questo commit.

Il tentativo locale `./gradlew :core:testAndroidHostTest --no-daemon` si è fermato prima dei test: distribuzione Gradle 9.7.1 non scaricabile, errore di rete. Non sono disponibili qui Xcode/Swift né il Pixel. L'API GitHub Actions interrogata per questo SHA ha restituito zero run al momento del controllo: non è una prova di successo o fallimento della CI, né esclude run futuri. Non certifico dunque “100% test passanti” o “0 warning”.

La causa numerica storica del 29 resta aperta. I 35 mm sono compatibili con il calcolo riferito al 28 settembre, ma non provano la sequenza storica del dispositivo. Il nuovo 36/100 segnalato su Pixel appartiene a un'altra analisi: non chiude retroattivamente il caso.

## Priorità e tracciamento

| ID integrativo | Priorità | Residuo | Rilievi precedenti |
|---|---|---|---|
| C01 | P0 | Mapper iOS usa una variabile fuori scope | RES-06 |
| C02 | P1 | Android non migra al motore condiviso; chilling e registro disallineati | RES-04/08 |
| C03 | P1 | Il core mostra un cumulato come “mm ponderati” | RES-02 |
| C04 | P1 | Copertura chioma ancora ricavata da punteggi/proxy; differenze Android/iOS | RES-05 |
| C05 | P1 | Validazione dopo il calcolo e non applicata al percorso Android | RES-06 |
| C06 | P1 | Fattore idrico saltato in rami anticipati; diagnosi poco coerente | RES-02/03 |
| C07 | P1 | Data target, aggiornamento e risultato atomico incompleti | RES-06/09 |
| C08 | P1 | Fasi, note, luna e SPUN ancora descritti in modo assertivo | RES-01/07/09 |
| C09 | P2 | Heatmap stazionaria dipende dalla quota del punto; stato indisponibile non esposto | RES-07 |
| C10 | P1 | Test sintetici presentati come parità completa sul caso reale | RES-04/08 |
| C11 | P2 | Documenti dichiarano chiusi interventi ancora incompleti | RES-08 |

P0 indica un blocco della build iOS dal sorgente esaminato; le altre priorità riguardano correttezza e trasparenza, senza implicare una validazione scientifica del percorso A.

## C01 — Errore di scope nel mapper iOS

**Evidenza certa dal sorgente.** In `OpenMeteoDomainMapper.processedDays`, `var value` è dichiarata nel ciclo `for (index, timestamp)`. Il successivo `compactMap { date in ... }` usa `value.temperatures`, `value.precipitation` e le altre proprietà senza dichiarare una variabile locale. La variabile del ciclo non è in scope nella closure successiva. Questo è un errore di risoluzione del nome Swift; non ho eseguito Xcode localmente.

**Soluzione:** recuperare l'accumulatore nella closure, prima dell'uso:
```swift
guard let value = accumulators[date],
      let avgTemp = value.temperatures.average else { return nil }
```
Gestire insieme copertura e missing dei campi, senza sostituire umidità assente con zero o precipitazione assente con una somma vuota pari a zero.

**Accettazione:** build e XCTest iOS sullo stesso SHA della correzione; mapper con temperature mancanti, RH mancante, pioggia mancante e copertura oraria parziale. Il test deve verificare stato di qualità e semantica delle assenze, non solo l'assenza di crash.

**File:** `iosApp/MycoIOS/DomainAdapters/OpenMeteoDomainMapper.swift`, circa righe 30–43.

## C02 — Unificazione ancora non implementata e chilling disallineato

**Evidenza:** `MushroomViewModel.recalculateForSpecies()` continua a chiamare `MushroomAlgorithms.calculateWeatherScore`, `evaluateSpeciesHabitat`, `evaluateGrowthPhase`, `calculateSuitabilityScore`, `calculateFactors` e `calculateDailyOutlooks`. Nel viewmodel non compare `MycoAnalysisEngine`. Anche il caricamento iniziale esegue calcoli locali.

iOS chiama il motore core, ma quel motore aggiunge bonus habitat propri: canopyTypes non vuoto e ricchezza EcM modificano l'habitat ricevuto. Questi passaggi non costituiscono automaticamente lo stesso contratto della valutazione Android.

Il core mantiene chilling di **14 giorni e +2 giorni** in `calculateEffectiveRainfall`; Android usa **5 giorni e +1,5 giorni**. Le voci del registro con 5/+1,5 esistono ma non governano questa formula. Android mantiene inoltre i tre parametri idrici hardcoded (0,14/0,22/0,20), mentre il core li legge dal registro.

**Soluzione:** migrare realmente Android a un unico risultato core, dopo aver definito un contratto unico di evidenza habitat e modalità ALL/WEATHER_ONLY. Eliminare i bonus duplicati o assegnarli a un unico livello. Leggere i parametri chilling e suolo da una sola implementazione; mantenere la classificazione EXPERT_PRIOR senza attribuire universalità biologica.

**Accettazione:** stessa fixture normalizzata attraverso gli adattatori delle app; confronto di meteo, habitat, pioggia ponderata, fase, fattore idrico, score non arrotondato, score visualizzato e outlook. Includere una notte fredda fra 6 e 14 giorni prima, assente negli ultimi 5: il test attuale caldo non intercetta questa deriva.

## C03 — Il core rinomina una quantità diversa da quella dichiarata

**Evidenza:** i fattori del motore usano ancora `windows.rainWindowTotalMm` ma lo etichettano “Apporto ponderato per latenza” e “mm ponderati”. `EnvironmentalWindows.derive` definisce questo campo come somma rettangolare delle piogge in `[t−10, t−2)`, non convoluzione di 26 giorni per specie. Il meteo core usa invece `calculateEffectiveRainfall`.

Ne consegue che iOS può presentare come ponderato un numero che non lo è. Android usa la convoluzione per il fattore modificato: ulteriore divergenza.

**Soluzione:** calcolare e conservare nel risultato una sola grandezza pluviometrica fenologica con parametri, finestra e passaggi canopy, e riutilizzarla per meteo e spiegazione. Un eventuale cumulato reale deve restare un campo distinto, con intervallo e unità propri.

**Accettazione:** episodio isolato dodici giorni prima, senza pioggia nella finestra rettangolare: cumulato recente zero, memoria fenologica positiva e correttamente denominata. Test della riconciliazione dei 35 mm su fixture autentica quando disponibile. Verificare anche la versione legacy, che ha ricevuto lo stesso cambio di etichetta.

## C04 — La chioma iOS è ancora il punteggio habitat; il proxy resta fisico nei calcoli

**Evidenza:** `OverpassClient.habitat` assegna `score` in base al numero di elementi (1/0,95/0,6/0,1), quindi:
```swift
let canopyCover = min(1.0, max(0.0, score))
```
Passare questa nuova proprietà al viewmodel non disaccoppia la grandezza: cambia soltanto il contenitore. Persino zero elementi produce canopy 0,1, senza una misura di copertura.

Android conserva il proxy a settori, più fallback da conteggi, e lo usa per intercettazione/temperatura. Il fattore conserva il titolo “Copertura forestale” e il valore percentuale: il disclaimer aggiunto non trasforma il proxy in una percentuale geometrica.

**Soluzione:** separare `habitatSuitability`, `forestProximityIndex` e `canopyCoverEstimate`, con origine e qualità. Se manca una stima fisica idonea, usare un fallback esplicitamente modellistico e qualificarne i limiti, senza dichiararlo copertura osservata. Allineare semantica UNKNOWN e query delle piattaforme. In UI: “Indice di prossimità forestale”, con scala /100 se è un indice, non “100% copertura”.

**Accettazione:** zero elementi, elementi senza center, molti elementi lontani, stesso poligono segmentato e medesimi input Android/iOS. Il numero di oggetti non deve essere presentato come misura fisica locale.

## C05 — Controlli tardivi, non uniformi e incompleti

**Evidenza:** il core controlla finitezza/plausibilità **dopo** canopy, fase, meteo e probabilità. Un NaN può quindi raggiungere medie, response e conversioni intere prima della marcatura. Il controllo non include ordine Tmin/media/Tmax, continuità delle date e completezza oraria. I bounds sono hardcoded, non configurabili come pianificato.

Android, che non usa il motore, produce `dataQualityStatus` soltanto dalla numerosità dei valori superficiali: non eredita i controlli core. I mapper conservano RH mancante come zero e pioggia assente come zero in alcuni rami. Le query chiedono ancora precipitation aggregata; il filtro neve resta dipendente dal codice meteo e dalla temperatura media, senza separazione effettiva di rain/showers/snow.

**Soluzione:** validare e normalizzare prima di ogni formula; distinguere invalidità da plausibilità dubbia e non correggere silenziosamente. Definire una politica per dati invalidi/assenti (esclusione, mancata analisi o risultato parziale dichiarato). Conservare contatori delle ore valide e tipi/unità delle precipitazioni. Usare la stessa normalizzazione su entrambe le piattaforme.

**Accettazione:** NaN, Infinity, precipitazioni negative, inversione termica, serie vuota, giorno duplicato/mancante, RH assente, neve/pioggia mista, giorno parziale. Nessun crash o output apparentemente normale prima della diagnosi. Non convertire la neve in pioggia disponibile al suolo senza un modello di fusione appropriato.

## C06 — La nuova curva è corretta localmente, ma non applicata a tutti i rami

La curva `0,20 + 0,80 × smoothstep(0,14;0,22;θ)` è C¹ e per θ=0,16 restituisce **0,325**. Il prodotto `phiBase × phiSoil` sostituisce correttamente il gate precedente nel ramo che lo raggiunge.

**Residui:** entrambe le funzioni di fase ritornano anticipatamente quando non trovano eventi, prima del calcolo del fattore idrico. In quel ramo suolo asciutto e umido ricevono il medesimo moltiplicatore base 0,25. La diagnosi può comunque mostrare θ, senza spiegare che non ha limitato il risultato. Decidere esplicitamente se il fattore idrico è globale o condizionato a un evento; non dichiararlo globale mentre lo si salta.

La media usa due o tre elementi disponibili, non necessariamente tre giorni completi/consecutivi. Il target mancante può essere compensato dai due precedenti senza distinguere l'assenza corrente. Il core formatta θ con `oneDecimal`, che tronca: 0,16 viene mostrato **0,1 m³/m³**, perdendo una parte rilevante dell'informazione.

**Soluzione:** calcolare una diagnosi idrica condivisa prima dei rami della fase e applicarla secondo una regola documentata. Esporre phiBase/phiSoil/phiFinal separati. Distinguere copertura 2/3, target assente e ore parziali. Formattare θ almeno a due decimali con arrotondamento corretto. Valutare la penalizzazione totale insieme alla componente suolo già nel meteo; la continuità non prova validità scientifica.

**Accettazione:** con/senza evento, θ=0,13/0,16/0,22, target mancante, solo due giorni disponibili, suolo superficiale assente ma profondo presente. La UI deve dichiarare esattamente dato, finestra e limite del calcolo.

## C07 — Rollover aggiunto, ma date e aggiornamento atomico non completati

**Evidenza:** iOS continua a scegliere `todayIndex = min(28, days.count−1)`; Android mantiene un fallback posizionale in `deriveTodayIndex` quando la data non compare. Entrambi possono analizzare il giorno sbagliato su serie incompleta/stale.

`checkDayChangeAndRefresh` interviene al ritorno in foreground: non rileva da solo mezzanotte quando l'app resta aperta. Il viewmodel mantiene molti campi separati, non un unico risultato aggiornato atomicamente. Acquisizione rete, data target e timestamp vengono assegnati in momenti diversi; durante un fetch possono coesistere metadati nuovi e score precedente. La nuova qualità è impostata “OPTIMAL” dal conteggio dei valori suolo, non dalla qualità complessiva.

**Soluzione:** data target esplicita cercata nelle date normalizzate; errore/stato degradato se assente. Clock iniettabile, fuso locale e controllo sia in foreground sia al cambio giorno con app attiva. Un singolo immutable `AnalysisUiState` comprensivo di coordinate/specie/configurazione/date/score/fattori/qualità; publication unica e protezione da risposte obsolete. Esporre separatamente timestamp della cache e del calcolo anche offline.

**Accettazione:** app aperta oltre mezzanotte, background→foreground, cache scaduta offline, target assente, cambio specie/località durante caricamento. Nessuna miscela di generazioni; stesso target su Android/iOS.

## C08 — Testi e indicatori non ancora aderenti alla diagnosi

**Evidenza:** i tier neutrali sono corretti, ma gli algoritmi conservano “Buttata attiva (finestra ottimale di raccolta)” e “Buttata attiva e culmine epigeo”. Il fallback Android `generateSummaryText` non riceve fase/fattore idrico e può dichiarare potenziale ottimo senza leggere lo stress. Il core `deterministicNote` sceglie il limite soltanto fra meteo/habitat/altitudine/stagione, ignorando il fattore idrico e la nuova qualità.

Il prompt AI include ora una media θ, ma non necessariamente uno stato di stress strutturato o phiSoil. Il risultato AI viene accettato se non nullo dopo pulizia, senza il controllo semantico/fallback vincolato dichiarato nel piano. Luna rimane FAVORABLE quando moon.favorable, con “Fase crescente propizia”; la stringa generale riporta ancora “Favorevole”. SPUN_HYPHAL core resta “Biomassa ifale”, FAVORABLE; titolo mappa ancora “CONTESTO ECOLOGICO E BIOMASSA”.

**Soluzione:** derivare sintesi e fattori da diagnosi strutturata dello stesso risultato. Usare “fase temporale potenziale stimata”; mostrare il limite idrico effettivo prima dei fattori favorevoli. Luna solo astronomica, AM informativo senza equipararlo a biomassa del fungo bersaglio. AI come riformulazione di fatti ammessi, con controllo e fallback; se non si può assicurare aderenza, mantenere la spiegazione deterministica.

**Accettazione:** meteo/habitat favorevoli con phiSoil=0,20; dati assenti/invalidi; indice alto con fase ignota; note AI contraddittorie. Nessuna buttata osservata dichiarata o causa lunare implicita.

## C09 — Mappa corretta nel meteo, ma ancora poco trasparente

**Evidenza:** W=100 e season=1 eliminano la dipendenza meteo attuale nei call site modificati. Tuttavia il raster riceve ancora un unico altitudeScore del punto selezionato, applicato a tutte le celle; l'anteprima usa 0,8 e il risultato finale la quota del punto. Non è una valutazione DEM cella per cella e non rappresenta automaticamente SPUN+OSM+orografia locale.

Gli stati UNAVAILABLE_GUILD_NOT_SUPPORTED esistono nei raster ma non risultano consumati dalle viste Android/iOS cercate: un raster trasparente può essere interpretato come vocazione nulla anziché informazione indisponibile. `ecm == 0` viene escluso: occorre distinguere un vero zero dal nodata del formato, senza supporre che siano identici.

**Soluzione:** usare quote territoriali cella per cella o neutralizzare la quota e dichiarare il layer come contesto SPUN. Rendere leggibile lo stato del layer e la sua motivazione; distinguere indisponibile, nessun dato e valore basso. Specificare reference scenario ed evitare la legenda “biomassa”.

**Accettazione:** stessa area con centri a quote diverse, anteprima/finale, specie non supportate, celle nodata e zero valido. Non cambiare l'intero layer per la quota del punto centrale senza dichiararlo.

## C10 — Le nuove prove non dimostrano la parità operativa dichiarata

**Evidenza:** `MindinoHydrologyAndC1ContinuityTest` costruisce 36 giorni con meteo costante e due eventi. Non legge la fixture storica né esegue gli adattatori delle app. Il confronto finale fissa W=78, H=A=S=T=1 e confronta singole formule: non è un end-to-end su Mindino reale. Il test C¹ definisce una propria `evalPhiSoil` copiando la formula; può restare verde anche se il codice operativo diverge.

**Soluzione:** estrarre una funzione pura di risposta idrica effettivamente usata dalla produzione e testarla direttamente. Separare suite sintetica (invarianti) da fixture reale (riproduzione), con hash/provenienza/versione e metadati as-of. Testare le app attraverso i mapper e la pipeline reale; confrontare tutti i componenti, non solo lo score finale.

**Accettazione:** Android/iOS/core sul medesimo input; caso canopy/chilling; serie incompleta; supporto storico completo; test regressione del cumulato erroneamente etichettato. Allegare run/log sullo SHA esatto. Se il payload originale manca, nominare la ricostruzione come tale; non completare il caso storico per dichiarazione.

## C11 — Chiusure documentali e vincoli scientifici incoerenti

**Evidenza:** la nuova tabella v1.3.4 dichiara chiuse attività che C01–C10 mostrano incomplete. AGENTS mantiene il vincolo generale “25 mm seguiti da 12 giorni asciutti ⇒ S≤35” accanto al nuovo divieto di tetti arbitrari, senza condizioni sullo stato del suolo. Questa non deve diventare una legge universale da soddisfare a forza.

Il registro classifica correttamente i nuovi nodi come EXPERT_PRIOR, ma descrive 0,22 come riserva “pienamente sufficiente”: qualificare tale affermazione come risposta del modello. Le costanti chilling registrate non guidano il calcolo. Gli interventi non implementano il percorso B e non calibrano una probabilità di raccolta.

**Soluzione:** tabella ticket con “implementato parzialmente / da verificare / chiuso con evidenza”, commit e test di chiusura. Riscrivere i gate come fixture condizionate con stato idrico/specie/input espliciti. Evitare “rigore termodinamico” o certezza fisiologica per offset/soglie euristici. Non dichiarare il caso 29 risolto mentre lo si mantiene formalmente aperto.

## Consegna richiesta ad Antigravity

1. Correggere C01 e ottenere build/XCTest iOS sullo stesso commit.
2. Implementare motore unico e normalizzazione condivisa (C02/C05/C07), senza propagare il cumulato errato di C03.
3. Completare diagnosi idrica e semantica UI (C06/C08); distinguere proxy canopy da copertura fisica.
4. Correggere la rappresentazione della mappa e degli stati indisponibili.
5. Eseguire test operativi e pubblicare evidenze riferite a SHA, piattaforma e data, distinguendole dalla validazione micologica sul campo.
6. Aggiornare documenti dopo la verifica, lasciando il caso storico aperto se non ricostruibile.

Non introdurre bonus rugiada o tarature per ottenere un numero desiderato. La ricerca rugiada/VPD resta opzionale e separata dalla bonifica.

## Riferimenti al sorgente esatto

- [iosApp/MycoIOS/DomainAdapters/OpenMeteoDomainMapper.swift](https://github.com/naturewhisp/myco/blob/67b5362a78480ff647d8d3259c34cba35f17bfeb/iosApp/MycoIOS/DomainAdapters/OpenMeteoDomainMapper.swift)
- [app/src/main/java/github/naturewhisp/myco/ui/viewmodel/MushroomViewModel.kt](https://github.com/naturewhisp/myco/blob/67b5362a78480ff647d8d3259c34cba35f17bfeb/app/src/main/java/github/naturewhisp/myco/ui/viewmodel/MushroomViewModel.kt)
- [core/src/commonMain/kotlin/github/naturewhisp/myco/core/MycoAnalysisEngine.kt](https://github.com/naturewhisp/myco/blob/67b5362a78480ff647d8d3259c34cba35f17bfeb/core/src/commonMain/kotlin/github/naturewhisp/myco/core/MycoAnalysisEngine.kt)
- [core/src/commonMain/kotlin/github/naturewhisp/myco/core/MycoAlgorithms.kt](https://github.com/naturewhisp/myco/blob/67b5362a78480ff647d8d3259c34cba35f17bfeb/core/src/commonMain/kotlin/github/naturewhisp/myco/core/MycoAlgorithms.kt)
- [core/src/commonMain/kotlin/github/naturewhisp/myco/core/EnvironmentalWindows.kt](https://github.com/naturewhisp/myco/blob/67b5362a78480ff647d8d3259c34cba35f17bfeb/core/src/commonMain/kotlin/github/naturewhisp/myco/core/EnvironmentalWindows.kt)
- [iosApp/MycoIOS/Data/OverpassClient.swift](https://github.com/naturewhisp/myco/blob/67b5362a78480ff647d8d3259c34cba35f17bfeb/iosApp/MycoIOS/Data/OverpassClient.swift)
- [iosApp/MycoIOS/App/MycoViewModel.swift](https://github.com/naturewhisp/myco/blob/67b5362a78480ff647d8d3259c34cba35f17bfeb/iosApp/MycoIOS/App/MycoViewModel.swift)
- [app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt](https://github.com/naturewhisp/myco/blob/67b5362a78480ff647d8d3259c34cba35f17bfeb/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt)
- [app/src/test/java/github/naturewhisp/myco/MindinoHydrologyAndC1ContinuityTest.kt](https://github.com/naturewhisp/myco/blob/67b5362a78480ff647d8d3259c34cba35f17bfeb/app/src/test/java/github/naturewhisp/myco/MindinoHydrologyAndC1ContinuityTest.kt)
- [core/src/commonMain/kotlin/github/naturewhisp/myco/core/HeatmapEngine.kt](https://github.com/naturewhisp/myco/blob/67b5362a78480ff647d8d3259c34cba35f17bfeb/core/src/commonMain/kotlin/github/naturewhisp/myco/core/HeatmapEngine.kt)
- [app/src/main/java/github/naturewhisp/myco/ui/screens/MapScreen.kt](https://github.com/naturewhisp/myco/blob/67b5362a78480ff647d8d3259c34cba35f17bfeb/app/src/main/java/github/naturewhisp/myco/ui/screens/MapScreen.kt)
- [AGENTS.md](https://github.com/naturewhisp/myco/blob/67b5362a78480ff647d8d3259c34cba35f17bfeb/AGENTS.md)
- [docs/FUTURE_DEVELOPMENTS_ANALYSIS.md](https://github.com/naturewhisp/myco/blob/67b5362a78480ff647d8d3259c34cba35f17bfeb/docs/FUTURE_DEVELOPMENTS_ANALYSIS.md)

