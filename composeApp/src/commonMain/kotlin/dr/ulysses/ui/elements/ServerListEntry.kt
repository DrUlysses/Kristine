package dr.ulysses.ui.elements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.network.NetworkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ServerListEntry(
    serverAddress: String,
    serverPort: Int,
    isCurrentServer: Boolean,
    scope: CoroutineScope,
) {
    // Set card colors based on connection status
    val cardColors = if (isCurrentServer) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    } else {
        CardDefaults.cardColors()
    }

    Card(
        colors = cardColors,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                scope.launch {
                    if (!isCurrentServer) {
                        NetworkManager.connectToDiscoveredServer(serverAddress, serverPort)
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Server: $serverAddress" + if (isCurrentServer) " (Connected)" else "",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Port: $serverPort",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
