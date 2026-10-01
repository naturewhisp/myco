# Myco — integrazione della revisione: problemi residui e caso Mindino

**Data:** 1 ottobre 2026.  
**Repository:** https://github.com/naturewhisp/myco  
**Commit verificato:** `d2822246d988e125d98892e57320d88e016a1014`, committato il 30 settembre 2026 alle 20:18 UTC.  
**Confronto:** rapporto del 29 settembre sul commit `0dd3bdce3a46883d0878379e1a340c41084a9dfe`.  
**Caso segnalato:** Mindino, frazione di Garessio; analisi riferita esplicitamente al **29 settembre 2026**, indice mostrato come **29% — «Innesco»**, stress idrico e ultima pioggia significativa indicata al 17 settembre, valore pluviometrico di circa 35 mm.

Questo documento riporta soltanto problemi ancora aperti, regressioni introdotte dalle correzioni e istruzioni per completare l'indagine. Diverse correzioni precedenti sono effettive, ma non giustificano ancora la dichiarazione di completamento integrale. **Il percorso A rimane un indice euristico autonomo; nessuno degli interventi richiesti comporta l'implementazione del percorso B.**

## 1. Risposta sul caso Mindino e limiti dell'attribuzione

Tre aspetti sono già verificabili dal sorgente:

1. **«Innesco» non deriva dalla presenza di una pioggia recente.** È il nome assegnato automaticamente alla fascia numerica 20–39. Un valore 29 riceve questa etichetta anche se la fase fenologica o il suolo indicano stress. È un difetto semantico certo.
2. **I millimetri mostrati come «Precipitazioni efficaci» su Android non sono una riserva d'acqua residua dopo evaporazione.** Sono una convoluzione delle piogge pregresse con il kernel temporale della specie, modificata dalla compensazione del suolo profondo e, nel percorso operativo, dall'intercettazione della chioma. La funzione non usa ET₀ e non calcola un bilancio idrico.
3. **Non è presente un modello esplicito della rugiada.** Le query e le funzioni operative esaminate non stimano un apporto da condensazione da aggiungere agli eventi piovosi. L'umidità relativa può aumentare una componente del punteggio, ma questo non significa che siano stati misurati o stimati millimetri di rugiada.

Non è possibile attribuire con certezza il valore numerico **29** a un singolo errore: mancano il payload meteo utilizzato, la specie selezionata, le impostazioni, le coordinate esatte, il momento dell'analisi e la versione dell'app installata. Inoltre il commit più recente è successivo al 29 settembre: **l'output osservato potrebbe provenire da una versione precedente**, anche se il repository attuale contiene correzioni.

L'assenza di piogge significative dal 17 al 29 settembre non implica da sola assenza di carpofori o disponibilità idrica nulla: dipende anche da suolo, microclima, specie e persistenza degli organismi già presenti. Non si propone quindi di forzare retroattivamente l'indice a zero. Si propone di rendere coerenti dati, stato idrico, fase e spiegazione, e di verificare il punteggio con gli input reali.

