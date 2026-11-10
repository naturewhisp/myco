package github.naturewhisp.myco.core

/**
 * Epistemological provenance classification for ecological modeling parameters.
 */
enum class ParameterProvenance {
    /**
     * Empirically measured biophysical constants derived from published experimental
     * field measurements or micrometeorological reanalysis data.
     */
    MEASURED,

    /**
     * Parameters calibrated or fitted via statistical regression, mathematical convergence,
     * or asymptotic ground-truth optimization.
     */
    FITTED,

    /**
     * Mycological domain heuristics and priors derived from expert literature, field guide
     * consensus, and physiological cardinal thresholds.
     */
    EXPERT_PRIOR,
}

/**
 * Structured descriptor for a formal ecological parameter in Myco.
 */
data class EcologicalParameter<T>(
    val key: String,
    val name: String,
    val value: T,
    val unit: String,
    val provenance: ParameterProvenance,
    val reference: String,
    val domain: String,
    val description: String,
)

/**
 * Canonical registry of all mathematical, meteorological, and mycological parameters
 * governing the Percorso A suitability engine.
 */
object ParameterRegistry {

    val HABITAT_RESPONSE_PRIORS = EcologicalParameter(
        key = "habitat.response_priors", name = "Nodi Hermite e livelli habitat per guild",
        value = listOf(0.10, 0.20, 0.25, 0.35, 0.40, 0.60, 0.65, 0.85, 0.95, 0.30, 0.45, 0.50, 0.15, 0.55, 0.05),
        unit = "frazioni e fattori", provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Bonifica operativa 2026-10-06", domain = "Habitat",
        description = "Nodi e livelli delle curve per guild in evaluateHabitat; stime comparative, prive di calibrazione di campo.",
    )
    val ECM_RESPONSE_PRIORS = EcologicalParameter(
        key = "habitat.ecm_response_priors", name = "Nodi EcM e fattore minimo",
        value = listOf(5.0, 25.0, 40.0, 60.0, 0.8), unit = "ricchezza e fattore",
        provenance = ParameterProvenance.EXPERT_PRIOR, reference = "Bonifica operativa 2026-10-06",
        domain = "Habitat EcM", description = "Raccordi Hermite del modificatore correlato agli ospiti; non applicabile alle altre guild.",
    )
    val PHASE_WANING_DAYS = EcologicalParameter(
        key = "phenology.waning_days", name = "Raccordo esaurimento evento", value = 11.0, unit = "giorni",
        provenance = ParameterProvenance.EXPERT_PRIOR, reference = "Bonifica operativa 2026-10-06",
        domain = "Fenologia", description = "Transizione Hermite da 0.85 a 0.30 dopo il limite di fruttificazione.",
    )
    val CARDINAL_BOUNDARY_POWER = EcologicalParameter(
        key = "temperature.cardinal_boundary_power", name = "Raccordo cardinale C1", value = 2.0, unit = "esponente",
        provenance = ParameterProvenance.EXPERT_PRIOR, reference = "Bonifica operativa 2026-10-06",
        domain = "Temperatura", description = "Potenza della risposta cardinale normalizzata, derivata nulla ai limiti; non è calibrazione biologica.",
    )

    val PHASE_RAIN_ACTIVATION_MM = EcologicalParameter(
        key = "phenology.phase_rain_activation", name = "Raccordo attivazione eventi", value = 10.0,
        unit = "mm", provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Bonifica operativa 2026-10-06", domain = "Ensemble fenologico",
        description = "Pesi continui R*smoothstep(0,10,R), senza soglie di esclusione o reset. Non altera il volume della convoluzione pluviometrica.",
    )

    val NOCTURNAL_MIN_OFFSET_C = EcologicalParameter(
        key = "nocturnal.min_offset_c", name = "Scarto media-minima per le soglie notturne", value = 6.0,
        unit = "°C", provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Revisione algoritmi 2026-11-09 (A2)", domain = "Inibizione termica notturna",
        description = "Le soglie notturne sono le soglie di temperatura media della specie meno questo scarto: metà dell'escursione diurna (12 °C) oltre la quale inizia la penalità DTR. Coerenza interna, non calibrazione biologica.",
    )

