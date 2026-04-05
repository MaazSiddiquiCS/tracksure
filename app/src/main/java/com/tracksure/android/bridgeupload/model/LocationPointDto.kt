package com.tracksure.android.bridgeupload.model

/**
 * Backend point DTO.
 */
data class LocationPointDto(
    val clientPointId: String,
    val lat: Double,
    val lon: Double,
    val accuracy: Double,
    val recordedAt: String,
    val source: String
)

