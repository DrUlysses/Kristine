package dr.ulysses.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dr.ulysses.api.internal.isRemote
import dr.ulysses.entities.*
import dr.ulysses.models.MainViewModel
import dr.ulysses.player.Player
import dr.ulysses.ui.components.ToolChip
import dr.ulysses.ui.elements.ListScrollbar
import kotlinx.coroutines.launch

/**
 * Opens [OrganizeDialog]; shown only where song files can be moved.
 */
@Composable
fun OrganizeFoldersChip() {
    var isAvailable by remember { mutableStateOf(false) }
    var isOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // A connected server has its music folder
        isAvailable = isRemote || musicFolder() != null
    }
    if (!isAvailable) {
        return
    }
    ToolChip(
        label = "Organize folders",
        icon = Icons.AutoMirrored.Outlined.DriveFileMove,
        onClick = { isOpen = true }
    )
    if (isOpen) {
        OrganizeDialog(
            onClose = { isOpen = false }
        )
    }
}

/**
 * Shows where each sorted song goes, moves them on confirmation, then tells how it went.
 */
@Composable
private fun OrganizeDialog(onClose: () -> Unit) {
    var plan by remember { mutableStateOf<OrganizePlan?>(null) }
    var isMoving by remember { mutableStateOf(false) }
    // Set once the moves are done
    var failed by remember { mutableStateOf<List<Move>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        plan = planOrganize()
    }
    val current = plan
    val result = failed

    AlertDialog(
        // A move that is half done must not be left by tapping outside
        onDismissRequest = {
            if (!isMoving) {
                onClose()
            }
        },
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.DriveFileMove,
                contentDescription = null
            )
        },
        title = {
            Text(
                when {
                    result != null -> "Folders organized"
                    current == null || current.moves.isEmpty() -> "Organize folders"
                    else -> "Move ${current.moves.size} sorted songs?"
                }
            )
        },
        text = {
            when {
                current == null || isMoving -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator()
                }

                result != null -> Text(
                    if (result.isEmpty()) {
                        "All ${current.moves.size} songs are in their Artist / Album folders now."
                    } else {
                        "${current.moves.size - result.size} songs moved. ${result.size} couldn't be moved and " +
                                "stay where they were, e.g. ${result.first().song.path.removePrefix("file://")}."
                    }
                )

                else -> PlanContent(
                    plan = current
                )
            }
        },
        confirmButton = {
            when {
                result != null -> TextButton(
                    onClick = onClose
                ) {
                    Text("Done")
                }

                current != null && current.moves.isNotEmpty() && !isMoving -> TextButton(
                    onClick = {
                        isMoving = true
                        scope.launch {
                            // Their old paths are gone after the move
                            current.moves.forEach { Player.onRemoveFromQueueCommand(it.song) }
                            failed = organize(current.moves)
                            isMoving = false
                            MainViewModel.loadSongs()
                        }
                    }
                ) {
                    Text("Move")
                }
            }
        },
        dismissButton = {
            if (result == null && !isMoving) {
                TextButton(
                    onClick = onClose
                ) {
                    Text(
                        if (current?.moves.isNullOrEmpty()) {
                            "Close"
                        } else {
                            "Cancel"
                        }
                    )
                }
            }
        }
    )
}

/**
 * Where each song goes, relative to the music folder, and which songs stay and why.
 */
@Composable
private fun PlanContent(plan: OrganizePlan) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            if (plan.moves.isEmpty()) {
                "All sorted songs are in their Artist / Album folders in ${plan.root}."
            } else {
                "Each song moves to Artist / Album / Title in ${plan.root}. Playlists keep their songs."
            }
        )
        if (plan.moves.isEmpty() && plan.skipped.isEmpty()) {
            return@Column
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            val listState = rememberLazyListState()
            Box(
                modifier = Modifier.heightIn(
                    max = 320.dp
                )
            ) {
                LazyColumn(
                    state = listState,
                    // Room for the scrollbar
                    contentPadding = PaddingValues(
                        end = 12.dp
                    )
                ) {
                    items(
                        items = plan.moves,
                        key = { it.song.path }
                    ) { move ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    move.target.substringAfter(plan.root).trimStart('/')
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "From ${move.song.path.removePrefix("file://").substringBeforeLast('/')}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.DriveFileMove,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent
                            )
                        )
                    }
                    items(
                        items = plan.skipped,
                        key = { (song, _) -> "skipped:${song.path}" }
                    ) { (song, reason) ->
                        ListItem(
                            headlineContent = { Text(song.path.substringAfterLast('/')) },
                            supportingContent = {
                                Text(
                                    text = "Stays where it is: $reason",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent
                            )
                        )
                    }
                }
                ListScrollbar(
                    state = listState
                )
            }
        }
    }
}
