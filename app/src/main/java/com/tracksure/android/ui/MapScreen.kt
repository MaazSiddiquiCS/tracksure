package com.tracksure.android.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.preference.PreferenceManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.runtime.snapshotFlow
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker


@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onNavigateToChat: (String) -> Unit
) {
    val state by viewModel.state.observeAsState(MapState())

    MapScreenContent(
        state = state,
        onSelectPeer = { viewModel.selectPeer(it) },
        onNavigateToChat = onNavigateToChat
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreenContent(
    state: MapState,
    onSelectPeer: (String?) -> Unit,
    onNavigateToChat: (String) -> Unit
) {
    val context = LocalContext.current

    // Permissions
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // OSM Configuration
    LaunchedEffect(Unit) {
        try {
            Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
            Configuration.getInstance().userAgentValue = context.packageName
            locationPermissions.launchMultiplePermissionRequest()
        } catch (_: Exception) { }
    }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // --- DOWNLOAD DIALOG STATE ---
    var showDownloadDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    // --- NETWORK MONITORING STATE ---
    var isWifiConnected by remember { mutableStateOf(false) }

    // 1. Monitor Network Changes
    LaunchedEffect(Unit) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkFlow = callbackFlow {
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                // Helper to check current state
                fun checkWifi(): Boolean {
                    val activeNetwork = connectivityManager.activeNetwork ?: return false
                    val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
                    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                }

                override fun onAvailable(network: Network) { trySend(checkWifi()) }
                override fun onLost(network: Network) { trySend(checkWifi()) }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    // If capabilities changed (e.g. Cellular -> Wifi), re-check
                    trySend(checkWifi())
                }
            }

            // Register
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)

            // Initial check
            val activeNetwork = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            val initialWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            trySend(initialWifi)

            awaitClose { connectivityManager.unregisterNetworkCallback(networkCallback) }
        }

        networkFlow.collect { connected ->
            isWifiConnected = connected
        }
    }
    var hasCenteredOnUser by remember { mutableStateOf(false) }

    // --- ADD THIS NEW LAUNCHED EFFECT ---
    // As soon as 'state.myLocation' becomes not null (GPS fix acquired), this runs.
    LaunchedEffect(state.myLocation) {
        if (!hasCenteredOnUser && state.myLocation != null) {
            mapViewRef?.controller?.let { controller ->
                controller.setZoom(18.0) // Optional: Zoom in closer when we find you
                controller.animateTo(state.myLocation!!.geoPoint)
                hasCenteredOnUser = true
            }
        }
    }
    // 2. Unified Trigger Logic
    // Triggers whenever Location changes OR WiFi status changes
    LaunchedEffect(state.myLocation, isWifiConnected) {
        state.myLocation?.let { myLoc ->
            // Only proceed if WiFi is connected AND we aren't already doing something
            if (isWifiConnected && !isDownloading && !showDownloadDialog) {

                // We pass 'assumeWifi = true' because we already checked isWifiConnected above.
                // This bypasses the internal check in MapCacheManager which might be slightly delayed.
                val needsDownload = MapCacheManager.isDownloadNeeded(
                    context,
                    myLoc.geoPoint,
                    assumeWifi = true
                )

                if (needsDownload) {
                    showDownloadDialog = true
                }
            }
        }
    }

    // --------------------------

    Box(modifier = Modifier.fillMaxSize()) {

        if (!LocalInspectionMode.current) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        controller.setCenter(GeoPoint(30.3753, 69.3451))
                        mapViewRef = this
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()

                    // Draw Me
                    state.myLocation?.let { myLoc ->
                        val marker = Marker(mapView)
                        marker.position = myLoc.geoPoint
                        marker.title = "Me"
                        try {
                            marker.icon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_mylocation)
                        } catch (_: Exception) {}
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        mapView.overlays.add(marker)
                    }

                    // Draw Peers
                    state.peerLocations.values.forEach { peer ->
                        val marker = Marker(mapView)
                        marker.position = peer.geoPoint
                        marker.title = peer.nickname
                        marker.icon = createColoredMarker(context, MapUIUtils.getPeerColor(peer.peerID))
                        marker.setOnMarkerClickListener { _, _ ->
                            onSelectPeer(peer.peerID)
                            true
                        }
                        mapView.overlays.add(marker)
                    }
                    mapView.invalidate()
                }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Map Placeholder")
            }
        }

        // --- DIALOG UI ---
        if (showDownloadDialog) {
            AlertDialog(
                onDismissRequest = { showDownloadDialog = false },
                icon = { Icon(Icons.Default.Download, contentDescription = null) },
                title = { Text("Download Offline Map?") },
                text = {
                    Text("You have entered a new area. Would you like to download the map for this region (10km radius) to use offline?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDownloadDialog = false
                            isDownloading = true
                            state.myLocation?.let { loc ->
                                mapViewRef?.let { map ->
                                    MapCacheManager.startDownload(context, map, loc.geoPoint) {
                                        isDownloading = false
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Download")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDownloadDialog = false }) {
                        Text("Not Now")
                    }
                }
            )
        }

        // Loading Indicator (Overlay)
        if (isDownloading) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Downloading map tiles...")
                }
            }
        }
        // -----------------

        // Header
        MapHeader(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp, start = 16.dp, end = 16.dp),
            peerCount = state.peerLocations.size,
            onMenuClick = { }
        )

        // FAB
        FloatingActionButton(
            onClick = {
                state.myLocation?.let {
                    mapViewRef?.controller?.animateTo(it.geoPoint)
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = 64.dp)
        ) {
            Icon(Icons.Default.MyLocation, "My Location")
        }

        // User Sheet
        state.selectedPeerID?.let { id ->
            state.peerLocations[id]?.let { peer ->
                MapUserSheet(
                    selectedPeer = peer,
                    myLocation = state.myLocation,
                    onDismiss = { onSelectPeer(null) },
                    onChatClick = onNavigateToChat
                )
            }
        }
    }
}

fun createColoredMarker(context: Context, color: Int): Drawable {
    val size = 40
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    paint.color = color
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    return BitmapDrawable(context.resources, bitmap)
}
