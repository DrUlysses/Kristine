package dr.ulysses.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository
import dr.ulysses.entities.base.Searchable
import dr.ulysses.models.PlayerService.onPlaySongCommand
import dr.ulysses.ui.elements.PlaylistListEntry
import dr.ulysses.ui.elements.SongListEntry

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchList(
    modifier: Modifier = Modifier,
    entries: List<Searchable>,
    onPlaylistClicked: (Playlist) -> Unit,
    onSongClicked: (Song) -> Unit = ::onPlaySongCommand,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val listState = rememberLazyListState()

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overscroll(ScrollableDefaults.overscrollEffect()),
            content = {
                items(items = entries) { entry ->
                    when (entry) {
                        is Song -> {
                            val image: ByteArray? = remember { entry.artwork }
                            image ?: LaunchedEffect(image) {
                                SongRepository.getArtwork(entry.path)
                            }
                            SongListEntry(
                                image = image,
                                title = entry.title,
                                artist = entry.artist,
                                onClick = { onSongClicked(entry) }
                            )
                        }

                        is Playlist -> {
                            val image: ByteArray? = remember { entry.artwork }
                            image ?: LaunchedEffect(image) {
                                SongRepository.getArtwork(entry.name)
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