    val PHENOLOGY_MEMORY_TAPER_DAYS = EcologicalParameter(
        key = "phenology.memory_taper_days", name = "Raccordo di estinzione della memoria pluviometrica", value = 6.0,
        unit = "giorni", provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Revisione algoritmi 2026-11-09 (B1)", domain = "Inerzia fenologica",
        description = "Negli ultimi giorni della finestra di memoria il peso del kernel scende con raccordo Hermite C1 fino a zero, evitando il gradino all'uscita dell'evento.",
    )

    val FROST_DAMAGE_ONSET_C = EcologicalParameter(
        key = "frost.damage_onset_c", name = "Minima di innesco del danno da gelo", value = 1.0,
        unit = "°C", provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Revisione algoritmi 2026-11-09 (A4)", domain = "Gelo post-innesco",
        description = "Minima (sottochioma se applicato il microclima) sotto la quale inizia la perdita del flusso già innescato. Indipendente dalla specie; non calibrata.",
    )

    val FROST_DAMAGE_FULL_C = EcologicalParameter(
        key = "frost.damage_full_c", name = "Minima di danno completo da gelo", value = -2.0,
        unit = "°C", provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Revisione algoritmi 2026-11-09 (A4) / Nocturnal Frost Gate", domain = "Gelo post-innesco",
        description = "Minima alla quale una sola notte azzera memoria pluviometrica e maturità degli eventi precedenti; raccordo Hermite C1 fino alla soglia di innesco.",
    )

    val HABITAT_SURFACE_GRID = EcologicalParameter(
        key = "habitat.surface_grid", name = "Risoluzione quadratura superfici", value = 20,
        unit = "passi per semiraggio", provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Bonifica operativa 2026-10-06", domain = "Proxy geografico",
        description = "Unione di poligoni campionata su disco; non misura chioma o necromassa.",
    )

    val HABITAT_HOST_MODIFIER = EcologicalParameter(
        key = "habitat.host_modifier", name = "Modificatore ospite confermato", value = 1.15,
        unit = "moltiplicatore", provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Bonifica operativa 2026-10-06", domain = "Habitat",
        description = "Indizio correlato a EcM: non si moltiplicano i due bonus.",
    )

    val WEATHER_MEAN_COVERAGE = EcologicalParameter(
        key = "quality.weather_mean_coverage",
        name = "Copertura minima delle medie orarie",
        value = 0.75,
        unit = "frazione delle ore locali attese",
        provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Bonifica operativa 2026-10-06",
        domain = "Qualità dati",
        description = "Temperatura, umidità e suolo richiedono almeno il 75%; pioggia ed ET0 richiedono tutte le ore. Non è una soglia biologica.",
    )

    val KNEE_THRESHOLD = EcologicalParameter(
        key = "calibration.knee_threshold",
        name = "Soglia asintotica (Knee)",
        value = 70.0,
        unit = "punti (0–100)",
        provenance = ParameterProvenance.FITTED,
        reference = "Percorso A / Revisione v1.3 (§4.1)",
        domain = "Calibrazione asintotica",
        description = "Soglia oltre la quale il punteggio grezzo transita verso la compressione tanh per evitare sovrastime."
    )

    val ASYMPTOTE_SCALE = EcologicalParameter(
        key = "calibration.asymptote_scale",
        name = "Fattore di scala asintotico",
        value = 22.0,
        unit = "punti",
        provenance = ParameterProvenance.FITTED,
        reference = "Percorso A / Revisione v1.3 (§4.1)",
        domain = "Calibrazione asintotica",
        description = "Ampiezza massima di accrescimento oltre la soglia knee: S_max = 70 + 22 = 92/100."
    )

    val WEATHER_EXPONENT = EcologicalParameter(
        key = "scoring.weather_exponent",
        name = "Esponente di sensitività meteo",
        value = 1.2,
        unit = "adimensionale",
        provenance = ParameterProvenance.FITTED,
        reference = "Percorso A (§4.1.A)",
        domain = "Punteggio meteo composito",
        description = "Esponente superlineare che amplifica il contrasto tra condizioni meteorologiche ottimali e mediocri."
    )

