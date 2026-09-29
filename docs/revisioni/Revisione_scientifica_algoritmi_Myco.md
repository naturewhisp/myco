# Myco — Revisione scientifica e numerica degli algoritmi di previsione micologica

**Data:** 22 settembre 2026  
**Repository:** https://github.com/naturewhisp/myco  
**Branch esaminato:** `master`  
**Commit:** `99bd52f8d2565aeb7f16978485d1e84498741a65`  
**Data del commit:** 21 settembre 2026, 22:44:47 +02:00  
**Oggetto:** solidità scientifica, correttezza delle formule, anomalie numeriche, coerenza Android/iOS/mappa e specifiche di miglioramento.  
**Intervento effettuato:** revisione; nessuna modifica al repository remoto o agli algoritmi.  
**Precisazione progettuale:** indice euristico come prodotto autonomo; probabilità calibrata come evoluzione opzionale. Questa precisazione non costituisce una nuova revisione del codice.

## 1. Valutazione complessiva

**Il risultato attuale è un indice euristico di favorevolezza ambientale, non una probabilità di raccolta scientificamente calibrata.** Il codice combina variabili ecologicamente pertinenti, ma attribuisce loro pesi, soglie, latenze e trasformazioni non accompagnati da una stima statistica riproducibile e da una validazione indipendente del risultato finale.

Questo non rende inutile il progetto: pioggia antecedente, temperatura, disponibilità idrica, habitat, ospiti vegetali e stagionalità sono una base sensata per un sistema di supporto. Non consente però di interpretare un risultato di `70%` come «sette uscite riuscite su dieci». Manca anche una definizione operativa dell'evento: presenza del fungo nel sito, fruttificazione, ritrovamento durante una visita o raccolta di una quantità minima sono obiettivi differenti.

Sono presenti inoltre **difetti deterministici**, separati dalla questione della validazione:

- il ramo razionale dichiarato CTMI è irraggiungibile;
- un aumento della pioggia da 17,49 a 17,51 mm può far passare il risultato da 68 a 26, a parità di punteggio meteo;
- il raggruppamento delle piogge perde quantità degli eventi;
- le transizioni di fase introducono salti consistenti;
- la modalità `WEATHER_ONLY` non è applicata uniformemente a scheda e outlook;
- Android e iOS eseguono modelli differenti;
- la mappa può visualizzare un indice di 72 anche con punteggio meteo nullo;
- parti dell'analisi temporale conservano indici fissi incompatibili con il nuovo storico di 28 giorni.

**Scelta di prodotto raccomandata per Myco:** sviluppare e validare un **indice di favorevolezza ambientale**, utile per confrontare luoghi e giornate. Questo può essere un prodotto completo e definitivo: non è obbligatorio convertirlo in una probabilità di raccolta. L'eventuale probabilità calibrata è un percorso separato, da attivare soltanto con dati adeguati e un beneficio dimostrato per l'utente.

**Priorità consigliata:** correggere semantica, difetti numerici e coerenza delle pipeline; poi migliorare covariate e validare l'utilità comparativa dell'indice. Aggiungere ulteriori correttivi non stimati prima di questi interventi aumenterebbe soprattutto la complessità. Chiamare il risultato «indice» non elimina i difetti né sostituisce la validazione sul campo.

## 2. Metodo, copertura e limiti della revisione

La revisione ha seguito la catena acquisizione → aggregazione → risposta ambientale → habitat → fase fenologica → punteggio → outlook/mappa. Sono stati esaminati il motore Android, il core Kotlin condiviso, gli adattatori iOS, repository meteo/habitat/SPUN, cataloghi, configurazioni, test scientifici e documentazione di supporto.

Le evidenze sono classificate così:

| Sigla | Significato |
|---|---|
| C | Comportamento dimostrato dal codice del commit esaminato |
| N | Controesempio numerico eseguito su una trascrizione delle formule |
| S | Valutazione scientifica supportata da fonte primaria consultata |
| V | Ipotesi, provenienza o prestazione che richiede verifica ulteriore |

**Esecuzione:** tentato `bash gradlew :core:allTests --console=plain`. Il wrapper si è fermato scaricando `gradle-9.7.1-bin.zip` con `Network is unreachable`. Non sono quindi attestati build, suite JUnit, test iOS o comportamenti su dispositivo. Le riproduzioni numeriche sono state eseguite in Python e confrontate con i rami Kotlin pertinenti; non equivalgono all'esecuzione dell'app. L'appendice contiene il codice riproducibile.

**Limiti scientifici:** non è stata svolta una campagna sul campo, non sono disponibili esiti indipendenti delle uscite né i GeoTIFF originali richiamati dallo script SPUN. Il preprint “Predicting porcini” è stato rintracciato, ma l'accesso al testo integrale è risultato bloccato; i suoi coefficienti e le finestre specifiche non sono considerati verificati in questa revisione. Non viene stimata un'accuratezza predittiva del prodotto.

## 3. Ricostruzione del calcolo effettivo

### 3.1 Android, configurazione di produzione

`MushroomViewModel.recalculateForSpecies()` usa esplicitamente `EcologicalWeightsConfig.PHENOLOGICAL`.

1. Stima la copertura di chioma da conteggi OSM e, per alcune specie, dalla categoria ecologica.
2. Trasforma temperatura, pioggia e umidità con `applyCanopyBuffering()`.
3. Convolve le piogge antecedenti con un kernel a picco unitario e una compensazione di umidità profonda.
4. Forma il meteo `W`, arrotondato a intero, con massimi di 40 punti pioggia, 30 temperatura, 15 umidità e 15 shock termico.
5. Calcola habitat `H`, quota `A`, stagione `S`, versante `T` e fase `Φ`.
6. Applica la trasformazione Weibull `h(H,A)` e il prodotto finale.

```text
k(τ) = exp[α · (log(τ / τpicco) − (τ / τpicco − 1))]
Reff = compensazioneSuolo · Σ pioggiaSottochioma(t − τ) · k(τ)
W = round(clamp(Rscore + Tscore + Uscore + Shock, 0, 100))
h = 1 − exp[−(Heff · A / σ)^2,5],  σ = clamp(0,35 · strictness, 0,05, 0,50)
Q = 100 · (W / 100)^1,2 · H · A · S · T · h · Φ
C(Q) = Q                                           se Q ≤ 70
C(Q) = 70 + 22 · tanh((Q − 70) / 22)                se Q > 70
output = trunc(C(Q))
```

Per i saprotrofi `Heff = max(H, 0,85)` nel solo hurdle; il fattore esterno `H` resta presente. La fase `Φ` usa la pioggia **grezza**, mentre `Reff` usa quella corretta sottochioma. Il termine meteo è una somma compensativa: una componente nulla non annulla automaticamente il risultato.

La trasformazione `C` è continua, monotona e ha raccordo con derivata uno alla soglia. L'uso di `tanh` non è di per sé un errore numerico. Il problema è chiamarla **calibrazione probabilistica** senza dati di calibrazione: fissare un asintoto a 92 non misura l'incertezza.

### 3.2 Core condiviso e iOS

`MycoAnalysisEngine` usa `MycoAlgorithms.weatherScore()` con finestra pluviometrica rettangolare, non il modello fenologico Android. Mancano nel dominio condiviso minime e massime giornaliere; la penalità notturna usa la minima delle **temperature medie**. Mancano nella pipeline iOS convoluzione avanzata, fase moltiplicativa, hurdle e buffering di chioma equivalenti. L'API iOS chiede 14 giorni passati; Android ne chiede 28.

### 3.3 Mappa

`HeatmapGenerator` e `HeatmapEngine` trasformano ricchezza EcM e densità ifale in un potenziale biologico, poi applicano:

```text
M = 0,60 + 0,60 · clamp(W/100, 0,2, 1) · clamp(S, 0,3, 1) · clamp(A, 0,4, 1)
indiceMappa = clamp(potenzialeBiologico · M, 0, 100)
```

Non è la formula della scheda puntuale. Meteo, stagione e quota vengono propagati dal centro della mappa; non sono ricalcolati localmente per ogni pixel.

## 4. Registro dei problemi

Priorità: **P1** = da risolvere prima di presentare il risultato come previsione quantitativa affidabile; **P2** = affidabilità e robustezza da integrare nella revisione successiva. Non sono stime di rischio sanitario né gravità di sicurezza informatica.

| ID | Priorità | Evidenza | Problema |
|---|---|---|---|
| F01 | P1 | C/S | Percentuale senza evento definito e calibrazione empirica |
| F02 | P1 | C/N | CTMI irraggiungibile e parametri problematici |
| F03 | P1 | C/N | Reset della buttata a soglia 70% e discontinuità delle piogge |
| F04 | P1 | C/N | Cluster pluviometrici con perdita della quantità |
| F05 | P1 | C/N | Moltiplicatore di fase discontinuo e segnali idrici incoerenti |
| F06 | P1 | C/N | Fattori limitanti aggirabili dalla somma meteo |
| F07 | P1 | C/S | Umidità del suolo presentata come van Genuchten senza modello idraulico |
| F08 | P1 | C | Conteggio OSM trattato come copertura, habitat e area basimetrica |
| F09 | P1 | C | Bonus ospite non specifico, obsoleto al cambio specie; cache senza raggio |
| F10 | P1 | C/S/V | SPUN: interpretazione biologica e provenienza insufficienti |
| F11 | P1 | C/N | Heatmap incompatibile con il punteggio puntuale |
| F12 | P1 | C | Divergenza Android/iOS e test di parità riferiti al legacy |
| F13 | P1 | C/N | `WEATHER_ONLY` diversa tra scheda e outlook |
| F14 | P1 | C | Indici temporali, date, fusi e cambio mese incoerenti |
| F15 | P1 | C | Dati assenti e serie incomplete trattati come valori validi |
| F16 | P1 | C/S | Neve trattata come pioggia disponibile; manca il tempo di emissione |
| F17 | P2 | C/N | Memoria idrica dipendente dalla lunghezza dello storico |
| F18 | P2 | C/S | Buffering di chioma e parametri di specie non stimati |
| F19 | P1 | C | Test sintetici presentati come validazione empirica |
| F20 | P2 | C | Quantizzazione, contratti numerici e spiegazioni non coerenti |

