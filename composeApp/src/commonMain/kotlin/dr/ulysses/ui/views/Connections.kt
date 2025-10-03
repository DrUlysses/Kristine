package dr.ulysses.ui.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.network.NetworkManager
import dr.ulysses.ui.components.CustomServerConnectCard
import dr.ulysses.ui.components.DiscoveredServersList
import dr.ulysses.ui.components.LocalServerCard
import kotlinx.coroutines.flow.collectLatest

@Composable
fun Connections() {
    val discoveredServers = remember { mutableStateOf(emptyMap<String, Int>()) }
    val customServerAddress = remember { mutableStateOf("") }
    val customServerPort = remember { mutableStateOf("") }
    val showCustomServerError = remember { mutableStateOf(false) }
    val currentServer = remember { mutableStateOf<Pair<String, Int>?>(null) }
    val scope = rememberCoroutineScope()

    // Collect the discovered servers and local server port from the NetworkManager
    LaunchedEffect(NetworkManager.discoveredServers) {
        NetworkManager.discoveredServers.collectLatest { servers ->
            // Filter out the local server from the discovered servers list
            discoveredServers.value = servers.filterValues { port -> port != NetworkManager.localServer.value.port }
        }
    }

    LaunchedEffect(Unit) {
        NetworkManager.localServer.collectLatest { server ->
            // Update the discovered servers list to exclude the local server
            discoveredServers.value = discoveredServers.value.filterValues { it != server.port }
        }
    }

    // Collect the current server from NetworkManager
    LaunchedEffect(Unit) {
        NetworkManager.currentServer.collectLatest { server ->
            currentServer.value = server
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Display local server information
            LocalServerCard(
                localServer = NetworkManager.localServer.value
            )

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
}
