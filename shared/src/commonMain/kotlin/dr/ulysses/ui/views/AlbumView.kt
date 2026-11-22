package dr.ulysses.ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dr.ulysses.api.external.Catalog
import dr.ulysses.api.external.CatalogAlbum
import dr.ulysses.api.external.CatalogTrack
import dr.ulysses.entities.*
import dr.ulysses.models.MainViewModel
import dr.ulysses.player.Player
import dr.ulysses.ui.elements.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * An album of the library, or of the artist's discography ([catalog] known) when the library lacks it.
 */
data class AlbumRef(
    val title: String,
    val artist: String,
    val catalog: CatalogAlbum? = null,
)

/**
 * A track of the album: [song] is null for a catalog track the library lacks, [track] null for a song the catalog
 * doesn't list.
 */
private data class AlbumRow(
    val track: CatalogTrack?,
    val song: Song?,
)

/**
 * Its songs in the catalog's order, and the catalog's tracks the library lacks, each fetchable from a YouTube link.
 */
@Composable
fun AlbumView(
    album: AlbumRef,
    allSongs: List<Song>,
) {
    val inLibrary = remember(
        album,
        allSongs
    ) {
        // Grouped like the Albums tab, so the counts agree
        allSongs.filter { song ->
            albumTitleOf(song)?.let(::nameKey) == nameKey(album.title) &&
                    nameKey(artistOf(song)) == nameKey(album.artist)
        }
    }
    // The artist's other albums in the library, by the spelling most of their songs use
    val otherAlbums = remember(
        album,
        allSongs
    ) {
        allSongs.filter { nameKey(artistOf(it)) == nameKey(album.artist) && albumTitleOf(it) != null }
            .groupBy { nameKey(albumTitleOf(it)!!) }
            .filterKeys { it != nameKey(album.title) }
            .values
            .map { songs -> mostCommon(songs.mapNotNull(::albumTitleOf)) }
            .sorted()
    }
    var isMergingInto by remember(album) { mutableStateOf(false) }
    var catalog by remember(album) { mutableStateOf(album.catalog) }
    // Null while loading
    var tracks by remember(album) { mutableStateOf<List<CatalogTrack>?>(null) }
    var lookupFailed by remember(album) { mutableStateOf(false) }
    LaunchedEffect(album) {
        runCatching {
            val found = album.catalog ?: Catalog.album(
                artist = album.artist,
                title = album.title
            )
            catalog = found
            tracks = found?.let { Catalog.tracks(it) }.orEmpty()
        }
            .onFailure { currentCoroutineContext().ensureActive() }
            .onFailure {
                lookupFailed = true
                tracks = emptyList()
            }
    }
    val rows = remember(
        inLibrary,
        tracks
    ) {
        val listed = tracks.orEmpty().map { track ->
            AlbumRow(
                track = track,
                song = inLibrary.find {
                    Catalog.sameName(
                        a = it.title,
                        b = track.title
                    )
                }
            )
        }
        val matched = listed.mapNotNullTo(HashSet()) { it.song?.path }
        listed + inLibrary.filter { it.path !in matched }.map {
            AlbumRow(
                track = null,
                song = it
            )
        }
    }
    val playable = rows.mapNotNull { row -> row.song?.takeIf { it.state != Song.State.NotDownloaded } }
    var linkFor by remember(album) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                AlbumHeader(
                    album = album,
                    catalog = catalog,
                    inLibrary = inLibrary,
                    status = when {
                        tracks == null -> null
                        lookupFailed -> "${songs(inLibrary.size)} · Couldn't reach Deezer for the track list"
                        catalog == null -> "${songs(inLibrary.size)} · Not found on Deezer"
                        else -> {
                            val have = rows.count { it.song != null && it.track != null }
                            "$have of ${tracks.orEmpty().size} songs in library"
                        }
                    },
                    playable = playable,
                    onMergeInto = { isMergingInto = true }.takeIf {
                        otherAlbums.isNotEmpty() && inLibrary.isNotEmpty()
                    }
                )
            }
            if (tracks == null) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
            }
            items(rows) { row ->
                TrackRow(
                    row = row,
                    catalog = catalog,
                    onPlay = { song ->
                        Player.startQueue(
                            songs = playable,
                            first = song
                        )
                    },
                    onAddLink = { linkFor = it }
                )
            }
        }
        ListScrollbar(
            state = listState
        )
    }

    if (isMergingInto) {
        MergeIntoDialog(
            album = album,
            others = otherAlbums,
            onDismiss = { isMergingInto = false },
            onMerge = { target ->
                isMergingInto = false
                NameMerger.mergeInto(
                    songs = inLibrary,
                    album = target
                )
                // This album is that one now
                MainViewModel.setCurrentAlbum(
                    AlbumRef(
                        title = target,
                        artist = album.artist
                    )
                )
                MainViewModel.setTopBarText(target)
            }
        )
    }

    val found = catalog
    val title = linkFor
    if (found != null && title != null) {
        LinkDialog(
            artist = album.artist,
            album = found,
            title = title,
            onDismiss = { linkFor = null }
        )
    }
}