## 5. Rilievi dettagliati e soluzioni implementabili

### F01 — Il punteggio non è una probabilità calibrata

**Localizzazione:** [dailyGrowthProbability e trasformazione finale][K-prob], [configurazione dei pesi][K-config], [modello hurdle][K-hurdle].

**Problema.** Pesi 40/30/15/15, esponente 1,2, Weibull con forma 2,5, soglie 70/92 e fattori H/A/S non sono accompagnati da fitting, likelihood, coefficienti con incertezza, dataset di training e test indipendente. La moltiplicazione di indici di idoneità non li trasforma in probabilità. Habitat e quota compaiono sia direttamente sia nell'hurdle: non è algebra sbagliata, ma la penalizzazione ripetuta richiede stima o una motivazione verificata.

Il lavoro di de-Miguel et al. usa un primo stadio logistico stimato e un secondo stadio di produzione condizionata, per una resa annuale. Non giustifica automaticamente la Weibull scelta nel codice né una probabilità giornaliera di raccolta. [Fonte primaria: de-Miguel et al. 2014][S-demiguel].

**Soluzione immediata.** Rinominare il risultato `suitabilityScore`/«Indice di favorevolezza, 0–100», dichiarando che non è calibrato. Distinguere lo stato di conoscenza da un valore basso: dati insufficienti → risultato non stimabile o esplicitamente parziale.

**Evoluzione statistica opzionale, se si decide di offrire una probabilità.** Definire specie, area, intervallo di visita, sforzo di ricerca ed evento osservato. Addestrare un modello binomiale per il ritrovamento, oppure un modello esplicito di presenza/fruttificazione/rilevamento quando esistono visite ripetute e dati sufficienti. Calibrare su dati separati e valutare Brier score, log-loss e curva di affidabilità.

**Accettazione.** Nessun campo chiamato `probability` è esposto come percentuale di successo senza `targetDefinition`, `modelVersion`, dominio di validità e rapporto di validazione. La versione euristica resta identificabile e confrontabile.

### F02 — CTMI: ramo mai eseguito e correzione del solo segno insufficiente

**Localizzazione:** [ctmi(), righe 1330–1349][K-ctmi]; profilo `boletus_edulis` nel [catalogo][K-species].

**Dimostrazione.** Nell'intervallo `Tmin < T < Tmax`, il numeratore `(T − Tmax)(T − Tmin)²` è negativo. Il codice valuta il quoziente soltanto se `den > 0`, producendo un valore negativo, che viene poi escluso da `v in 0..1`. Se `den < 0`, salta direttamente il calcolo. Perciò **nessun valore interno usa il CTMI razionale**: viene sempre utilizzato il fallback.

Con i parametri edulis `Tmin=9`, `Topt=14`, `Tmax=24`:

| Temperatura | Quoziente razionale del codice | Risultato effettivo del fallback |
|---|---:|---:|
| 12 °C | 1,080000 | 0,929516 |
| 14 °C | 1,000000 | 1,000000 |
| 16 °C | 0,980000 | 0,946573 |
| 20 °C | 0,691429 | 0,593296 |

Il denominatore con questi parametri si annulla a circa **10,6667 °C**, all'interno dell'intervallo. Cambiare semplicemente `den > 0` in `den != 0` non rende valida la curva: rimangono singolarità, valori negativi e valori sopra uno. Il fallback ha esponente `b=0,5`; vicino a Tmin cresce come una radice quadrata, quindi la derivata diverge e la dichiarazione di Lipschitz-continuità non è corretta.

**Soluzione.** Scegliere e versionare una sola famiglia termica. Se si mantiene Rosso, validare preventivamente il dominio dei parametri e verificare l'intera curva con riferimenti indipendenti. In alternativa adottare esplicitamente una curva cardinale regolare che ammetta l'optimum scelto, con parametri da stimare; non mascherarla da fallback automatico di un altro modello. Il suo impiego nella fruttificazione va validato separatamente dalla crescita microbica.

**Accettazione.** Test della curva su griglia fitta e bordi; caso nominale valido che esegue realmente il ramo razionale; rifiuto esplicito di tuple singolari; test del valore a 16 °C e dell'optimum; nessuna commutazione silenziosa fra modelli. Il test attuale che controlla solo limiti, optimum e assenza di NaN non intercetta l'errore.

### F03 — Una pioggia quasi identica può cancellare una buttata

**Localizzazione:** [estrazione e selezione degli eventi][K-events], in particolare `resolveActiveRainTrigger()`.

**Riproduzione N.** Edulis, indice bersaglio 24; pioggia di 25 mm all'indice 13; seconda pioggia all'indice 23; temperatura media/minima/massima costanti 16/14/18 °C, UR 85%; niente suolo, SPUN o buffering; H=A=S=T=1.

| Pioggia recente | W | Evento selezionato | Φ | Output |
|---|---:|---|---:|---:|
| 17,49 mm | 73 | 11 giorni prima | 1,0000 | 68 |
| 17,51 mm | 73 | 1 giorno prima | 0,3875 | 26 |

La differenza è **42 punti per 0,02 mm**, perché la condizione `recent < earlier * 0.70` cambia ramo. Non dipende dall'arrotondamento del meteo.

La regola impone inoltre che una nuova pioggia sufficientemente grande azzeri la fase precedente. Non è un risultato derivabile dalla legge del minimo: una nuova precipitazione non dimostra che gli sporocarpi già sviluppati siano scomparsi. Nel repository la regola è protetta come «invariante», ma rimane un'ipotesi di modello.

**Soluzione.** Mantenere coorti/eventi con età distinte e uno stato di disponibilità degli sporocarpi; aggiungere nuova attivazione senza eliminare quella già presente. Modellare separatamente mortalità, senescenza e danni meteorologici, se supportati da dati. Per una correzione transitoria, rendere continua la fusione degli eventi e togliere il reset assoluto non validato.

**Accettazione.** Sweep fitto attorno al 70% e alle soglie 12/18/25 mm, usando il percorso completo con Φ. I test non devono imporre monotonicità universale della pioggia — ristagno e danni possono ridurre la fruttificazione — ma vietare salti dovuti solo alla classificazione numerica.

### F04 — Il clustering perde pioggia e dipende dalla segmentazione

**Localizzazione:** [clusterRainEvents ed extractCandidateRainEvents][K-events].

`clusterRainEvents()` conserva il primo evento incontrato e scarta quelli entro due indici, senza sommare né ricostruire l'evento. Con 30 mm al giorno 13 e 15 mm al giorno 14, il cluster risultante è `(14, 15 mm)`: non rappresenta i 45 mm del periodo. Questo modifica la verifica di evento saturante e la protezione della buttata precedente.

L'estrazione crea inoltre candidati sovrapposti usando finestre di tre giorni, talvolta retrodatandoli a `i-2`, anche se la maggior parte della pioggia è caduta al termine. La soglia in tre giorni usa valori giornalieri non inferiori a zero ma non separa eventi fisici indipendenti.

**Soluzione.** Prima segmentare la serie originale in intervalli non sovrapposti, secondo una regola dichiarata di separazione asciutta; poi calcolare `startDate`, `endDate`, pioggia lorda/netta, copertura e baricentro temporale. La somma deve avvenire sulle osservazioni originali, non sulle somme mobili già sovrapposte. Se si passa a un modello idrico continuo, evitare del tutto eventi rigidi come motore numerico.

**Accettazione.** Conservazione della massa 30+15=45; nessun doppio conteggio per 6+6+6; indipendenza dall'ordine di input; casi con giorni mancanti e pioggia distribuita su 1, 2 e 3 giorni. La data di attivazione può dipendere dalla distribuzione temporale, ma tale dipendenza deve essere esplicita.

### F05 — Salti fra fasi e discordanza fra acqua grezza e netta

**Localizzazione:** [evaluateStageFromTrigger][K-events], [calculateDailyOutlooks][K-outlook], [ricalcolo Android][K-vm].

Con un singolo evento edulis, Φ passa da 0,791496 al giorno 8 a 0,989104 al giorno 9; dal giorno 15 al 16 passa da 0,971111 a 0,508605. A W fissato a 85, il solo cambio di fase produce rispettivamente **65→80** e **79→41**. Questi sono confronti isolati del moltiplicatore, non l'affermazione che ogni previsione reale compia lo stesso salto.

Un altro controesempio: pioggia costante di 5,9 mm/giorno per 29 giorni non supera mai 12 mm/giorno né 18 mm/3 giorni, quindi Φ=0,25 e «in attesa di precipitazioni». A 6,0 mm/giorno Φ=0,425. Nella riproduzione W=85 in entrambi i casi, mentre il risultato passa da 20 a 34.

Il meteo utilizza dati sottochioma, la fase quelli grezzi; la latenza del kernel viene modificata dal freddo, quella della fase resta invariata. Il sistema può così descrivere stadi biologici incompatibili con il segnale usato per la disponibilità idrica.

**Soluzione.** Una funzione di maturazione continua, con transizioni raccordate o uno stato dinamico, deve produrre il valore numerico; le etichette di fase vanno derivate da quel valore/stato e non modificarlo. Usare un unico oggetto di forzanti e una sola convenzione per pioggia, freddo e tempo termico. Non basta applicare un secondo smoothstep all'output finale.

**Accettazione.** Continuità ai raccordi di fase e test su pioggia debole persistente. Tutte le pipeline devono ricevere lo stesso stato fenologico calcolato una sola volta.

### F06 — Le penalità fisiche non limitano necessariamente l'output finale

**Localizzazione:** [calculateWeatherScore][K-weather], [soilMoistureScoreSmooth][K-soil].

Temperatura nulla nella curva non implica W nullo: pioggia e umidità possono contribuire 40+15=55 punti. Con altri fattori favorevoli e Φ=1, la formula finale produce circa **48**. L'esempio isola la struttura additiva; non dimostra da solo che qualunque freddo renda biologicamente impossibile trovare sporocarpi persistenti.

