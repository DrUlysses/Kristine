package dr.ulysses.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.PlaylistRepository
import dr.ulysses.ui.elements.PlaylistListEntry

@Composable
fun PlaylistsList(
    modifier: Modifier = Modifier,
    playlists: List<Playlist>,
    onPlaylistsChanged: (List<Playlist>) -> Unit,
    onPlaylistClicked: (Playlist) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    initialLoad: Boolean = true,
    /**
     * Opens the player's queue, listed first; null hides it, e.g. while nothing is queued.
     */
    onQueueClicked: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        LaunchedEffect(Unit) {
            onPlaylistsChanged(PlaylistRepository.getAllPlaylists())
            if (initialLoad) {
                listState.scrollToItem(0)
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overscroll(rememberOverscrollEffect()),
            content = {
                onQueueClicked?.let { onClick ->
                    item {
                        PlaylistListEntry(
                            image = null,
                            name = CURRENT_QUEUE,
                            onClick = onClick
                        )
                    }
                }
                items(
                    items = playlists
                ) { playlist ->
                    val image: ByteArray? = remember { playlist.artwork }
                    image ?: LaunchedEffect(image) {
                        PlaylistRepository.getArtwork(playlist.name)
                    }
                    PlaylistListEntry(
                        image = image,
                        name = playlist.name,
                        onClick = { onPlaylistClicked(playlist) }
                    )
                }
            }
        )
    }
}

const val CURRENT_QUEUE = "Current queue"
