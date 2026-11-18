package dr.ulysses.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dr.ulysses.ui.elements.ServerListEntry
import kotlinx.coroutines.CoroutineScope

@Composable
fun DiscoveredServersList(
    discoveredServers: Map<String, Int>,
    currentServer: Pair<String, Int>?,
    scope: CoroutineScope,
) {
    Text(
        text = "Discovered Servers",
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(bottom = 16.dp)
    )

    if (discoveredServers.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Searching for servers on the network...",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The connected server comes first
            val sortedServers = discoveredServers.toList().sortedBy { it != currentServer }

            items(sortedServers) { (serverAddress, serverPort) ->
                ServerListEntry(
                    serverAddress = serverAddress,
                    serverPort = serverPort,
                    isCurrentServer = (serverAddress to serverPort) == currentServer,
                    scope = scope
                )
            }
        }
    }
}
