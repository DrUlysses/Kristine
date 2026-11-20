package dr.ulysses.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.AutoSort
import dr.ulysses.entities.albumOf
import dr.ulysses.ui.elements.ListScrollbar
import dr.ulysses.ui.elements.songs

/**
 * Also the title of the auto-sort screen.
 */
const val AUTO_SORT = "Auto-sort"

/**
 * The unsorted songs that match the catalog closely, all checked, to sort at once; the rest stay for a manual look.
 */
@Composable
fun AutoSortView() {
    val state by AutoSort.state.collectAsState()
    // By path: unchecking is the exception
    var unchecked by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) {
        AutoSort.start()
    }
    // Once per change: the confidence rules run over every match
    val proposals = remember(state.matches) { state.proposals }
    val selected = proposals.filter { it.song.path !in unchecked }
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            if (state.isRunning) {
                Text(
                    text = "Looking up ${state.checked} of ${state.total} unsorted songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { state.checked / state.total.coerceAtLeast(1).toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                text = "${songs(proposals.size)} match the catalog closely. Uncheck any that are wrong; " +
                        "sorting gives the others the shown title and artist, keeps their album, and files them in " +
                        "Artist / Album folders. " +
                        "Songs not listed stay in Manage Unsorted for a manual look.",
                style = MaterialTheme.typography.bodyMedium
            )
            val sorting = state.sorting
            Button(
                enabled = selected.isNotEmpty() && sorting == null,
                onClick = { AutoSort.sort(selected) }
            ) {
                Text(
                    if (sorting != null) {
                        "Sorting ${sorting.first} of ${sorting.second}…"
                    } else {
                        "Sort ${songs(selected.size)}"
                    }
                )
            }
        }
        HorizontalDivider()
        Box(
            modifier = Modifier.weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = proposals,
                    key = { it.song.path }
                ) { proposal ->
                    val match = proposal.match
                    val isChecked = proposal.song.path !in unchecked
                    ListItem(
                        leadingContent = {
                            Checkbox(
                                checked = isChecked,
                                // The row toggles it
                                onCheckedChange = null
                            )
                        },
                        overlineContent = {
                            Text(
                                text = proposal.song.path.substringAfterLast('/'),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        headlineContent = {
                            Text(
                                text = match.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                text = listOfNotNull(
                                    match.artist,
                                    albumOf(proposal.song) ?: match.album,
                                    if (match.soundMatch != null) {
                                        "same sound"
                                    } else {
                                        "same name"
                                    }
                                ).joinToString(" · "),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.toggleable(
                            value = isChecked,
                            enabled = state.sorting == null,
                            role = Role.Checkbox,
                            onValueChange = { checked ->
                                unchecked = if (checked) {
                                    unchecked - proposal.song.path
                                } else {
                                    unchecked + proposal.song.path
                                }
                            }
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