@Composable
private fun AlbumHeader(
    album: AlbumRef,
    catalog: CatalogAlbum?,
    inLibrary: List<Song>,
    // Null while the track list loads
    status: String?,
    playable: List<Song>,
    onMergeInto: (() -> Unit)?,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(16.dp)
    ) {
        Cover(
            key = album,
            size = 112.dp
        ) {
            inLibrary.firstOrNull()?.let { MainViewModel.artwork(it.path) } ?: catalog?.let { Catalog.cover(it) }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    album.artist,
                    catalog?.year
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = status ?: songs(inLibrary.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                if (playable.isNotEmpty()) {
                    PlayButton(
                        text = "Play",
                        songs = playable
                    )
                }
                if (onMergeInto != null) {
                    IconButton(
                        onClick = onMergeInto,
                        enabled = canManageLibrary()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MergeType,
                            contentDescription = "Merge into another album"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    row: AlbumRow,
    catalog: CatalogAlbum?,
    onPlay: (Song) -> Unit,
    onAddLink: (String) -> Unit,
) {
    val song = row.song
    val track = row.track
    val isPlayable = song != null && song.state != Song.State.NotDownloaded
    val dimmed = MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        leadingContent = {
            Text(
                text = track?.position?.toString().orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = dimmed,
                modifier = Modifier.width(24.dp)
            )
        },
        headlineContent = {
            Text(
                text = song?.title ?: track?.title.orEmpty(),
                color = if (isPlayable) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    dimmed
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = when {
            song == null -> {
                { Text("Not in library") }
            }

            track == null -> {
                { Text("Not on the album's track list") }
            }

            else -> null
        },
        trailingContent = {
            when {
                song != null && isPlayable -> SongPlayButton(
                    song = song,
                    onPlay = { onPlay(song) }
                )

                song != null -> SongDownloadButton(song)
                track != null && catalog != null -> DownloadButton(
                    key = SongDownloader.linkKey(
                        album = catalog,
                        title = track.title
                    ),
                    icon = Icons.Outlined.AddLink,
                    description = "Add from a YouTube link",
                    onClick = { onAddLink(track.title) }
                )
            }
        },
        modifier = Modifier.clickable(
            enabled = isPlayable,
            onClick = { song?.let(onPlay) }
        )
    )
}

/**
 * Takes the YouTube link the user found for [title]; Kristine doesn't search for it.
 */
@Composable
private fun LinkDialog(
    artist: String,
    album: CatalogAlbum,
    title: String,
    onDismiss: () -> Unit,
) {
    var link by remember { mutableStateOf("") }
    val isValid = SongDownloader.isYouTubeLink(link)
    val failure = SongDownloader.status.collectAsState().value[
        SongDownloader.linkKey(
            album = album,
            title = title
        )
    ] as? SongDownloader.Status.Failed
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.AddLink,
                contentDescription = null
            )
        },
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Find \"$title\" by $artist on YouTube and paste its link. It's added to ${album.title} " +
                            "with the album's tags and cover."
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("YouTube link") },
                    singleLine = true,
                    isError = link.isNotBlank() && !isValid,
                    supportingText = if (link.isNotBlank() && !isValid) {
                        { Text("A youtube.com or youtu.be link") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (failure != null) {
                    Text(
                        text = "The last try failed: ${failure.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    SongDownloader.downloadFromLink(
                        link = link,
                        artist = artist,
                        album = album,
                        title = title
                    )
                    onDismiss()
                }
            ) {
                Text("Download")
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
 * Picks another album of the artist for this one's songs, for spellings the rules don't catch.
 */
@Composable
private fun MergeIntoDialog(
    album: AlbumRef,
    others: List<String>,
    onDismiss: () -> Unit,
    onMerge: (String) -> Unit,
) {
    var chosen by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MergeType,
                contentDescription = null
            )
        },
        title = { Text("Merge into…") },
        text = {
            Column {
                Text("The songs of ${album.title} get the album you pick, written into their tags.")
                Spacer(
                    modifier = Modifier.height(8.dp)
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    others.forEach { name ->
                        RadioRow(
                            selected = chosen == name,
                            onClick = { chosen = name }
                        ) {
                            Text(name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = chosen != null,
                onClick = { chosen?.let(onMerge) }
            ) {
                Text("Merge")
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
