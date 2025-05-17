package dr.ulysses.ui.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.skydoves.landscapist.coil3.CoilImage
import dr.ulysses.Logger
import dr.ulysses.SUPPORTED_EXTENSIONS
import dr.ulysses.api.SpotifyAppApi
import dr.ulysses.api.SpotifySearchResult
import dr.ulysses.entities.SettingKey
import dr.ulysses.entities.SettingsRepository
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kristine.composeapp.generated.resources.Res
import kristine.composeapp.generated.resources.icon
import org.jetbrains.compose.resources.painterResource

@Serializable
object ManageSong

// Data class to hold Spotify search state
private data class SpotifySearchState(
    val isSearching: Boolean = false,
    val results: List<SpotifySearchResult> = emptyList(),
    val selectedResult: SpotifySearchResult? = null,
)

@Composable
fun ManageUnsortedSong(
    song: Song,
    onSongEdited: (Song) -> Unit,
) {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()

    // Reduced state variables
    var spotifySearchState by remember { mutableStateOf(SpotifySearchState()) }

    // These state variables are derived when needed
    val unsortedSongsState = produceState(initialValue = emptyList()) {
        value = SongRepository.getByNotState(Song.State.Sorted)
    }

    // Derive current index from unsorted songs list and current song
    val currentIndex = unsortedSongsState.value.indexOfFirst { it.path == song.path }

    // Derive directory from settings when needed
    val songsPathState = produceState(initialValue = song.path) {
        value = SettingsRepository.get(SettingKey.SongsPath)?.value ?: song.path
    }

    // Perform Spotify search when song changes
    LaunchedEffect(song) {
        spotifySearchState = spotifySearchState.copy(isSearching = true)

        // Create search query from song info
        val query = "${song.title} ${song.artist} ${song.album ?: ""}"

        // Search Spotify
        SpotifyAppApi.searchTrack(query).fold(
            onSuccess = { results ->
                spotifySearchState = SpotifySearchState(
                    isSearching = false,
                    results = results,
                    selectedResult = results.firstOrNull()
                )
            },
            onFailure = { error ->
                Logger.e { "Failed to search Spotify: ${error.message}" }
                spotifySearchState = SpotifySearchState(isSearching = false)
            }
        )
    }

    val imagePicker = rememberFilePickerLauncher(
        type = PickerType.Image,
        initialDirectory = songsPathState.value,
    ) { image ->
        image?.let {
            scope.launch {
                val bytes = image.readBytes()
                onSongEdited(song.copy(artwork = bytes))
            }
        } ?: Logger.e {
            "Image not found"
        }
    }

    val songPicker = rememberFilePickerLauncher(
        type = PickerType.File(SUPPORTED_EXTENSIONS),
        initialDirectory = songsPathState.value
    ) { songFile ->
        songFile?.path?.let {
            onSongEdited(song.copy(path = it))
        }
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Clickable path at the top
                Text(
                    text = "Path: ${song.path}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { songPicker.launch() }
                        .padding(bottom = 16.dp)
                )

                // Two columns layout
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Left column (Spotify data)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Spotify Data",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (spotifySearchState.isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(16.dp)
                            )
                        } else if (spotifySearchState.selectedResult != null) {
                            val selectedResult = spotifySearchState.selectedResult

                            // Title - Using OutlinedTextField for consistent height with Song column
                            OutlinedTextField(
                                value = selectedResult?.title.orEmpty(),
                                onValueChange = { },
                                label = { Text("Title") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            onSongEdited(song.copy(title = selectedResult?.title.orEmpty()))
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Use Spotify title"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            )

                            // Album - Using OutlinedTextField for consistent height with Song column
                            OutlinedTextField(
                                value = selectedResult?.album.orEmpty(),
                                onValueChange = { },
                                label = { Text("Album") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            onSongEdited(song.copy(album = selectedResult?.album.orEmpty()))
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Use Spotify album"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            )

                            // Artist - Using OutlinedTextField for consistent height with Song column
                            OutlinedTextField(
                                value = selectedResult?.artist.orEmpty(),
                                onValueChange = { },
                                label = { Text("Artist") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            onSongEdited(song.copy(artist = selectedResult?.artist.orEmpty()))
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Use Spotify artist"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            )

                            // Artwork
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Artwork",
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Box(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    CoilImage(
                                        imageRequest = {
                                            ImageRequest
                                                .Builder(context)
                                                .data(selectedResult?.artworkUrl)
                                                .build()
                                        },
                                        imageLoader = {
                                            ImageLoader.Builder(context).build()
                                        },
                                        modifier = Modifier.size(64.dp),
                                        success = { _, painter ->
                                            Image(
                                                painter = painter,
                                                contentDescription = "Spotify Artwork",
                                                modifier = Modifier.size(64.dp)
                                            )
                                        },
                                        failure = {
                                            Image(
                                                painter = painterResource(Res.drawable.icon),
                                                contentDescription = "Default Artwork",
                                                modifier = Modifier.size(64.dp)
                                            )
                                        }
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        // We can't directly use the artwork URL, but in a real implementation
                                        // we would download the image and convert it to ByteArray
                                        song.artwork?.let {
                                            onSongEdited(song.copy(artwork = it))
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Use Spotify artwork"
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No Spotify results found",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    // Right column (Song data)
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    ) {
                        Text(
                            text = "Song Data",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Title
                        OutlinedTextField(
                            value = song.title,
                            onValueChange = { value ->
                                onSongEdited(song.copy(title = value))
                            },
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )

                        // Album
                        OutlinedTextField(
                            value = song.album ?: "Unknown Album",
                            onValueChange = { value ->
                                onSongEdited(song.copy(album = value))
                            },
                            label = { Text("Album") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )

                        // Artist
                        OutlinedTextField(
                            value = song.artist,
                            onValueChange = { value ->
                                onSongEdited(song.copy(artist = value))
                            },
                            label = { Text("Artist") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )

                        // Artwork
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Artwork",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            CoilImage(
                                imageRequest = {
                                    ImageRequest.Builder(context).data(song.artwork).build()
                                },
                                imageLoader = {
                                    ImageLoader.Builder(context).build()
                                },
                                modifier = Modifier
                                    .size(64.dp)
                                    .clickable { imagePicker.launch() },
                                success = { _, painter ->
                                    Image(
                                        painter = painter,
                                        contentDescription = "Artwork",
                                        modifier = Modifier.size(64.dp)
                                    )
                                },
                                failure = {
                                    Image(
                                        painter = painterResource(Res.drawable.icon),
                                        contentDescription = "Default Artwork",
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Navigation buttons
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .weight(1f)
            ) {
                Button(
                    onClick = {
                        if (currentIndex > 0) {
                            val previousSong = unsortedSongsState.value[currentIndex - 1]
                            onSongEdited(previousSong)
                        }
                    },
                    enabled = currentIndex > 0
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous Song"
                    )
                    Text("Previous")
                }

                Button(
                    onClick = {
                        // Save current song as sorted
                        onSongEdited(song.copy(state = Song.State.Sorted))

                        // Navigate to next song if available
                        if (currentIndex < unsortedSongsState.value.size - 1) {
                            val nextSong = unsortedSongsState.value[currentIndex + 1]
                            onSongEdited(nextSong)
                        }
                    }
                ) {
                    Text("Save & Next")
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next Song"
                    )
                }
            }
        }
    }
}

expect fun onSongSave(song: Song): Result<Song>