La risposta pedologica pesa soltanto il 60% della componente umidità di 15 punti, cioè al massimo **9 punti di W**. Anche abbassandola da 1 a circa 0,17, la perdita è di circa 7,5 punti meteo; un test che verifica solo la curva del suolo non prova che l'output finale sia fortemente soppresso. Lo stesso vale per «gelata letale»: l'inibizione 0,3 è applicata alla componente termica, non all'intero processo.

**Soluzione.** Distinguere tasso di formazione di nuovi sporocarpi, stock già presente e probabilità di rilevamento. Se esistono limiti fisiologici dimostrati, applicarli al processo pertinente e con durata dell'esposizione; non inventare un gate universale a partire da una temperatura istantanea. Nel modello euristico dichiarare che la somma è compensativa e non chiamarla legge del minimo.

**Accettazione.** Test end-to-end di stress prolungato, recupero, ristagno e persistenza; soglie biologiche documentate per specie. Verificare l'output, non soltanto il termine intermedio.

### F07 — Il modello di umidità non implementa van Genuchten

**Localizzazione:** [soilMoistureScoreSmooth][K-soil], [AGENTS.md][K-agents].

Il codice applica smoothstep su soglie fisse di contenuto volumetrico. Non contiene curva di ritenzione, pressione/potenziale matriciale, porosità, contenuto residuo/saturo, parametri α/n/m né conducibilità. È quindi una risposta euristica all'umidità, non un'implementazione della relazione idraulica citata. La formulazione di van Genuchten riguarda relazioni idrauliche parametrizzate del suolo. [Fonte primaria][S-vg].

Un identico contenuto volumetrico non corrisponde alla stessa disponibilità idrica o aerazione in suoli diversi. Inoltre l'istruzione «oltre 0,44 → ≤0,15» non corrisponde al codice superficiale: a 0,44 la risposta è 0,50; il valore 0,15 si raggiunge a 0,52.

**Soluzione immediata.** Correggere nome/commenti e separare le ipotesi dall'evidenza. **Evoluzione:** usare proprietà del suolo e relativa incertezza per ricavare saturazione efficace, disponibilità idrica e porosità riempita d'aria. Integrare durata del ristagno e drenaggio; evitare coefficienti pedologici universali per tutti gli orizzonti e tutte le specie. Mancanza di tessitura/porosità deve abbassare l'affidabilità del modello, non introdurre valori esatti fittizi.

**Accettazione.** Test con differenti tipologie di suolo, controllo delle unità, casi di saturazione e deficit, tracciabilità dei parametri; confronto su sensori o osservazioni indipendenti prima di attribuire significato fisiologico alle soglie.

### F08 — Il numero di oggetti OSM non misura copertura né densità forestale

**Localizzazione:** [fetchHabitat][K-repo], [evaluateSpeciesHabitat][K-habitat], [stima chioma nel ViewModel][K-vm].

La dimensione di `habitat.elements` viene tradotta in categorie di copertura e idoneità. Un unico poligono forestale molto esteso può ricevere un valore inferiore a molti piccoli poligoni dello stesso bosco. Suddividere la cartografia modifica il risultato senza cambiare l'ambiente. La prossimità a geometrie OSM non dimostra che il punto sia nel bosco. Assenza di elementi, inoltre, non prova un prato: con `forestCount=0` i saprotrofi ricevono H=0,90 anche se il contesto è ignoto o incompatibile.

La conversione `G = 50 · C^1,15` trasforma una copertura già presunta in area basimetrica; quest'ultima dipende da diametro e numero degli alberi, non è determinata univocamente dalla copertura. Il coefficiente non dispone nel codice di una calibrazione locale.

**Soluzione.** Usare geometrie di superficie, unione e intersezione con area bersaglio; distinguere punto interno, margine e vicinanza. Per la chioma usare una misura/raster documentato. Ricavare area basimetrica da inventari o stimarla con un modello validato, conservando l'incertezza; altrimenti omettere il fattore. Distinguere `KNOWN_SUITABLE`, `KNOWN_UNSUITABLE`, `UNKNOWN`.

**Accettazione.** Stesso bosco descritto da 1 o 20 poligoni → stessa frazione di copertura; zero dati OSM → unknown; prato e superficie urbanizzata non ricevono lo stesso prior saprotrofico. Il raggio di valutazione deve essere parte del target, non una preferenza grafica che cambia silenziosamente la probabilità.

### F09 — Bonus ospiti non specifico e riutilizzo del risultato della specie precedente

**Localizzazione:** [fetchSpecificHabitatBonus][K-repo], `selectSpecies()` e `lastSpecificForestCount` in [MushroomViewModel][K-vm].

La query accetta elementi con `leaf_type=broadleaved|needleleaved` in alternativa al genere desiderato. Il conteggio positivo attiva un bonus da «albero ospite» anche senza aver identificato un genere compatibile. `selectSpecies()` cambia specie e ricalcola usando `lastSpecificForestCount`, ottenuto con la query della specie precedente.

Le chiavi cache di habitat e bonus comprendono coordinate, e nel secondo caso specie, ma **non il raggio** letto da `cacheManager.radius`. Richieste con raggio diverso possono usare lo stesso risultato cache.

**Soluzione.** Conservare evidenze vegetazionali strutturate, filtrando compatibilità per specie; foglia larga/ago è un'informazione generica, non una conferma dell'ospite. Al cambio specie, rivalutare dati completi o recuperare il sottoinsieme corretto prima di attribuire il bonus. Includere raggio, versione della query e tassonomia nella chiave cache.

**Accettazione.** Cambio fra specie con ospiti diversi non eredita evidenza incompatibile; query contenente soltanto `leaf_type` non attiva conferma di genere; test cache 500/1500/5000 m; aggiornamenti asincroni non sovrascrivono la selezione più recente.

### F10 — Uso SPUN non sufficientemente giustificato e asset non riproducibile

**Localizzazione:** [modulazione meteo][K-weather], [habitat][K-habitat], [SpunDataManager][K-spun], [build_spun_asset.py][K-buildspun].

La ricchezza EcM è una previsione di biodiversità, non un censimento locale di porcini né attività del micelio nella giornata. L'atlante documenta risoluzione, unità per area e incertezza, che il binario del progetto non conserva. [Documentazione primaria SPUN][S-spun].

Il prodotto pubblico di densità ifale SPUN consultato riguarda **micorrize arbuscolari (AM)**. Non è intercambiabile con biomassa di specie ectomicorriziche o saprotrofe. [SPUN, produttore del dataset][S-am]. Il file locale `hyphal_density_m_cm3_Classified_mean.tif` non è accompagnato nel repository da DOI/checksum/metadati sufficienti a confermare l'identità: **la corrispondenza esatta resta da verificare**, non va data per certa dal solo nome.

Nel codice la densità aumenta il punteggio pluviometrico di 6 punti e abbassa la soglia dello shock; nella heatmap domina i saprotrofi. Non è documentata una stima di questi effetti. Il campionamento usa l'80° percentile dei valori positivi nel raggio: una scelta ottimistica che va distinta dal valore atteso al punto.

**Soluzione.** Mettere i correttivi ifali fuori dal modello operativo finché provenienza e valore predittivo non siano dimostrati, conservandoli eventualmente come covariate sperimentali. Aggiungere manifest con DOI/versione, gilda, unità, risoluzione, CRS, nodata, checksum, licenza e incertezza. Separare zero biologico da nodata. Lo script attuale pulisce dopo interpolazione e quantizza a byte: verificare che nodata non contamini la bilineare, e misurare saturazione/clipping a 12,75 m/cm³. Non è stato dimostrato che tale clipping si verifichi nei dati originali.

**Accettazione.** Ricostruzione dell'asset da fonti versionate; unit test dei nodata; statistiche dell'errore di quantizzazione; ablation test fuori campione con/senza SPUN e per gilda. Le soglie 2,5/5,0 devono essere stimate o esplicitamente euristiche.

### F11 — La heatmap rappresenta una quantità diversa

**Localizzazione:** [HeatmapGenerator][K-mapandroid], [HeatmapEngine][K-mapcore].

Con potenziale biologico 100, W=0, S=A=1, la mappa restituisce **72**. Con W=0 e valori S/A inferiori ai floor, resta **61,44** prima del troncamento. La formula della scheda, con W=0, restituisce zero. L'overlay non applica lo stesso hurdle, Φ o limite asintotico e può raggiungere 100.

Propagare meteo e quota del centro a un raggio di decine di chilometri introduce precisione spaziale apparente, soprattutto sui rilievi. I pixel SPUN non diventano previsioni micologiche locali solo perché la rasterizzazione è fine.

**Soluzione immediata.** Etichettare la mappa «Potenziale ambientale relativo», con legenda separata dall'output giornaliero, oppure sospendere l'uso di classi di probabilità. **Soluzione completa:** produrre un risultato numerico per cella con lo stesso motore della scheda e input locali documentati; applicare colore e trasparenza soltanto dopo. Esplicitare risoluzione effettiva, aggregazione e aree unknown.

**Accettazione.** Pixel e scheda con identici input coincidono prima del rendering se dichiarano lo stesso target; W=0 non produce classe favorevole nello stesso modello; nessun incremento numerico introdotto solo per rendere visibile la mappa.

### F12 — Android e iOS non condividono il modello scientifico di produzione

**Localizzazione:** [core MycoAlgorithms][K-core], [MycoAnalysisEngine][K-engine], [dominio core][K-domain], [mapper iOS][K-iosmapper], [test parità][K-parity].

I test `CrossPlatformScientificParityTest` verificano soprattutto curve legacy, formula DEFAULT e palette. Il test della probabilità confronta due percorsi di cui quello Android DEFAULT delega già al core. Non dimostra che la produzione Android PHENOLOGICAL coincida con iOS.

Il core perde Tmin/Tmax; `minOf { avgTemp }` non sostituisce le minime notturne. Parametri fenologici e basimetrici Android non sono rappresentati nel catalogo core. I due sistemi possono quindi dare risultati diversi senza alcun fallimento dei test di parità esistenti.

**Soluzione.** Trasferire un motore revisionato, dominio ambientale e catalogo versionato in `commonMain`. Adattatori Android/iOS devono soltanto mappare dati e formattare risultati. Non copiare nel core tutti i difetti Android in nome della parità: prima definire la specifica corretta, poi mantenere un adapter legacy solo per confronti storici.

