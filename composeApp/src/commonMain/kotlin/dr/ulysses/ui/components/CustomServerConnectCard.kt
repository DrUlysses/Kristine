package dr.ulysses.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dr.ulysses.network.NetworkManager

@Composable
fun CustomServerConnectCard(
    customServerAddress: MutableState<String>,
    customServerPort: MutableState<String>,
    showCustomServerError: MutableState<Boolean>,
) {
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
}
