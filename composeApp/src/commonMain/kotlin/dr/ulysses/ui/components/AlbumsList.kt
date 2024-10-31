package dr.ulysses.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.SongRepository
import dr.ulysses.entities.refreshSongs
import dr.ulysses.ui.elements.AlbumListEntry

@Composable
fun AlbumsList(
    modifier: Modifier = Modifier,
    albums: List<String>,
    onAlbumsChanged: (List<String>) -> Unit,
    onAlbumClicked: (String) -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        val listState = rememberLazyListState()

        LaunchedEffect(listState) {
            onAlbumsChanged(
                SongRepository.getAllAlbums().ifEmpty {
                    refreshSongs().run { SongRepository.getAllAlbums() }
                }
            )
            listState.animateScrollToItem(0)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = listState,
            modifier = Modifier.fillMaxSize(),
            content = {
                items(items = albums.sortedBy { it }) { album ->
                    AlbumListEntry(
                        album = album,
                        onClick = {
                            onAlbumClicked(album)
                        }
                    )
                }
            }
        )
    }
}
