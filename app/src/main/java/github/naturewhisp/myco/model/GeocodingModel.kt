package github.naturewhisp.myco.model

import com.google.gson.annotations.SerializedName

data class GeocodeResult(
    @SerializedName("lat") val lat: String,
    @SerializedName("lon") val lon: String,
    @SerializedName("display_name") val displayName: String
)
