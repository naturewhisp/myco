package github.naturewhisp.myco.model

// Identificatore univoco del fattore ecologico o meteorologico
enum class FactorId {
    TEMPERATURE,
    PRECIPITATION,
    HUMIDITY,
    HABITAT,
    ALTITUDE,
    SEASONALITY,
    MYCELIAL_PHASE,
    LUNAR_PHASE,
    SLOPE,
    SPUN_ECM,
    SPUN_HYPHAL
}

// Giudizio qualitativo sintetico del fattore
enum class FactorLevel {
    FAVORABLE,
    NEUTRAL,
    ADVERSE,
    INFORMATIVE
}

// Modello tipizzato per un fattore analitico di crescita
data class Factor(
    val id: FactorId,
    val label: String,
    val formattedValue: String,
    val level: FactorLevel,
    val detail: String,
    val iconGlyph: String? = null
)
