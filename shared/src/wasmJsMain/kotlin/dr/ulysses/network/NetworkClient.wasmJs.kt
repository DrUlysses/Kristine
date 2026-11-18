package dr.ulysses.network

import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

actual fun startDiscovery(
    discoveryJob: MutableState<Job?>,
    scope: CoroutineScope,
    discoveredServers: MutableMap<String, Int>,
    serverLastSeen: MutableMap<String, Long>,
    cleanupStaleServers: () -> Unit,
    onServersDiscovered: (Map<String, Int>) -> Unit,
) {
    // Nothing to do. Can't start UDP
}
