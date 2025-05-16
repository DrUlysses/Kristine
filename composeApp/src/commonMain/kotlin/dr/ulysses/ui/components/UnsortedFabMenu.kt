package dr.ulysses.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp

/**
 * A FAB menu for the ManageUnsortedList screen.
 * When clicked, it expands to show options like "remove non-existing".
 * This implementation mimics the behavior of FloatingActionButtonMenu from material3:1.4.0-alpha14
 */
@Composable
fun UnsortedFabMenu() {
    var expanded by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    // Animation values
    val rotation by animateFloatAsState(targetValue = if (expanded) 90f else 0f, label = "rotation")
    val fabScale by animateFloatAsState(targetValue = if (expanded) 1.1f else 1.0f, label = "fabScale")

    // Show dialog if needed
    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove non-existing?") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDialog = false
                    // Action for removing non-existing songs would go here
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

    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.fillMaxSize().padding(16.dp)
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
