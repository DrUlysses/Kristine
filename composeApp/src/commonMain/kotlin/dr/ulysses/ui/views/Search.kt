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
import dr.ulysses.models.PlayerService.onPlaySongCommand
import dr.ulysses.ui.components.SearchList
import kotlinx.coroutines.launch

@Composable
fun Search(
    modifier: Modifier = Modifier,
    playlistsIncluded: Boolean = true,
    onPlaylistClicked: (Searchable) -> Unit = {},
    onQueryChanged: (String) -> Unit,
    onSongClicked: (Song) -> Unit = ::onPlaySongCommand,
) {
    var searchQuery by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(emptyList<Searchable>()) }
    val scope = rememberCoroutineScope()

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
                    scope.launch {
                        entries = SongRepository.search(searchQuery) +
                                if (playlistsIncluded) PlaylistRepository.search(searchQuery)
                                else emptyList()
                    }
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
                onSongClicked = onSongClicked
            )
        }
    }
}
