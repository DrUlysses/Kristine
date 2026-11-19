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

@Composable
fun Connections() {
    val customServerAddress = remember { mutableStateOf("") }
    val customServerPort = remember { mutableStateOf("") }
    val showCustomServerError = remember { mutableStateOf(false) }
    val localServer by NetworkManager.localServer.collectAsState()
    val discoveredServers by NetworkManager.discoveredServers.collectAsState()
    val currentServer by NetworkManager.currentServer.collectAsState()
    val scope = rememberCoroutineScope()

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
            LocalServerCard(
                localServer = localServer
            )
            CustomServerConnectCard(
                customServerAddress = customServerAddress,
                customServerPort = customServerPort,
                showCustomServerError = showCustomServerError
            )
            DiscoveredServersList(
                // This device hears its own announcements too
                discoveredServers = discoveredServers.filterValues { it != localServer.port },
                currentServer = currentServer,
                scope = scope
            )
        }
    }
}
