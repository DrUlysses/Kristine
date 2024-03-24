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
import dr.ulysses.ui.elements.ArtistsListEntry

@Composable
fun ArtistsList(
    modifier: Modifier = Modifier,
    artists: List<String>,
    onArtistsChanged: (List<String>) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val listState = rememberLazyListState()

        LaunchedEffect(listState) {
            onArtistsChanged(
                SongRepository().getAllArtists().ifEmpty {
                    refreshSongs().run { SongRepository().getAllArtists() }
                }
            )
            listState.animateScrollToItem(0)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = listState,
            modifier = Modifier.fillMaxSize(),
            content = {
                items(items = artists.sortedBy { it }) { artist ->
                    ArtistsListEntry(
                        artist = artist,
                        onClick = { TODO() } // TODO: Navigate to artist's songs
                    )
                }
            }
        )
    }
}
