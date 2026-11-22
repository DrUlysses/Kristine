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
import dr.ulysses.entities.Sync
import dr.ulysses.entities.albumOf
import dr.ulysses.entities.artistOf
import dr.ulysses.ui.elements.ListScrollbar
import dr.ulysses.ui.elements.songs

/**
 * Also the title of the sync screen.
 */
const val SYNC = "Sync"

/**
 * Compares this library with the connected server's, then sorts the matched songs here and downloads the checked
 * missing ones.
 */
@Composable
fun SyncView() {
    val state by Sync.state.collectAsState()
    // By path: downloads can be large, so nothing is checked at first
    var checked by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) {
        Sync.compare()
    }
    val missing = remember(state.missing) {
        state.missing.sortedWith(compareBy({ artistOf(it).lowercase() }, { albumOf(it)?.lowercase() }, { it.title }))
    }
    val isIdle = state.step == null
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            state.step?.let { step ->
                Text(
                    text = if (state.total > 0) {
                        "$step ${state.done} of ${state.total}"
                    } else {
                        step
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { state.done / state.total.coerceAtLeast(1).toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            state.result?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (state.isCompared) {
                Text(
                    text = "${songs(state.toSort.size)} unsorted here match the server's sorted songs and take " +
                            "their title, artist, album and cover. ${songs(missing.size)} of the server are " +
                            "missing here: check the ones to download.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        enabled = isIdle && (state.toSort.isNotEmpty() || checked.isNotEmpty()),
                        onClick = { Sync.sync(missing.filter { it.path in checked }) }
                    ) {
                        Text("Sync")
                    }
                    TextButton(
                        enabled = isIdle && missing.isNotEmpty(),
                        onClick = {
                            checked = if (checked.size == missing.size) {
                                emptySet()
                            } else {
                                missing.mapTo(HashSet()) { it.path }
                            }
                        }
                    ) {
                        Text(
                            if (checked.size == missing.size) {
                                "Check none"
                            } else {
                                "Check all"
                            }
                        )
                    }
                }
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
                    items = missing,
                    key = { it.path }
                ) { song ->
                    val isChecked = song.path in checked
                    ListItem(
                        leadingContent = {
                            Checkbox(
                                checked = isChecked,
                                // The row toggles it
                                onCheckedChange = null
                            )
                        },
                        headlineContent = {
                            Text(
                                text = song.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                text = listOfNotNull(
                                    artistOf(song),
                                    albumOf(song)
                                ).joinToString(" · "),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.toggleable(
                            value = isChecked,
                            enabled = isIdle,
                            role = Role.Checkbox,
                            onValueChange = { check ->
                                checked = if (check) {
                                    checked + song.path
                                } else {
                                    checked - song.path
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
