package com.tracksure.android.geohash

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationServiceManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission") // Permissions are checked in UI before calling this
    fun getLocationUpdates(): Flow<Location> = callbackFlow {
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // Request updates: Min time 5s, Min distance 5m
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L,
                5f,
                locationListener
            )
            // Also try Network provider for faster initial fix
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                5000L,
                5f,
                locationListener
            )
        } catch (e: Exception) {
            Log.e("LocationServiceManager", "Error requesting location updates", e)
        }

        awaitClose {
            locationManager.removeUpdates(locationListener)
        }
    }
}
