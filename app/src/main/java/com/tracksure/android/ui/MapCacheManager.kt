package com.tracksure.android.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

object MapCacheManager {
    private const val TAG = "MapCacheManager"
    private const val PREFS_NAME = "map_cache_prefs"
    private const val KEY_LAST_LAT = "last_cache_lat"
    private const val KEY_LAST_LON = "last_cache_lon"

    // --- OPTIMIZATION SETTINGS ---
    private const val ROAMING_THRESHOLD_METERS = 8000.0
    private const val DOWNLOAD_RADIUS_KM = 10.0
    private const val MIN_ZOOM = 12
    private const val MAX_ZOOM = 15

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Checks if a download is recommended.
     * @param assumeWifi If true, skips the internal WiFi check (useful if UI already validated connection)
     */
    fun isDownloadNeeded(context: Context, currentLoc: GeoPoint, assumeWifi: Boolean = false): Boolean {
        // 1. Check Connection
        // If assumeWifi is TRUE, we skip the check. If FALSE, we perform the check.
        if (!assumeWifi && !isWifiConnected(context)) {
            return false
        }

        // 2. Check Distance (Have we moved far enough?)
        if (!shouldDownloadNewArea(context, currentLoc)) {
            return false
        }

        return true
    }

    /**
     * Actual download logic - Called ONLY when user confirms the dialog
     */
    fun startDownload(context: Context, mapView: MapView, center: GeoPoint, onComplete: () -> Unit) {
        Toast.makeText(context, "Starting map download...", Toast.LENGTH_SHORT).show()

        val boundingBox = getBoundingBox(center, DOWNLOAD_RADIUS_KM)

        // "Fake Mapnik" Source to bypass bulk-download restriction
        val customMapnik = XYTileSource(
            "Mapnik_Custom",
            0, 19, 256, ".png",
            arrayOf("https://tile.openstreetmap.org/")
        )

        val originalSource = mapView.tileProvider.tileSource
        mapView.setTileSource(customMapnik)

        val cacheManager = CacheManager(mapView)

        cacheManager.downloadAreaAsync(
            context,
            boundingBox,
            MIN_ZOOM,
            MAX_ZOOM,
            object : CacheManager.CacheManagerCallback {

                override fun onTaskComplete() {
                    Log.d(TAG, "Map cache complete")
                    saveLastCacheLocation(context, center)

                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Download complete!", Toast.LENGTH_LONG).show()
                        mapView.setTileSource(originalSource)
                        onComplete()
                    }
                }

                override fun onTaskFailed(errors: Int) {
                    Log.e(TAG, "Map cache failed with $errors errors")
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Download failed. Check internet.", Toast.LENGTH_SHORT).show()
                        mapView.setTileSource(originalSource)
                        onComplete()
                    }
                }

                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) { }
                override fun downloadStarted() { }
                override fun setPossibleTilesInArea(total: Int) { }
            }
        )
    }

    private fun shouldDownloadNewArea(context: Context, currentPoint: GeoPoint): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LAST_LAT)) return true // First run ever

        val lastLat = prefs.getFloat(KEY_LAST_LAT, 0f).toDouble()
        val lastLon = prefs.getFloat(KEY_LAST_LON, 0f).toDouble()
        val lastPoint = GeoPoint(lastLat, lastLon)

        val distance = currentPoint.distanceToAsDouble(lastPoint)
        return distance > ROAMING_THRESHOLD_METERS
    }

    private fun saveLastCacheLocation(context: Context, point: GeoPoint) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_LAST_LAT, point.latitude.toFloat())
            .putFloat(KEY_LAST_LON, point.longitude.toFloat())
            .apply()
    }

    private fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun getBoundingBox(center: GeoPoint, radiusKm: Double): BoundingBox {
        val latDegrees = radiusKm / 111.0
        val lonDegrees = radiusKm / (111.0 * Math.cos(Math.toRadians(center.latitude)))
        return BoundingBox(
            center.latitude + latDegrees,
            center.longitude + lonDegrees,
            center.latitude - latDegrees,
            center.longitude - lonDegrees
        )
    }
}
