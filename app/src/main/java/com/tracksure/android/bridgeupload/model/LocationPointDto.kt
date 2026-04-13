package com.tracksure.android.bridgeupload.model

import com.google.gson.annotations.SerializedName

/**
 * Backend point DTO.
 */
data class LocationPointDto(
    @SerializedName("clientPointId") val clientPointId: String,
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double,
    @SerializedName("accuracy") val accuracy: Double,
    @SerializedName("recordedAt") val recordedAt: String,
    @SerializedName("source") val source: String
)

