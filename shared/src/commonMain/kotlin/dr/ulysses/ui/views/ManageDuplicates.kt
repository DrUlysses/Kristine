package dr.ulysses.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.*
import dr.ulysses.models.MainViewModel
import dr.ulysses.player.Player
import dr.ulysses.ui.elements.ListScrollbar
import dr.ulysses.ui.elements.PlayIconButton
import dr.ulysses.ui.elements.count
import dr.ulysses.ui.elements.songs
import kotlinx.coroutines.launch

/**
 * Songs in the library more than once: one recording of each stays, its best copy.
 */
@Composable
fun ManageDuplicates() {
    var groups by remember { mutableStateOf<List<DuplicateGroup>?>(null) }
    var step by remember { mutableStateOf("Comparing the sound of") }
    var progress by remember { mutableStateOf(0 to 0) }
    LaunchedEffect(Unit) {
        // Groups of one recording first: their copies are all the same, so there is nothing to choose
        groups = findDuplicates { currentStep, done, total ->
            step = currentStep
            progress = done to total
        }.sortedBy { it.recordings.size > 1 }
    }

    val found = groups
    when {
        found == null -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val (done, total) = progress
            Text("$step $done of $total…")
            LinearProgressIndicator(
                progress = {
                    if (total == 0) {
                        0f
                    } else {
                        done.toFloat() / total
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        found.isEmpty() -> Text(
            text = "No duplicates found",
            modifier = Modifier.padding(16.dp)
        )

        else -> Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    RemainingHeader(
                        groups = found,
                        onResolved = { removed ->
                            groups = groups?.mapNotNull { it.without(removed) }
                        }
                    )
                }
                items(
                    items = found,
                    key = { group -> group.songs.first().path }
                ) { group ->
                    DuplicateGroupCard(
                        group = group,
                        onRemoved = { removed ->
                            groups = groups?.mapNotNull { it.without(removed) }
                        }
                    )
                }
            }
            ListScrollbar(
                state = listState
            )
        }
    }
}

/**
 * How many songs are left to resolve and how many extra copies they still have, and resolving at once every song
 * whose copies all sound the same: there, nothing is to choose and the best copy stays.
 */
@Composable
private fun RemainingHeader(
    groups: List<DuplicateGroup>,
    onResolved: (Set<String>) -> Unit,
) {
    val singles = groups.filter { it.recordings.size == 1 }
    var isConfirming by remember { mutableStateOf(false) }
    var isResolving by remember { mutableStateOf(false) }
    var notDeleted by remember { mutableStateOf(emptyList<Song>()) }
    val scope = rememberCoroutineScope()
    val copyCount = groups.sumOf { it.songs.size - 1 }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${songs(groups.size)} left to resolve · ${copies(copyCount)} to delete",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (isResolving) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            } else if (singles.isNotEmpty()) {
                FilledTonalButton(
                    onClick = { isConfirming = true }
                ) {
                    Text("Auto-resolve ${singles.size}")
                }
            }
        }
        if (notDeleted.isNotEmpty()) {
            Text(
                text = "Couldn't delete ${files(notDeleted.size)}. These stay in the library.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (isConfirming) {
        AutoResolveDialog(
            groups = singles,
            onDismiss = { isConfirming = false },
            onConfirm = {
                isConfirming = false
                isResolving = true
                scope.launch {
                    val removed = HashSet<String>()
                    val failed = mutableListOf<Song>()
                    singles.forEach { group ->
                        val resolution = resolve(
                            group = group,
                            keep = group.recordings
                        )
                        removed += resolution.removedPaths
                        failed += resolution.notDeleted
                    }
                    notDeleted = failed
                    onResolved(removed)
                    MainViewModel.loadSongs()
                    isResolving = false
                }
            }
        )
    }
}

/**
 * Confirms resolving every song whose copies all sound the same: names the file that stays for each.
 */
