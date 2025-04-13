package dr.ulysses.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dr.ulysses.network.NetworkManager
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

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                items(discoveredServers.value.entries.toList()) { (serverAddress, serverPort) ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Server: $serverAddress",
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