**Accettazione.** Golden fixture identica da JSON meteo grezzo a output completo sulle due piattaforme, includendo minime/massime, missingness, fase, as-of, modello, outlook e valori della mappa. Soglia numerica in double prima della formattazione; differenze tollerate e motivate solo dove inevitabili.

### F13 — `WEATHER_ONLY`: scheda e outlook non seguono la stessa modalità

**Localizzazione:** [ricalcolo e chiamata outlook][K-vm], [calculateDailyOutlooks][K-outlook].

Nella scheda Android la modalità forza H=A=S=T=1. L'outlook riceve H e T già neutralizzati, ma ricalcola quota e stagione dalla quota reale e dal mese. La modalità non è un parametro di `calculateDailyOutlooks()`.

A W=85 e Φ=1, la scheda meteorologica vale 81. Un outlook con A=0,4 e S=0,1, lasciando H=T=1, vale 2 con il modello fenologico: differenza dovuta a configurazione, non al meteo.

**Soluzione.** Definire `CalculationMode` nel motore condiviso, applicandolo una sola volta ai contributi per ogni data. Chiarire se «solo meteo» include la risposta specie-specifica e la fase: oggi sì, ma il nome non specifica questa scelta.

**Accettazione.** Il primo giorno dell'outlook coincide con la scheda per ogni modalità, specie, quota e mese. Il confronto deve includere fattori realmente passati al prodotto.

### F14 — Cronologia, date e previsioni non completamente coerenti

**Localizzazione:** [analyzeFutureTrend][K-trend], [outlook Android][K-outlook], [ViewModel Android][K-vm], [ViewModel iOS][K-iosvm], [engine core][K-engine].

1. `analyzeFutureTrend()` assume `todayIndex=14`. Con lo storico Android di 28 giorni, legge indici 15–19, cioè giorni precedenti a oggi, descrivendoli come futuro.
2. L'outlook calcola la stagionalità una sola volta col mese corrente. Attraversando un cambio mese, non usa il mese del giorno bersaglio. Anche il core conserva `seasonality` e `terrainModifier` per l'intera previsione.
3. Android cerca oggi con il fuso del dispositivo, non con `weather.timezone`; se non lo trova ripiega su `min(28,lastIndex)`. Dati vecchi possono quindi essere associati a «oggi» senza corrispondenza di data. iOS usa direttamente `min(14,lastIndex)`.
4. Le latenze sono differenze di indice, non differenze di calendario: un giorno mancante comprime artificialmente il tempo.
5. La curva altimetrica salta da 0,4 fuori tolleranza a 0,6 sul limite; per edulis 299,9→300 m cambia bruscamente A. La stagione è a gradini 0,1/0,6/1,0, non continua come suggeriscono alcuni commenti.

**Soluzione.** `LocalDate` e `ZoneId` della località, ricerca esplicita del giorno, griglia giornaliera completa con missingness. Outlook e riepiloghi devono usare la stessa `targetDate`. Eliminare fallback impliciti che cambiano la data. Versionare la semantica delle finestre. Per stagionalità usare una funzione ciclica del giorno dell'anno stimata per regione/specie; per quota una risposta continua o covariate ecologiche più dirette.

**Accettazione.** Test a mezzanotte con fusi diversi, DST, anno bisestile, cambio mese, cache di uno o più giorni, assenza di oggi, giorno intermedio mancante. Il riepilogo futuro non legge alcun giorno passato. Test ai bordi altimetrici con ±0,1 m.

### F15 — Dato mancante confuso con zero o con assenza ecologica

**Localizzazione:** [processWeatherData][K-aggregate], [WeatherModel][K-weathermodel], [ricalcolo Android][K-vm].

Array più corti di `hourly.time` ricevono default 0 per temperatura, pioggia e UR. Non sono richieste copertura minima né validazione delle unità e dei valori finiti. Le liste dichiarate `List<Float>` non modellano esplicitamente i null possibili nel payload. In `recalculateForSpecies()` la finestra umidità usa `todayIndex-3` senza `max(0,...)`: una serie corta può causare indice negativo. Se Overpass non è disponibile, `habitat?.elements?.size ?: 0` converte unknown in assenza di elementi.

**Soluzione.** DTO nullable, validatore delle serie prima dell'aggregazione, conteggi di osservazioni valide e attese per variabile, policy di copertura e imputazione dichiarata. Distinguere `missing`, `zero`, `notApplicable`, `outsideCoverage`, `stale`. Non assegnare al motore osservazioni fittizie; non rendere «assente» un habitat semplicemente non scaricato.

**Accettazione.** Array vuoti/troncati, null, NaN, infinito, pioggia negativa, UR fuori range e 23/25 ore locali producono uno stato controllato; nessun crash e nessuna probabilità apparentemente completa. Serie incomplete non devono acquisire informazioni ecologiche dal valore di fallback.

### F16 — Precipitazione totale, neve e informazione disponibile al momento della previsione

**Localizzazione:** [API meteo Android][K-api], [aggregazione][K-aggregate], [estrazione eventi][K-events].

Open-Meteo definisce `precipitation` come totale di pioggia, rovesci e neve; il codice la usa come acqua piovana disponibile. Una nevicata può quindi attivare il medesimo percorso fenologico, senza modello di accumulo/fusione. Le variabili suolo dipendono dal prodotto meteorologico; non va attribuita automaticamente l'etichetta ERA5-Land a una risposta `forecast` con scelta automatica dei modelli. [Documentazione ufficiale][S-meteo].

L'aggregazione usa tutta la giornata, comprese ore future se presenti nella previsione. `evaluateGrowthPhase()` considera anche la pioggia del giorno bersaglio, mentre la convoluzione la esclude. Un temporale previsto in serata può cambiare la fase mostrata la mattina: legittimo per un forecast di fine giornata, ambiguo per una valutazione della raccolta «adesso».

**Soluzione.** Separare pioggia liquida, neve equivalente, neve al suolo e fusione; dichiarare la semplificazione se non c'è modello nivologico. Introdurre `issuedAt`, `validTime`, `sourceModel`, `isForecast` e target temporale. Per validazione storica usare le previsioni disponibili al tempo di emissione, non dati corretti a posteriori o meteo futuro osservato.

**Accettazione.** Neve senza fusione non è trattata come identico evento di pioggia; una previsione congelata alle 08:00 non usa run emessi dopo le 08:00; test distinti nowcast/forecast giornaliero. Contratti live o fixture ufficiali devono verificare nomi, profondità e disponibilità delle variabili: la documentazione corrente non basta a dichiarare errati gli alias storici senza provare la risposta API.

### F17 — Memoria idrica, normalizzazione e isteresi dipendono da scelte non esplicite

**Localizzazione:** [kernel e pioggia efficace][K-rain].

`calculateEffectiveRainfall()` usa tutti i giorni antecedenti disponibili, non una finestra fissa di 26 giorni. La compensazione profonda è la media dell'intero passato disponibile. Aggiungere giorni remoti modifica tale media e può modificare anche il risultato relativo alla stessa data. Previsione fatta oggi per domani e ricalcolo domani possono avere memoria diversa per il solo scorrimento del dataset.

Il kernel è normalizzato **al picco**, non alla somma. Con α=4, la somma discreta dei pesi è circa 14,076 per picco 11 giorni e 7,678 per picco 6. A pioggia giornaliera costante, cambiare latenza cambia anche la quantità equivalente accumulata. Non è un errore dimensionale — i pesi sono adimensionali — ma non si può interpretare il risultato come un comune cumulato di pioggia e riusare una soglia tarata per un'altra finestra senza ricalibrazione.

`deepSoilMoistureCompensation()` restituisce 1,10 a θ=0,35, ma 1,00 appena sopra: salto del 9,09% nel moltiplicatore. L'isteresi fredda è un booleano «almeno una minima sotto soglia negli ultimi cinque giorni»: una perturbazione minima o l'uscita del giorno freddo dalla finestra sposta istantaneamente il picco di 1,5 giorni.

**Soluzione.** Definire un kernel con supporto/memoria e significato delle unità espliciti. Se è una media ponderata, normalizzarne l'area; se è un'esposizione cumulata, conservare la massa del kernel e stimare le soglie insieme alla latenza. Non applicare una normalizzazione automatica ai giorni disponibili: farebbe sembrare completa una serie incompleta. Stato idrico e stress termico devono avere inizializzazione, decadimento e spin-up dichiarati.

**Accettazione.** Aggiungere dati precedenti al supporto dichiarato non cambia il risultato; una serie troncata segnala copertura insufficiente. Sweep su θ=0,35 e soglia fredda; verifica di kernel, supporto, unità e massa per tutte le specie.

### F18 — Microclima, gilde e parametri di specie: plausibilità non equivale a stima

**Localizzazione:** [applyCanopyBuffering][K-canopy], [catalogo][K-species], [standDensityResponseUnimodal][K-stand].

Il buffering termico forestale ha supporto osservazionale; lo studio di De Frenne documenta la moderazione degli estremi sottochioma. Non determina però automaticamente gli offset, l'intercettazione e l'aumento di UR scelti in questa app. [Fonte primaria][S-defrenne].

La copertura viene perfino impostata a 0,10 per specie associate a prati/radure: il cambio di specie può così cambiare la forzante microclimatica del medesimo punto senza una nuova osservazione del sito. Sarebbe ammissibile soltanto dichiarando che il target è cambiato da «punto» a «microhabitat presunto della specie nell'area».

Ulteriori criticità: massima termica con ramo distinto sopra 18 °C; nessuna fenologia fogliare; intercettazione non realmente parametrizzata per specie arborea; min/max eventualmente invertite vengono scambiate per ristabilire l'ordine, nascondendo un eccesso di correzione. Le latenze, i fabbisogni pluviometrici e gli optimum di area basimetrica sono costanti senza provenienza a livello di parametro. Una categoria unica SAPROTROPHIC non descrive tutti i substrati e i cicli dei taxa assegnati.

**Soluzione.** Registro dei parametri con fonte, popolazione, variabile misurata, unità, intervallo di incertezza e stato `measured/fitted/expert_prior`. Separare gilda, substrato, ospiti, sito e stadio del ciclo vitale. Offset forestali stimati/validati per contesto e stagione; evitare di correggere due volte effetti già rappresentati nel prodotto meteo. Eseguire analisi di sensibilità e confronti con modello senza buffering.

