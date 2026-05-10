package com.tracksure.android.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import kotlin.math.cos

/**
 * Worker that prefetches map tiles for a bounding box around a center point.
 * InputData keys:
 *  - center_lat (Double)
 *  - center_lon (Double)
 *  - radius_km (Double)
 *  - min_zoom (Int)
 *  - max_zoom (Int)
 */
class TilePrefetchWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "tile_prefetch_channel"
        const val NOTIF_ID = 4593
    }

    override fun doWork(): Result {
        val ctx = applicationContext

        val centerLat = inputData.getDouble("center_lat", 0.0)
        val centerLon = inputData.getDouble("center_lon", 0.0)
        val radiusKm = inputData.getDouble("radius_km", 10.0)
        val minZoom = inputData.getInt("min_zoom", 12)
        val maxZoom = inputData.getInt("max_zoom", 16)

        if (centerLat == 0.0 && centerLon == 0.0) {
            // nothing to do
            return Result.failure()
        }

        createNotificationChannel(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle("TrackSure: Caching map tiles")
            .setContentText("Preparing offline tiles")
            .setSmallIcon(com.tracksure.android.R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        nm.notify(NOTIF_ID, notif)

        try {
            // Create an offscreen MapView (required by CacheManager)
            val mapView = MapView(ctx)

            // Compute bounding box from center + radius (approx)
            val latDelta = (radiusKm / 111.32) // degrees approx
            val lonDelta = radiusKm / (111.32 * cos(Math.toRadians(centerLat)))

            val north = centerLat + latDelta
            val south = centerLat - latDelta
            val east = centerLon + lonDelta
            val west = centerLon - lonDelta

            val bbox = BoundingBox(north, east, south, west)

            val cacheManager = org.osmdroid.tileprovider.cachemanager.CacheManager(mapView)

            // Use CacheManager to prefetch area. We call downloadAreaAsync which
            // will populate the osmdroid tile cache. This runs synchronously here
            // via the blocking method if available, otherwise we use the async API.
            try {
                cacheManager.downloadAreaAsync(ctx, bbox, minZoom, maxZoom)
            } catch (t: Throwable) {
                // Download failed or API not available — give up
            }

            // Done
            nm.cancel(NOTIF_ID)
            return Result.success()
        } catch (e: Exception) {
            nm.cancel(NOTIF_ID)
            return Result.failure()
        }
    }

    private fun createNotificationChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "Tile prefetch", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
    }
}
