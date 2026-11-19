package dr.ulysses.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.PlaylistRepository
import dr.ulysses.entities.Song
import dr.ulysses.entities.base.Searchable
import dr.ulysses.models.MainViewModel
import dr.ulysses.ui.elements.PlaylistListEntry
import dr.ulysses.ui.elements.SongDownloadButton
import dr.ulysses.ui.elements.SongListEntry

@Composable
fun SearchList(
    modifier: Modifier = Modifier,
    entries: List<Searchable>,
    onPlaylistClicked: (Playlist) -> Unit,
    onSongClicked: (Song) -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        val listState = rememberLazyListState()

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overscroll(rememberOverscrollEffect()),
            content = {
                items(
                    items = entries
                ) { entry ->
                    when (entry) {
                        is Song -> {
                            var image: ByteArray? by remember { mutableStateOf(entry.artwork) }
                            if (image == null) {
                                LaunchedEffect(entry.path) {
                                    image = MainViewModel.artwork(entry.path)
                                }
                            }
                            SongListEntry(
                                image = image,
                                title = entry.title,
                                artist = entry.artist,
                                isAvailable = entry.state != Song.State.NotDownloaded,
                                trailing = { SongDownloadButton(entry) },
                                onClick = { onSongClicked(entry) }
                            )
                        }

                        is Playlist -> {
                            var image: ByteArray? by remember { mutableStateOf(entry.artwork) }
                            if (image == null) {
                                LaunchedEffect(entry.name) {
                                    image = PlaylistRepository.getArtwork(entry.name)
                                }
                            }
                            PlaylistListEntry(
                                image = image,
                                name = entry.name,
                                onClick = { onPlaylistClicked(entry) }
                            )
                        }
                    }
                }
            }
        )
    }
}
