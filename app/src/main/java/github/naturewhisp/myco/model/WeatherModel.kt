package github.naturewhisp.myco.model

import com.google.gson.annotations.SerializedName

/**
 * Risposta strutturata dell'API meteorologica Open-Meteo per la serie temporale a 21 giorni.
 *
 * @property elevation Quota altimetrica del punto stimata dal modello di elevazione Open-Meteo in metri s.l.m.
 * @property timezone Fuso orario geografico della località (es. "Europe/Rome").
 * @property hourly Serie temporali di dati meteorologici orari [HourlyData].
 * @property daily Serie temporali di parametri aggregati giornalieri [DailyData].
 */
data class WeatherResponse(
    @SerializedName("elevation") val elevation: Float,
    @SerializedName("timezone") val timezone: String,
    @SerializedName("hourly") val hourly: HourlyData,
    @SerializedName("daily") val daily: DailyData
)

/**
 * Dati meteorologici orari continui per temperatura, umidità relativa e precipitazioni.
 *
 * @property time Timestamp ISO orari delle misurazioni ("YYYY-MM-DDTHH:00").
 * @property temperature2m Temperatura dell'aria a 2 metri dal suolo in gradi Celsius.
 * @property relativeHumidity2m Umidità relativa a 2 metri dal suolo in percentuale (0..100).
 * @property precipitation Precipitazioni orarie in millimetri.
 */
data class HourlyData(
    @SerializedName("time") val time: List<String>,
    @SerializedName("temperature_2m") val temperature2m: List<Float>,
    @SerializedName("relativehumidity_2m") val relativeHumidity2m: List<Float>,
    @SerializedName("precipitation") val precipitation: List<Float>
)

/**
 * Dati meteorologici aggregati giornalieri Open-Meteo.
 *
 * @property time Date dei giorni in formato ISO ("YYYY-MM-DD").
 * @property weatherCode Codici meteorologici WMO sintetici per la giornata.
 */
data class DailyData(
    @SerializedName("time") val time: List<String>,
    @SerializedName("weathercode") val weatherCode: List<Int?>
)

/**
 * Dati meteorologici elaborati e aggregati per un singolo giorno (media 24 ore e precipitazioni cumulate).
 *
 * @property date Data ISO del giorno ("YYYY-MM-DD").
 * @property avgTemp Temperatura media calcolata sulle 24 ore in gradi Celsius.
 * @property totalPrecip Precipitazione cumulata nelle 24 ore in millimetri.
 * @property avgHumidity Umidità relativa media nelle 24 ore in percentuale.
 * @property weatherCode Codice meteorologico WMO prevalente o nullo.
 */
data class ProcessedDay(
    val date: String,
    val avgTemp: Float,
    val totalPrecip: Float,
    val avgHumidity: Float,
    val weatherCode: Int?
)
