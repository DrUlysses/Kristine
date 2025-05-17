package dr.ulysses.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.SongRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Data class to hold all the state for the UnsortedFabMenu
 */
private data class UnsortedFabMenuState(
    val expanded: Boolean = false,
    val showRemoveDialog: Boolean = false,
    val showResultDialog: Boolean = false,
    val showProgressDialog: Boolean = false,
    val removedCount: Int = 0,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val progress: Float = 0f,
)

/**
 * A FAB menu for the ManageUnsortedList screen.
 * When clicked, it expands to show options like "remove non-existing".
 * This implementation mimics the behavior of FloatingActionButtonMenu from material3:1.4.0-alpha14
 */
@Composable
fun UnsortedFabMenu() {
    var state by remember { mutableStateOf(UnsortedFabMenuState()) }
    val coroutineScope = rememberCoroutineScope()

    // Animation values
    val rotation by animateFloatAsState(targetValue = if (state.expanded) 90f else 0f, label = "rotation")
    val fabScale by animateFloatAsState(targetValue = if (state.expanded) 1.1f else 1.0f, label = "fabScale")

    // Show confirmation dialog
    if (state.showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { state = state.copy(showRemoveDialog = false) },
            title = { Text("Remove non-existing?") },
            text = { Text("This will remove all songs from the database where the file no longer exists on the device.") },
            confirmButton = {
                TextButton(onClick = {
                    state = state.copy(
                        showRemoveDialog = false,
                        showProgressDialog = true
                    )

                    // Launch a coroutine to remove non-existing songs
                    coroutineScope.launch {
                        SongRepository.removeNonExistingSongs().collectLatest { progressState ->
                            state = state.copy(
                                processedCount = progressState.processed,
                                totalCount = progressState.total,
                                removedCount = progressState.removed,
                                progress = if (progressState.total > 0) progressState.processed.toFloat() / progressState.total else 0f
                            )

                            if (progressState.isComplete) {
                                state = state.copy(
                                    showProgressDialog = false,
                                    showResultDialog = true
                                )
                            }
                        }
                    }
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { state = state.copy(showRemoveDialog = false) }) {
                    Text("No")
                }
            }
        )
    }

    // Show progress dialog
    if (state.showProgressDialog) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissal while the operation is in progress */ },
            title = { Text("Removing non-existing songs") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Processed ${state.processedCount} of ${state.totalCount} songs",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = ProgressIndicatorDefaults.linearColor,
                        trackColor = ProgressIndicatorDefaults.linearTrackColor,
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    )
                    Text(
                        "Removed ${state.removedCount} songs so far",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = { /* No confirmation button during operation */ }
        )
    }

    // Show result dialog
    if (state.showResultDialog) {
        AlertDialog(
            onDismissRequest = { state = state.copy(showResultDialog = false) },
            title = { Text("Operation Complete") },
            text = { Text("Removed ${state.removedCount} non-existing song${if (state.removedCount != 1) "s" else ""} from the database.") },
            confirmButton = {
                TextButton(onClick = { state = state.copy(showResultDialog = false) }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.fillMaxSize().padding(8.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Mini FABs (only visible when expanded)
        if (state.expanded) {
            // Option 1: Remove non-existing
            SmallFloatingActionButton(
                onClick = {
                    state = state.copy(
                        expanded = false,
                        showRemoveDialog = true
                    )
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove non-existing"
                )
            }

            // Add more mini FABs here as needed
        }

        // Main FAB
        FloatingActionButton(
            onClick = { state = state.copy(expanded = !state.expanded) },
            modifier = Modifier.scale(fabScale)
        ) {
            Icon(
                imageVector = if (state.expanded) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = if (state.expanded) "Close menu" else "Open menu",
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}
