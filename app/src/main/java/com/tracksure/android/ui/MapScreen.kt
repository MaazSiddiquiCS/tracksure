package com.tracksure.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
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
    val accountUsername by viewModel.accountUsername.observeAsState("Unknown user")
    val accountEmail by viewModel.accountEmail.observeAsState("")
    val ownerInvite by viewModel.ownerTrackingInvite.observeAsState()
    val importedInvite by viewModel.importedTrackingInvite.observeAsState()

    // Map State View
    var hasCenteredOnce by remember { mutableStateOf(false) }
    var mapController by remember { mutableStateOf<org.osmdroid.api.IMapController?>(null) }

    // --- Dialog States ---
    var showProfileScreen by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showTrackSelectionDialog by remember { mutableStateOf(false) }
    var showTrackingAuthDialog by remember { mutableStateOf(false) }

    // --- Inputs ---
    var selectedPeerIdToTrack by remember { mutableStateOf<String?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var qrPayloadInput by remember { mutableStateOf("") }
    var editNickname by remember { mutableStateOf("") }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result: ScanIntentResult ->
        val payload = result.contents
        if (payload.isNullOrBlank()) {
            Toast.makeText(context, "QR scan cancelled", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val imported = viewModel.importTrackingInviteFromPayload(payload)
        if (imported == null) {
            Toast.makeText(context, "Invalid or expired QR invite", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        selectedPeerIdToTrack = imported.ownerPeerId
        qrPayloadInput = payload
        pinInput = imported.password
        Toast.makeText(context, "Invite imported for ${imported.ownerNickname}", Toast.LENGTH_SHORT).show()
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val options = ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Scan tracking invite QR")
                .setBeepEnabled(true)
                .setOrientationLocked(true)
            scanLauncher.launch(options)
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
    }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Account Button
                FloatingActionButton(
                    onClick = {
                        viewModel.refreshAccountProfile()
                        editNickname = myNickname
                        showProfileScreen = true
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.AccountCircle, "Account")
                }

                // 2. Share Device Button
                ExtendedFloatingActionButton(
                    onClick = {
                        if (ownerInvite == null) {
                            viewModel.createOrRotateOwnerTrackingInvite()
                        }
                        showShareDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(Icons.Default.QrCode, "Share")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share device")
                }

                // 3. Track Device Button
                ExtendedFloatingActionButton(
                    onClick = { showTrackSelectionDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Radar, "Track")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Track a device")
                }

                // 4. Center Button
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

            // 1. Profile Screen
            if (showProfileScreen) {
                ProfileScreenDialog(
                    username = accountUsername,
                    email = accountEmail,
                    nickname = editNickname,
                    onNicknameChange = { editNickname = it },
                    onSave = {
                        viewModel.updateSettings(editNickname)
                        showProfileScreen = false
                        Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showProfileScreen = false }
                )
            }

            // 2. Share Dialog
            if (showShareDialog) {
                val invite = ownerInvite
                val qrPayload = invite?.toPayload().orEmpty()
                val qrBitmap = remember(qrPayload) { encodeTrackingInviteAsQrBitmap(qrPayload) }

                AlertDialog(
                    onDismissRequest = { showShareDialog = false },
                    title = { Text("Share Tracking", fontFamily = FontFamily.Monospace) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (invite == null) {
                                Text("Generate an invite to share your location access.")
                            } else {
                                Text("Ask follower to scan this QR, then enter password.")
                                Text("Device ID: ${invite.ownerPeerId.take(6)}...", fontSize = 12.sp)
                                Text("Password: ${invite.password}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                qrBitmap?.let {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = "Tracking invite QR",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(240.dp)
                                    )
                                }
                                OutlinedTextField(
                                    value = qrPayload,
                                    onValueChange = {},
                                    label = { Text("QR payload") },
                                    readOnly = true,
                                    minLines = 3,
                                    maxLines = 5,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.createOrRotateOwnerTrackingInvite() }) {
                            Text("Regenerate")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showShareDialog = false }) { Text("Close") }
                    }
                )
            }

            // 3. Select Device Dialog
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
                                    val displayName = viewModel.getDisplayNameForPeer(peer.id, peer.nickname)
                                    ListItem(
                                        headlineContent = { Text(displayName, fontWeight = FontWeight.Bold) },
                                        supportingContent = {
                                            Text(
                                                if (isTracked) "Live tracking enabled" else "Tap to start tracking"
                                            )
                                        },
                                        leadingContent = { Icon(Icons.Default.Smartphone, null) },
                                        trailingContent = {
                                            if (isTracked) Icon(Icons.Default.CheckCircle, null, tint = Color.Green)
                                        },
                                        modifier = Modifier.clickable {
                                            if (!isTracked) {
                                                selectedPeerIdToTrack = peer.id
                                                pinInput = ""
                                                qrPayloadInput = ""
                                                showTrackSelectionDialog = false
                                                showTrackingAuthDialog = true
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

            // 4. Tracking Auth Dialog
            if (showTrackingAuthDialog && selectedPeerIdToTrack != null) {
                AlertDialog(
                    onDismissRequest = { showTrackingAuthDialog = false },
                    title = { Text("Unlock Tracking", fontFamily = FontFamily.Monospace) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Scan invite QR and enter password.")
                            importedInvite?.let { invite ->
                                Text("Authorized owner: ${invite.ownerNickname}", fontWeight = FontWeight.SemiBold)
                            }
                            Button(onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    val options = ScanOptions()
                                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                        .setPrompt("Scan tracking invite QR")
                                        .setBeepEnabled(true)
                                        .setOrientationLocked(true)
                                    scanLauncher.launch(options)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan QR")
                            }
                            OutlinedTextField(
                                value = qrPayloadInput,
                                onValueChange = { qrPayloadInput = it },
                                label = { Text("QR payload (optional paste)") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { pinInput = it },
                                label = { Text("Password") },
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (qrPayloadInput.isNotBlank()) {
                                val imported = viewModel.importTrackingInviteFromPayload(qrPayloadInput)
                                if (imported == null) {
                                    Toast.makeText(context, "Invalid or expired QR payload", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                selectedPeerIdToTrack = imported.ownerPeerId
                            }

                            val success = viewModel.authorizeTracking(selectedPeerIdToTrack!!, pinInput)
                            if (success) {
                                Toast.makeText(context, "Tracking Started", Toast.LENGTH_SHORT).show()
                                showTrackingAuthDialog = false
                            } else {
                                Toast.makeText(context, "Verification failed. Ensure selected device matches scanned invite.", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Verify") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTrackingAuthDialog = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileScreenDialog(
    username: String,
    email: String,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("My Profile", style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Account", style = MaterialTheme.typography.titleMedium)
                        Text("Username: $username", fontWeight = FontWeight.Bold)
                        Text("Email: ${if (email.isBlank()) "Not available" else email}")
                    }
                }

                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Profile Settings", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = onNicknameChange,
                            label = { Text("Nickname") },
                            supportingText = { Text("Default is your device name. You can change it anytime.") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        HorizontalDivider()
                        Text("More settings (static preview)", style = MaterialTheme.typography.bodyMedium)
                        ListItem(
                            headlineContent = { Text("Profile photo") },
                            supportingContent = { Text("Coming soon") },
                            leadingContent = { Icon(Icons.Default.Person, contentDescription = null) }
                        )
                        ListItem(
                            headlineContent = { Text("Privacy controls") },
                            supportingContent = { Text("Coming soon") },
                            leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) }
                        )
                        ListItem(
                            headlineContent = { Text("Notification preferences") },
                            supportingContent = { Text("Coming soon") },
                            leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
