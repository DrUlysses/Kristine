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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository
import dr.ulysses.entities.refreshSongs
import dr.ulysses.ui.elements.SongListEntry

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongList(
    modifier: Modifier = Modifier,
    songs: List<Song>,
    onSongsChanged: (List<Song>) -> Unit,
    onPlaySongCommand: (Song) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val listState = rememberLazyListState()

        LaunchedEffect(listState) {
            onSongsChanged(SongRepository.getAll().ifEmpty { refreshSongs() })
            listState.animateScrollToItem(0)
        }

        val overscrollEffect = ScrollableDefaults.overscrollEffect()

        LaunchedEffect(overscrollEffect) {
            refreshSongs()
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overscroll(overscrollEffect),
            content = {
                items(items = songs) { song ->
                    SongListEntry(
                        title = song.title,
                        artist = song.artist,
                        onClick = { onPlaySongCommand(song) }
                    )
                }
            }
        )
    }
}
