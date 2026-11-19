package dr.ulysses.ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dr.ulysses.network.NetworkManager
import dr.ulysses.network.SERVER_PORT
import dr.ulysses.network.ServerInfo
import dr.ulysses.ui.elements.toClipEntry
import kotlinx.coroutines.launch

@Composable
fun Connections() {
    val localServer by NetworkManager.localServer.collectAsState()
    val discoveredServers by NetworkManager.discoveredServers.collectAsState()
    val currentServer by NetworkManager.currentServer.collectAsState()
    val isConnected by NetworkManager.isConnected.collectAsState()
    val playHere by NetworkManager.playHere.collectAsState()
    val certificateChange by NetworkManager.certificateChange.collectAsState()
    val clipboard = LocalClipboard.current

    // What another device needs to connect here, copied right away; null where this device runs no server
    val ownServer = localServer.addresses.firstOrNull()
        ?.takeIf { localServer.fingerprint != null }
        ?.let {
            serverText(
                address = it,
                port = localServer.port
            )
        }
    LaunchedEffect(ownServer) {
        ownServer?.let { clipboard.setClipEntry(it.toClipEntry()) }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        certificateChange?.let {
            CertificateChangeCard(
                change = it
            )
        }
        currentServer?.let { (address, port) ->
            CurrentServerCard(
                server = serverText(
                    address = address,
                    port = port
                ),
                isConnected = isConnected,
                playHere = playHere
            )
        }
        ConnectField()
        // Only a device with a server hears announcements; a browser can't
        if (ownServer != null) {
            NearbyServers(
                // This device hears its own announcements too, also over loopback
                servers = discoveredServers.filterKeys { it !in localServer.addresses && !it.startsWith("127.") },
                currentServer = currentServer
            )
            OwnServerCard(
                localServer = localServer
            )
        }
    }
}

/** How a server is shown, copied and typed: the address, plus the port only when it isn't the usual one. */
private fun serverText(address: String, port: Int) = if (port == SERVER_PORT) {
    address
} else {
    "$address:$port"
}

/** Reads [serverText]'s format back; an https:// prefix, as from the browser's address bar, is fine too. */
// shortcut: IPv6 addresses aren't understood, fine while servers announce IPv4 only
private fun parseServer(text: String): Pair<String, Int>? {
    val server = text.trim().removePrefix("https://").removeSuffix("/")
    val address = server.substringBefore(':')
    val portText = server.substringAfter(
        delimiter = ':',
        missingDelimiterValue = ""
    )
    if (address.isEmpty() || address.any { it.isWhitespace() }) {
        return null
    }
    val port = if (portText.isEmpty()) {
        SERVER_PORT
    } else {
        portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    }
    return address to port
}

@Composable
private fun ConnectField() {
    var text by remember { mutableStateOf("") }
    var isInvalid by remember { mutableStateOf(false) }
    fun connect() {
        val server = parseServer(text)
        isInvalid = server == null
        if (server != null) {
            NetworkManager.connectToCustomServer(
                address = server.first,
                port = server.second
            )
            text = ""
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            isInvalid = false
        },
        label = { Text("Server address") },
        placeholder = { Text("Paste the address copied on the server") },
        singleLine = true,
        isError = isInvalid,
        supportingText = if (isInvalid) {
            { Text("Expected an address like 192.168.1.5, optionally with :port") }
        } else {
            null
        },
        trailingIcon = {
            IconButton(
                onClick = ::connect
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Connect"
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Go
        ),
        keyboardActions = KeyboardActions(
            onGo = { connect() }
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NearbyServers(servers: Map<String, Int>, currentServer: Pair<String, Int>?) {
    Column {
        Text(
            text = "Nearby servers",
            style = MaterialTheme.typography.titleMedium
        )
        if (servers.isEmpty()) {
            Text(
                text = "Searching the network…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    vertical = 8.dp
                )
            )
        }
        servers.forEach { (address, port) ->
            val isCurrent = (address to port) == currentServer
            ListItem(
                headlineContent = {
                    Text(
                        serverText(
                            address = address,
                            port = port
                        )
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Dns,
                        contentDescription = null
                    )
                },
                trailingContent = {
                    Text(
                        if (isCurrent) {
                            "Current"
                        } else {
                            "Connect"
                        }
                    )
                },
                modifier = Modifier.clickable(
                    enabled = !isCurrent
                ) {
                    NetworkManager.connect(
                        address = address,
                        port = port
                    )
                }
            )
        }
    }
}

@Composable
private fun CurrentServerCard(server: String, isConnected: Boolean, playHere: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isConnected) {
                            "Connected to"
                        } else {
                            "Connecting to"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = server,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                TextButton(
                    onClick = NetworkManager::disconnect,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text("Disconnect")
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    top = 8.dp
                )
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Play on this device",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Off: only control the server. On: also play its songs here",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = playHere,
                    onCheckedChange = NetworkManager::setPlayHere
                )
            }
        }
    }
}

@Composable
private fun OwnServerCard(localServer: ServerInfo) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "This device's server",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Its address is copied. Paste it on the other device to connect.",
                style = MaterialTheme.typography.bodySmall
            )
            localServer.addresses.forEach { address ->
                val text = serverText(
                    address = address,
                    port = localServer.port
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { scope.launch { clipboard.setClipEntry(text.toClipEntry()) } }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy $text"
                        )
                    }
                }
            }
            localServer.fingerprint?.let {
                // Lets the user check, on a client's first connection, that it pinned this server
                Text(
                    text = "Certificate SHA-256: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CertificateChangeCard(change: NetworkManager.CertificateChange) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
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
                        "another device may be pretending to be this server. Compare with the certificate under " +
                        "\"This device's server\" on that device:",
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
