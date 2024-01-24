package dr.ulysses.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.Song
import dr.ulysses.entities.refreshSongs
import dr.ulysses.ui.elements.SongListEntry
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

@Composable
fun SongList(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        var songs: List<Song> by remember { mutableStateOf(emptyList()) }

        MainScope().launch {
            songs = refreshSongs()
            listState.animateScrollToItem(0)
        }

        LaunchedEffect(listState) {
            listState.scrollToItem(0)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(songs) { song ->
                SongListEntry(
                    title = song.title,
                    artist = song.artist,
                )
            }
        }
    }
}
