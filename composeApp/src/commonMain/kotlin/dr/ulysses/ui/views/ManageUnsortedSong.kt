package dr.ulysses.ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dr.ulysses.Logger
import dr.ulysses.SUPPORTED_EXTENSIONS
import dr.ulysses.api.SpotifyAppApi
import dr.ulysses.api.SpotifySearchResult
import dr.ulysses.entities.SettingKey
import dr.ulysses.entities.SettingsRepository
import dr.ulysses.entities.Song
import dr.ulysses.player.Player
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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
    unsortedSongs: List<Song>,
    onSongEdited: (Song) -> Unit,
    onNextSong: () -> Unit,
    onPreviousSong: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // Reduced state variables
    var spotifySearchState by remember { mutableStateOf(SpotifySearchState()) }
    var songArtwork by mutableStateOf(song.artwork)

    // Derive directory from settings when needed
    val songsPathState = produceState(initialValue = song.path) {
        value = SettingsRepository.get(SettingKey.SongsPath)?.value ?: song.path
    }

    // Perform Spotify search when song changes
    // Using DisposableEffect to ensure proper cleanup when the composable leaves the composition
    DisposableEffect(song) {
        spotifySearchState = spotifySearchState.copy(isSearching = true)

        // Create a search query from song info
        val query = "${song.title} ${song.artist}"

        // Launch in the scope that will be canceled when the effect leaves composition
        val job = scope.launch {
            try {
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
            } catch (e: Exception) {
                Logger.e { "Exception during Spotify search: ${e.message}" }
                spotifySearchState = SpotifySearchState(isSearching = false)
            }
        }

        // Provide a cleanup function that cancels the job when the effect leaves composition
        onDispose {
            job.cancel()
        }
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
        songFile?.path?.let { path ->
            onSongEdited(song.copy(path = path))
        }
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = 4.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Clickable path at the top with Material 3 styling
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { songPicker.launch() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Path: ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = song.path,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

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
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Spotify Data",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

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
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            onSongEdited(song.copy(title = selectedResult?.title.orEmpty()))
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Use Spotify title",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            )

                            // Album - Using OutlinedTextField for consistent height with Song column
                            OutlinedTextField(
                                value = selectedResult?.album.orEmpty(),
                                onValueChange = { },
                                label = { Text("Album") },
                                readOnly = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            onSongEdited(song.copy(album = selectedResult?.album.orEmpty()))
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Use Spotify album",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            )

                            // Artist - Using OutlinedTextField for consistent height with Song column
                            OutlinedTextField(
                                value = selectedResult?.artist.orEmpty(),
                                onValueChange = { },
                                label = { Text("Artist") },
                                readOnly = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            onSongEdited(song.copy(artist = selectedResult?.artist.orEmpty()))
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Use Spotify artist",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            )

                            // Artwork
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                tonalElevation = 1.dp
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = "Artwork",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Box(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(72.dp)
                                        ) {
                                            AsyncImage(
                                                model = selectedResult?.artworkUrl,
                                                contentDescription = "Spotify Artwork",
                                                modifier = Modifier.fillMaxSize(),
                                                placeholder = painterResource(Res.drawable.icon),
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            // Download the artwork from Spotify and update the song
                                            selectedResult?.artworkUrl?.let { artworkUrl ->
                                                scope.launch {
                                                    try {
                                                        // Use HttpClient to download the image
                                                        val imageBytes = HttpClient().use { client ->
                                                            client.get(artworkUrl)
                                                        }.bodyAsBytes()
                                                        // Update the UI
                                                        onSongEdited(song.copy(artwork = imageBytes))
                                                        songArtwork = imageBytes
                                                    } catch (e: Exception) {
                                                        Logger.e(e) { "Failed to download artwork." }
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Use Spotify artwork",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
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
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Song Data",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        // Title
                        OutlinedTextField(
                            value = song.title,
                            onValueChange = { value ->
                                onSongEdited(song.copy(title = value))
                            },
                            label = { Text("Title") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                focusedLabelColor = MaterialTheme.colorScheme.secondary,
                                cursorColor = MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )

                        // Album
                        OutlinedTextField(
                            value = song.album ?: "Unknown Album",
                            onValueChange = { value ->
                                onSongEdited(song.copy(album = value))
                            },
                            label = { Text("Album") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                focusedLabelColor = MaterialTheme.colorScheme.secondary,
                                cursorColor = MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )

                        // Artist
                        OutlinedTextField(
                            value = song.artist,
                            onValueChange = { value ->
                                onSongEdited(song.copy(artist = value))
                            },
                            label = { Text("Artist") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                focusedLabelColor = MaterialTheme.colorScheme.secondary,
                                cursorColor = MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )

                        // Artwork
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = "Artwork",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clickable { imagePicker.launch() }
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalPlatformContext.current)
                                            .data(songArtwork)
                                            .build(),
                                        contentDescription = "Artwork",
                                        modifier = Modifier
                                            .size(72.dp),
                                        placeholder = painterResource(Res.drawable.icon),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Navigation buttons
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                FilledTonalButton(
                    onClick = onPreviousSong,
                    enabled = unsortedSongs.indexOfFirst { it.path == song.path } > 0,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous Song",
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Previous")
                }

                // Play button
                FilledTonalButton(
                    onClick = {
                        // Play the current song
                        Player.onSongsChanged(listOf(song))
                        Player.onPlaySongCommand(song)
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Song",
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Play")
                }

                FilledTonalButton(
                    onClick = {
                        onSongEdited(song)
                        onNextSong()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Save & Next")
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next Song",
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

expect fun onSongSave(song: Song): Result<Song>