Le pipeline del commit esaminato risultano verdi: [Android](https://github.com/naturewhisp/myco/actions/runs/36771661936), [KMP Core](https://github.com/naturewhisp/myco/actions/runs/36771661879), [iOS](https://github.com/naturewhisp/myco/actions/runs/36771661916). Non è stato possibile rieseguire localmente Gradle perché il wrapper non riesce a scaricare la distribuzione dalla rete. I controesempi numerici sotto sono **riproduzioni indipendenti delle formule del sorgente**, non esecuzioni dell'app e non ricostruzioni del meteo reale di Mindino.

## 2. RES-01 — Le fasce dell'indice continuano a dichiarare eventi biologici

**Priorità P1 — confermato.** Collega direttamente il caso segnalato alla UI.

`ProbabilityTier` Android e core assegna:

- 20–39: «Innesco», «EMERGENTE • INNESCO MICELIARE»;
- 60–74: «Propizio», con descrizione «BUTTATA IN CORSO»;
- 75–100: «Culmine».

Il mapping dipende soltanto dal numero. Non legge `GrowthPhaseEvaluation`, stress o età dell'evento. Un indice basso dovuto a siccità diventa così un presunto «innesco miceliare», mentre un indice elevato è descritto come buttata in corso senza osservazione dei carpofori.

La bonifica delle percentuali su iOS non ha eliminato tutte quelle Android: `ProbabilityHeadline.kt`, `DayRow.kt` e `ForecastScreen.kt` mostrano ancora `%` riferito all'indice.

**Soluzione.** Separare due output:

- **Classe dell'indice:** favorevolezza molto bassa / bassa / media / alta / molto alta, con valore `29/100`.
- **Stato inferito:** attesa di apporto, idratazione, maturazione potenziale, stress idrico o fase non determinabile; ricavato dal motore e dichiarato come inferenza, non osservazione.

Lo stato limitante deve essere leggibile accanto all'indice: per esempio «29/100 · favorevolezza bassa — stress idrico». Non dedurre una fase da una soglia del numero. Cambiare anche legenda, accessibilità, cronologia e testi AI. Evitare di usare la sola parola «Culmine» come prova di presenza.

**Chiusura.** Fixture con indice 29 e fase WANING/stress: nessun output deve affermare innesco attuale. Fixture con indice alto ma fase non determinabile: nessuna buttata dichiarata come certa. Nessuna percentuale riferita all'indice A.

**Riferimenti:** [ProbabilityTier Android](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/app/src/main/java/github/naturewhisp/myco/model/ProbabilityTier.kt), [Domain core](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/core/src/commonMain/kotlin/github/naturewhisp/myco/core/Domain.kt), [ProbabilityHeadline](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/app/src/main/java/github/naturewhisp/myco/ui/components/ProbabilityHeadline.kt), [DayRow](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/app/src/main/java/github/naturewhisp/myco/ui/components/DayRow.kt), [ForecastScreen](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/app/src/main/java/github/naturewhisp/myco/ui/screens/ForecastScreen.kt).

## 3. RES-02 — Memoria fenologica della pioggia e disponibilità idrica sono ancora confuse

**Priorità P1 — confermato per la formula; contributo al 29 da ricostruire.**

Android e core calcolano, schematicamente:

$$R_{\mathrm{pheno}}(t)=c_{\mathrm{deep}}\sum_{i=t-26}^{t-1}P_{\mathrm{liquid}}(i)\,f_{\mathrm{species}}(t-i).$$

Questa quantità conserva un contributo elevato proprio quando l'apporto si avvicina alla latenza di picco. Per B. edulis, senza chilling, picco 11 giorni e alpha 4:

$$f(12)\simeq0{,}98453.$$

Un apporto di 35 mm dodici giorni prima, senza intercettazione e con compensazione profonda neutra, produce quindi **34,46 mm ponderati**, anche se nel frattempo ET₀ è elevata. Cambiare solo ET₀ lascia invariato questo valore. Con due piogge illustrative, 22,3 mm diciannove giorni prima e 25,3 mm dodici giorni prima, la somma ponderata è circa **35,73 mm** prima delle altre correzioni. Questi esempi spiegano perché un valore vicino a 35 sia algoritmicamente plausibile senza piogge recenti; **non dimostrano che sia la ricostruzione reale del caso segnalato**.

I 35 mm non possono essere interpretati come «35 litri/m² ancora disponibili nel suolo». Il moltiplicatore profondo può inoltre superare 1: ulteriore ragione per considerare questa grandezza un indicatore, non un bilancio di massa dell'acqua.

### Come lo stress entra attualmente nel calcolo

| Componente | Uso attuale | Limite residuo |
|---|---|---|
| Pioggia fenologicamente pesata | Alimenta una componente fino a 40 punti del meteo | Non usa ET₀ né disponibilità superficiale corrente |
| Suolo ed ET₀ | Suolo entra nella componente umidità, con peso 60% dei 15 punti; ET₀ riduce la risposta del suolo fino al 15% | Non è un bilancio; i fattori idrici restano parzialmente compensabili dalla pioggia |
| Gate siccità | Moltiplica la fase se sono soddisfatte alcune condizioni | Può non attivarsi o disattivarsi bruscamente, RES-03 |
| Riga «Idratazione suolo» Android | Mostra valori del giorno target e può aggiungere «Stress idrico/secco» sotto 0,14 | Non coincide necessariamente con la finestra media usata nel meteo |
| Riga pluviometrica core | Mostra ancora il cumulato della finestra di `EnvironmentalWindows` | Non è necessariamente il valore di convoluzione usato dal meteo operativo |

La penalizzazione ET₀ interessa un massimo di 9 punti di componente pedologica (60% di 15); la sola modulazione ET₀ può quindi togliere fino a **1,35 punti del meteo** in quella componente. Non è una penalizzazione equivalente a evaporare tutta la pioggia disponibile. Il nuovo gate della fase può avere un effetto molto maggiore, ma segue un altro ramo logico.

### Soluzione scientificamente sostenibile per A

**Prima correzione:** rinominare la grandezza in «Apporto pluviometrico ponderato per latenza», con dettaglio «Indicatore fenologico; non è acqua residua nel suolo». Mostrare separatamente cumulato reale e data dell'ultimo evento, specificando lordo/sotto chioma e finestra temporale. Se si conservano unità mm, dichiararle come mm ponderati/equivalenti dell'indicatore; non «precipitazioni efficaci» nel senso di disponibilità idrologica.

**Correzione del motore:** separare memoria di maturazione e stato idrico corrente. La prima rappresenta la cronologia potenziale; il secondo deve limitare coerentemente il processo che il prodotto intende descrivere. Per A è possibile mantenere un fattore continuo di disponibilità idrica basato su suolo e qualità del dato, con parametri esplicitamente euristici, evitando che una pioggia vecchia da sola mantenga una fase favorevole su suolo secco. Definire se si modella formazione di nuovi carpofori, persistenza o entrambi; non trattarli come lo stesso evento.

**Non applicare una sottrazione meccanica `R_pheno − ΣET₀`.** Si mescolerebbe una somma pesata per fenologia con una domanda atmosferica cumulata. ET₀ si riferisce a una superficie standard ben irrigata, non è automaticamente evaporazione reale della lettiera forestale. L'eventuale bilancio deve usare apporti, accumulo e perdite sullo stesso intervallo e nelle stesse unità, con una giustificazione delle trasformazioni locali. La distinzione è documentata da [FAO-56](https://www.fao.org/4/x0490e/x0490e04.htm) e [Open-Meteo](https://open-meteo.com/en/docs).

Se si decide di sviluppare un serbatoio idrico, dichiarare stato iniziale, capacità, infiltrazione, ruscellamento, drenaggio e stima di ET effettiva. Trattarlo come evoluzione separata da validare, non come soluzione immediata certa. Evitare anche di sottrarre di nuovo ET a un dato di umidità del suolo modellato che già riflette le perdite: potrebbe introdurre doppia penalizzazione.

**Chiusura.** A parità di apporto passato, serie con suolo asciutto e serie con suolo umido devono avere stati idrici diversi senza far passare una memoria pluviometrica per acqua disponibile. La UI deve spiegare perché «pioggia storica presente» e «stress attuale» possono coesistere. Le componenti esposte devono usare le stesse finestre e trasformazioni della diagnosi, oppure dichiarare chiaramente la differenza.

**Riferimenti:** `calculateEffectiveRainfall`, `calculateWeatherScore`, `soilMoistureScoreSmooth`, `calculateFactors` negli [algoritmi Android](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/app/src/main/java/github/naturewhisp/myco/utils/MushroomAlgorithms.kt); `calculateEffectiveRainfall`, `weatherScore` e `soilMoistureResponse` negli [algoritmi core](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/core/src/commonMain/kotlin/github/naturewhisp/myco/core/MycoAlgorithms.kt); [fattori del motore core](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/core/src/commonMain/kotlin/github/naturewhisp/myco/core/MycoAnalysisEngine.kt).

## 4. RES-03 — Il nuovo gate siccità ha ancora interruttori discontinui e presupposti non provati

**Priorità P1 — confermato.** Il raccordo alla vecchia soglia di 25 mm è migliorato; il problema seguente è nel nuovo gate.

Il gate si applica solo se `nDry >= 5` e sono disponibili valori superficiali. Il conteggio dei giorni asciutti si interrompe al primo giorno con pioggia liquida **≥1 mm**. La penalizzazione usa il minimo fra media post-trigger, media recente e valore corrente del suolo. Non usa direttamente ET₀ cumulata, nonostante il criterio presente in AGENTS menzioni anche `ΣET₀ > P_trigger`.

### Controesempio della soglia a 1 mm

Serie con evento principale isolato dodici giorni prima, suolo superficiale rimasto a 0,13 m³/m³ e nessuna pioggia successiva, tranne la piccola quantità del giorno target. Specie B. edulis; il piccolo episodio non supera le soglie per diventare un nuovo evento fenologico.

| Pioggia liquida nel giorno target | Conteggio asciutto | Gate | Moltiplicatore finale della fase |
|---|---:|---|---:|
| 0,99 mm | Almeno 5 giorni | Attivo, fattore 0,20 | circa 0,19801 |
| 1,01 mm | 0 | Disattivato | circa 0,99005 |

Con W=80 e H=A=S=T=1, mantenuti fissi per isolare il ramo, la formula finale passa da **circa 15,15 a 75,62**. La pioggia del giorno target è esclusa dalla convoluzione idrica: nel controesempio il salto non richiede una variazione di W. Se il gate riceve dati sotto chioma, i valori indicati sono quelli dopo l'intercettazione; occorre testare anche il corrispondente valore lordo.

Bastano dunque 0,02 mm per riabilitare una fase favorevole **senza alcuna variazione dell'umidità del suolo**. Il salto non è giustificato come recupero idrico reale.

### Altri residui del medesimo ramo

- Passare da quattro a cinque giorni asciutti può accendere tutta la penalizzazione in un unico passo; un giorno di tempo discreto non giustifica automaticamente una soglia biologica universale.
- Se manca il suolo superficiale, il gate non opera. Il dato mancante non viene però rappresentato come impossibilità di valutare lo stress.
- Il trigger usato per la siccità è scelto con `weightEarlier >= 0.5`: mentre il moltiplicatore base è interpolato, la finestra del gate cambia con una selezione rigida. Va verificata anche questa soglia.
- Le soglie dell'estrazione degli eventi restano 10 mm/giorno, 17,5 mm/3 giorni e 24 mm/5 giorni: servono test del risultato finale attorno a ciascuna soglia.
- La stringa «primordi compromessi» e il parametro chiamato «soglia letale» attribuiscono un effetto fisiologico forte a una soglia volumetrica euristica. Il modello non osserva direttamente né primordi né danni irreversibili.

**Soluzione.** Far dipendere lo stato idrico da una risposta continua del suolo e dalla persistenza del deficit, senza consentire che una pioggia minima annulli da sola lo stress. La ripresa deve essere sostenuta da una ricarica coerente, o da un modello di ricarica dichiarato se il suolo non è disponibile. Rendere continua la modulazione temporale ed evitare la scelta discreta di una sola finestra per un blending di eventi. Produrre uno stato di qualità esplicito se mancano i dati necessari.

Non copiare senza verifica l'interruttore `ΣET₀ > P_trigger`: anche questa condizione introdurrebbe una soglia, e ET₀ non è una perdita reale misurata. Aggiornare insieme algoritmo, test e AGENTS. Presentare le soglie come prior di progetto; usare «rischio di disseccamento» finché il danno non è sostenuto da dati specifici.

**Chiusura.** Test 0,99/1,01 mm con θ identica; test 4/5/6 giorni; sweep del blending intorno a 0,5; suolo assente; ricarica reale contro semplice pioviggine. Nessuna riattivazione numerica forte su suolo ancora secco. Non imporre universalmente `12 giorni senza pioggia ⇒ indice ≤35` indipendentemente da suolo/specie: è una fixture di stress condizionata, non una legge biologica.

## 5. RES-04 — Unificazione operativa ancora incompleta; doppio buffering nel core

**Priorità P1 — confermato.**

Android usa ancora `MushroomAlgorithms` nel viewmodel operativo: non delega l'intera analisi a `MycoAnalysisEngine`. Il core contiene ora una pipeline più completa, ma alcune formule sono diverse da Android. Due discrepanze sono direttamente verificabili:

1. **Buffering applicato due volte nel meteo core.** `MycoAnalysisEngine.analyze()` crea `bufferedDays`, poi passa quegli stessi giorni a `weatherScore(... canopyCover = siteCanopyCover)`. `weatherScore()` applica nuovamente `applyCanopyBuffering`. La fase e i fattori vedono una trasformazione, il meteo ne vede due; anche gli outlook ripetono questo percorso.
2. **Formule della chioma non coincidenti.** Android limita gli offset a una frazione dell'escursione termica e preserva l'ordine minimo/media/massimo. Il core usa ancora una formula diversa, che può invertirli. Con minimo 12 °C, massimo 13 °C, media 12,5 °C e canopy 0,8, la formula core restituisce minimo **12,96**, massimo **12,20** e media **13,16 °C**. È una violazione del contratto fisico dei dati.

Per la pioggia l'effetto doppio è altrettanto chiaro: con 50 mm e canopy 0,8, il throughfall è circa **43,98 mm dopo un passaggio**, **38,68 mm dopo due**. Non è una seconda intercettazione fisica dichiarata.

Anche la compensazione del chilling nella convoluzione differisce: Android cerca il trauma negli ultimi cinque elementi e aggiunge 1,5 giorni al picco; il core usa quattordici elementi e aggiunge 2 giorni. Copiare funzioni nel core non dimostra parità.

**Soluzione.** Un solo punto di trasformazione: o il motore passa dati grezzi e le funzioni correggono una volta, oppure il motore produce dati normalizzati e le funzioni ricevono un tipo esplicito che non ricorreggono. La seconda opzione facilita il tracciamento. Condividere una sola implementazione e usare lo stesso contratto su Android/iOS. Testare che temperatura, pioggia, fase e spiegazioni usino la stessa versione del dato.

`EndToEndOperationalParityTest` verifica casi del core, ma non confronta le app tramite i loro adattatori. `FullAnalysisResultParityTest` continua invece a chiamare `analyzeLegacy`. La CI non esercita quindi la parità richiesta in modo sufficiente.

**Chiusura.** Pioggia corretta una volta; `min ≤ mean ≤ max` per input validi; confronto con tolleranza documentata dei componenti e dell'indice, usando gli adattatori realmente chiamati dalle app. Includere bassa DTR, chilling e canopy non nulla. Non rinominare semplicemente il test legacy come operativo.

**Riferimenti:** [MycoAnalysisEngine](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/core/src/commonMain/kotlin/github/naturewhisp/myco/core/MycoAnalysisEngine.kt), [MycoAlgorithms core](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/core/src/commonMain/kotlin/github/naturewhisp/myco/core/MycoAlgorithms.kt), [viewmodel Android](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/app/src/main/java/github/naturewhisp/myco/ui/viewmodel/MushroomViewModel.kt), [test operativo core](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/core/src/commonTest/kotlin/github/naturewhisp/myco/core/EndToEndOperationalParityTest.kt), [test di parità legacy](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/app/src/test/java/github/naturewhisp/myco/FullAnalysisResultParityTest.kt).

## 6. RES-05 — OSM resta un proxy a centroidi, non una copertura fisica

**Priorità P1 — confermato.**

Il fallback UNKNOWN è stato migliorato, ma `extractHabitatEvidence()` continua a calcolare copertura dagli otto settori occupati dai centroidi, con minimi 0,75/0,50 in funzione della distanza del centro. La query restituisce ancora centri, non poligoni. Il ticket REV2-06 dichiara invece completata una misura per unione geometrica delle superfici.

L'indicatore è ancora usato come canopy e come input di una conversione in area basimetrica. Una presenza forestale per direzione non è né frazione di superficie a bosco, né copertura delle chiome, né area basimetrica. Queste differenze alterano anche la pioggia al suolo nel caso Mindino.

Su iOS `canopyCover` viene inoltre impostata dal punteggio habitat quando questo è ≥0,6: un indice di idoneità è trasformato in proprietà fisica della vegetazione. Non è equivalente all'evidenza Android.

**Soluzione.** Implementare realmente geometrie/unioni/intersezioni se si vuole la frazione di area forestale; mantenere separata la canopy e usare una misura adeguata o uno scenario dichiarato. In alternativa declassare l'output a indicatore di presenza e sospendere la pretesa di unità dendrometriche. Non derivare canopy dal punteggio habitat. Rendere gli stati della documentazione aderenti all'implementazione.

**Chiusura.** Invarianza alla suddivisione dei poligoni; distanza zero per punto interno; piccolo boschetto vicino senza 75% di copertura automatico; nessun dato habitat convertito in canopy fisicamente conosciuta. Stesso modello di evidenza nelle due piattaforme.

**Riferimenti:** [MushroomRepository](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/app/src/main/java/github/naturewhisp/myco/repository/MushroomRepository.kt), [OverpassModel](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/app/src/main/java/github/naturewhisp/myco/model/OverpassModel.kt), [viewmodel iOS](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/iosApp/MycoIOS/App/MycoViewModel.swift).

## 7. RES-06 — Data, disponibilità dei dati e neve ancora gestite parzialmente

**Priorità P1 — confermato.**

- iOS mantiene `todayIndex = min(14, days.count - 1)`; Android cerca la data, ma se manca usa un ripiego posizionale. I giorni trascorsi sono differenze di indice, senza validazione di continuità del calendario.
- Android richiede 28 giorni di passato, iOS 14, a fronte di una convoluzione con supporto di 26 elementi. Anche senza altri bug, uno storico meno esteso può omettere apporti ancora pesati dal modello. Questo è particolarmente pertinente alle piogge precedenti al 17 settembre.
- Il mapper Android aggiunge zeri per serie orarie più corte; quello iOS può assegnare zero a giorni senza temperatura/umidità o precipitazioni valide. Il core non espone un contratto completo di dati insufficienti o finitezza.
- `liquidPrecip` resta un filtro basato su codice meteo giornaliero e temperatura media: non separa quantitativamente pioggia e neve in una giornata mista. Le query non richiedono le componenti liquide dedicate.
- Le finestre della UI e quelle del meteo possono essere diverse, senza che la distinzione sia visibile all'utente.

Il caso Mindino non può essere riprodotto impostando semplicemente la data del sistema a posteriori: occorre anche sapere **quali dati erano disponibili al momento dell'analisi**. Un payload ottenuto oggi può differire da quello usato il 29 settembre.

**Soluzione.** Contratto esplicito con `targetDate`, `analysisAsOf`, fuso del luogo, copertura e provenance; nessun ripiego silenzioso su un indice. Storico sufficiente e identico; buchi rappresentati come tali. Validazione `isFinite`, range e numerosità. Pioggia/rovesci acquisiti separatamente dalla neve, con fallback dichiarato. Finestre diagnostiche allineate o denominate con periodo esatto.

**Chiusura.** Storico più lungo non cambia la data target; un giorno mancante non comprime la latenza; stessa serie valida in entrambe le app; dati assenti non equivalgono a siccità osservata. Test di giornata parziale, cambio fuso, NaN e precipitazione mista.

**Riferimenti:** [ApiServices Android](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/app/src/main/java/github/naturewhisp/myco/network/ApiServices.kt), [OpenMeteoClient iOS](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/iosApp/MycoIOS/Data/OpenMeteoClient.swift), [mapper iOS](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/iosApp/MycoIOS/DomainAdapters/OpenMeteoDomainMapper.swift), [Domain core](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/core/src/commonMain/kotlin/github/naturewhisp/myco/core/Domain.kt).

## 8. RES-07 — Residui di interpretazione SPUN nella mappa e nelle spiegazioni

**Priorità P2 — confermato.**

La dipendenza numerica dalle ife AM per saprotrofi e parassiti è stata eliminata nei rami esaminati, ma i sostituti non costituiscono nuove misure validate:

- per saprotrofi la mappa assegna un potenziale uniforme 50 dove il raster è considerato valido;
- per parassiti usa ricchezza EcM come proxy della matrice arborea ospite, senza prova che misuri disponibilità di legno o ospiti della specie bersaglio;
- il test di validità della cella resta `ecm == 0 && hyphal == 0`: anche AM può determinare dove compare il layer saprotrofico, pur non variandone il valore;
- il core presenta ancora «Biomassa ifale» con livello FAVORABLE per un layer AM non usato nel modello operativo.

**Soluzione.** Nella mappa di contesto scelta dal progetto, distinguere validità del dataset, presenza ambientale e valore del proxy. Un 50 costante non deve apparire come previsione locale informata. Per parassiti usare dati su substrato/ospiti oppure dichiarare l'assenza della covariata pertinente. Mostrare AM come layer informativo sperimentale, nominato con gilda, senza badge di favorevolezza per la specie bersaglio. Tenere distinta ricchezza di comunità da biomassa o fruttificazione.

**Chiusura.** Modificare solo AM non cambia né indice né supporto cartografico operativo della specie quando il layer AM è dichiarato estraneo. I dati non pertinenti sono informativi, non favorevoli per definizione.

**Riferimenti:** [HeatmapGenerator](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/app/src/main/java/github/naturewhisp/myco/utils/HeatmapGenerator.kt), [HeatmapEngine](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/core/src/commonMain/kotlin/github/naturewhisp/myco/core/HeatmapEngine.kt), `factors` in [MycoAnalysisEngine](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/core/src/commonMain/kotlin/github/naturewhisp/myco/core/MycoAnalysisEngine.kt).

## 9. RES-08 — Prove, registro parametri e dichiarazioni di completamento restano disallineati

**Priorità P1 per documentazione e copertura dei difetti; P2 per validazione esterna.**

Il test `ComparativeValidationTest` confronta stazioni **sintetiche** con condizioni impostate nel codice e baseline stagionale. È utile come controllo di comportamento, ma non misura utilità predittiva su raccolte indipendenti. La differenza di punteggio chiamata `informationGain` non è automaticamente informazione guadagnata nel senso statistico. Le nuove fixture generano inoltre date con `"2026-09-${index + 1}"` su 35/36 elementi, reintroducendo 31–36 settembre; tali stringhe possono passare se si legge solo il mese.

`ParameterRegistry` è un progresso documentale, ma non alimenta attualmente le formule: le costanti restano duplicate. È possibile cambiare una formula senza aggiornare il registro. La documentazione tecnica contiene anche parametri dichiarati MEASURED, con generalizzazioni che richiedono una fonte precisa e un dominio di trasferibilità. Un riferimento a un rapporto o a «Mindino Gate» non equivale a misura fisiologica.

Rimane il requisito C1 non soddisfatto su tutto il dominio da alcune curve: la CTMI adottata è continua, ma almeno a uno dei cardinali la derivata interna non coincide con quella del tratto esterno costante a zero. Non è necessario cambiare una curva soltanto per una preferenza estetica: occorre decidere il requisito e testarlo correttamente.

I ticket REV2-04/06/09 dichiarati completati sovrastimano rispettivamente unificazione, geometrie e validazione. AGENTS contiene inoltre un criterio ET₀ del gate non implementato dalla funzione.

**Soluzione.** Collegare il registro alla configurazione realmente usata oppure verificare automaticamente identità/versione delle costanti. Distinguere misurato, stimato, trasferito da letteratura ed expert prior; aggiungere limiti di applicazione. Generare date con un calendario reale. Separare chiaramente test sintetici, integrazione e verifica comparativa osservativa. Correggere gli stati documentali a parziale finché i criteri non sono soddisfatti.

Per validare A sul campo, registrare anche uscite negative, specie, durata/sforzo, area e data; congelare la versione e confrontarla fuori campione con baseline semplici su periodi/siti distinti. La verifica di ranking/utilità di A non richiede una probabilità calibrata e non deve essere rinviata a B.

**Chiusura.** Tutte le date parsabili; test delle pipeline reali; registro coerente con la configurazione effettiva; nessun benchmark sintetico presentato come validazione osservativa. Formule e requisiti di continuità descritti con precisione.

**Riferimenti:** [ComparativeValidationTest](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/app/src/test/java/github/naturewhisp/myco/ComparativeValidationTest.kt), [ParameterRegistry](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/core/src/commonMain/kotlin/github/naturewhisp/myco/core/ParameterRegistry.kt), [TECHNICAL_DOCUMENTATION](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/docs/TECHNICAL_DOCUMENTATION.md), [FUTURE_DEVELOPMENTS_ANALYSIS](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/docs/FUTURE_DEVELOPMENTS_ANALYSIS.md), [AGENTS](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/AGENTS.md).

## 10. RES-09 — La sintesi AI e alcune diciture possono rendere il responso meno veritiero

**Priorità P1 per sintesi/contraddizioni; P2 per presentazione — confermato dal sorgente, senza verifica visuale su dispositivo.**

La revisione delle informazioni a video ha individuato problemi ulteriori rispetto al badge «Innesco»:

- **Prompt AI Android:** descrive il valore fenologico con «Pioggia ultimi 10 giorni», mentre il dato proviene dalla convoluzione su 26 elementi; chiede di valutare «le probabilità di comparsa». Non passa in modo esplicito il gate di siccità, il moltiplicatore finale della fase e i valori pedologici che possono limitare il risultato. Una sintesi ottimista può quindi contraddire la riga stress anche dopo una correzione numerica.
- **Titolo «OSSERVAZIONI DI CAMPO & MODELLO BIOLOGICO»:** il testo generato da dati remoti non è un'osservazione micologica sul posto. Una descrizione plausibile non deve diventare un fatto osservato.
- **Mappa Android:** il titolo «CONTESTO ECOLOGICO E BIOMASSA» e il testo sui macromiceti suggeriscono una misura di biomassa locale; le covariate non sostengono questa interpretazione. La legenda conserva «Innesco/Propizio/Culmine», semanticamente simili alle fasi della scheda.
- **Fase lunare:** «Fase crescente propizia» e livello FAVORABLE la fanno sembrare un fattore scientificamente dimostrato. Se non è parte del modello validato, deve essere solo informativa, senza indicazione di favorevolezza biologica.
- **Grafico iOS:** temperatura e pioggia condividono l'asse «°C / mm». Due grandezze con unità diverse su una scala non spiegata possono suggerire comparazioni numeriche prive di significato.
- **Qualità/stato dei dati:** un valore ordinario non permette da solo di distinguere analisi completa, cache vecchia, dati parziali e suolo non disponibile. La UI deve riflettere questi stati anziché affidarsi a una nota generica.

**Soluzione della sintesi.** Generare prima una spiegazione deterministica dal medesimo `AnalysisResult` mostrato a video: indice, fase inferita, fattore limitante, finestre e qualità. La sintesi AI può soltanto riformularla, con divieto di inventare rugiada, eventi piovosi, carpofori o probabilità. Se non riesce a rispettare il contratto, mostrare la spiegazione deterministica. Trasmettere i dati di stress e le finestre effettive; evitare un secondo calcolo o una narrazione indipendente.

Usare «Sintesi delle condizioni stimate»; «osservazioni» solo per dati realmente registrati sul campo. Mantenere la distinzione anche nelle esportazioni e nelle note salvate.

### 10.1 Specifica delle informazioni a video

| Elemento | Dicitura/comportamento proposto | Informazione necessaria |
|---|---|---|
| Titolo principale | «Indice di idoneità micologica» | Specie, punto e data target |
| Valore | `29/100 · favorevolezza bassa` | Punteggio A, senza percentuale |
| Spiegazione breve dell'indice | «Sintesi delle condizioni ambientali considerate dal modello» | Aiuto espandibile: non misura la frequenza di raccolta |
| Stato prioritario | «Suolo superficiale secco» oppure «Rischio di disseccamento» | Solo se supportato dalla diagnosi; mai dall'indice da solo |
| Fase | «Fase potenziale stimata dalle piogge» | Cronologia + stato idrico; qualità se non determinabile |
| Pioggia osservativa/modellata | «Pioggia cumulata» con periodo, oppure «Ultimo episodio significativo» | Distinguere sopra chioma/sotto chioma; soglia nel dettaglio |
| Pioggia pesata | «Apporto ponderato per latenza» | mm ponderati, finestra 26 giorni, «non indica acqua residua» |
| Suolo | «Umidità del suolo stimata · 0–7 cm» | θ, profondità, fonte e periodo; nessuna implicazione di sensore sul posto |
| ET₀ | «Domanda evaporativa di riferimento» | mm/giorno e periodo; «non è perdita effettiva misurata nel bosco» nel dettaglio |
| Qualità | «Dati completi/parziali · aggiornati …» | Copertura effettiva e timestamp; niente percentuale di confidenza inventata |
| Dato mancante | «Non disponibile» | Non sostituire con 0 o «neutro» senza spiegazione |
| Layer mappa | «Contesto ecologico SPUN» | Covariata/gilda, risoluzione e limiti; «non localizza funghi presenti» |
| Legenda mappa | «Valore basso/medio/alto del layer» | Soglie proprie del layer; niente fasi biologiche |
| Fase lunare | «Fase lunare · informazione astronomica» | «Non usata nel calcolo» se vero; nessun badge favorevole |
| Testo AI | «Sintesi delle condizioni stimate» | Aderenza ai dati strutturati e alla diagnosi di stress |
| Previsioni | «Indice stimato per [data]» | Separare dati futuri da storici e la loro acquisizione |

Il riferimento «26 giorni» deve essere mostrato solo se quello è il periodo reale disponibile; se i dati coprono 14 giorni, indicare «storico incompleto rispetto alla finestra del modello». Non nascondere il limite sotto una dicitura standard.

### 10.2 Ordine consigliato nella scheda

1. Località/punto, specie, **data dell'analisi** e aggiornamento dei dati.
2. Indice e classe neutra, seguiti subito dal principale limite e dalla qualità.
3. Stato idrico e fase potenziale: separati, con spiegazione del rapporto fra i due.
4. Fattori meteorologici/ecologici; dettaglio espandibile con periodo e origine.
5. Sintesi deterministica/AI e dati informativi di contesto.

Per Mindino, un esempio di presentazione coerente, **da usare soltanto dopo riconciliazione della diagnosi**, è:

> **Mindino · [specie selezionata] · 29 settembre 2026**  
> **29/100 — favorevolezza bassa**  
> **Limite principale: stress idrico superficiale**  
> Ultima pioggia significativa: 17 settembre, secondo i dati disponibili.  
> Apporto ponderato per latenza: 35 mm ponderati. Indica l'effetto temporale delle piogge precedenti, non l'acqua ancora disponibile nel suolo.

Il testo non dichiara «nessuna pioggia dopo il 17» finché non sono controllati anche gli apporti sotto la soglia significativa. Non dichiara rugiada favorevole in assenza di dati pertinenti. I numeri dell'esempio sono quelli segnalati dall'utente, non nuove misure.

### 10.3 Regole di implementazione e verifica

- Costruire un unico modello di presentazione da risultato, diagnosi e qualità della **stessa analisi**. Durante aggiornamenti, evitare combinazioni tra indice nuovo e fattori vecchi; mostrare una fase di aggiornamento o conservare l'intero risultato precedente con timestamp.
- Le etichette sullo stress devono dipendere da uno stato strutturato, non da ricerche di parole in stringhe localizzate. Distinguere «suolo secco», «possibile rischio per nuovi primordi», «fase temporale potenziale» e «danno osservato».
- Usare colori insieme a testo/icona: un pallino verde non basta e non deve qualificare come favorevole un dato solo informativo. Mantenere leggibili i dettagli di periodo e qualità.
- Separare grafico pioggia e temperatura in due pannelli, oppure usare assi chiaramente distinti con unità e legenda; evitare un asse unico non interpretabile.
- Testare Android/iOS, accessibilità parlata, caratteri ingranditi e schermi stretti con le diciture lunghe. I valori a destra devono restare compatti; le spiegazioni stanno sotto o nel dettaglio.
- Casi obbligatori: 29/stress, indice alto ma fase ignota, suolo assente, cache scaduta, giorno parziale, nessuna pioggia significativa e possibile pioviggine. La nota AI non può contraddire nessuno stato bloccante o limitante.

**Limite della revisione UI:** sono state lette le implementazioni Compose/SwiftUI; non sono stati verificati layout, contrasto o comportamento visuale su un dispositivo. L'ispezione automatica orientata al frontend web ha analizzato i prototipi HTML, non certifica le schermate native. La verifica visuale va condotta da Antigravity su build realmente installata.

**Riferimenti:** [HomeScreen](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/app/src/main/java/github/naturewhisp/myco/ui/screens/HomeScreen.kt), prompt in [MushroomViewModel](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/app/src/main/java/github/naturewhisp/myco/ui/viewmodel/MushroomViewModel.kt), [MapScreen](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/app/src/main/java/github/naturewhisp/myco/ui/screens/MapScreen.kt), [FactorRow](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/app/src/main/java/github/naturewhisp/myco/ui/components/FactorRow.kt), [ForecastView iOS](https://github.com/naturewhisp/myco/blob/d2822246d988e125d98892e57320d88e016a1014/iosApp/MycoIOS/Features/ForecastView.swift).

## 11. Rugiada: valutazione delle evidenze e decisione sull'algoritmo

**Conclusione:** l'intuizione merita un approfondimento scientifico. Esistono prove sperimentali del ruolo dell'umidità non piovosa nell'attività fungina e prove pertinenti del ruolo della domanda evaporativa nei carpofori. **Le fonti esaminate non forniscono però un effetto quantitativo direttamente trasferibile della rugiada sulla fruttificazione di B. edulis a Mindino.** Non è giustificato introdurre ora un bonus universale, millimetri di rugiada ricavati dalla sola RH o un reset automatico della siccità.

La ricerca svolta è mirata, non una revisione sistematica esaustiva. L'assenza di una prova diretta trovata non dimostra che non possa esistere; limita la decisione attuale ai risultati verificabili sotto.

### 11.1 Evidenze primarie, pertinenza e limiti

| Fonte | Risultato pertinente | Cosa non autorizza per Myco |
|---|---|---|
| **Jacobson et al., 2015**, PLOS ONE, [DOI 10.1371/journal.pone.0126977](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0126977) | Esperimenti su lettiera nel Namib mostrano rapida riattivazione di funghi decompositori quando il substrato si bagna; risposta diversa fra materiale fine e grossolano. Nebbia e alta umidità possono alimentare attività senza pioggia. La rugiada è discussa come possibile ulteriore fonte, non isolata come causa di produzione di porcini. | Trasferire soglie/tempi a Boletus o equiparare decomposizione, crescita ifale e formazione di carpofori |
| **Evans et al., 2020** (online 2019), Ecosystems, [DOI 10.1007/s10021-019-00461-y](https://link.springer.com/article/10.1007/s10021-019-00461-y) | Misure di campo e modellazione in ambienti prativi sostengono il contributo dell'umidità non piovosa alla respirazione microbica e decomposizione. Durata della bagnatura e caratteristiche del substrato sono importanti; il proxy basato su RH necessita di calibrazione locale. | Considerare la RH media una misura di rugiada o applicare il risultato alla buttata forestale della specie bersaglio |
| **Lilleskov et al., 2009**, New Phytologist, [DOI 10.1111/j.1469-8137.2009.02775.x](https://nph.onlinelibrary.wiley.com/doi/10.1111/j.1469-8137.2009.02775.x) | Misure su sporocarpi ectomicorrizici, incluso B. edulis, collegano perdita d'acqua a VPD e morfologia. Analisi isotopiche sono compatibili con accesso ad acqua profonda durante siccità, con limiti nell'attribuzione delle sorgenti. | Concludere che la rugiada inneschi la fruttificazione; imporre che suolo superficiale secco equivalga sempre a impossibilità biologica |
| **Sibley et al., 2022**, Agricultural and Forest Meteorology, [DOI 10.1016/j.agrformet.2022.109069](https://andrewsforest.oregonstate.edu/publications/5232) | Quattro anni di misure in un Douglas-fir mostrano variazioni verticali della bagnatura; nella stagione secca la rugiada era concentrata nella parte alta della chioma. Il modello migliore dipendeva anche dalla posizione delle misure. | Assumere che rugiada sulla chioma raggiunga automaticamente lettiera o micelio, o trasferire un modello di radura al sottobosco di Mindino |

Queste prove sono solide per i rispettivi processi misurati, ma la trasferibilità al bersaglio dell'app varia. La conclusione operativa è una **inferenza di progetto**: conservare l'ipotesi, sviluppare misure pertinenti e non attribuirle un coefficiente di fruttificazione non stimato.

### 11.2 Meccanismi da tenere distinti

1. **Minor perdita d'acqua:** aria con basso deficit di pressione di vapore può ridurre la domanda evaporativa sui carpofori. È il meccanismo con evidenza più direttamente pertinente alle specie ectomicorriziche, ma non è la stessa cosa della condensazione.
2. **Bagnatura superficiale/lettiera:** rugiada, nebbia e assorbimento di vapore possono mantenere bagnato un substrato. La risposta dipende da superficie, durata e posizione; attività di decomposizione non equivale a buttata.
3. **Apporto alla riserva idrica:** richiede quantità e destinazione dell'acqua. Condensazione sulla chioma, bagnatura della lettiera e ricarica del suolo sono stati diversi. Senza misure, non ricavare l'uno dall'altro.
4. **Formazione di nuovi carpofori e persistenza di quelli presenti:** vanno studiati separatamente. Una notte favorevole alla persistenza non prova l'innesco di un nuovo ciclo riproduttivo.

La rugiada si verifica quando una superficie raggiunge condizioni di condensazione: conoscere T e RH dell'aria fornisce informazioni sul punto di rugiada, ma non misura temperatura della superficie né quantità deposta. Un prato esposto e una lettiera sotto chioma non possono ricevere la stessa correzione per definizione.

### 11.3 Evoluzione raccomandata, senza un bonus prematuro

**Fase di ricerca:** acquisire serie orarie T/RH e, se disponibili, punto di rugiada e VPD. T/RH permettono già di calcolare un VPD dell'aria, ma non la rugiada depositata. Sviluppare diagnostiche di durata della bassa domanda evaporativa e di bagnatura notturna; tenerle inizialmente fuori dal punteggio operativo.

Il VPD è un candidato scientificamente più diretto per descrivere il rischio di perdita d'acqua rispetto a una sola RH media giornaliera. Sostituire o ridefinire la componente d'aria potrebbe essere preferibile ad aggiungere un nuovo bonus correlato: RH, VPD ed ET₀ non sono informazioni indipendenti. **La formula fisica del VPD è determinabile; la sua trasformazione in punti di idoneità richiede comunque stima/verifica specifica**, non è fornita dal solo articolo.

Per una componente rugiada esplicita usare un parametro/oggetto separato, ad esempio `nonRainfallWetnessDuration`, con provenienza, posizione e qualità. Non aggiungerlo a `precipitation`, non farlo diventare un evento di pioggia e non rinominare il cumulato. Non usare soglie universali del tipo «RH >95% per due ore ⇒ buttata»; valori studiati su altri substrati non sono regole per il porcino.

**Nessun contributo operativo senza validazione pertinente.** Il monitoraggio può iniziare durante il consolidamento di A; non implica implementare B. Se la nuova covariata non aggiunge capacità utile oltre suolo/pioggia/VPD, lasciarla informativa oppure escluderla.

### 11.4 Disegno di prova per Mindino e ambienti comparabili

- Rilevare microclima vicino alla lettiera e almeno un confronto in radura/sotto chioma; misurare T/RH, temperatura superficiale ove possibile e durata della bagnatura con sensori appropriati. Le sole misure del telefono non bastano.
- Separare pioggia, nebbia e condensazione con osservazioni e strumenti; un sensore di bagnatura segnala acqua sulla superficie, non da solo la sua sorgente o il volume. Per stimare deposizione usare un metodo di massa/micro-lisimetria validato per quella superficie, correggendo artefatti.
- Registrare umidità/potenziale idrico del substrato, tessitura, condizioni degli ospiti e profondità pertinente. Non attribuire alla rugiada ciò che può derivare da riserve profonde o microclima.
- Marcare le parcelle e distinguere nuovi primordi, nuovi carpofori, crescita e sopravvivenza di quelli presenti; registrare specie ed esiti negativi. Non usare conteggi di spore come equivalenti di raccolta.
- Confrontare periodi senza pioggia con diversa bagnatura notturna a parità, per quanto possibile, di stagione, temperatura, substrato e stato idrico. Se fattibile e progettata con competenza, una manipolazione di condensazione/bagnatura può aiutare l'attribuzione causale, ma deve controllare gli effetti collaterali su radiazione e ventilazione.
- Preregistrare ipotesi e criteri; determinare durata/numerosità con variabilità ed effetto atteso, evitando un campione fissato per convenienza. Validare su altri periodi e siti, tenendo separati quelli usati per stimare i parametri.
- Confrontare modello A consolidato, A con descrittore VPD e A con informazione aggiuntiva di bagnatura. Valutare utilità di ranking, errore rispetto al bersaglio e stabilità dei parametri; evitare doppio conteggio con RH/ET₀/suolo.

**Regola di decisione:** se le misure predicono bagnatura ma non migliorano il bersaglio micologico fuori campione, non modificare l'indice. Se migliorano il bersaglio con effetto riproducibile e limiti definiti, valutare l'introduzione di una componente della versione A successiva, dichiarando il dominio geografico/ecologico.

Per il caso del 29 settembre, questi studi **non permettono di affermare che a Mindino vi fosse rugiada né che spiegasse 29**. Consentono di progettare un'indagine, e suggeriscono di considerare sia perdite evaporative sia disponibilità idrica profonda invece di assimilare tutte le fonti d'umidità alla pioggia.

## 12. Procedura d'indagine per Antigravity: Mindino, 29 settembre

### 12.1 Obiettivo e materiale da preservare

Prima di modificare formule o svuotare cache, acquisire il caso che produceva 29. Non tarare l'algoritmo per ottenere un valore desiderato.

Creare un dossier di debug locale con:

- screenshot completo dell'indice, dei fattori e del dettaglio ultima pioggia; distinguere quale riga riportava stress: suolo, stato miceliare o testo AI;
- versione app, versionCode, commit del build se disponibile, piattaforma e identificativo della configurazione; identificare quale correzione era realmente installata il 29;
- coordinate del punto selezionato e della cella restituita dal provider, quota richiesta/restituita, raggio habitat, canopy e sua origine;
- specie/id, modalità ALL o WEATHER_ONLY, eventuali impostazioni;
- data target `2026-09-29`, ora effettiva dell'analisi e fuso Europe/Rome; conservare la distinzione fra dato storico, previsione e giorno parziale;
- richiesta API completa senza credenziali, risposta grezza, timestamp di acquisizione, unità, modello/provider se disponibile;
- cache originale con chiave, data, scadenza, fallback offline; stato del caricamento e generazione della richiesta per escludere sovrascritture asincrone;
- risposta habitat e campione SPUN, con relativa versione;
- hash dei file e una fixture immutabile ottenuta da questi dati.

Non sostituire il payload originale con una nuova richiesta senza etichettarla come ricostruzione. Se l'originale è perduto, dichiarare precisamente cosa è osservato, cosa recuperato da archivio e cosa simulato. Il nome «Mindino» non basta: non scegliere silenziosamente coordinate diverse dal punto usato dall'utente.

### 12.2 Tabella giornaliera obbligatoria

Esportare almeno 26 giorni prima del target più il giorno target; idealmente anche margine per verificare la completezza. Per ogni data:

| Campo | Verifica richiesta |
|---|---|
| Data locale e indice | Ordine, duplicati, buchi; differenza in giorni reali |
| Ore valide per variabile | Non assumere 24 se parziale o incompleta |
| Pioggia totale, liquida, neve | Origine, unità e assenza/zero distinti |
| Apporto sotto chioma | Valore e numero di applicazioni del buffering |
| T min/media/max, RH | Grezzi e corretti; ordine min/media/max |
| θ superficiale/profonda | Valori, fonte, profondità e disponibilità |
| ET₀ giornaliera | Somma oraria valida, unità; non media delle somme |
| Età della pioggia, peso f(τ) | Parametri della specie e modifica chilling |
| Contributo P×f(τ) | Prima/dopo compensazione profonda |
| Evento candidato/cluster | Motivo di inclusione, esclusione e volume |
| Osservato o forecast | Istante di disponibilità e uso nella previsione |

Ricostruire sia gli episodi dal 17 settembre sia eventuali apporti precedenti e piccole precipitazioni successive. «Ultima pioggia significativa» cerca ≥5 mm e, come fallback, ≥2 mm; il conteggio del gate usa 1 mm e l'estrazione degli eventi altre soglie. **La data 17 settembre non dimostra l'assenza di tutti gli apporti successivi**, perché la UI e il gate hanno criteri differenti.

### 12.3 Traccia completa dell'indice

Aggiungere temporaneamente una modalità diagnostica deterministica, senza affidare la ricostruzione a testo AI. Registrare:

```text
analysisId, buildCommit, engineVersion, configurationHash
requestedCoordinates, providerGridCoordinates, targetDate, analysisAsOf, timezone
rawDaysHash, normalizedDaysHash, missingDataSummary
canopyValue, canopySource, bufferingPassCount
speciesId, tauPeak, alpha, chillingWindow, chillingFlag, effectiveTauPeak
weightedRainBeforeDeepFactor, deepFactor, weightedRainAfterDeepFactor
rainComponent, temperatureComponent, humiditySoilComponent, shockComponent
thermalViability, weatherScoreBeforeRounding, weatherScoreAfterRounding
candidateEvents[], clusters[], recentTrigger, earlierTrigger
blendWeight, triggerSelectedForDrought, nDry
soilPostTriggerAverage, soilRecentAverage, soilCurrent, effectiveSoil
phiBase, phiDrought, phiFinal, inferredStage
habitatScore, altitudeScore, seasonalityScore, terrainModifier, hurdleFactor
rawSuitability, normalizedSuitability, displayedInteger
numericClassLabel, phaseLabel, limitingFactors[], factorWindows[]
```

Alcuni campi non sono ancora esposti dalle API: strumentare la configurazione di debug o una funzione pura di explainability. Conservare i valori non arrotondati. Alla fine deve essere possibile verificare per moltiplicazione il punteggio 29 e spiegare ogni millimetro visualizzato.

Controllare in particolare:

1. I 35 mm sono cumulato lordo, singolo evento, throughfall oppure convoluzione? Coincidono con l'input effettivo del meteo?
2. Lo stress indicato è solo un'annotazione su θ corrente o è anche un `phiDrought < 1` effettivamente applicato?
3. È presente anche un apporto ≥1 mm dopo il 17, non visibile come «significativo», che spegne il gate?
4. La pioggia vecchia alimenta ancora shockScore e rainScore? Quantificare i contributi.
5. Quanti passaggi canopy sono stati applicati? Il core può usarne due nel meteo.
6. Fattori e indice appartengono alla stessa analisi/versione oppure a stati aggiornati in momenti diversi?
7. La modalità WEATHER_ONLY neutralizza habitat/altitudine/season/terrain: era attiva nel caso?
8. Specie o coordinate sono cambiate dopo il caricamento senza una ricostruzione coerente di tutti i fattori?

### 12.4 Esperimenti controllati sulla fixture

Ogni esperimento modifica una sola famiglia di input. Usare la stessa data, specie e configurazione, evitando di interpretare una variazione come validazione sul campo.

| Esperimento | Risultato da esaminare |
|---|---|
| Originale congelato su build originario e HEAD | Riproduzione del 29, effetto preciso delle correzioni |
| Stesso caso con canopy zero e poi canopy reale | Un solo passaggio; separare effetto termico e intercettazione |
| Stessa pioggia, θ mantenuta alta/bassa | Coerenza dello stato idrico e della fase, non solo numero |
| Solo ET₀ variata | R_pheno invariato per definizione attuale; quantificare reale sensibilità di W/S |
| Solo pioggia target 0,99/1,01 mm liquidi | Assenza di riattivazione forte a θ secca invariata |
| 4/5/6 giorni asciutti | Nessun salto spurio dovuto al solo interruttore del gate |
| RH alta/bassa senza altra variazione | Misurare il contributo aria; non chiamarlo rugiada |
| Suolo superficiale mancante | Qualità esplicita, nessuna falsa sicurezza fisiologica |
| Stessa serie con 14/28/60 giorni di archivio | Separare troncamento necessario da invarianza quando il supporto è completo |
| Giorno mancante e cambio fuso | Latenza calcolata da date, nessun target sbagliato |
| Attraversamento delle soglie degli eventi/blending | Sweep sul punteggio finale e sullo stato |
| Android e iOS tramite adattatori reali | Stesso dato normalizzato e stessi componenti |

Per le somme idriche verificare anche intercettazione una sola volta e unità per ora/giorno. Non introdurre un limite globale al punteggio solo per far passare la fixture Mindino.

### 12.5 Come indagare l'ipotesi rugiada

Nel codice attuale non è una sorgente d'acqua esplicita. Antigravity deve prima confermarlo sul build installato, verificando anche prompt e note AI: un testo potrebbe parlare di rugiada senza che sia nel modello.

Se si desidera esplorarne l'effetto, distinguere RH elevata, prossimità al punto di rugiada e **quantità effettiva di condensazione**. Le prime due non forniscono automaticamente la terza. Non convertire una notte umida in pioggia equivalente inventata, non resettare per questo la siccità e non attribuirle retroattivamente il 29. Un futuro modello di condensazione richiederebbe dati e validazione pertinenti al microclima/superficie; non è necessario per correggere gli errori individuati.

### 12.6 Output richiesto ad Antigravity

Produrre:

1. `mindino_2026-09-29_case.json`: fixture proveniente dal payload preservato, con manifest e qualità.
2. Una tabella dei contributi che riconcili i 35 mm e l'indice, con confronto prima/dopo.
3. Test di regressione Kotlin sui difetti effettivamente dimostrati, usando date valide e il percorso operativo.
4. Correzioni ai ticket RES-01/03/04 e, secondo i dati, RES-02/06; motivare qualsiasi scelta del nuovo fattore idrico.
5. Rapporto causale distinto in «confermato», «riprodotto su fixture», «ipotesi non verificata».
6. Allineamento di AGENTS, registro, documentazione e comunicazione all'utente.
7. Prova visuale della scheda, mappa, previsioni e note con la stessa fixture, applicando RES-09; tenere l'eventuale diagnostica rugiada/VPD separata finché non è validata.

Se il payload storico non esiste, il primo risultato sarà una fixture ricostruita dichiarata come tale: il caso non dovrà essere certificato come riproduzione esatta.

## 13. Sequenza di correzione e criteri di consegna

| Ordine | Problema | Azione immediata | Evidenza necessaria per chiuderlo |
|---:|---|---|---|
| 1 | RES-01 | Separare classe numerica e stato; eliminare % su A | Caso 29/stress senza «innesco» |
| 2 | RES-03 | Eliminare bypass siccità a 1 mm e selezioni rigide del gate | Sweep su θ invariata e eventi multipli |
| 3 | RES-04 | Buffering unico, invarianti termiche, unico motore operativo | Confronto completo Android/iOS reale |
| 4 | RES-02 | Rinominare mm ponderati e definire fattore idrico coerente | Bilancio dei contributi di Mindino e test umido/secco |
| 5 | RES-06 | Data target, qualità e supporto storico uniformi | Serie incompleta, fuso, as-of e mapper reali |
| 6 | RES-05 | Geometrie reali o proxy esplicitamente declassato | Segmentazione e significato fisico verificati |
| 7 | RES-07 | Eliminare inferenze ecologiche non sostenute nel layer | Gilda, validità e covariate pertinenti |
| 8 | RES-08 | Stati documentali e prove aderenti al codice | Date valide, registro coerente, prove ben qualificate |
| 9 | RES-09 | Allineare testi, prompt AI, legenda e qualità | Nessuna affermazione biologica non supportata; verifica native UI |

La ricerca sulla rugiada della sezione 11 è facoltativa e subordinata a prove pertinenti; non è una correzione da applicare per far tornare il numero di Mindino.

**Criterio finale:** non è sufficiente un test «indice ≤35» sul caso se restano il badge di innesco, un gate aggirabile e fattori che descrivono dati diversi. La consegna deve rendere spiegabile il numero, coerente lo stato e riproducibile l'intera analisi del percorso A.
