package dr.ulysses.ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dr.ulysses.network.NetworkManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Your Server",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Port: ${localServerPort.value}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // Custom server connection section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Connect to Server",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row {
                            OutlinedTextField(
                                value = customServerAddress.value,
                                onValueChange = { customServerAddress.value = it },
                                label = { Text("Server Address") },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = 8.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedTextField(
                                value = customServerPort.value,
                                onValueChange = {
                                    // Only allow numeric input
                                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                        customServerPort.value = it
                                    }
                                },
                                label = { Text("Server Port") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = 8.dp)
                            )
                        }

                        if (showCustomServerError.value) {
                            Text(
                                text = "Please enter a valid server address and port",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        Button(
                            onClick = {
                                val address = customServerAddress.value.trim()
                                val portStr = customServerPort.value.trim()

                                if (address.isNotEmpty() && portStr.isNotEmpty()) {
                                    val port = portStr.toIntOrNull()
                                    if (port != null && port > 0) {
                                        // Connect to the custom server
                                        NetworkManager.connectToCustomServer(address, port)
                                        showCustomServerError.value = false
                                    } else {
                                        showCustomServerError.value = true
                                    }
                                } else {
                                    showCustomServerError.value = true
                                }
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Connect")
                        }
                    }
                }

                // Discovered servers section
                Text(
                    text = "Discovered Servers",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (discoveredServers.value.isEmpty()) {
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
                        // Sort the servers so that the connected server appears first
                        val sortedServers = discoveredServers.value.entries.toList().sortedBy { (address, port) ->
                            // If this is the current server, sort it first
                            if (currentServer.value != null &&
                                currentServer.value?.first == address &&
                                currentServer.value?.second == port
                            ) {
                                0
                            } else {
                                1
                            }
                        }

                        items(sortedServers) { (serverAddress, serverPort) ->
                            // Check if this is the current server
                            val isCurrentServer = currentServer.value != null &&
                                    currentServer.value?.first == serverAddress &&
                                    currentServer.value?.second == serverPort

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
                    }
                }
            }
        }
    )
}
