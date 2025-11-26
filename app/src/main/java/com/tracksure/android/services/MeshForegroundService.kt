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

class MeshForegroundService : Service()
{
    private lateinit var meshService: BluetoothMeshService

    companion object{
        private const val NOTIFICATION_ID=101
        private const val NOTIFICATION_CHANNEL_ID = "MeshNetworkServiceChannel"
        private const val TAG = "MeshForegroundService"
        var instance: BluetoothMeshService? = null

    }


    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ForegroundService is being Created...")

        // --- FIX 2: Reuse existing instance if MainActivity created it ---
        if (instance == null) {
            meshService = BluetoothMeshService(applicationContext)
            instance = meshService
        } else {
            // If instance exists, use it (avoids resetting connections)
            meshService = instance!!
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ForegroundServiceisbeingStarted...")
        createNotificationChannel()
        val notification : Notification= NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Mesh Network Service")
            .setContentText("Running in the background")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        meshService.startServices()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Foreground service is being destroyed")
        meshService.stopServices()
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
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