package com.tracksure.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tracksure.android.mesh.PeerInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapUserSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    targetNickname: String,
    peerInfo: PeerInfo,
    modifier: Modifier = Modifier
) {
    if (isPresented) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = targetNickname,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Divider()

                InfoRow("ID", peerInfo.id)
                InfoRow("Status", if (peerInfo.isConnected) "Connected (Mesh)" else "Offline")
                InfoRow("Coordinates", String.format("%.5f, %.5f", peerInfo.latitude ?: 0.0, peerInfo.longitude ?: 0.0))

                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                InfoRow("Last Seen", sdf.format(Date(peerInfo.lastSeen)))

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary)
        Text(text = value)
    }
}