@Composable
private fun AutoResolveDialog(
    groups: List<DuplicateGroup>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val copyCount = groups.sumOf { it.songs.size - 1 }
    DeleteDialog(
        title = "Delete ${copies(copyCount)} of ${songs(groups.size)}?",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    ) {
        Text(
            "The file listed for each song stays in the library. Its other copies, " +
                    "${copies(copyCount)} in all, are deleted from this device and can't be restored."
        )
        Text(
            text = "These files stay · ${groups.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        // Only the list scrolls: what stays and what goes must stay readable at any length
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
                        items = groups,
                        key = { group -> group.songs.first().path }
                    ) { group ->
                        KeptFileItem(
                            group = group
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

/**
 * What both delete confirmations share: a delete icon, [title], [content], the playlist note and a red Confirm.
 */
@Composable
private fun DeleteDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.DeleteForever,
                contentDescription = null
            )
        },
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
                Text(
                    text = "Playlists with a deleted copy get the one that stays instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * A song's file that stays, and how many of its copies go.
 */
@Composable
private fun KeptFileItem(group: DuplicateGroup) {
    val kept = group.recordings.single().best
    ListItem(
        headlineContent = { Text(kept.path.substringAfterLast('/')) },
        supportingContent = {
            Text(
                text = "${kept.title} – ${kept.artist}",
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = "Stays",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Text(
                text = "−${copies(group.songs.size - 1)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

/**
 * What resolving one group did.
 */
private data class Resolution(
    val removedPaths: Set<String>,
    val notDeleted: List<Song>,
)

/**
 * Keeps [kept] and deletes the group's other copies; a copy that couldn't be deleted stays.
 */
private suspend fun resolve(
    group: DuplicateGroup,
    keep: List<Recording>,
): Resolution {
    // The group is done, like a song after Save & Next in Manage Unsorted: the previewed copy stops,
    // and the player must not hold on to a file that is about to disappear
    group.songs.forEach(Player::onRemoveFromQueueCommand)
    val notDeleted = resolveDuplicates(
        group = group,
        keep = keep
    )
    // By path: copies have equal tags, and Song.equals compares the tags
    val notDeletedPaths = notDeleted.mapTo(HashSet()) { it.path }
    return Resolution(
        // The kept copies leave the screen too: the group is resolved
        removedPaths = group.songs.mapTo(HashSet()) { it.path } - notDeletedPaths,
        notDeleted = notDeleted
    )
}

private fun copies(number: Int) = count(
    number = number,
    one = "copy",
    many = "copies"
)

private fun files(number: Int) = count(
    number = number,
    one = "file",
    many = "files"
)

/**
 * The group without the songs at [paths]; null once less than two songs are left.
 */
private fun DuplicateGroup.without(paths: Set<String>): DuplicateGroup? = DuplicateGroup(
    recordings.mapNotNull { recording ->
        recording.copies.filter { it.path !in paths }.takeIf { it.isNotEmpty() }?.let { copies ->
            Recording(
                copies = copies,
                match = recording.match
            )
        }
    }
).takeIf { it.songs.size > 1 }

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    onRemoved: (Set<String>) -> Unit,
) {
    val suggestion = remember(group) { suggest(group) }
    // Until the user ticks any, the suggestion is the selection
    var picked by remember(group) { mutableStateOf<List<Recording>?>(null) }
    var isConfirming by remember { mutableStateOf(false) }
    var notDeleted by remember { mutableStateOf(emptyList<Song>()) }
    val scope = rememberCoroutineScope()
    val keep = picked ?: listOf(suggestion.recording)
    val keptPaths = keep.mapTo(HashSet()) { it.best.path }
    val remove = group.songs.filter { it.path !in keptPaths }

    fun resolveGroup() {
        scope.launch {
            val resolution = resolve(
                group = group,
                keep = keep
            )
            notDeleted = resolution.notDeleted
            onRemoved(resolution.removedPaths)
            MainViewModel.loadSongs()
        }
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        // The catalog's name where it knows the song: tags can say as little as "1"
        val match = group.recordings.firstNotNullOfOrNull { it.match }
        val tags = group.recordings.first().best
        ListItem(
            headlineContent = {
                Text(
                    text = match?.title ?: tags.title,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = { Text(match?.artist ?: tags.artist) },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
        SuggestionLine(
            suggestion = suggestion
        )
        group.recordings.forEach { recording ->
            RecordingItem(
                recording = recording,
                isSelected = recording in keep,
                isSuggested = recording == suggestion.recording,
                onSelect = {
                    picked = if (recording in keep) {
                        keep - recording
                    } else {
                        keep + recording
                    }
                }
            )
        }
        if (notDeleted.isNotEmpty()) {
            Text(
                text = "Couldn't delete ${notDeleted.joinToString { displayPath(it) }}. These stay in the library.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(
                    horizontal = 16.dp
                )
            )
        }
        Button(
            // Nothing to delete: the songs stay as they are and are remembered as different
            onClick = {
                if (remove.isEmpty()) {
                    resolveGroup()
                } else {
                    isConfirming = true
                }
            },
            enabled = keep.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.End)
                .padding(16.dp)
        ) {
            Text(
                when {
                    remove.isEmpty() -> "Keep all"
                    keep.size > 1 -> "Keep ${keep.size}"
                    else -> "Keep selected"
                }
            )
        }
    }

    if (isConfirming) {
        DeleteCopiesDialog(
            kept = keep.map { it.best },
            remove = remove,
            onDismiss = { isConfirming = false },
            onConfirm = {
                isConfirming = false
                resolveGroup()
            }
        )
    }
}

/**
 * Why one recording is suggested.
 */
@Composable
private fun SuggestionLine(suggestion: Suggestion) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(
            horizontal = 16.dp
        )
    ) {
        Icon(
            imageVector = Icons.Outlined.Lightbulb,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = suggestion.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Confirms deleting the copies: names the files that stay and every file that goes.
 */
@Composable
private fun DeleteCopiesDialog(
    kept: List<Song>,
    remove: List<Song>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DeleteDialog(
        title = "Delete ${copies(remove.size)}?",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    ) {
        Text(
            if (kept.size == 1) {
                "One copy of this song stays in the library. The others are deleted from this device and " +
                        "can't be restored."
            } else {
                "${kept.size} versions of this song stay in the library and are no longer offered as " +
                        "duplicates. The other copies are deleted from this device and can't be restored."
            }
        )
        // The weight lets the file lists scroll while the explanation and the buttons stay in view
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .weight(
                    weight = 1f,
                    fill = false
                )
                .verticalScroll(rememberScrollState())
        ) {
            FileSection(
                label = "Stays",
                labelColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                songs = kept,
                icon = Icons.Outlined.CheckCircle,
                iconTint = MaterialTheme.colorScheme.primary
            )
            FileSection(
                label = "Deleted from this device",
                labelColor = MaterialTheme.colorScheme.error,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                songs = remove,
                icon = Icons.Outlined.Delete,
                iconTint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun FileSection(
    label: String,
    labelColor: Color,
    containerColor: Color,
    songs: List<Song>,
    icon: ImageVector,
    iconTint: Color,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = labelColor
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = containerColor
        ) {
            Column {
                songs.forEach { song ->
                    ListItem(
                        headlineContent = { Text(song.path.substringAfterLast('/')) },
                        supportingContent = {
                            Text(
                                text = listOf(
                                    folderOf(song),
                                    details(song)
                                ).filter { it.isNotEmpty() }.joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

/**
 * One recording, shown by its best copy; its identical copies are listed below it.
 */
@Composable
private fun RecordingItem(
    recording: Recording,
    isSelected: Boolean,
    isSuggested: Boolean,
    onSelect: () -> Unit,
) {
    val song = recording.best
    val identical = recording.copies.filter { it.path != song.path }
    ListItem(
        overlineContent = if (isSuggested) {
            {
                Text(
                    text = "Suggested",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            null
        },
        headlineContent = { Text(song.path.substringAfterLast('/')) },
        supportingContent = {
            Text(
                text = listOfNotNull(
                    folderOf(song),
                    details(song),
                    // Their paths are in the confirmation dialog
                    when (identical.size) {
                        0 -> null
                        1 -> "+1 identical copy"
                        else -> "+${identical.size} identical copies"
                    }
                ).joinToString("\n")
            )
        },
        leadingContent = {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null
            )
        },
        trailingContent = {
            // Alone in the queue: one copy, not the others with the same tags
            PlayIconButton(
                name = displayPath(song),
                songs = listOf(song)
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        modifier = Modifier.toggleable(
            value = isSelected,
            role = Role.Checkbox,
            onValueChange = { onSelect() }
        )
    )
}

private fun displayPath(song: Song) = song.path.removePrefix("file://")

private fun folderOf(song: Song) = displayPath(song).substringBeforeLast(
    delimiter = '/',
    missingDelimiterValue = ""
)

/**
 * Album, length and state, e.g. "Album · 4:28 · Sorted".
 */
private fun details(song: Song) = listOfNotNull(
    song.album?.takeIf { it.isNotBlank() },
    song.duration?.let(::formatTime),
    song.state.name
).joinToString(" · ")
