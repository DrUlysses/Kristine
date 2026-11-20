package dr.ulysses.ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dr.ulysses.api.Catalog
import dr.ulysses.api.CatalogAlbum
import dr.ulysses.api.CatalogTrack
import dr.ulysses.entities.*
import dr.ulysses.models.MainViewModel
import dr.ulysses.ui.elements.*

/**
 * The artist's albums in the library, to play or open, then the albums of their discography the library lacks.
 */
@Composable
fun ArtistView(
    artist: String,
    allSongs: List<Song>,
    onAlbumClick: (AlbumRef) -> Unit,
) {
    val bySong = remember(
        artist,
        allSongs
    ) {
        allSongs.filter { nameKey(artistOf(it)) == nameKey(artist) }
    }
    val (withAlbum, withoutAlbum) = bySong.partition { albumOf(it) != null }
    val libraryAlbums = withAlbum.groupBy { nameKey(albumTitleOf(it)!!) }.entries.sortedBy { it.key }.map { it.value }
    val playable = bySong.filter { it.state != Song.State.NotDownloaded }
    // Null while loading
    var discography by remember(artist) { mutableStateOf<List<CatalogAlbum>?>(null) }
    var lookupFailed by remember(artist) { mutableStateOf(false) }
    LaunchedEffect(artist) {
        discography = runCatching { Catalog.albums(artist) }.getOrElse {
            lookupFailed = true
            emptyList()
        }
    }
    val missing = discography.orEmpty().filter { release ->
        release.type in FULL_RELEASES && libraryAlbums.none {
            Catalog.sameName(
                a = albumTitleOf(it.first())!!,
                b = release.title
            )
        }
    }
    val listState = rememberLazyListState()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "${songs(bySong.size)} · ${albums(libraryAlbums.size)} in library",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (playable.isNotEmpty()) {
                        PlayButton(
                            text = "Play all",
                            songs = playable
                        )
                    }
                }
            }
            section("In library")
            items(
                items = libraryAlbums,
                key = { "library:${nameKey(albumTitleOf(it.first())!!)}" }
            ) { album ->
                LibraryAlbumRow(
                    artist = artist,
                    inLibrary = album,
                    discography = discography,
                    onClick = onAlbumClick
                )
            }
            if (withoutAlbum.isNotEmpty()) {
                item {
                    ListItem(
                        leadingContent = {
                            Cover(
                                key = "loose:$artist"
                            ) { null }
                        },
                        headlineContent = { Text("Songs without an album") },
                        supportingContent = { Text(songs(withoutAlbum.size)) },
                        modifier = Modifier.clickable {
                            withoutAlbum.firstOrNull { it.state != Song.State.NotDownloaded }?.let { first ->
                                play(
                                    songs = withoutAlbum.filter { it.state != Song.State.NotDownloaded },
                                    first = first
                                )
                            }
                        }
                    )
                }
            }
            section("Not in library")
            item {
                when {
                    discography == null -> LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )

                    lookupFailed -> Hint("Couldn't reach Deezer for the discography.")
                    discography.orEmpty().isEmpty() -> Hint("Deezer doesn't know an artist of this name.")
                    missing.isEmpty() -> Hint("The library has every album.")
                }
            }
            items(
                items = missing,
                key = { "missing:${it.id}" }
            ) { release ->
                ListItem(
                    leadingContent = {
                        Cover(
                            key = release.id
                        ) { Catalog.cover(release) }
                    },
                    headlineContent = {
                        Text(
                            text = release.title,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                release.year,
                                release.type?.replaceFirstChar { it.uppercase() }?.replace(
                                    oldValue = "Ep",
                                    newValue = "EP"
                                )
                            ).joinToString(" · ")
                        )
                    },
                    modifier = Modifier.clickable {
                        onAlbumClick(
                            AlbumRef(
                                title = release.title,
                                artist = artist,
                                catalog = release
                            )
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

/**
 * An album of the library: how much of it the library has, once the catalog tells.
 */
@Composable
private fun LibraryAlbumRow(
    artist: String,
    inLibrary: List<Song>,
    discography: List<CatalogAlbum>?,
    onClick: (AlbumRef) -> Unit,
) {
    val title = mostCommon(inLibrary.mapNotNull(::albumTitleOf))
    val release = discography?.find {
        Catalog.sameName(
            a = it.title,
            b = title
        )
    }
    // Null until the catalog tells
    var tracks by remember(release) { mutableStateOf<List<CatalogTrack>?>(null) }
    LaunchedEffect(release) {
        tracks = release?.let { runCatching { Catalog.tracks(it) }.getOrNull() }
    }
    val playable = inLibrary.filter { it.state != Song.State.NotDownloaded }
    ListItem(
        leadingContent = {
            Cover(
                key = inLibrary.first().path
            ) { MainViewModel.artwork(inLibrary.first().path) }
        },
        headlineContent = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                listOfNotNull(
                    release?.year,
                    tracks?.let { listed ->
                        val have = listed.count { track ->
                            inLibrary.any {
                                Catalog.sameName(
                                    a = it.title,
                                    b = track.title
                                )
                            }
                        }
                        "$have of ${listed.size} songs"
                    } ?: songs(inLibrary.size)
                ).joinToString(" · ")
            )
        },
        trailingContent = {
            if (playable.isNotEmpty()) {
                PlayIconButton(
                    name = title,
                    songs = playable
                )
            }
        },
        modifier = Modifier.clickable {
            onClick(
                AlbumRef(
                    title = title,
                    artist = artist,
                    catalog = release
                )
            )
        }
    )
}

private fun LazyListScope.section(title: String) = item {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 8.dp
        )
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

private fun albums(number: Int) = count(
    number = number,
    one = "album",
    many = "albums"
)

// Singles and compilations would bury the albums
private val FULL_RELEASES = setOf(
    "album",
    "ep"
)
