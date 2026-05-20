package github.naturewhisp.myco.model

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    @SerializedName("elevation") val elevation: Float,
    @SerializedName("timezone") val timezone: String,
    @SerializedName("hourly") val hourly: HourlyData,
    @SerializedName("daily") val daily: DailyData
)

data class HourlyData(
    @SerializedName("time") val time: List<String>,
    @SerializedName("temperature_2m") val temperature2m: List<Float>,
    @SerializedName("relativehumidity_2m") val relativeHumidity2m: List<Float>,
    @SerializedName("precipitation") val precipitation: List<Float>
)

data class DailyData(
    @SerializedName("time") val time: List<String>,
    @SerializedName("weathercode") val weatherCode: List<Int?>
)

data class ProcessedDay(
    val date: String,
    val avgTemp: Float,
    val totalPrecip: Float,
    val avgHumidity: Float,
    val weatherCode: Int?
)
