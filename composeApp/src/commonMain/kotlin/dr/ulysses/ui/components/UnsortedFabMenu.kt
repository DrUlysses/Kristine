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
 * A FAB menu for the ManageUnsortedList screen.
 * When clicked, it expands to show options like "remove non-existing".
 * This implementation mimics the behavior of FloatingActionButtonMenu from material3:1.4.0-alpha14
 */
@Composable
fun UnsortedFabMenu() {
    var expanded by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var removedCount by remember { mutableStateOf(0) }
    var processedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    // Animation values
    val rotation by animateFloatAsState(targetValue = if (expanded) 90f else 0f, label = "rotation")
    val fabScale by animateFloatAsState(targetValue = if (expanded) 1.1f else 1.0f, label = "fabScale")

    // Show confirmation dialog
    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove non-existing?") },
            text = { Text("This will remove all songs from the database where the file no longer exists on the device.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDialog = false
                    showProgressDialog = true

                    // Launch a coroutine to remove non-existing songs
                    coroutineScope.launch {
                        SongRepository.removeNonExistingSongs().collectLatest { progressState ->
                            processedCount = progressState.processed
                            totalCount = progressState.total
                            removedCount = progressState.removed
                            progress = if (totalCount > 0) processedCount.toFloat() / totalCount else 0f

                            if (progressState.isComplete) {
                                showProgressDialog = false
                                showResultDialog = true
                            }
                        }
                    }
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("No")
                }
            }
        )
    }

    // Show progress dialog
    if (showProgressDialog) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissal while operation is in progress */ },
            title = { Text("Removing non-existing songs") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Processed $processedCount of $totalCount songs",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Removed $removedCount songs so far",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = { /* No confirmation button during operation */ }
        )
    }

    // Show result dialog
    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = { Text("Operation Complete") },
            text = { Text("Removed $removedCount non-existing song${if (removedCount != 1) "s" else ""} from the database.") },
            confirmButton = {
                TextButton(onClick = { showResultDialog = false }) {
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
        if (expanded) {
            // Option 1: Remove non-existing
            SmallFloatingActionButton(
                onClick = {
                    expanded = false
                    showRemoveDialog = true
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
            onClick = { expanded = !expanded },
            modifier = Modifier.scale(fabScale)
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = if (expanded) "Close menu" else "Open menu",
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}
