package dr.ulysses.ui.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.skydoves.landscapist.coil3.CoilImage
import dr.ulysses.Logger
import dr.ulysses.SUPPORTED_EXTENSIONS
import dr.ulysses.entities.SettingKey
import dr.ulysses.entities.SettingsRepository
import dr.ulysses.entities.Song
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kristine.composeapp.generated.resources.Res
import kristine.composeapp.generated.resources.icon
import org.jetbrains.compose.resources.painterResource

@Serializable
object ManageSong

@Composable
fun ManageUnsortedSong(
    song: Song,
    onSongEdited: (Song) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        val context = LocalPlatformContext.current
        val scope = rememberCoroutineScope()
        var initialDirectory by remember { mutableStateOf(song.path) }
        var updatedImage by remember { mutableStateOf(song.artwork) }
        scope.launch {
            initialDirectory = SettingsRepository.get(SettingKey.SongsPath)?.value.orEmpty()
        }
        val imagePicker = rememberFilePickerLauncher(
            type = PickerType.Image,
            initialDirectory = initialDirectory,
        ) { image ->
            image?.let {
                CoroutineScope(Dispatchers.Main).launch {
                    val bytes = image.readBytes()
                    updatedImage = bytes
                    onSongEdited(song.copy(artwork = bytes))
                }
            } ?: Logger.e {
                "Image not found"
            }
        }
        val songPicker = rememberFilePickerLauncher(
            type = PickerType.File(SUPPORTED_EXTENSIONS),
            initialDirectory = initialDirectory
        ) { songFile ->
            songFile?.path?.let {
                onSongEdited(song.copy(path = it))
            }
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Path: ${song.path}",
                modifier = Modifier.clickable(
                    onClick = { songPicker.launch() }
                )
            )
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = song.title,
                onValueChange = { value ->
                    onSongEdited(song.copy(title = value))
                },
                supportingText = {
                    Text(
                        text = "Title"
                    )
                }
            )
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = song.album ?: "Unknown Album",
                onValueChange = { value ->
                    onSongEdited(song.copy(album = value))
                },
                supportingText = {
                    Text(
                        text = "Album"
                    )
                }
            )
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = song.artist,
                onValueChange = { value ->
                    onSongEdited(song.copy(artist = value))
                },
                supportingText = {
                    Text(
                        text = "Artist"
                    )
                }
            )
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Artwork"
            )
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
                        contentDescription = "Artwork",
                        modifier = Modifier
                            .padding(8.dp)
                            .size(64.dp)
                    )
                },
                failure = {
                    Image(
                        painter = painterResource(Res.drawable.icon),
                        contentDescription = "Artwork",
                        modifier = Modifier
                            .padding(8.dp)
                            .size(64.dp)
                    )
                }
            )
        }
    }
}

expect fun onSongSave(song: Song): Result<Song>
