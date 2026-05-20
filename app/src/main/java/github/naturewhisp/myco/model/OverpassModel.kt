package github.naturewhisp.myco.model

import com.google.gson.annotations.SerializedName

data class OverpassResponse(
    @SerializedName("elements") val elements: List<OverpassElement>
)

data class OverpassElement(
    @SerializedName("type") val type: String,
    @SerializedName("id") val id: Long
)
