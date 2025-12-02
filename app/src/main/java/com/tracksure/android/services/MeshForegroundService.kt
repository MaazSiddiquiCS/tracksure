package com.tracksure.android.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tracksure.android.R
import com.tracksure.android.mesh.BluetoothMeshService

class MeshForegroundService : Service() {
    private lateinit var meshService: BluetoothMeshService

    companion object {
        private const val NOTIFICATION_ID = 101
        private const val NOTIFICATION_CHANNEL_ID = "MeshNetworkServiceChannel"
        private const val TAG = "MeshForegroundService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ForegroundService is being Created...")
        meshService = BluetoothMeshService.getInstance(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ForegroundService is being Started...")
        createNotificationChannel()

        // Ensure notification icon exists or use a system default to prevent crash
        val icon = if (R.drawable.ic_launcher_foreground != 0) R.drawable.ic_launcher_foreground else android.R.drawable.ic_dialog_info

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Mesh Network Service")
            .setContentText("Running in the background")
            .setSmallIcon(icon)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // FIX: Call startServices on connectionManager, not meshService directly
        if (::meshService.isInitialized) {
            meshService.connectionManager.startServices()
        }

        return START_NOT_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "App task removed (swiped away), stopping service")

        // Stop internal logic
        try {
            if (::meshService.isInitialized) {
                meshService.connectionManager.stopServices()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mesh on task removed: ${e.message}")
        }

        // Stop the service and remove notification immediately
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Foreground service is being destroyed")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        try {
            meshService.connectionManager.stopServices()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping services: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Mesh Network Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
