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
import dr.ulysses.network.NetworkManager.isConnected
import dr.ulysses.network.NetworkManager.playSongOnServer
import dr.ulysses.player.Player
import dr.ulysses.ui.components.SearchList
import kotlinx.coroutines.launch

@Composable
fun Search(
    modifier: Modifier = Modifier,
    playlistsIncluded: Boolean = true,
    onPlaylistClicked: (Searchable) -> Unit = {},
    onQueryChanged: (String) -> Unit,
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
                onSongClicked = { song ->
                    // Get all songs from the search results that are of type Song
                    val songsList = entries.filterIsInstance<Song>()
                    // Update the playlist with all songs from search results
                    Player.onSongsChanged(songsList)

                    // If connected to a server, only send the play command to the server
                    // and don't try to play locally
                    if (isConnected.value) {
                        playSongOnServer(song)
                    } else {
                        // Only play locally if not connected to a server
                        Player.onPlaySongCommand(song)
                    }
                }
            )
        }
    }
}
