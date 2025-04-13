package dr.ulysses.ui.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.network.NetworkManager
import dr.ulysses.ui.components.CustomServerConnectCard
import dr.ulysses.ui.components.DiscoveredServersList
import dr.ulysses.ui.components.LocalServerCard
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Serializable

@Serializable
object Connections

@Composable
fun Connections() {
    val discoveredServers = remember { mutableStateOf(emptyMap<String, Int>()) }
    val localServerPort = remember { mutableStateOf(0) }
    val customServerAddress = remember { mutableStateOf("") }
    val customServerPort = remember { mutableStateOf("") }
    val showCustomServerError = remember { mutableStateOf(false) }
    val currentServer = remember { mutableStateOf<Pair<String, Int>?>(null) }
    val scope = rememberCoroutineScope()

    // Collect the discovered servers and local server port from the NetworkManager
    LaunchedEffect(Unit) {
        NetworkManager.discoveredServers.collectLatest { servers ->
            // Filter out the local server from the discovered servers list
            discoveredServers.value = servers.filterValues { port -> port != localServerPort.value }
        }
    }

    LaunchedEffect(Unit) {
        NetworkManager.localServerPort.collectLatest { port ->
            localServerPort.value = port
            // Update the discovered servers list to exclude the local server
            discoveredServers.value = discoveredServers.value.filterValues { it != port }
        }
    }

    // Collect the current server from NetworkManager
    LaunchedEffect(Unit) {
        NetworkManager.currentServer.collectLatest { server ->
            currentServer.value = server
        }
    }

    Scaffold(
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Display local server information
                LocalServerCard(localServerPort = localServerPort.value)

                // Custom server connection section
                CustomServerConnectCard(
                    customServerAddress = customServerAddress,
                    customServerPort = customServerPort,
                    showCustomServerError = showCustomServerError
                )

                // Discovered servers section
                DiscoveredServersList(
                    discoveredServers = discoveredServers.value,
                    currentServer = currentServer.value,
                    scope = scope
                )
            }
        }
    )
}
