# Bridge Upload Sidecar (Zero-Touch)

This module adds backend bulk location upload as a sidecar without changing existing mesh behavior.

## What it does
- Reads current peer locations through existing `BluetoothMeshService` public APIs.
- Converts mesh runtime coordinates into backend bulk JSON points.
- Persists pending points to a local JSON queue file.
- Uploads in batches only on Wi-Fi with validated internet.
- Retries failed uploads with exponential backoff.

## What it does NOT do
- It does not alter mesh packet format (`"lat,lon"` remains unchanged).
- It does not auto-start itself.
- It does not modify existing services, delegates, manifests, or app flows.

## Files used at runtime
- Queue: `<filesDir>/bridge_upload_queue.json`
- Peer ID map: `<filesDir>/bridge_upload_peer_device_map.json`

## Peer ID to backend device ID mapping format
```json
{
  "a1b2c3d4e5f60708": 12345,
  "1122334455667788": 98765
}
```

## Manual usage example
```kotlin
val config = BridgeUploadConfig(
    endpointUrl = "https://your-backend/api/location/batch",
    uploaderDeviceId = 42L
)

val orchestrator = BridgeUploadFactory.create(context, config)

// Optional: preload meshPeerId -> backend subjectDeviceId mappings
BridgeUploadFactory.createDeviceIdResolver(context, config).putMappings(
    mapOf("a1b2c3d4e5f60708" to 1001L)
)

// One-shot
orchestrator.captureSnapshotAndEnqueue()
val report = orchestrator.flushNow()

// Or periodic
val scheduler = BridgeUploadScheduler(orchestrator, config)
scheduler.start()
// ... later
scheduler.stop()
```

## Backend request shape
The uploader sends:
- `subjectDeviceId: Long`
- `uploaderDeviceId: Long`
- `points[]` with `clientPointId`, `lat`, `lon`, `accuracy`, `recordedAt`, `source`

This matches the backend batch upload contract.

