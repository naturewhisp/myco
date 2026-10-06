# Myco — Bonifica operativa del 6 ottobre 2026

Base di sviluppo: `b1a3be60efd178dc3f551ccf8941c50b3fa91b09` (master). Le modifiche sono nel working tree; questo documento non certifica una release iOS o una calibrazione empirica.

## Modifiche e criteri

- `WeatherAggregation` è il mapper condiviso usato dagli adapter produttivi Android e Swift. Conserva copertura per variabile, ore attese nel fuso della località, provenienza e conflitti. Le medie richiedono almeno il 75% delle ore; pioggia ed ET0 richiedono tutte le ore. La soglia è `EXPERT_PRIOR`. Duplicati identici non aggiungono volume; duplicati discordanti invalidano la variabile. Valori assenti non diventano zero.
- Sono conservate anche le giornate tronche, con copertura inutilizzabile. Il target mancante non è sostituito da un giorno adiacente. Le prospettive incomplete espongono `isCalculable=false`, motivi e «Dati incompleti», senza barre o trend numerici fuorvianti. L'analisi corrente viene validata separatamente dalle prospettive.
- `EnvironmentalWindows` seleziona date di calendario: temperatura `[t-5,t-1]`, pioggia `[t-10,t-3]`, umidità/ET0 `[t-3,t]`, suolo `[t-2,t]`. Suolo assente e meteo lacunoso sono motivi concomitanti, anche quando un solo enum indica il degrado principale.
- `HabitatGeometry` unisce superfici OSM nel disco di ricerca, ricompone anelli frammentati, sottrae buchi, elimina duplicati e misura distanze dai segmenti. La quadratura a griglia è un'approssimazione geografica registrata come prior; non misura la chiusura della chioma. Senza geometrie e coordinate valide non vengono inventati copertura o distanza di 50 m.
- Le evidenze sono indipendenti dalla specie. Il core rivaluta la specie selezionata e usa un solo modificatore per ospiti/EcM correlati; EcM non modifica le guild praticole o lignicole. Densità, compatibilità e prossimità rimangono concetti distinti. La presenza di bosco non dimostra necromassa o ceppaie.
- I raccordi habitat, bonus/penalità, esaurimento fenologico, buffering termico ed ET0 usano Hermite C1; la risposta cardinale ha derivata nulla ai limiti. Gli eventi pluviometrici contribuiscono con pesi continui, senza reset al rapporto 70% né soglia di drizzle. L'indice rimane euristico Percorso A; la regolarità matematica non valida i prior biologici.
- Le cache habitat hanno chiave `habitat_geom_v2`, indipendente dalla specie. I vecchi snapshot Swift si decodificano con fallback sicuri; gli indizi mancanti devono essere riacquisiti e non ricostruiti dai punteggi. Preferenze e preferiti sono preservati. I decoder Open-Meteo leggono sia nomi canonici sia alias storici.
- Il ViewModel Android conserva dati ed evidenze e chiama il core per l'analisi scientifica. Rimosso il fetch tardivo di bonus per specie che poteva contaminare le evidenze dopo un cambio località. Le prove AI includono cambi di località, data e modalità con risposta non cooperativa successiva alla cancellazione.
- Il warning JVM CDS dei test Android è eliminato tramite `-Xshare:off`, mantenendo la strumentazione necessaria ai mock.

## Mindino: evidenze e limiti

Prima dell'aggiornamento è stata arrestata l'app sul Pixel `56271FDCH00BHR` ed è stata copiata la cache accessibile in `build/audit/2026-10-06/`. Database: 786432 byte, SHA256 `a321fcdbe906d77c59facce9c6f46f300d8245ad8a5d20c215ea26c89e88a2d0`, `PRAGMA integrity_check=ok`. Journal vuoto; WAL/SHM non presenti all'acquisizione (le risposte testuali di errore ADB sono identificate nel manifest e non considerate sidecar validi). La copia delle preferenze contiene il preferito Mindino, coordinate `44.2148955, 7.9754994`.

Il record corrente del dispositivo e il payload storico non sono lo stesso record. Il payload storico è stato recuperato dall'artefatto locale Antigravity `myco_cache.db`, chat `bbaeae07-45ce-4585-9fe2-6ef60c411ea9`: chiave `weather_44.2149_7.9755`, timestamp `1791133963798`, SHA256 `6fee1178c9ce95e15e2b4828ff74ed72d5256dcb90e49f8bdd91fb9b6b0b5467`, coincidente con la provenienza dichiarata nel test precedente. È conservato senza riformattazione in `testFixtures/mindino/weather-original.json`, con manifest separato.

Il nuovo test attraversa `InMemoryCacheStore`, `CacheManager`, repository reale, mapper produttivo e ViewModel. La rete meteo viene resa esplicitamente offline; il clock storico/target è deterministico. Gli output intermedi, copertura, fattori, qualità, testi e prospettive sono esportati in `build/audit/2026-10-06/replay-production.json`. Specie iniziale *Boletus edulis* e habitat sconosciuto sono assunzioni del replay; il cambio a guild lignicola è uno scenario controfattuale.

**Classificazione: ricostruzione aperta.** Mancano traccia originale di esecuzione, specie/modalità esatte e contesto habitat. La corrispondenza dell'hash certifica il payload recuperato, non l'esecuzione che produsse il vecchio risultato. Nessun valore 29/35/60 è una prova storica o un obiettivo di fitting. I test con piogge/suolo costruiti a mano restano scenari sintetici distinti.

## Verifica e chiusura

