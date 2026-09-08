package github.naturewhisp.myco.model

import com.google.gson.annotations.SerializedName

data class AddressDetails(
    @SerializedName("village") val village: String? = null,
    @SerializedName("town") val town: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("municipality") val municipality: String? = null,
    @SerializedName("county") val county: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("country") val country: String? = null
)

data class GeocodeResult(
    @SerializedName("lat") val lat: String,
    @SerializedName("lon") val lon: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("address") val address: AddressDetails? = null
)

