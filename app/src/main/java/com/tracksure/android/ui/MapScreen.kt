package com.tracksure.android.ui

import android.preference.PreferenceManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.data.position
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun MapScreen(viewModel: MapViewModel) {
    val context = LocalContext.current

    // Observe Data
    val peerLocations by viewModel.peerLocations.observeAsState(emptyMap())
    val myLocation by viewModel.myLocation.observeAsState()
    val isConnected by viewModel.isConnected.observeAsState(false)
    val connectedCount by viewModel.connectedPeersCount.observeAsState(0)

    // --- Settings & Auth Data ---
    val authorizedPeers by viewModel.authorizedPeers.observeAsState(emptySet())
    val myNickname by viewModel.myNickname.observeAsState("")
    val myMagicCode by viewModel.myMagicCode.observeAsState("")

    // Map State
    var hasCenteredOnce by remember { mutableStateOf(false) }
    var mapController by remember { mutableStateOf<org.osmdroid.api.IMapController?>(null) }

    // --- Dialog States ---
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showTrackSelectionDialog by remember { mutableStateOf(false) }
    var showPinEntryDialog by remember { mutableStateOf(false) }

    // --- Inputs ---
    var selectedPeerIdToTrack by remember { mutableStateOf<String?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var editNickname by remember { mutableStateOf("") }
    var editMagicCode by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
    }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Settings Button
                FloatingActionButton(
                    onClick = {
                        editNickname = myNickname
                        editMagicCode = myMagicCode
                        showSettingsDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Settings, "Settings")
                }

                // 2. Track Device Button
                ExtendedFloatingActionButton(
                    onClick = { showTrackSelectionDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Radar, "Track")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Track a device")
                }

                // 3. Center Button
                SmallFloatingActionButton(
                    onClick = {
                        myLocation?.let { loc ->
                            mapController?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                            mapController?.setZoom(18.0)
                        }
                    }
                ) {
                    Icon(Icons.Default.MyLocation, "Center")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {

            // 1. MAP VIEW
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        controller.setCenter(GeoPoint(0.0, 0.0))
                        mapController = controller
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()

                    // Draw Me
                    myLocation?.let { loc ->
                        val myMarker = Marker(mapView)
                        myMarker.position = GeoPoint(loc.latitude, loc.longitude)
                        myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        myMarker.title = "Me"

                        val icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.person)
                        if (icon != null) myMarker.icon = icon

                        mapView.overlays.add(myMarker)

                        if (!hasCenteredOnce) {
                            mapView.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                            mapView.controller.setZoom(18.0)
                            hasCenteredOnce = true
                        }
                    }

                    // Draw Peers - ONLY if Authorized via Code
                    peerLocations.forEach { (id, info) ->
                        // Only show markers if user has successfully entered the PIN
                        if (authorizedPeers.contains(id) && info.latitude != null && info.longitude != null) {
                            val marker = Marker(mapView)
                            marker.position = GeoPoint(info.latitude!!, info.longitude!!)
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            marker.title = info.nickname
                            marker.snippet = "Live Tracking Active"

                            val peerIcon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)
                            if (peerIcon != null) marker.icon = peerIcon

                            mapView.overlays.add(marker)
                        }
                    }
                    mapView.invalidate()
                }
            )

            // 2. CONNECTION HUD
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .width(220.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    if (isConnected) Color.Green else Color.Red,
                                    RoundedCornerShape(50)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConnected) "Mesh Active" else "Scanning...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Peers Connected: $connectedCount", fontSize = 12.sp)
                }
            }

            // --- DIALOGS ---

            // 1. Settings Dialog
            if (showSettingsDialog) {
                AlertDialog(
                    onDismissRequest = { showSettingsDialog = false },
                    title = { Text("My Settings", fontFamily = FontFamily.Monospace) },
                    text = {
                        Column {
                            Text("Configure your details.")
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = editNickname,
                                onValueChange = { editNickname = it },
                                label = { Text("Nickname") },
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editMagicCode,
                                onValueChange = { editMagicCode = it },
                                label = { Text("Magic Code") },
                                supportingText = { Text("Share this code to let others track you.") },
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.updateSettings(editNickname, editMagicCode)
                            showSettingsDialog = false
                            Toast.makeText(context, "Settings Saved", Toast.LENGTH_SHORT).show()
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSettingsDialog = false }) { Text("Cancel") }
                    }
                )
            }

            // 2. Select Device Dialog
            if (showTrackSelectionDialog) {
                AlertDialog(
                    onDismissRequest = { showTrackSelectionDialog = false },
                    title = { Text("Select Device to Track", fontFamily = FontFamily.Monospace) },
                    text = {
                        if (peerLocations.isEmpty()) {
                            Text("No devices found nearby.")
                        } else {
                            LazyColumn {
                                items(peerLocations.values.toList()) { peer ->
                                    val isTracked = authorizedPeers.contains(peer.id)
                                    ListItem(
                                        headlineContent = { Text(peer.nickname, fontWeight = FontWeight.Bold) },
                                        supportingContent = { Text("ID: ${peer.id.take(6)}...") },
                                        leadingContent = { Icon(Icons.Default.Smartphone, null) },
                                        trailingContent = {
                                            if (isTracked) Icon(Icons.Default.CheckCircle, null, tint = Color.Green)
                                        },
                                        modifier = Modifier.clickable {
                                            if (!isTracked) {
                                                selectedPeerIdToTrack = peer.id
                                                pinInput = ""
                                                showTrackSelectionDialog = false
                                                showPinEntryDialog = true
                                            } else {
                                                Toast.makeText(context, "Already tracking", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showTrackSelectionDialog = false }) { Text("Close") }
                    }
                )
            }

            // 3. PIN Entry Dialog
            if (showPinEntryDialog && selectedPeerIdToTrack != null) {
                AlertDialog(
                    onDismissRequest = { showPinEntryDialog = false },
                    title = { Text("Enter Magic Code", fontFamily = FontFamily.Monospace) },
                    text = {
                        Column {
                            Text("Enter the code shared by this user.")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { pinInput = it },
                                label = { Text("Code") },
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val success = viewModel.authorizeTracking(selectedPeerIdToTrack!!, pinInput)
                            if (success) {
                                Toast.makeText(context, "Tracking Started", Toast.LENGTH_SHORT).show()
                                showPinEntryDialog = false
                            } else {
                                Toast.makeText(context, "Invalid Code", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Verify") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPinEntryDialog = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}
