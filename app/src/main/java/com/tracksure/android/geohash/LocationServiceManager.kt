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
import android.os.Looper // Import this

class LocationServiceManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    fun getLocationUpdates(): Flow<Location> = callbackFlow {
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // Log to prove we got FRESH data from OS
                Log.v("LocationServiceManager", "New GPS Fix: ${location.latitude}, ${location.longitude}")
                trySend(location)
            }

            // Boilerplate
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        Log.d("LocationServiceManager", "Requesting FRESH location updates...")

        try {
            // FIX 1: Use the Looper.getMainLooper() to ensure callbacks run on the main thread
            // This prevents background thread stalls in some emulators/devices
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L, // Reduced to 2 seconds for testing (was 5000L)
                0f,    // Reduced distance to 0m so ANY movement triggers update
                locationListener,
                Looper.getMainLooper()
            )

            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                2000L,
                0f,
                locationListener,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.e("LocationServiceManager", "Error requesting updates", e)
            close(e)
        }

        awaitClose {
            Log.d("LocationServiceManager", "Stopping location updates")
            locationManager.removeUpdates(locationListener)
        }
    }
}