Fixture JSON comuni: `testFixtures/weather/coverage.json`, `testFixtures/habitat/surfaces.json`, `testFixtures/mindino/weather-original.json`. Gli stessi file sono risorse XCTest e input dei mapper Android. Si confrontano evidenze e quantità prima dell'arrotondamento UI.

Verifica locale: `testDebugUnitTest`, `:core:allTests`, `lintDebug`, `compileDebugKotlin`, `assembleDebug` e `scripts/check_swift_contracts.py`. Log finale in `build/audit/2026-10-06/verification-final.log`; lint: **0 errori, 0 warning** (hint informativi distinti). Il core e i test common compilano anche per `iosSimulatorArm64`; esecuzione dei test nativi saltata su Windows.

Esito JUnit: **236 test Android e 48 test core, zero fallimenti/errori/skip**. `testDebugUnitTest --rerun-tasks` è stato eseguito anche senza cache dei task, con output privo di warning compiler/JVM. APK finale SHA256 `47671f0523cadb0a130f79152af24c93b92423701b05bd21c429df2a97e212c6`, versione `1.2.0`, codice `2`. Il manifest locale `verification-summary.json` identifica mediante hash i file dello snapshot verificato.

Prove aggiunte: una sola ora, medie al limite 75%, ET0 parziale, duplicati identici/discordanti, target assente, lacune meteo insieme a suolo assente, separazione outlook corrente/futuro, 23/25 ore nel fuso Europe/Rome, buchi e duplicati OSM, tutte le specie/guild, monotonia e derivate dei raccordi, soglie pluviometriche precedenti e risposte AI dopo cancellazione. L'ora autunnale ripetuta senza offset è ambigua e resta conservativamente incompleta; con offset distinti entrambe le ore sono utilizzabili.

La verifica del Pixel è documentata in `build/audit/2026-10-06/device/`. **Build dell'app iOS e XCTest richiedono macOS/Xcode sul medesimo snapshot**: il solo controllo statico non ne certifica il risultato. La revisione indipendente richiesta dall'utente avviene dopo la chiusura dello sviluppo e della verifica dispositivo; i suoi rilievi vengono riportati senza correzioni automatiche.

Esito Pixel: installazione stream dell'APK finale riuscita, app avviata dopo pulizia logcat, preferito Mindino conservato e visualizzato, nessun crash `AndroidRuntime/FATAL` osservato. Screenshot finali: `device/final-launch.png`, `device/final-mindino.png`; rapporto `device/report.txt`. I dettagli dei fattori e la schermata prospettive sono stati acquisiti sulla build preliminare, distinti nel rapporto. La selezione EcM originale è conservata; cambio a guild praticola/lignicola e fallback prospettive non sono stati esercitati nell'ultimo controllo manuale. Le relative prove automatizzate non equivalgono a questo collaudo manuale.

## Correzioni successive alla revisione indipendente

La prima revisione ha rilevato due problemi: riferimento Swift a `payload` anziché `meadowPayload` nel test praticolo; anelli interni irrisolti scartati senza degradare `geometryComplete`. Entrambi corretti. L'assemblaggio conserva ora la completezza; l'intera superficie incompleta è esclusa da copertura/distanza, mentre elementi indipendenti validi sono conservati. La regressione copre buco irrisolto, elementi validi concomitanti e ricomposizione di frammenti invertiti.

Verifica dopo le correzioni: 236 test Android e 49 core passati; build/compile/lint e contratti Swift passati, senza warning compiler/JVM e con lint a zero errori/warning. Log `review-fixes-verification.log`. La revisione viene ripetuta sul working tree completo e il dispositivo sarà ricollaudato sull'APK successivo; gli hash e le prove precedenti sopra riportati descrivono il controllo precedente a queste correzioni.

Seconda revisione indipendente: **OK**, entrambi i rimedi verificati, diff completo rispetto a `b1a3be60efd178dc3f551ccf8941c50b3fa91b09` inclusi staged/unstaged/untracked, nessun ulteriore difetto actionable. APK corretto SHA256 `13e6adf507885ba9e301cf97da0568d65ebcdbf0a70f64895bb098967c980d49`. Il nuovo collaudo Pixel è separato in `build/audit/2026-10-06/device-post-review/`. Il controllo nativo dell'app iOS/XCTest su macOS resta distinto e non eseguito su questo host.

Collaudo conclusivo completato sul Pixel `56271FDCH00BHR`: installazione stream, avvio dopo pulizia logcat, Mindino conservato, fattori e prospettive acquisiti sul medesimo APK corretto, nessun crash applicativo rilevato. Esercitate EcM, *Macrolepiota procera* (habitat praticolo 76/100) e *Armillaria mellea* (habitat lignicolo 99/100, necromassa non verificata); ripristinate specie originale e località Mindino. Prove: `device-post-review/report.txt`, `logcat-filtered.txt`, `original-restored.png` e screenshot delle tre guild. Il fallback prospettive incomplete non è stato esercitato manualmente perché le previsioni erano disponibili; resta coperto dalle prove automatizzate. Ciclo correzioni → seconda revisione OK → nuovo collaudo dispositivo concluso. Nessuna certificazione storica o calibrazione biologica è inferita dai punteggi visualizzati.

## Prima verifica GitHub

Commit c67525f: Android e KMP Core passati, inclusi framework e test core Apple. La build dell'app iOS ha rilevato una deriva del nome parametro esportato: analyze(rawInput:) rispetto al contratto Swift analyze(input:). Ripristinata la firma pubblica input, separando la normalizzazione in un metodo privato. Il controllo contratti ora verifica anche questa firma; build e XCTest nativi saranno ripetuti sul commit corretto. Run iOS iniziale: https://github.com/naturewhisp/myco/actions/runs/37496618216.
