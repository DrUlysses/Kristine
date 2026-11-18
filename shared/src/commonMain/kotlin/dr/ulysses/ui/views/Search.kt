package dr.ulysses.ui.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dr.ulysses.entities.PlaylistRepository
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository
import dr.ulysses.entities.base.Searchable
import dr.ulysses.player.Player
import dr.ulysses.ui.components.SearchList

@Composable
fun Search(
    modifier: Modifier = Modifier,
    playlistsIncluded: Boolean = true,
    onPlaylistClicked: (Searchable) -> Unit = {},
    onQueryChanged: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(emptyList<Searchable>()) }

    // Restarted per keystroke, so a slow earlier search can't overwrite newer results
    LaunchedEffect(searchQuery) {
        // An empty query would match everything ('%' || '' || '%')
        entries = if (searchQuery.isBlank()) emptyList() else SongRepository.search(searchQuery) +
                if (playlistsIncluded) PlaylistRepository.search(searchQuery) else emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { value ->
                    searchQuery = value
                    onQueryChanged(searchQuery)
                },
                label = { Text("Search") },
                maxLines = 1,
            )
        }
        Row {
            SearchList(
                modifier = modifier,
                entries = entries,
                onPlaylistClicked = onPlaylistClicked,
                onSongClicked = { song ->
                    Player.onSongsChanged(entries.filterIsInstance<Song>())
                    Player.onPlaySongCommand(song)
                }
            )
        }
    }
}
