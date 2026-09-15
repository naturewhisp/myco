# Protocollo Metodologico: Implementazione di DeepMaxent per la Modellazione della Distribuzione delle Specie

## 1. Introduzione e Inquadramento Strategico

Nel contesto critico dell'attuale crisi della biodiversità, i Modelli di Distribuzione delle Specie (SDM) rappresentano la spina dorsale del supporto decisionale per la conservazione. Tuttavia, l'ecologia computazionale moderna si trova ad affrontare una sfida strutturale: la dipendenza da dati di citizen science "opportunistici" (Presence-Only, PO), intrinsecamente distorti da bias di campionamento. Superare i limiti dei modelli statistici tradizionali — come il Maxent classico — richiede un passaggio obbligato verso l’apprendimento profondo.

La transizione a **DeepMaxent** non è un semplice aggiornamento algoritmico, ma un cambio di paradigma necessario. Mentre i modelli tradizionali richiedono la definizione manuale delle trasformazioni delle variabili (feature engineering), DeepMaxent utilizza reti neurali profonde per apprendere automaticamente rappresentazioni latenti e catturare relazioni non lineari complesse. La superiorità strategica di questo approccio risiede nella capacità di addestrare modelli multi-specie in cui i gradienti delle feature sono condivisi tra diversi taxa; questo permette di modellare specie rare sfruttando il segnale biologico di quelle comuni, garantendo un'accuratezza predittiva irraggiungibile con modellazioni a singola specie.

## 2. Fondamenti Teorici: Dal Principio di Massima Entropia alle Reti Neurali

DeepMaxent estende il principio di Maxent interpretandolo attraverso la lente dei **Processi di Poisson non omogenei (PPP)**. La funzione di intensità $\lambda_j(x)$, che rappresenta l'abbondanza attesa della specie $j$ nel sito $x$, è modellata come una funzione log-lineare di feature estratte da una rete neurale (MLP o Residual Networks).

### Equivalenza Matematica e Funzione di Perdita
Il protocollo si basa sulla massimizzazione della verosimiglianza di un PPP, che risulta matematicamente equivalente al principio di massima entropia. Per garantire la convergenza verso il minimo della perdita globale, il ricercatore deve applicare la **Gibbs Inequality**, assicurando che la distribuzione stimata si avvicini progressivamente a quella osservata.

> ### **Analisi Tecnica della Loss Function**
>
> **Poisson Loss (Eq. 1):** Utilizzata per la calibrazione dell'intensità grezza.
> $$\mathcal{L}_{\mathcal{P}}(\lambda, y) = \frac{1}{KN} \sum_{i=1}^{K} \sum_{j=1}^{N} (\lambda_{ij} - y_{ij} \log \lambda_{ij})$$
>
> **Weighted Cross-Entropy (Eq. 5):** Fondamentale nei modelli multi-specie. Il ricercatore deve implementare il **Species-Proportional Weighting** ($w_j = \sum y_{ij}$) per evitare che il segnale delle specie rare sia "sommerso" dal rumore di quelle abbondanti.
> $$\mathcal{L}_{\mathcal{H,W}}(\tilde{\lambda}, y) = -\frac{1}{KN} \sum_{i=1}^{K} \sum_{j=1}^{N} y_{ij} \log \left( \frac{\lambda_{ij}}{\sum_{k=1}^{K} \lambda_{kj}} \right)$$

## 3. Gestione dei Dati di Citizen Science: Preparazione e Requisiti

La qualità dell'output è direttamente proporzionale alla precisione dei metadati ambientali. DeepMaxent eccelle nell'identificare dipendenze biologiche latenti (es. associazioni micorriziche) senza la necessità di inserire manualmente termini di interazione.

### Caso di Studio: *Boletus edulis* e Dinamiche Climatiche
Prendiamo il caso del porcino (*Boletus edulis*). Un modello tradizionale fatica a interpretare la nicchia reale senza variabili esplicite sui partner simbionti come *Abies alba* o *Picea*. DeepMaxent, invece, estrae automaticamente queste relazioni dalle covariate di suolo ed elevazione. Inoltre, l'integrazione di studi di *mycosilviculture* rivela relazioni non lineari critiche: picchi improvvisi di temperatura massima inibiscono la produttività del *B. edulis* in siti densi, ma possono favorirla in siti sottoposti a diradamento (thinning) dopo circa 20 giorni. DeepMaxent è l'unico framework capace di mappare tali interazioni clima-gestione forestale in modo endogeno.

### Criteri di Selezione "Target-Group"
Per la correzione del bias, è obbligatorio definire un set di specie "Target-Group" (TG) secondo questi criteri:
*   **Sforzo di Campionamento Comune:** Specie che i volontari segnalerebbero contestualmente alla specie target (es. altri funghi epigei).
*   **Siti di Background Dinamici:** Includere solo siti dove è stata registrata almeno una presenza del gruppo target.

## 4. Architettura del Modello e Algoritmo Batched

