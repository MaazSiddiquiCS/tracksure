package com.tracksure.android.bridgeupload

/**
 * Runtime config for sidecar bridge uploads.
 */
data class BridgeUploadConfig(
    val endpointUrl: String = "",
    val uploaderDeviceId: Long,
    val maxBatchSizeDefault: Int = 100,
    val minPointsToUpload: Int = 1,
    val maxBatchAgeMs: Long = 5_000L,
    val minUploadIntervalMs: Long = 2_000L,
    val maxBackoffMs: Long = 10 * 60 * 1000L,
    val backoffJitterRatio: Double = 0.20,
    val captureIntervalMs: Long = 5_000L,
    val flushIntervalMs: Long = 5_000L,
    val requireWifiTransport: Boolean = true,
    val requireValidatedNetwork: Boolean = true,
    val autoAssignMissingPeerToUploader: Boolean = true,
    val minMovementMetersForQueueWrite: Double = 50.0,
    val queueFileName: String = "bridge_upload_queue.json",
    val deviceMapFileName: String = "bridge_upload_peer_device_map.json"
)



