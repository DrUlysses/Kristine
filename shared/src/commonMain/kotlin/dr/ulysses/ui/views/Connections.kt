package dr.ulysses.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
    val playHere by NetworkManager.playHere.collectAsState()
    val certificateChange by NetworkManager.certificateChange.collectAsState()
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
            certificateChange?.let {
                CertificateChangeCard(
                    change = it
                )
            }
            if (currentServer != null) {
                PlayHereCard(
                    playHere = playHere,
                    onPlayHereChange = NetworkManager::setPlayHere
                )
            }
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

@Composable
private fun CertificateChangeCard(change: NetworkManager.CertificateChange) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                bottom = 16.dp
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "${change.address} has a different certificate",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Connecting was refused. This is expected after the server app was reinstalled; otherwise " +
                        "another device may be pretending to be this server. Compare with the fingerprint under " +
                        "\"Your Server\" on that device:",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = change.fingerprint,
                style = MaterialTheme.typography.bodySmall
            )
            Row {
                TextButton(
                    onClick = NetworkManager::dismissCertificateChange
                ) {
                    Text("Don't connect")
                }
                TextButton(
                    onClick = { NetworkManager.trustChangedCertificate(change) }
                ) {
                    Text("Trust and connect")
                }
            }
        }
    }
}

@Composable
private fun PlayHereCard(playHere: Boolean, onPlayHereChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 8.dp
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Play on this device",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Off: only control the server. On: also play its songs here, from this library or downloaded",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = playHere,
                onCheckedChange = onPlayHereChange
            )
        }
    }
}