**Accettazione.** Ogni parametro attivo è tracciabile; nessuna preferenza della specie viene presentata come misura ambientale; termini senza guadagno predittivo indipendente restano sperimentali. Il modello generale «porcino e simbiotici» non va descritto come probabilità di trovare almeno una qualunque specie.

### F19 — Le suite attuali non dimostrano validità scientifica

**Localizzazione:** [PhenologicalInvariantsTest][K-tests], [CrossPlatformScientificParityTest][K-parity], [AGENTS.md][K-agents].

Sono utili test di bounds, regressione e scenari, ma diversi test fissano regole introdotte dal modello come se fossero leggi biologiche. L'invariante del reset 70% consolida F03. Il test di continuità chiama `dailyGrowthProbability()` senza passare il moltiplicatore fenologico: proprio il ramo responsabile dei grandi salti non è incluso nel confronto.

I casi chiamati “ground truth” comprendono serie sintetiche con temperatura/UR costanti, aspettative qualitative e una proiezione futura. `createMindinoHistoricalSeries()` associa l'indice 24 al 18 settembre nel commento ma genera la data `2026-09-25`; l'indice 23 è etichettato `2026-09-24`, non 17 settembre. Il prolungamento genera anche `2026-09-31`, `2026-09-32`, `2026-09-33`. Il test del weekend verifica fase e moltiplicatore, non l'asserzione di probabilità ≥65% descritta nel commento.

Un'uscita senza ritrovamenti non stabilisce un limite numerico sulla probabilità: anche un evento previsto all'80% può non verificarsi. La mancata rilevazione non coincide necessariamente con assenza. [MacKenzie et al., fonte primaria][S-occupancy].

**Soluzione.** Separare `unit`, `numerical`, `scenario_regression`, `historical_observations`, `external_validation`. Preservare le osservazioni reali con provenienza e sforzo; marcare ricostruzioni, imputazioni e scenari. Correggere calendario e dichiarazioni dei test. Rivedere con change record i vincoli scientificamente contestati, invece di abbassare arbitrariamente le soglie per far passare le nuove implementazioni.

**Accettazione.** Dataset indipendente, date valide e identificativi del campione; tutti i test di continuità includono Φ e pipeline reale. Una suite tutta verde non viene pubblicizzata come validazione sul campo. Aggiornare anche AGENTS.md: le sue regole attuali sono parte della specifica da revisionare, non evidenza scientifica esterna.

### F20 — Quantizzazione, contratti numerici e spiegazioni

**Localizzazione:** [meteo][K-weather], [probabilità][K-prob], [fattori][K-factors], [config][K-config].

W è arrotondato e l'output troncato con `toInt()`: l'ultimo passaggio introduce un bias verso il basso inferiore a un punto per valori positivi, il primo perde differenze prima della trasformazione non lineare. Non è la causa dei salti di 42 punti, ma complica sensibilità e calibrazione.

I parametri configurabili non hanno vincoli centralizzati: pesi negativi, finestre negative, soglie termiche coincidenti o `pMax == pKnee` possono rendere le formule invalide. Nel ramo DEFAULT di `dailyGrowthProbability()` non viene inoltrato `growthPhaseMultiplier` al core: il parametro viene ignorato per quel percorso. Va considerato un difetto del contratto legacy, non confuso con la produzione PHENOLOGICAL.

Le schede fattori non descrivono sempre gli stessi input del punteggio: per esempio il suolo visualizzato usa la giornata bersaglio, mentre W ne usa la finestra. La luna è marcata come favorevole pur non entrando nel prodotto; alcune classi qualitative dicono «buttata in corso» da un semplice intervallo di indice.

**Soluzione.** Calcolare in `Double` e arrotondare solo in presentazione. Validare profilo/configurazione all'ingresso. Un `ContributionBreakdown` deve contenere dati, finestra, trasformazioni e contributi effettivamente usati. La luna può restare informazione astronomica, senza attribuzione favorevole non dimostrata. Etichette come «condizioni favorevoli» devono essere distinte da uno stato biologico osservato.

**Accettazione.** Parametri non validi → errore controllato; test di inoltro per ogni wrapper; output raw riproducibile; nessuna spiegazione attribuisce al punteggio un fattore escluso o un'osservazione non disponibile.

## 6. Bilancio delle basi scientifiche

| Componente | Valutazione | Cosa è necessario per l'uso rigoroso |
|---|---|---|
| Pioggia antecedente e idratazione | Covariate pertinenti; kernel e soglie specifiche non validati nel progetto | Dati di risposta per specie/sito, lag stimati, confronto con cumulati semplici |
| Temperatura e minime | Pertinenti; difetto CTMI e perdita delle minime su iOS | Separare crescita, induzione e persistenza; stimare le curve |
| Suolo | Pertinente; risposta attuale euristica | Proprietà pedologiche, misura/incertezza, durata di stress |
| Habitat e ospiti | Fondamentali per una previsione specie-specifica | Geometrie/cover reali, evidenza tassonomica, missingness |
| Struttura forestale | Relazioni empiriche disponibili in contesti specifici | Misura/validazione di G e trasferibilità al contesto italiano |
| Microclima sottochioma | Fenomeno osservato | Correzioni locali validate, input ambientale indipendente dalla specie |
| Modello a due stadi | Famiglia statistica sensata se stimata | Target esplicito, likelihood, dati zero/nonzero e validazione |
| Weibull + prodotto + tanh | Scelta euristica del progetto | Non chiamarla calibrazione senza evidenza empirica |
| SPUN | Informazione geografica potenzialmente utile | Verifica gilda, provenienza, scala, incertezza e ablation |
| Luna | Non contribuisce al calcolo finale | Nessuna etichetta causale favorevole senza evidenza |
| DeepMaxent / massima entropia nei documenti | Non è il motore operativo esaminato | Se sperimentato, distinguere idoneità spaziale e rilevamento giornaliero |

Le raccolte `docs/articoli/` contengono studi, sintesi e materiali con obiettivi molto diversi. Essiccazione o qualità dei funghi durante il trasporto non sono evidenza diretta per stimare il ritrovamento di sporocarpi in un bosco. Le sintesi interne devono rimandare a pagine, tabelle e parametri dei lavori originali; la presenza di una citazione non basta a validare un coefficiente.

**Sul preprint “Predicting porcini”.** L'esistenza del lavoro è stata rintracciata, ma non è stata verificata l'applicazione dei presunti `P(d−26)` e `T(d−20)` leggendo il testo integrale. Nel codice Android non compare comunque una replica esplicita di un modello stimato di quel lavoro: compare una convoluzione su tutto lo storico e una combinazione 75/25 di risposta termica breve/media. Prima di rivendicarne l'implementazione servono versione, equazione, coefficienti e dominio di campionamento. [Lavoro da completare nella verifica bibliografica][S-porcini].

## 7. Architettura e modello proposti

### 7.0 Due percorsi distinti e criteri di scelta

| Aspetto | Percorso A — Indice di favorevolezza, prodotto principale | Percorso B — Probabilità calibrata, evoluzione opzionale |
|---|---|---|
| Domanda dell'utente | Dove e quando le condizioni sono relativamente più favorevoli? | Qual è la possibilità di successo di una ricerca definita? |
| Output | «Favorevolezza ambientale: 72/100» | «Probabilità di ritrovamento: 65%», con target e condizioni espliciti |
| Significato | Punteggio relativo del modello; non frequenza di successo | Frequenza di successo stimata in situazioni comparabili |
| Requisito scientifico | Coerenza ecologica, stabilità numerica e utilità comparativa verificata | Anche calibrazione, dati sugli esiti e protocollo di osservazione adeguato |
| Dati sul campo | Necessari per dimostrare utilità e limiti dell'indice | Necessari per stimare e validare le probabilità, inclusi esiti negativi e sforzo |
| Criterio di completamento | Indice corretto, trasparente e sufficientemente validato per il dominio dichiarato | Modello probabilistico validato e utile per il target dichiarato |
| Obbligatorietà | Percorso raccomandato per Myco | Non richiesto per completare il percorso A |

**Regola decisionale:** mantenere il percorso A se l'esigenza è confrontare condizioni, oppure se i dati non consentono una probabilità difendibile. Attivare B solo dopo aver definito il target, verificato la disponibilità di dati adeguati e stabilito come misurarne il valore aggiunto. Nessun requisito di completamento dell'indice dipende dalla pubblicazione di percentuali.

I percorsi possono condividere dati e componenti. Un indice che ordina bene le situazioni può essere calibrato mediante una trasformazione stimata su dati separati, oppure diventare una covariata di un modello probabilistico. La calibrazione non corregge automaticamente un cattivo ordinamento: se il punteggio non distingue situazioni migliori e peggiori, occorre rivedere il modello.

### 7.1 Percorso A: rendere affidabile un indice dichiaratamente euristico

Unificare il motore e correggere difetti senza attribuire a nuovi coefficienti una precisione che i dati non consentono. Versionare l'indice precedente, conservare fixture di confronto e pubblicare un change log del significato delle differenze.

Contratto suggerito, orientativo:

```kotlin
data class AnalysisRequest(
    val targetDate: LocalDate,
    val issuedAt: Instant,
    val siteTimeZone: String,
    val spatialUnitId: String,
    val speciesId: String,
    val mode: CalculationMode,
    val environmentalSeries: List<EnvironmentalDay>,
    val habitatEvidence: HabitatEvidence,
    val modelVersion: String,
)

data class AnalysisResult(
    val suitabilityScore: Double?,
    val calibratedProbability: Double?,
    val targetDefinitionId: String,
    val quality: DataQuality,
    val uncertainty: UncertaintySummary?,
    val contributions: List<Contribution>,
    val modelVersion: String,
)
```

I tipi temporali devono essere quelli disponibili nel progetto KMP; il frammento descrive il contratto, non è una patch compilata. `calibratedProbability` resta null nella versione euristica. Uno stato unknown non diventa automaticamente zero. Mappa, riepilogo e outlook consumano lo stesso risultato; l'AI eventualmente produce soltanto testo vincolato ai dati strutturati.

