package com.tracksure.android.ui

import android.preference.PreferenceManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

    var hasCenteredOnce by remember { mutableStateOf(false) }
    var mapController by remember { mutableStateOf<org.osmdroid.api.IMapController?>(null) }
    var selectedPeerId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                myLocation?.let { loc ->
                    mapController?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                    mapController?.setZoom(18.0)
                }
            }) {
                Icon(Icons.Default.MyLocation, "Center")
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
                        mapView.overlays.add(myMarker)

                        if (!hasCenteredOnce) {
                            mapView.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                            mapView.controller.setZoom(18.0)
                            hasCenteredOnce = true
                        }
                    }

                    // Draw Peers
                    peerLocations.forEach { (id, info) ->
                        if (info.latitude != null && info.longitude != null) {
                            val marker = Marker(mapView)
                            marker.position = GeoPoint(info.latitude!!, info.longitude!!)
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            marker.title = info.nickname
                            marker.snippet = "Tap for details"

                            marker.setOnMarkerClickListener { m, _ ->
                                selectedPeerId = id
                                m.showInfoWindow()
                                true
                            }
                            mapView.overlays.add(marker)
                        }
                    }
                    mapView.invalidate()
                }
            )

            // 2. CONNECTION HUD (Top Left)
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

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // List of peers
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(peerLocations.values.toList()) { peer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPeerId = peer.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Column {
                                    Text(peer.nickname, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if(peer.latitude!=null) "Lat: ${peer.latitude.toString().take(6)}" else "No Loc",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. BOTTOM SHEET
            if (selectedPeerId != null) {
                peerLocations[selectedPeerId]?.let { info ->
                    MapUserSheet(
                        isPresented = true,
                        onDismiss = { selectedPeerId = null },
                        targetNickname = info.nickname,
                        peerInfo = info
                    )
                }
            }
        }
    }
}