    val HURDLE_BETA = EcologicalParameter(
        key = "hurdle.shape_beta",
        name = "Parametro di forma Weibull hurdle",
        value = 2.5,
        unit = "adimensionale",
        provenance = ParameterProvenance.FITTED,
        reference = "de-Miguel et al. (2014) / Hurdle modeling",
        domain = "Hurdle a due stadi",
        description = "Esponente di forma beta della CDF di Weibull, garantendo transizione C-infinito con p_hurdle(0)=0."
    )

    val HURDLE_SIGMA_SCALE = EcologicalParameter(
        key = "hurdle.scale_sigma_base",
        name = "Fattore di scala Weibull hurdle",
        value = 0.35,
        unit = "adimensionale",
        provenance = ParameterProvenance.FITTED,
        reference = "de-Miguel et al. (2014)",
        domain = "Hurdle a due stadi",
        description = "Fattore moltiplicativo di base della selettività della specie: sigma = 0.35 * strictness."
    )

    val CANOPY_COOLING_MAX = EcologicalParameter(
        key = "canopy.cooling_max_c",
        name = "Raffreddamento diurno massimo chioma",
        value = 4.0,
        unit = "°C",
        provenance = ParameterProvenance.MEASURED,
        reference = "De Frenne et al. (Nature Ecol. Evol. 2019, 2021)",
        domain = "Microclima forestale",
        description = "Massima attenuazione termica estiva delle temperature massime al suolo per ombreggiamento."
    )

    val CANOPY_WARMING_BASE = EcologicalParameter(
        key = "canopy.warming_base_c",
        name = "Isolamento notturno base chioma",
        value = 1.2,
        unit = "°C",
        provenance = ParameterProvenance.MEASURED,
        reference = "De Frenne et al. (Nature Ecol. Evol. 2019, 2021)",
        domain = "Microclima forestale",
        description = "Innalzamento notturno delle temperature minime sub-canopy per contenimento radiativo."
    )

    val THROUGHFALL_INTERCEPTION_BASE = EcologicalParameter(
        key = "canopy.throughfall_base_fraction",
        name = "Frazione base intercettazione chioma",
        value = 0.15,
        unit = "frazione (0–1)",
        provenance = ParameterProvenance.MEASURED,
        reference = "Zellweger et al. (2020) / De Frenne (2021)",
        domain = "Idrologia sub-canopy",
        description = "Perdita idrica minima per trattenimento su chioma fogliare prima del ruscellamento al suolo."
    )

    val SOIL_DROUGHT_STRESS_THRESHOLD = EcologicalParameter(
        key = "soil.drought_stress_threshold",
        name = "Soglia stress idrico suolo 0-7 cm (theta_max)",
        value = 0.22,
        unit = "m³/m³",
        provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Mindino Gate / Revisione v1.3 (§4.7 / RES-03)",
        domain = "Idrologia pedologica C1",
        description = "Contenuto volumetrico superficiale oltre il quale la riserva idrica è pienamente sufficiente (phi_soil = 1.0)."
    )

    val SOIL_DROUGHT_MIN_THRESHOLD = EcologicalParameter(
        key = "soil.drought_min_threshold",
        name = "Soglia minima idoneità suolo 0-7 cm (theta_min)",
        value = 0.14,
        unit = "m³/m³",
        provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Mindino Gate / Revisione v1.3 (§4.7 / RES-03)",
        domain = "Idrologia pedologica C1",
        description = "Contenuto idrico superficiale sotto il quale la progressione biologica subisce la massima penalizzazione continua (phi_soil = y_min)."
    )

    val SOIL_FLOOR_FACTOR = EcologicalParameter(
        key = "soil.floor_factor",
        name = "Fattore pavimento idrico (y_min)",
        value = 0.20,
        unit = "moltiplicatore (0–1)",
        provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Mindino Gate / RES-03",
        domain = "Idrologia pedologica C1",
        description = "Valore minimo asintotico di phi_soil raggiunto per suoli fortemente disidratati."
    )

    val CHILLING_DURATION_DAYS = EcologicalParameter(
        key = "chilling.duration_days",
        name = "Finestra temporale chilling",
        value = 5,
        unit = "giorni",
        provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Revisione v1.3 / RES-04",
        domain = "Inibizione termica notturna",
        description = "Numero di giorni recenti considerati per il calcolo dell'inversione o chilling notturno."
    )