### 7.2 Percorso B opzionale: definire una probabilità che si possa misurare

Esempio di target iniziale da adottare e documentare prima della raccolta dati:

> Probabilità di rilevare almeno uno sporocarpo della specie bersaglio durante una visita di durata e area standardizzate, nel sito e nella data specificati.

La durata e l'area vanno fissate con un protocollo praticabile, non scelte per adattarsi al modello. «Raccolta di almeno 500 g» richiede un target differente e misure di quantità/qualità. Non sostituire presenza di qualsiasi fungo alla presenza della specie richiesta.

Con visite standardizzate, iniziare da una regressione logistica/GAM con effetti di sito, regione e stagione, includendo covariate laggate della pioggia e temperatura e interazioni limitate. È una baseline verificabile; un modello più complesso deve dimostrare un vantaggio fuori campione.

Quando si dispone di visite ripetute, una possibile scomposizione è:

```text
P(ritrovamento | X, sforzo)
 = P(presenza locale | X)
 × P(sporocarpi disponibili | presenza, storia ambientale)
 × P(rilevamento | sporocarpi disponibili, sforzo)
```

È una fattorizzazione condizionale, non un'assunzione che i tre processi siano indipendenti. Le componenti non sono identificabili da soli dati opportunistici di ritrovamento; senza disegno di campionamento adeguato, stimare direttamente il target osservato e dichiararne i limiti.

Un hurdle di resa ha invece `E[Y|X] = P(Y>0|X) · E[Y|Y>0,X]`: l'output è una quantità attesa, non automaticamente una probabilità. Per conteggi o biomassa valutare famiglie appropriate e sovradispersione, anziché convertire un rendimento in percentuale.

### 7.3 Dati condivisibili fra i percorsi e requisiti aggiuntivi per B

| Gruppo | Campi minimi |
|---|---|
| Visita | ID visita, data/ora inizio-fine, geometria, durata, osservatore/protocollo |
| Esito | Specie verificata, presenza/assenza rilevata, conteggi, eventuale biomassa, qualità tassonomica |
| Sforzo | Tempo, area/percorso, numero osservatori, metodo; anche uscite senza ritrovamenti |
| Ambiente | Habitat, ospiti, substrato, suolo, quota, eventuale copertura/struttura misurata |
| Meteo | Fonte, variabile/unità, modello, run/issuedAt, validTime, storico disponibile al momento |
| Provenienza | Metodo di misura, imputation flag, missingness, versione dei dataset |
| Previsione | Modello e parametri versionati, output emesso prima della visita, orizzonte |

Il protocollo completo sopra descritto è particolarmente importante per il percorso B; può essere avviato già durante A per verificare l’indice e preservare la possibilità di evoluzione. La raccolta dati non impegna il prodotto a pubblicare probabilità.

Una segnalazione positiva opportunistica non è un campione casuale. Non creare pseudo-assenze chiamandole visite negative. Deduplicare segnalazioni della stessa buttata e tenere insieme nello split le osservazioni fortemente dipendenti.

### 7.4 Validazione separata: utilità dell'indice e calibrazione della probabilità

**Percorso A — criterio di validità dell'indice.** Verificare su dati indipendenti se punteggi maggiori identificano situazioni mediamente più favorevoli, confrontando l'indice con una baseline semplice specie–regione–stagione. Usare separazione per sito e tempo, analizzare l'ordinamento dei risultati, le frequenze osservate per fascia con intervalli e numerosità, e l'utilità delle scelte suggerite. Controllare sforzo e selezione delle visite: differenze di ritrovamento possono riflettere differenze nella ricerca. Documentare dove la comparabilità è dimostrata; un indice valido per una specie non è automaticamente confrontabile con quello di un'altra.

L'indice non deve necessariamente corrispondere alle frequenze osservate: 80/100 non richiede l'80% di uscite riuscite. **Non calcolare Brier score o log-loss trattando arbitrariamente indice/100 come probabilità.** L'incertezza va descritta attraverso qualità dei dati, sensibilità e stabilità delle valutazioni; una classe di affidabilità non è una probabilità di raccolta.

**Completamento di A:** difetti prioritari risolti, output coerenti fra piattaforme e schermate, dati mancanti gestiti esplicitamente, limiti documentati e utilità comparativa verificata rispetto alla baseline nel dominio dichiarato. Se manca quest'ultima evidenza, l'indice va presentato come sperimentale. Si può completare e mantenere A senza avviare B.

**Percorso B — requisiti aggiuntivi per la probabilità.** Calibrazione e discriminazione sono proprietà distinte: una percentuale quasi costante può risultare calibrata sulla frequenza media ma aiutare poco nella scelta di un luogo o di una giornata. Per B occorre quindi dimostrare entrambe, oltre all'utilità per l'utente.

Proposta operativa per B:

1. Separare sviluppo, calibrazione e test bloccato; bloccare per sito/area e per periodo/anno, con dimensioni coerenti con l'autocorrelazione. Il riferimento metodologico per dati strutturati è Roberts et al. [Scheda degli autori][S-cv].
2. Confrontare almeno climatologia specie-regione-stagione, modello meteo semplice, euristica revisionata e modello completo.
3. Valutare Brier score, log-loss, calibrazione per fasce, discriminazione e prestazioni per specie/regione/lead time. AUROC da sola non misura la bontà delle probabilità.
4. Riportare intervalli mediante bootstrap a blocchi o modello probabilistico appropriato; quantificare separatamente incertezza di parametri, covariate, meteo futuro e osservazione.
5. Usare ensemble meteorologici quando disponibili: calcolare la previsione per membro e poi aggregare. Una banda fra scenari non è automaticamente un intervallo di confidenza calibrato della probabilità di raccolta.
6. Valutare fuori distribuzione e drift; prevedere astensione o output qualitativo quando mancano dati essenziali.
7. Congelare un test prospettico prima dell'impiego pubblico della percentuale e aggiornarlo senza riutilizzare continuamente gli stessi siti per scegliere i coefficienti.

Nessun numero minimo universale di visite viene prescritto: la numerosità deve derivare da prevalenza, numero di parametri, dipendenza spaziale e precisione desiderata. Gli obiettivi di rilascio vanno prestabiliti con intervalli e confronto alla baseline, non inventati dopo aver visto i risultati.

### 7.5 Miglioramenti da sperimentare, in ordine

| Miglioramento | Motivo | Condizione per attivarlo |
|---|---|---|
| Geometrie habitat, ospiti e substrato | Riduce errori ambientali macroscopici | Verifica geospaziale e tracciabilità |
| Funzioni di lag distribuito | Evita singolo evento dominante e soglie rigide | Guadagno fuori campione rispetto a cumulati semplici |
| Stato idrico con drenaggio e neve | Rappresenta disponibilità d'acqua, non solo precipitazione | Parametri e spin-up verificati; confronto con suolo osservato |
| Temperatura del suolo / microclima | Più vicina al substrato biologico | Dati appropriati e stima degli offset |
| Stato delle coorti e persistenza | Separa nuova formazione e sporocarpi già presenti | Monitoraggio temporale abbastanza frequente |
| Stagionalità ciclica regionale | Evita salti mensili e calendario universale | Campionamento su più stagioni/regioni |
| Covariate SPUN o altri raster | Informazione spaziale aggiuntiva | Provenienza certa e ablation positiva |
| Modelli gerarchici fra specie | Condivisione parziale dell'informazione | Ecologia compatibile e diagnostica per specie |

Non aggiungere come default nuove costanti di latenza, shock, gelo o idrofobicità senza stima o etichetta esplicita di prior esperto.

## 8. Backlog separato per prodotto principale ed evoluzione opzionale

Le dimensioni sono relative: **S** intervento circoscritto, **M** più moduli, **L** revisione architetturale o sperimentale. Non sono preventivi di giorni.

### 8.1 Percorso A — Correzione e completamento dell’indice

| Ticket | Priorità / dimensione | Rilievi | Consegna verificabile | Dipendenze |
|---|---|---|---|---|
| MYCO-SCI-01 | P1 / S | F01, F20 | Target e terminologia; output euristico distinto da probabilità | Nessuna |
| MYCO-SCI-02 | P1 / M | F02 | Famiglia termica esplicita, validatore cardinali, test indipendenti | Specifica scientifica della curva |
| MYCO-SCI-03 | P1 / M | F03–F05 | Eventi senza perdita/doppi conteggi e fase continua senza reset assoluto | Fixture dei controesempi |
| MYCO-SCI-04 | P1 / L | F12, F13, F20 | Motore KMP unico, catalogo e modalità condivisi, breakdown | SCI-02/03 per non trasferire difetti |
| MYCO-SCI-05 | P1 / M | F14–F16 | Date/fusi/as-of, qualità dati, neve distinta, no fallback 0 | Contratti dei dati |
| MYCO-SCI-06 | P1 / M–L | F08, F09 | Habitat geometrico; evidenze ospiti e cache corrette | Dati geospaziali disponibili |
| MYCO-SCI-07 | P1 / M | F10 | Manifest SPUN; rimozione/isolamento dei bonus non verificati | Accesso agli originali |
| MYCO-SCI-08 | P1 / M | F11 | Legenda separata o mappa prodotta dal motore unificato | SCI-01; SCI-04 per parità numerica |
| MYCO-SCI-09 | P1 / M | F19 | Test riclassificati, date corrette, regressioni end-to-end | SCI-02/03/05 |
| MYCO-SCI-10 | P2 / M | F06, F07, F17, F18 | Registro ipotesi, modelli idrico/termico candidati e sensibilità | Dati scientifici e ambientali |
| MYCO-SCI-13 | P1 per validare l'indice / M–L | F01, F19 | Protocollo di verifica dell'indice, confronto indipendente alla baseline e rapporto sui domini di validità | SCI-01/04/05/06 e osservazioni adeguate |

SCI-13 è aggiunto senza rinumerare i ticket precedenti. La sua estensione dipende dai dati disponibili: non include l'obbligo di stimare probabilità. Le correzioni possono essere rilasciate come indice sperimentale prima della sua conclusione, con stato e limiti espliciti.

