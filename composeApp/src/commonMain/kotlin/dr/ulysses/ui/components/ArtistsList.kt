package dr.ulysses.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.SongRepository
import dr.ulysses.entities.refreshSongs
import dr.ulysses.ui.elements.ArtistListEntry
import kotlinx.serialization.Serializable

@Serializable
object ArtistsGraph

@Serializable
object Artists

@Serializable
object ArtistSongs

@Composable
fun ArtistsList(
    modifier: Modifier = Modifier,
    artists: List<String>,
    onArtistsChanged: (List<String>) -> Unit,
    onArtistClicked: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    initialLoad: Boolean = true,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LaunchedEffect(Unit) {
            onArtistsChanged(
                SongRepository.getAllArtists().ifEmpty {
                    refreshSongs().run { SongRepository.getAllArtists() }
                }
            )
            if (initialLoad) {
                listState.animateScrollToItem(0)
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = listState,
            modifier = Modifier.fillMaxSize(),
            content = {
                items(items = artists.sortedBy { it }) { artist ->
                    ArtistListEntry(
                        artist = artist,
                        onClick = {
                            onArtistClicked(artist)
                        }
                    )
                }
            }
        )
    }
}
