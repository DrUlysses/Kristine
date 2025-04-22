package dr.ulysses.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dr.ulysses.network.NetworkManager

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CustomServerConnectCard(
    customServerAddress: MutableState<String>,
    customServerPort: MutableState<String>,
    showCustomServerError: MutableState<Boolean>,
) {
    val focusManager = LocalFocusManager.current
    val portFieldFocusRequester = remember { FocusRequester() }
    val onServerChosen = {
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
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = "Connect to Server",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Row {
                OutlinedTextField(
                    value = customServerAddress.value,
                    maxLines = 1,
                    onValueChange = { customServerAddress.value = it },
                    label = { Text("Server Address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            focusManager.moveFocus(FocusDirection.Next)
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 4.dp)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.key == Key.Tab && keyEvent.type == KeyEventType.KeyDown) {
                                portFieldFocusRequester.requestFocus()
                                true
                            } else {
                                false
                            }
                        }
                )

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = customServerPort.value,
                    maxLines = 1,
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
                        .padding(bottom = 4.dp)
                        .focusRequester(portFieldFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown) {
                                onServerChosen()
                                true
                            } else {
                                false
                            }
                        }
                )

                IconButton(
                    modifier = Modifier
                        .weight(0.2f)
                        .padding(top = 14.dp)
                        .fillMaxSize(),
                    onClick = onServerChosen,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Connect",
                    )
                }
            }

            if (showCustomServerError.value) {
                Text(
                    text = "Please enter a valid server address and port",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}