### 8.2 Percorso B — Attività opzionali, fuori dai requisiti di completamento di A

Queste attività diventano prioritarie **solo se viene deciso di offrire probabilità calibrate**. Il loro mancato avvio non costituisce un difetto o un'incompletezza del prodotto basato sull'indice.

| Ticket | Priorità / dimensione | Rilievi | Consegna verificabile | Dipendenze |
|---|---|---|---|---|
| MYCO-SCI-11 | Condizionata all'avvio di B / L | F01, F19 | Target probabilistico, protocollo visite completo, dataset versionato e modelli baseline | SCI-01/05/06; riuso di SCI-13 ove compatibile |
| MYCO-SCI-12 | Condizionata all'avvio di B / L | F01 | Validazione bloccata e prospettica, calibrazione, confronto di utilità e model card | SCI-11 e dati sufficienti |

**Sequenza di rilascio per A:** semantica corretta e bug risolti → motore e dati coerenti → verifica comparativa e documentazione dei limiti. Il prodotto può fermarsi e consolidarsi qui, mantenendo l'indice come risultato definitivo.

**Eventuale rilascio B:** decisione esplicita basata su esigenza dell'utente e fattibilità dei dati → SCI-11/12 → pubblicazione delle probabilità soltanto nel dominio validato e se utili. La riuscita delle correzioni o della validazione comparativa dell'indice non autorizza da sola l'etichetta «probabilità di raccolta».

**Documentazione da aggiornare con lo sviluppo:** `AGENTS.md`, `docs/TECHNICAL_DOCUMENTATION.md`, `docs/FUTURE_DEVELOPMENTS_ANALYSIS.md`, documenti di parità iOS, catalogo delle fonti/parametri e model card. Nel rapporto non vengono modificati questi file.

## 9. Piano dei test da implementare nel repository

| ID test | Caso | Proprietà / risultato atteso dopo la correzione |
|---|---|---|
| REG-01 | CTMI con parametri validi | Valori contro riferimento indipendente, ramo effettivamente eseguito |
| REG-02 | Cardinali 9/14/24 | Rifiuto o modello esplicitamente compatibile; niente fallback occulto |
| REG-03 | Eventi 25 mm a t−11; 17,49/17,51 a t−1 | Eliminazione del salto artificiale 68→26 |
| REG-04 | 30+15 mm in due giorni | Evento conserva 45 mm, senza duplicazioni |
| REG-05 | 5,9/6,0 mm al giorno | Nessuna classificazione «senza precipitazioni» con ricarica persistente |
| REG-06 | Fasi a giorni 4/5, 8/9, 15/16 | Curve raccordate; etichetta non introduce un salto numerico |
| REG-07 | θ profonda 0,349999/0,350001 | Nessun salto del coefficiente per semplice cambio ramo |
| REG-08 | Variazione minima attorno alla soglia fredda | Transizione controllata di stress e latenza |
| REG-09 | W=0 e massimo SPUN | Parità mappa/scheda per stesso target, o semantica separata esplicita |
| REG-10 | WEATHER_ONLY in quota/fuori stagione | Oggi coincide con primo outlook |
| REG-11 | Stesso bosco partizionato diversamente | Idoneità invariata rispetto alla segmentazione OSM |
| REG-12 | Cambio specie/ospite e raggio cache | Nessun riuso di evidenza incompatibile |
| REG-13 | Forecast che attraversa mese/anno | Covariate ricavate dalla data di ogni previsione |
| REG-14 | Storico 28 giorni e riepilogo futuro | Nessuna lettura degli indici passati come futuro |
| REG-15 | Giorni mancanti, null, serie corta | Unknown/errore controllato; tempo reale preservato |
| REG-16 | Neve, assenza di fusione | Nessuna equivalenza automatica a pioggia disponibile |
| REG-17 | Forecast al tempo di emissione | Nessun dato successivo a issuedAt nel backtest |
| REG-18 | JSON identico Android/iOS | Stesse forzanti, contributi e risultati raw |
| REG-19 | Stress fisico e disponibilità di sporocarpi | Processo limitato secondo specifica validata, non solo curva intermedia |
| REG-20 | Bounds e configurazioni invalide | Double finiti o stato non stimabile; errore di config esplicito |

I test REG-01–20 sono verifiche tecniche trasversali applicabili ai componenti adottati nel percorso A; non obbligano a implementare il percorso B. SCI-13 aggiunge la verifica empirica dell’indice. Le prove specifiche di calibrazione appartengono a SCI-11/12.

Le tolleranze di continuità devono essere definite sui valori non arrotondati e rispetto alla scala dell'input. Una soglia universale «5 punti per 0,2 °C» non è una legge biologica; è al massimo un requisito ingegneristico dichiarato. Test statistici e test matematici devono restare separati.

## 10. Esiti positivi da preservare

- Separazione di molte funzioni numeriche dalla UI, utile per test e migrazione KMP.
- Kernel calcolato in forma logaritmica, scelta favorevole alla stabilità numerica nell'intervallo ordinario.
- Profilazione per specie e riconoscimento di gilde differenti come punto di partenza.
- Uso di minime/massime nella pipeline Android, benché non ancora condiviso.
- Presenza di regression test, fuzzing e test di parità, da estendere e riclassificare.
- Compressione finale monotona e raccordata: utilizzabile come trasformazione di un indice, purché non scambiata per stima dell'incertezza.
- Nessun moltiplicatore lunare nella formula finale esaminata.

## 11. Appendice A — Riproduzione dei controesempi

Il seguente script usa soltanto la libreria standard Python. È una **trascrizione ridotta** dei rami pertinenti per edulis, non il motore Kotlin né un nuovo modello proposto. Usa double Python anziché Float Kotlin per le osservazioni; i casi sono scelti sufficientemente lontani dall'errore di rappresentazione da non dipendere da questa differenza. Le differenze di indice qui sono intenzionali per riprodurre l'implementazione attuale.

Salvare il blocco come `reproduce_myco_audit.py` ed eseguire `python3 reproduce_myco_audit.py`. La suite ufficiale Kotlin dovrà integrare gli stessi casi dopo il ripristino della toolchain.

```python
import math,json

def kernel(t,p=11,a=4):
    return math.exp(a*(math.log(t/p)-(t/p-1))) if t>0 else 0

def phase(rains,t):
    cand=[]
    for i in range(t,max(0,t-28)-1,-1):
        if rains[i]>=12:cand.append((i,rains[i]))
        elif i>=2 and sum(rains[i-2:i+1])>=18:cand.append((i-2,sum(rains[i-2:i+1])))
    dist=[]
    for e in sorted(cand,key=lambda e:-e[0]):
        if not any(abs(e[0]-x[0])<=2 for x in dist):dist.append(e)
    if not dist:return .25,None,dist
    active=dist[0]
    for old in dist[1:]:
        if 5<=t-old[0]<=15 and old[1]>=max(25,35*.7) and active[1]<old[1]*.7:
            active=old;break
    lag=t-active[0];k=kernel(lag)
    m=.35+.15*lag/4 if lag<=4 else .5+.35*k if lag<=8 else .85+.15*k if lag<=15 else max(.3,.7*k)
    return m,lag,dist

def prob(w,phase=1,h=1,a=1,s=1,t=1,hurdle=True):
    p=1-math.exp(-((h*a)/.35)**2.5) if hurdle else 1
    raw=100*(w/100)**1.2*h*a*s*t*p*phase
    return int(70+22*math.tanh((raw-70)/22) if raw>70 else raw)

def wscore(rains,t):
    # Constant 16 C mean, 14 C min, 18 C max, 85% RH; soil/SPUN/canopy absent.
    rain=sum(rains[i]*kernel(t-i) for i in range(t))
    medium=(24-16)/(24-14)*((16-9)/(14-9))**((14-9)/(24-14))
    return math.floor(40*min(1,rain/35)+30*(.75+.25*medium)+15+.5)

out={}
for temp in [10,12,14,16,20,23]:
    lo,opt,hi=9,14,24
    num=(temp-hi)*(temp-lo)**2
    den=(opt-lo)*((opt-lo)*(temp-opt)-(opt-hi)*(opt+lo-2*temp))
    fallback=(hi-temp)/(hi-opt)*((temp-lo)/(opt-lo))**((opt-lo)/(hi-opt))
    out['ctmi_'+str(temp)]={'num':num,'den':den,'ratio':num/den if den else None,'actual_fallback':fallback}
for r in [17.49,17.51]:
    rains=[0.]*29;rains[13]=25;rains[23]=r
    m,lag,ev=phase(rains,24);w=wscore(rains,24)
    out['reset_'+str(r)]={'phase':m,'lag':lag,'weather':w,'probability':prob(w,m),'events':ev}
for r in [5.9,6.0]:
    rains=[r]*29;m,lag,ev=phase(rains,28);w=wscore(rains,28)
    out['daily_'+str(r)]={'phase':m,'lag':lag,'weather':w,'probability':prob(w,m)}
for lag in [4,5,8,9,15,16]:
    rains=[0.]*40;rains[5]=35;m,_,_=phase(rains,5+lag)
    out['stage_'+str(lag)]={'phase':m,'probability_at_W85':prob(85,m)}
rains=[0.]*29;rains[13]=30;rains[14]=15
out['cluster_30_15']=phase(rains,24)
out['heatmap_worst']=100*(.60+.2*.3*.4*.60)
out['heatmap_weather_zero_other_ideal']=100*(.60+.2*1*1*.60)
out['weather_only_dashboard']=prob(85,1)
out['weather_only_outlook_A04_S01']=prob(85,1,a=.4,s=.1)
out['cold_ideal_rain_humidity']=prob(55,1)
out['kernel_area_peak11']=sum(kernel(i) for i in range(1,300))
out['kernel_area_peak6']=sum(kernel(i,6) for i in range(1,300))
print(json.dumps(out,indent=2))
```

### Risultati essenziali ottenuti