L'architettura raccomandata è una rete **Residual MLP** con 1-2 strati nascosti (hidden layers). L'uso di connessioni residuali previene la degradazione del gradiente, facilitando l'apprendimento di feature condivise tra centinaia di specie contemporaneamente.

### Il Paradosso della Funzione di Partizione
La gestione di dataset massivi richiede un **algoritmo batched** per approssimare la funzione di partizione (il denominatore della normalizzazione). Contrariamente all'intuizione standard del deep learning, un **Batch Size ridotto** produce mappe di intensità più **fluide (smoother)**. Questo accade perché la funzione di partizione, approssimata su pochi siti, impedisce all'intensità di "concentrarsi" eccessivamente attorno alle occorrenze puntuali, agendo come una forma implicita di regolarizzazione spaziale.

**Guida all'Ottimizzazione (Stochastic Gradient Descent):**
1.  **Campionamento:** Estrarre un batch casuale di siti dal background TG.
2.  **Logit Computation:** Calcolare $\gamma_j^T g_\theta(x_i) + b_j$.
3.  **Batch Softmax:** Normalizzare l'intensità esclusivamente all'interno del batch.
4.  **Update:** Aggiornare i pesi tramite ottimizzatore Adam per garantire stabilità.

## 5. Protocollo di Correzione del Bias Spaziale: Target-Group Background (TGB)

Il ricercatore **deve** implementare la correzione TGB per risolvere il bias di accessibilità (tendenza a campionare vicino a strade o centri abitati). Il TGB trasforma i dati PO filtrando i "falsi assenti" tramite la restrizione del dominio di studio.

### Istruzioni Operative per il Ricercatore:
*   **Step 1:** Identificare tutte le osservazioni del gruppo biologico di riferimento (es. genere *Boletus*, *Suillus*, *Leccinum*).
*   **Step 2:** Definire i siti di background del modello esclusivamente come le coordinate geografiche che presentano evidenza di sforzo di campionamento (almeno un record TG).
*   **Step 3:** Durante l'addestramento, campionare il background solo da questo sottoinsieme. Questo costringe il modello a distinguere tra l'idoneità dell'habitat e la semplice accessibilità del sito.

## 6. Ottimizzazione e Regolarizzazione (L2 Weight Decay)

La sensibilità del modello è massima rispetto al coefficiente di **Weight Decay** ($\tau$). La regolarizzazione L2 non serve solo a prevenire l'overfitting, ma agisce come un meccanismo di controllo della granularità spaziale.

| Valore Weight Decay ($\tau$) | Impatto sulla Distribuzione | Nota Metodologica |
| :--- | :--- | :--- |
| $0$ | Granularità estrema | Rischio elevato di overfitting sul rumore PO. |
| $3 \times 10^{-4}$ | **Optimal Benchmark** | Equilibrio ideale tra dettaglio ecologico e fluidità. |
| $1 \times 10^{-2}$ | Smoothing elevato | Raccomandato per dataset con bias geografico estremo. |
| $> 1 \times 10^{-1}$ | Oversmoothing | Perdita dei gradienti ambientali (mappa piatta). |

## 7. Valutazione delle Performance e Validazione Incrociata

Per evitare che l'autocorrelazione spaziale gonfi artificialmente le metriche, il protocollo impone l'uso dello **Spatial Blocking**. L'area di studio deve essere divisa (es. 25 blocchi spaziali) per eseguire una cross-validation a 10 fold, garantendo che i dati di test siano geograficamente distinti da quelli di addestramento.

### KPI di Qualità e Benchmarking
Il ricercatore deve monitorare l'AUC (Area Under the Curve) confrontando i risultati con i benchmark scientifici del framework DeepMaxent:
*   **AUC Target Regionale:** $> 0.75$ (media generale). In regioni ad alta densità di dati (es. Svizzera/SWI), puntare a **$0.85$**.
*   **Stabilità del Gradiente:** Verifica della convergenza SGD tramite monitoraggio della loss multi-specie pesata.
*   **Robustezza Multi-taxa:** Il modello deve mantenere performance costanti anche su specie rare (Rare class), beneficiando del segnale delle specie comuni (Abundant class).

## 8. Conclusioni e Output Operativi

DeepMaxent trasforma osservazioni frammentarie in strumenti di precisione per la gestione del territorio. Grazie all'apprendimento automatico delle feature e alla correzione sistematica del bias tramite TGB, questo protocollo supera i limiti di Maxent, particolarmente in scenari di campionamento non uniforme.

### Best Practices Operative:
1.  **Trasparenza Iper-parametrica:** Documentare sempre Batch Size e Weight Decay, poiché determinano lo smoothing della mappa.
2.  **Integrazione dei Dati (Integrated SDMs):** Il futuro della modellazione risiede nel combinare DeepMaxent (dati PO massivi) con piccoli set di dati standardizzati (PA) per separare definitivamente l'abbondanza reale dal bias di rilevamento.
3.  **Output Standardizzato:** Esportare le mappe di intensità in formato GeoTIFF ad alta risoluzione per l'integrazione immediata nei sistemi GIS di pianificazione forestale e ambientale.