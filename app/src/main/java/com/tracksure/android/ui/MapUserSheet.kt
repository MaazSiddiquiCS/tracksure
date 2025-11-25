package com.tracksure.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapUserSheet(
    selectedPeer: PeerLocation,
    myLocation: PeerLocation?,
    onDismiss: () -> Unit,
    onChatClick: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "@${selectedPeer.nickname}",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            )

            if (myLocation != null) {
                val dist = MapUIUtils.calculateDistance(myLocation.geoPoint, selectedPeer.geoPoint)
                Text(
                    text = "Distance: ${MapUIUtils.formatDistance(dist)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { onChatClick(selectedPeer.peerID) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Chat, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Chat")
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Close")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