| Esperimento | Risultato della riproduzione |
|---|---|
| CTMI a 16 °C, cardinali 9/14/24 | Razionale 0,98; codice effettivo 0,9465727653 |
| Soglia pioggia recente 17,49/17,51 | W=73 in entrambi; Φ=1/0,3875; output 68/26 |
| Pioggia quotidiana 5,9/6,0 | W=85 in entrambi; output 20/34 |
| Cambio fase giorno 8/9, W fisso 85 | Φ=0,791496/0,989104; output 65/80 |
| Cambio fase giorno 15/16, W fisso 85 | Φ=0,971111/0,508605; output 79/41 |
| Cluster 30+15 mm | Un evento con quantità 15 mm |
| Mappa, W=0 e S=A=1, bio=100 | 72 |
| Mappa ai floor di W/S/A, bio=100 | 61,44 prima del troncamento |
| WEATHER_ONLY, W=85 e Φ=1 | Scheda 81; con A=0,4/S=0,1 nell'outlook: 2 |
| Somma discreta kernel, picco 11/6 giorni | 14,076091 / 7,677968 |

## 12. Appendice B — Riferimenti al codice congelato

I riferimenti seguenti puntano al commit esaminato; non al ramo mobile `master`. Le righe sono quelle di questo snapshot.

| Riferimento | Ambito |
|---|---|
| [AGENTS.md][K-agents] | Vincoli e rivendicazioni scientifiche del progetto |
| [Aggregazione meteo][K-aggregate] | `processWeatherData`, righe 126–172 |
| [Kernel e compensazione][K-rain] | righe 398–477 |
| [Buffering sottochioma][K-canopy] | righe 509–565 |
| [Score meteorologico][K-weather] | righe 601–762 |
| [Fase/eventi][K-events] | righe 775–931 |
| [CTMI][K-ctmi] | righe 1330–1349 |
| [Suolo][K-soil] | righe 1426–1474 |
| [Struttura forestale][K-stand] | righe 1540–1582 |
| [Hurdle][K-hurdle] | righe 1599–1620 |
| [Habitat][K-habitat] | righe 1632–1747 |
| [Probabilità][K-prob] | righe 1767–1813 |
| [Fattori][K-factors] | da riga 1830 |
| [Outlook][K-outlook] | righe 2118–2149 |
| [Trend][K-trend] | righe 1216–1237 |
| [ViewModel Android][K-vm] | Selezione specie, ricalcolo, acquisizione e outlook |
| [Repository][K-repo] | Cache, query meteo e OSM |
| [SPUN manager][K-spun] | Campionamento e percentili |
| [Generatore asset][K-buildspun] | Nodata, interpolazione e quantizzazione |
| [Heatmap Android][K-mapandroid] / [core][K-mapcore] | Formula spaziale |
| [Core][K-core] / [engine][K-engine] / [dominio][K-domain] | Modello usato da iOS |
| [Mapper iOS][K-iosmapper] / [ViewModel iOS][K-iosvm] | Aggregazione e scelta del giorno |
| [Test fenologici][K-tests] / [parità][K-parity] | Limiti della verifica attuale |

## 13. Appendice C — Fonti scientifiche e documentali

**Fonti effettivamente consultate:**

1. **de-Miguel, S., Bonet, J.A., Pukkala, T., Martínez de Aragón, J. (2014).** *Impact of forest management intensity on landscape-level mushroom productivity: A regional model-based scenario analysis*. Forest Ecology and Management 330, 218–227. Consultato il PDF CTFC, in particolare §2.1, equazioni 1–4. [Testo primario][S-demiguel]. L'uso nel rapporto riguarda la distinzione fra probabilità di produzione e resa condizionata, non una trasposizione dei coefficienti.
2. **De Frenne et al. (2019).** *Global buffering of temperatures under forest canopies*. Nature Ecology & Evolution 3, 744–749. DOI 10.1038/s41559-019-0842-1. [Articolo][S-defrenne]. Supporta il fenomeno generale del buffering, non certifica le costanti implementate in Myco.
3. **van Genuchten, M.Th. (1980).** *A Closed-form Equation for Predicting the Hydraulic Conductivity of Unsaturated Soils*. Soil Science Society of America Journal 44, 892–898. DOI 10.2136/sssaj1980.03615995004400050002x. [Copia dell'articolo presso USDA][S-vg]. Riferimento per distinguere una relazione idraulica da una curva di punteggio a soglie.
4. **SPUN, Underground Atlas, Mycorrhizal Biodiversity Map v1.0.** [Documentazione tecnica del produttore][S-spun]. Consultati natura predittiva, scala, unità e trattamento dell'incertezza. Non equivale alla verifica dei GeoTIFF originali impiegati nel repository.
5. **SPUN (2026).** *Global density and biomass of arbuscular mycorrhizal fungal networks*, pagina degli autori e del progetto cartografico. [Pagina della ricerca][S-am] e [descrizione della mappa][S-am-map]. Fonte primaria istituzionale per identificare la gilda AM del prodotto pubblico; testo integrale dell'articolo Science non verificato qui.
6. **Open-Meteo.** [Documentazione ufficiale Forecast API][S-meteo], consultata il 22 settembre 2026. Riferimento per semantica della precipitazione, prodotto meteorologico e distinzione tra previsione e archivi. Non è stata eseguita una verifica live delle chiamate meteo dell'app.
7. **MacKenzie et al. (2002).** *Estimating site occupancy rates when detection probabilities are less than one*. Ecology 83, 2248–2255. [Abstract e scheda degli autori presso USGS][S-occupancy]. Consultato il principio di rilevamento imperfetto; non trasferiti parametri da quello studio ai funghi.
8. **Roberts et al. (2017).** *Cross-validation strategies for data with temporal, spatial, hierarchical, or phylogenetic structure*. Ecography 40, 913–929. DOI 10.1111/ecog.02881. [Scheda bibliografica presso l'istituzione di un autore][S-cv]. Metadati verificati; lo schema di validazione proposto in questo rapporto è una raccomandazione progettuale da dettagliare sul dataset concreto.

**Riferimenti da completare prima di rivendicare una replica scientifica:**

9. **Rosso, Lobry e Flandrois (1993).** *An unexpected correlation between cardinal temperatures of microbial growth highlighted by a new model*. Journal of Theoretical Biology 162, 447–463, DOI 10.1006/jtbi.1993.1099. Il testo primario non è stato acquisito integralmente. F02 è dimostrato algebricamente dalla formula presente nel repository e non richiede fiducia in una formula citata da un sito secondario. La scelta della curva sostitutiva richiede verifica del lavoro originale e del suo dominio.
10. **Predicting porcini: a decade of sporocarp monitoring reveals the meteorological triggers of Boletus edulis fruiting in central European beech forests.** [Versione bioRxiv v2][S-porcini]; [record istituzionale][S-porcini-record]. Testo integrale non accessibile nella sessione. Non verificati i coefficienti né il significato esatto dei 26/20 giorni richiamati nei documenti Myco. Non assumere che i risultati siano generalizzabili a tutte le specie e foreste.

Le fonti non consultabili non sono usate come prova di correttezza di una costante. Le soluzioni e i controesempi del rapporto sono distinti dai risultati sperimentali pubblicati.

[K-agents]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/AGENTS.md
[K-aggregate]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt#L126-L172
[K-rain]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt#L398-L477
[K-canopy]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt#L509-L565
[K-weather]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt#L601-L762
[K-events]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt#L775-L931
[K-ctmi]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt#L1330-L1349
[K-soil]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt#L1426-L1474
[K-stand]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt#L1540-L1582
[K-hurdle]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt#L1599-L1620
[K-habitat]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt#L1632-L1747
[K-prob]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt#L1767-L1813
[K-factors]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt#L1830
[K-outlook]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt#L2118-L2149
[K-trend]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt#L1216-L1237
[K-vm]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/ui/viewmodel/MushroomViewModel.kt
[K-repo]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/repository/MushroomRepository.kt
[K-spun]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/repository/SpunDataManager.kt
[K-buildspun]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/tools/build_spun_asset.py
[K-mapandroid]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/utils/HeatmapGenerator.kt
[K-mapcore]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/core/src/commonMain/kotlin/github/naturewhisp/myco/core/HeatmapEngine.kt
[K-core]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/core/src/commonMain/kotlin/github/naturewhisp/myco/core/MycoAlgorithms.kt
[K-engine]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/core/src/commonMain/kotlin/github/naturewhisp/myco/core/MycoAnalysisEngine.kt
[K-domain]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/core/src/commonMain/kotlin/github/naturewhisp/myco/core/Domain.kt
[K-iosmapper]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/iosApp/MycoIOS/DomainAdapters/OpenMeteoDomainMapper.swift
[K-iosvm]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/iosApp/MycoIOS/App/MycoViewModel.swift
[K-tests]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/test/java/github/naturewhisp/myco/PhenologicalInvariantsTest.kt
[K-parity]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/test/java/github/naturewhisp/myco/CrossPlatformScientificParityTest.kt
[K-config]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/model/EcologicalWeightsConfig.kt
[K-species]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/model/MushroomSpecies.kt
[K-weathermodel]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/model/WeatherModel.kt
[K-api]: https://github.com/naturewhisp/myco/blob/99bd52f8d2565aeb7f16978485d1e84498741a65/app/src/main/java/github/naturewhisp/myco/network/ApiServices.kt
[S-demiguel]: https://cris.ctfc.cat/docs/upload/27_431_De-Miguel%20et%20al-%202014.pdf
[S-defrenne]: https://www.nature.com/articles/s41559-019-0842-1
[S-vg]: https://www.ars.usda.gov/ARSUserFiles/20360500/pdf_pubs/P0682.pdf
[S-spun]: https://www.spun.earth/fr/underground-atlas/mycorrhizal-biodiversity
[S-am]: https://www.spun.earth/peer-reviewed-papers/the-planets-largest-biological-infrastructure-lies-beneath-our-feet
[S-am-map]: https://www.spun.earth/mapping/a-hidden-infrastructure
[S-meteo]: https://open-meteo.com/en/docs
[S-occupancy]: https://www.usgs.gov/publications/estimating-site-occupancy-rates-when-detection-probabilities-are-less-one
[S-cv]: https://research.wright.edu/en/publications/cross-validation-strategies-for-data-with-temporal-spatial-hierar/
[S-porcini]: https://www.biorxiv.org/content/10.64898/2025.12.12.693895v2
[S-porcini-record]: https://pub.uni-bielefeld.de/record/3012537
