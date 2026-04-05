package com.tracksure.android.bridgeupload

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.tracksure.android.bridgeupload.integration.DeviceIdResolver
import com.tracksure.android.bridgeupload.integration.MeshLocationSnapshotAdapter
import com.tracksure.android.bridgeupload.net.ConnectivityGate
import com.tracksure.android.bridgeupload.net.LocationBatchApiClient
import com.tracksure.android.bridgeupload.storage.LocationUploadQueueStore
import java.io.File

/**
 * Builder for the zero-touch bridge sidecar.
 */
object BridgeUploadFactory {
    private const val TAG = "BridgeUpload"

    fun create(context: Context, config: BridgeUploadConfig): BridgeUploadOrchestrator {
        val appContext = context.applicationContext
        val gson = Gson()
        val queueFile = File(appContext.filesDir, config.queueFileName)
        val mapFile = File(appContext.filesDir, config.deviceMapFileName)
        Log.d(
            TAG,
            "Creating orchestrator endpoint=${config.endpointUrl} queueFile=${queueFile.absolutePath} mapFile=${mapFile.absolutePath}"
        )

        val snapshotAdapter = MeshLocationSnapshotAdapter(appContext)
        val deviceIdResolver = DeviceIdResolver(mapFile, gson)
        val queueStore = LocationUploadQueueStore(queueFile, gson)
        val connectivityGate = ConnectivityGate(appContext, config)
        val apiClient = LocationBatchApiClient(config.endpointUrl, gson)

        return BridgeUploadOrchestrator(
            config = config,
            snapshotAdapter = snapshotAdapter,
            deviceIdResolver = deviceIdResolver,
            queueStore = queueStore,
            connectivityGate = connectivityGate,
            apiClient = apiClient
        )
    }

    fun createDeviceIdResolver(context: Context, config: BridgeUploadConfig): DeviceIdResolver {
        val mapFile = File(context.applicationContext.filesDir, config.deviceMapFileName)
        return DeviceIdResolver(mapFile)
    }
}


