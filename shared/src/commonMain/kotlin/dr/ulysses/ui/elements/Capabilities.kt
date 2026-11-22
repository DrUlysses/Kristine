package dr.ulysses.ui.elements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dr.ulysses.network.NetworkManager

/**
 * Whether the shown library can be managed: this device's, or a server's that allows it. What can't is disabled,
 * not hidden, so it's clear it exists.
 */
@Composable
fun canManageLibrary(): Boolean {
    val isConnected by NetworkManager.isConnected.collectAsState()
    val type by NetworkManager.serverType.collectAsState()
    return !isConnected || type.canManageLibrary
}
