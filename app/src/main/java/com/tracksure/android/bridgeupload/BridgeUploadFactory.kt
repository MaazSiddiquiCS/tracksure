package com.tracksure.android.bridgeupload

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.tracksure.android.bridgeupload.integration.MeshLocationSnapshotAdapter
import com.tracksure.android.bridgeupload.net.ConnectivityGate
import com.tracksure.android.bridgeupload.net.LocationBatchApiClient
import com.tracksure.android.bridgeupload.storage.LocationUploadQueueStore
import com.tracksure.android.identity.AuthTokenStore
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
        Log.d(
            TAG,
            "Creating orchestrator endpoint=${config.endpointUrl} queueFile=${queueFile.absolutePath}"
        )

        val snapshotAdapter = MeshLocationSnapshotAdapter(appContext)
        val queueStore = LocationUploadQueueStore(
            queueFile = queueFile,
            minMovementMeters = config.minMovementMetersForQueueWrite,
            gson = gson
        )
        val connectivityGate = ConnectivityGate(appContext, config)
        val tokenStore = AuthTokenStore(appContext)
        val apiClient = LocationBatchApiClient(
            endpointUrl = config.endpointUrl,
            accessTokenProvider = { tokenStore.load()?.accessToken },
            gson = gson
        )

        return BridgeUploadOrchestrator(
            config = config,
            snapshotAdapter = snapshotAdapter,
            queueStore = queueStore,
            connectivityGate = connectivityGate,
            apiClient = apiClient
        )
    }
}


