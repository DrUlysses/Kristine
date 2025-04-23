package dr.ulysses.ui.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.skydoves.landscapist.coil3.CoilImage
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository
import dr.ulysses.ui.components.SongsList
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kristine.composeapp.generated.resources.Res
import kristine.composeapp.generated.resources.compose_multiplatform
import org.jetbrains.compose.resources.painterResource

@Serializable
object ManagePlaylist

@Composable
fun ManagePlaylist(
    modifier: Modifier = Modifier,
    playlist: Playlist,
    onPlaylistChanged: (Playlist) -> Unit,
) {
    var updatedPlaylist by remember { mutableStateOf(playlist) }
    val scope = rememberCoroutineScope()
    var updatedImage by remember { mutableStateOf(playlist.artwork) }
    val imagePicker = rememberFilePickerLauncher(
        type = PickerType.Image,
    ) { image ->
        image?.let {
            scope.launch {
                updatedImage = image.readBytes()
                updatedPlaylist = updatedPlaylist.copy(artwork = updatedImage)
                onPlaylistChanged(updatedPlaylist)
            }
        }
    }
    val context = LocalPlatformContext.current
    var searchQuery by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(emptyList<Song>()) }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Row {
            Column {
                CoilImage(
                    imageRequest = {
                        ImageRequest.Builder(context).data(updatedImage).build()
                    },
                    imageLoader = {
                        ImageLoader.Builder(context)
                            .build()
                    },
                    modifier = Modifier
                        .padding(8.dp)
                        .size(64.dp)
                        .clickable {
                            imagePicker.launch()
                        },
                    success = { _, painter ->
                        Image(
                            painter = painter,
                            contentDescription = "Art",
                            modifier = Modifier
                                .padding(8.dp)
                                .size(64.dp)
                        )
                    },
                    failure = {
                        Image(
                            painter = painterResource(Res.drawable.compose_multiplatform),
                            contentDescription = "Art",
                            modifier = Modifier
                                .padding(8.dp)
                                .size(64.dp)
                        )
                    }
                )
            }

            Column {
                TextField(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(
                            start = 8.dp,
                            end = 8.dp,
                        ),
                    value = updatedPlaylist.name,
                    onValueChange = {
                        updatedPlaylist = updatedPlaylist.copy(name = it)
                        onPlaylistChanged(updatedPlaylist)
                    },
                    singleLine = true
                )
            }
        }

        Row {
            HorizontalDivider(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }

        if (updatedPlaylist.songs.isNotEmpty()) {
            Column(
                modifier = modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                SongsList(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    songs = updatedPlaylist.songs,
                    onSongsChanged = { songs ->
                        updatedPlaylist = updatedPlaylist.copy(songs = songs)
                        onPlaylistChanged(updatedPlaylist)
                    },
                    rearrangeable = true,
                )
            }
        }

        Column(
            modifier = modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            OutlinedTextField(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                value = searchQuery,
                onValueChange = { value ->
                    searchQuery = value
                    scope.launch {
                        entries = SongRepository.search(searchQuery)
                    }
                },
                label = { Text("Search") },
                maxLines = 1,
            )

            SongsList(
                modifier = modifier.fillMaxWidth(),
                songs = entries,
                onSongsChanged = { entries = it },
                onClick = { song ->
                    updatedPlaylist = updatedPlaylist.copy(songs = updatedPlaylist.songs + song)
                    onPlaylistChanged(updatedPlaylist)
                }
            )
        }
    }
}
