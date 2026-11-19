package dr.ulysses.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private data class UnsortedFabMenuState(
    val expanded: Boolean = false,
    val showRemoveDialog: Boolean = false,
    val showUpdateDurationDialog: Boolean = false,
    val showResultDialog: Boolean = false,
    val showProgressDialog: Boolean = false,
    val changedCount: Int = 0,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val progress: Float = 0f,
    val operationType: OperationType = OperationType.NONE,
)

private enum class OperationType {
    NONE,
    REMOVE_NON_EXISTING,
    UPDATE_DURATIONS
}

/**
 * FAB menu of the ManageUnsortedList screen, modeled on material3's FloatingActionButtonMenu.
 */
@Composable
fun UnsortedFabMenu() {
    var state by remember { mutableStateOf(UnsortedFabMenuState()) }
    val coroutineScope = rememberCoroutineScope()

    val rotation by animateFloatAsState(
        targetValue = if (state.expanded) {
            90f
        } else {
            0f
        },
        label = "rotation"
    )
    val fabScale by animateFloatAsState(
        targetValue = if (state.expanded) {
            1.1f
        } else {
            1.0f
        },
        label = "fabScale"
    )

    fun run(operation: OperationType, progress: Flow<SongRepository.Progress>) {
        state = state.copy(
            showRemoveDialog = false,
            showUpdateDurationDialog = false,
            showProgressDialog = true,
            operationType = operation
        )
        coroutineScope.launch {
            progress.collect { p ->
                state = state.copy(
                    processedCount = p.processed,
                    totalCount = p.total,
                    changedCount = p.changed,
                    progress = if (p.total > 0) {
                        p.processed.toFloat() / p.total
                    } else {
                        0f
                    },
                    showProgressDialog = !p.isComplete,
                    showResultDialog = p.isComplete,
                )
            }
        }
    }

    if (state.showRemoveDialog) {
        AlertDialog(
            onDismissRequest = {
                state = state.copy(
                    showRemoveDialog = false
                )
            },
            title = { Text("Remove non-existing?") },
            text = { Text("This will remove all songs from the database where the file no longer exists on the device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        run(
                            operation = OperationType.REMOVE_NON_EXISTING,
                            progress = SongRepository.removeNonExistingSongs()
                        )
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        state = state.copy(
                            showRemoveDialog = false
                        )
                    }
                ) {
                    Text("No")
                }
            }
        )
    }

    if (state.showUpdateDurationDialog) {
        AlertDialog(
            onDismissRequest = {
                state = state.copy(
                    showUpdateDurationDialog = false
                )
            },
            title = { Text("Update song durations?") },
            text = { Text("This will update the duration for all songs in the database from their metadata. This can fix issues with the player slider.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        run(
                            operation = OperationType.UPDATE_DURATIONS,
                            progress = SongRepository.updateSongDurations()
                        )
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        state = state.copy(
                            showUpdateDurationDialog = false
                        )
                    }
                ) {
                    Text("No")
                }
            }
        )
    }

    if (state.showProgressDialog) {
        AlertDialog(
            onDismissRequest = { /* Not dismissable while the operation runs */ },
            title = {
                when (state.operationType) {
                    OperationType.REMOVE_NON_EXISTING -> Text("Removing non-existing songs")
                    OperationType.UPDATE_DURATIONS -> Text("Updating song durations")
                    else -> Text("Processing...")
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Processed ${state.processedCount} of ${state.totalCount} songs",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(
                            bottom = 8.dp
                        )
                    )
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = ProgressIndicatorDefaults.linearColor,
                        trackColor = ProgressIndicatorDefaults.linearTrackColor,
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    )
                    when (state.operationType) {
                        OperationType.REMOVE_NON_EXISTING ->
                            Text(
                                text = "Removed ${state.changedCount} songs so far",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(
                                    top = 8.dp
                                )
                            )

                        OperationType.UPDATE_DURATIONS ->
                            Text(
                                text = "Updated ${state.changedCount} songs so far",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(
                                    top = 8.dp
                                )
                            )

                        else ->
                            Text(
                                text = "Processing...",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(
                                    top = 8.dp
                                )
                            )
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (state.showResultDialog) {
        AlertDialog(
            onDismissRequest = {
                state = state.copy(
                    showResultDialog = false
                )
            },
            title = { Text("Operation Complete") },
            text = {
                when (state.operationType) {
                    OperationType.REMOVE_NON_EXISTING ->
                        Text(
                            "Removed ${state.changedCount} non-existing song${
                                if (state.changedCount != 1) {
                                    "s"
                                } else {
                                    ""
                                }
                            } from the database."
                        )

                    OperationType.UPDATE_DURATIONS ->
                        Text(
                            "Updated the duration of ${state.changedCount} song${
                                if (state.changedCount != 1) {
                                    "s"
                                } else {
                                    ""
                                }
                            }. The player slider should now work correctly."
                        )

                    else ->
                        Text("Operation completed successfully.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        state = state.copy(
                            showResultDialog = false
                        )
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.fillMaxSize().padding(8.dp)
    ) {
        Spacer(
            modifier = Modifier.weight(1f)
        )

        if (state.expanded) {
            SmallFloatingActionButton(
                onClick = {
                    state = state.copy(
                        expanded = false,
                        showRemoveDialog = true
                    )
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(
                    bottom = 8.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove non-existing"
                )
            }

            SmallFloatingActionButton(
                onClick = {
                    state = state.copy(
                        expanded = false,
                        showUpdateDurationDialog = true
                    )
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(
                    bottom = 8.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Update,
                    contentDescription = "Update song durations"
                )
            }
        }

        FloatingActionButton(
            onClick = {
                state = state.copy(
                    expanded = !state.expanded
                )
            },
            modifier = Modifier.scale(fabScale)
        ) {
            Icon(
                imageVector = if (state.expanded) {
                    Icons.Filled.Close
                } else {
                    Icons.Filled.Add
                },
                contentDescription = if (state.expanded) {
                    "Close menu"
                } else {
                    "Open menu"
                },
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}
