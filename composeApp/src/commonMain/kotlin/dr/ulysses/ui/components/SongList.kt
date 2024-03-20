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
import dr.ulysses.entities.Player
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository
import dr.ulysses.entities.refreshSongs
import dr.ulysses.ui.elements.SongListEntry

@Composable
fun SongList(
    modifier: Modifier = Modifier,
    songs: List<Song>,
    onSongsChanged: (List<Song>) -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        val listState = rememberLazyListState()

        LaunchedEffect(listState) {
            onSongsChanged(SongRepository().getAll().ifEmpty { refreshSongs() })
            listState.animateScrollToItem(0)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = listState,
            modifier = Modifier.fillMaxSize(),
            content = {
                items(items = songs) { song ->
                    SongListEntry(
                        title = song.title,
                        artist = song.artist,
                        onClick = { Player.setAndPlay(song) }
                    )
                }
            }
        )
    }
}