    val CHILLING_LATENCY_EXPANSION_DAYS = EcologicalParameter(
        key = "chilling.latency_expansion_days",
        name = "Espansione latenza chilling",
        value = 1.5,
        unit = "giorni",
        provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Revisione v1.3 / RES-04",
        domain = "Inerzia fenologica differita",
        description = "Allungamento euristico del tempo di latenza primordiale in risposta a shock termico da freddo."
    )

    val SOIL_SATURATION_ANOXIA_THRESHOLD = EcologicalParameter(
        key = "soil.anoxia_saturation_threshold",
        name = "Soglia asfissia e anossia radicale",
        value = 0.38,
        unit = "m³/m³",
        provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Revisione scientifica F07 / Diluvio Gate",
        domain = "Idrologia pedologica",
        description = "Contenuto volumetrico oltre il quale subentra la deossigenazione della lettiera, deprimendo la fruttificazione."
    )

    val PHENOLOGY_MEMORY_DAYS = EcologicalParameter(
        key = "phenology.memory_days",
        name = "Finestra di memoria fenologica idrica",
        value = 26,
        unit = "giorni",
        provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Revisione v1.3 (§4.7 / MYCO-SCI-10)",
        domain = "Inerzia fenologica",
        description = "Memoria minima della convoluzione delle precipitazioni passate f(tau) e finestra fissa della compensazione del suolo profondo (7-28 cm); la memoria di ciascuna specie non scende mai sotto questo valore."
    )

    val PHENOLOGY_MEMORY_PEAK_MULTIPLE = EcologicalParameter(
        key = "phenology.memory_peak_multiple",
        name = "Multiplo del picco di latenza per la memoria pluviometrica",
        value = 3.0,
        unit = "multiplo di tau_peak",
        provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Revisione algoritmi 2026-11-09 (B1, §8)",
        domain = "Inerzia fenologica",
        description = "La memoria pluviometrica della specie vale questo multiplo del picco di latenza (arrotondato per eccesso), così le specie a latenza lunga non perdono pioggia ancora pesata dal kernel. Scelta di forma, non calibrazione di campo."
    )

    val PHENOLOGY_MEMORY_MAX_DAYS = EcologicalParameter(
        key = "phenology.memory_max_days",
        name = "Memoria pluviometrica massima",
        value = 56,
        unit = "giorni",
        provenance = ParameterProvenance.EXPERT_PRIOR,
        reference = "Revisione algoritmi 2026-11-09 (B1, §8)",
        domain = "Inerzia fenologica",
        description = "Tetto della memoria pluviometrica per specie, compatibile con lo storico di 60 giorni richiesto alla fonte meteo."
    )

    val ALL: List<EcologicalParameter<*>> = listOf(
        HABITAT_RESPONSE_PRIORS,
        ECM_RESPONSE_PRIORS,
        PHASE_WANING_DAYS,
        CARDINAL_BOUNDARY_POWER,
        WEATHER_MEAN_COVERAGE,
        PHASE_RAIN_ACTIVATION_MM,
        NOCTURNAL_MIN_OFFSET_C,
        PHENOLOGY_MEMORY_TAPER_DAYS,
        FROST_DAMAGE_ONSET_C,
        FROST_DAMAGE_FULL_C,
        HABITAT_SURFACE_GRID,
        HABITAT_HOST_MODIFIER,
        KNEE_THRESHOLD,
        ASYMPTOTE_SCALE,
        WEATHER_EXPONENT,
        HURDLE_BETA,
        HURDLE_SIGMA_SCALE,
        CANOPY_COOLING_MAX,
        CANOPY_WARMING_BASE,
        THROUGHFALL_INTERCEPTION_BASE,
        SOIL_DROUGHT_STRESS_THRESHOLD,
        SOIL_DROUGHT_MIN_THRESHOLD,
        SOIL_FLOOR_FACTOR,
        CHILLING_DURATION_DAYS,
        CHILLING_LATENCY_EXPANSION_DAYS,
        SOIL_SATURATION_ANOXIA_THRESHOLD,
        PHENOLOGY_MEMORY_DAYS,
        PHENOLOGY_MEMORY_PEAK_MULTIPLE,
        PHENOLOGY_MEMORY_MAX_DAYS,
    )
}
