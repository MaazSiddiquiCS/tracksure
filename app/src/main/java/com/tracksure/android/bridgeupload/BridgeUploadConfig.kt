package com.tracksure.android.bridgeupload

/**
 * Runtime config for sidecar bridge uploads.
 */
data class BridgeUploadConfig(
    val endpointUrl: String = "http://192.168.18.246:8080/v1/locations:batch",
    val uploaderDeviceId: Long,
    val maxBatchSizeDefault: Int = 200,
    val minPointsToUpload: Int = 25,
    val maxBatchAgeMs: Long = 2 * 60 * 1000L,
    val minUploadIntervalMs: Long = 30_000L,
    val maxBackoffMs: Long = 10 * 60 * 1000L,
    val backoffJitterRatio: Double = 0.20,
    val captureIntervalMs: Long = 30_000L,
    val flushIntervalMs: Long = 45_000L,
    val requireWifiTransport: Boolean = true,
    val requireValidatedNetwork: Boolean = true,
    val autoAssignMissingPeerToUploader: Boolean = true,
    val queueFileName: String = "bridge_upload_queue.json",
    val deviceMapFileName: String = "bridge_upload_peer_device_map.json"
)



