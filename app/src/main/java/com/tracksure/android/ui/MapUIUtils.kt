package com.tracksure.android.ui

import android.graphics.Color
import org.osmdroid.util.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object MapUIUtils {

    /**
     * Calculate distance in meters (Haversine formula)
     */
    fun calculateDistance(p1: GeoPoint, p2: GeoPoint): Double {
        val R = 6371e3 // Earth radius in meters
        val phi1 = p1.latitude * Math.PI / 180
        val phi2 = p2.latitude * Math.PI / 180
        val deltaPhi = (p2.latitude - p1.latitude) * Math.PI / 180
        val deltaLambda = (p2.longitude - p1.longitude) * Math.PI / 180

        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2) * sin(deltaLambda / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }

    fun formatDistance(meters: Double): String {
        return if (meters >= 1000) {
            "%.1f km".format(meters / 1000)
        } else {
            "${meters.toInt()} m"
        }
    }

    /**
     * Generate a consistent color integer for a peerID
     */
    fun getPeerColor(peerID: String): Int {
        var hash = 5381UL
        for (byte in peerID.toByteArray()) {
            hash = ((hash shl 5) + hash) + byte.toUByte().toULong()
        }
        val hue = (hash % 360UL).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 0.8f, 0.9f))
    }
}
