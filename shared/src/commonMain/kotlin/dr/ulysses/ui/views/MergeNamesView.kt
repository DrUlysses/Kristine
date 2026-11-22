package dr.ulysses.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dr.ulysses.api.external.Catalog
import dr.ulysses.entities.*
import dr.ulysses.ui.elements.ListScrollbar
import dr.ulysses.ui.elements.RadioRow
import dr.ulysses.ui.elements.songs
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Also the title of the screen.
 */
const val MERGE_NAMES = "Merge names"

/**
 * The artists and albums spelled more than one way, each to merge into one spelling. Those with one spelling to merge
 * into come first, to merge all at once.
 */
@Composable
fun MergeNamesView() {
    val progress by NameMerger.progress.collectAsState()
    // This device's library: the tags are written here. Null while loading
    var groups by remember { mutableStateOf<List<NameGroup>?>(null) }
    var skipped by remember { mutableStateOf(emptySet<String>()) }
    var isConfirmingAuto by remember { mutableStateOf(false) }
    // Deezer's spelling by group, null when it has none; a group is in here once Deezer answered
    var deezer by remember { mutableStateOf(emptyMap<String, String?>()) }
    // Again once merging is done
    LaunchedEffect(progress == null) {
        if (progress == null) {
            groups = findNameGroups(SongRepository.getAllSongs())
        }
    }
    // One after the other: an artist's groups then share one Deezer lookup from the catalog's cache
    LaunchedEffect(groups) {
        groups.orEmpty().filter { it.id !in deezer }.forEach { group ->
            deezer = deezer + (group.id to deezerSpelling(group))
        }
    }

    // Nothing to pick: one spelling in the library, and Deezer has no other
    fun isOneChoice(group: NameGroup) = group.id in deezer && group.spellings.size == 1 &&
            deezer[group.id].let { it == null || it == group.suggestion }

    // By the library alone, so cards don't jump while Deezer answers
    val shown = groups.orEmpty().filter { it.id !in skipped }.sortedBy { it.spellings.size > 1 }
    val oneChoice = shown.filter(::isOneChoice)
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            val running = progress
            when {
                running != null -> {
                    Text(
                        text = "Merging ${running.first} of ${running.second}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = { running.first / running.second.coerceAtLeast(1).toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                groups == null -> LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )

                shown.isEmpty() -> Text(
                    text = "Every artist and album is spelled one way.",
                    style = MaterialTheme.typography.bodyMedium
                )

                else -> {
                    Text(
                        text = "${shown.size} names are spelled more than one way. Merging writes one spelling into " +
                                "the songs' tags and moves sorted songs to its folder. Featured artists move to " +
                                "the song titles, so only the main artist remains.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    when {
                        deezer.size < groups.orEmpty().size -> Text(
                            text = "Asking Deezer for its spellings…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        oneChoice.isNotEmpty() -> FilledTonalButton(
                            onClick = { isConfirmingAuto = true }
                        ) {
                            Text("Auto-merge ${oneChoice.size}")
                        }
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = shown,
                    key = { it.id }
                ) { group ->
                    GroupCard(
                        group = group,
                        deezer = deezer[group.id],
                        isEnabled = progress == null,
                        onSkip = { skipped = skipped + group.id },
                        onMerge = { name ->
                            NameMerger.merge(
                                ids = listOf(group.id),
                                names = mapOf(group.id to name)
                            )
                        }
                    )
                }
            }
            ListScrollbar(
                state = listState
            )
        }
    }

    if (isConfirmingAuto) {
        AlertDialog(
            onDismissRequest = { isConfirmingAuto = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.MergeType,
                    contentDescription = null
                )
            },
            title = { Text("Auto-merge ${oneChoice.size}?") },
            text = {
                Text(
                    "These artists and albums have one spelling to merge into. It's written into the tags of " +
                            "their ${songs(oneChoice.sumOf { it.songs.size })}, and sorted ones move to its folder."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isConfirmingAuto = false
                        NameMerger.merge(
                            ids = oneChoice.map { it.id }
                        )
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { isConfirmingAuto = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * The spellings of one name, the most used chosen, and [deezer]'s when it differs from all of them.
 */
@Composable
private fun GroupCard(
    group: NameGroup,
    deezer: String?,
    isEnabled: Boolean,
    onSkip: () -> Unit,
    onMerge: (String) -> Unit,
) {
    var chosen by remember(group.id) { mutableStateOf(group.suggestion) }
    val options = group.spellings + listOfNotNull(
        deezer?.takeIf { name -> group.spellings.none { it.first == name } }?.let { it to 0 }
    )
    val featured = group.songs.firstOrNull { featuredOf(it).isNotEmpty() }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(
                vertical = 12.dp
            )
        ) {
            Text(
                text = if (group.isAlbum) {
                    "Album · ${group.artist} · ${songs(group.songs.size)}"
                } else {
                    "Artist · ${songs(group.songs.size)}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = "Now: " + group.songs.map { song ->
                    if (group.isAlbum) {
                        albumOf(song).orEmpty()
                    } else {
                        song.artist.trim()
                    }
                }.distinct().joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            options.forEach { (name, count) ->
                RadioRow(
                    selected = chosen == name,
                    enabled = isEnabled,
                    onClick = { chosen = name }
                ) {
                    Column {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = listOfNotNull(
                                songs(count).takeIf { count > 0 },
                                "Deezer's spelling".takeIf { name == deezer }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (featured != null) {
                Text(
                    text = "Featured artists go to the titles, e.g. \"${featured.title}\" by ${featured.artist} " +
                            "becomes \"${featured.title} (feat. ${featuredOf(featured).joinToString(" & ")})\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(
                    space = 8.dp,
                    alignment = Alignment.End
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                TextButton(
                    enabled = isEnabled,
                    onClick = onSkip
                ) {
                    Text("Skip")
                }
                FilledTonalButton(
                    enabled = isEnabled,
                    onClick = { onMerge(chosen) }
                ) {
                    Text("Merge")
                }
            }
        }
    }
}

/**
 * How Deezer spells the group's artist or album; null when it doesn't know it or can't be reached.
 */
private suspend fun deezerSpelling(group: NameGroup) = runCatching {
    if (group.isAlbum) {
        Catalog.album(
            artist = group.artist,
            title = albumTitleOf(group.songs.first())!!
        )?.title
    } else {
        Catalog.artistName(group.artist)
    }
}
    .onFailure { currentCoroutineContext().ensureActive() }
    .getOrNull()
