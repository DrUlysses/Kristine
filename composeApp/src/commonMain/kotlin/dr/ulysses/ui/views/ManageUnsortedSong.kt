package dr.ulysses.ui.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.skydoves.landscapist.coil3.CoilImage
import dr.ulysses.SUPPORTED_EXTENSIONS
import dr.ulysses.entities.Song
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kristine.composeapp.generated.resources.Res
import kristine.composeapp.generated.resources.icon
import org.jetbrains.compose.resources.painterResource

@Serializable
data class ManageSong(
    val path: String,
)

@Composable
fun ManageUnsortedSong(
    song: Song,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        var editedSong by remember { mutableStateOf(song) }
        val context = LocalPlatformContext.current
        val scope = rememberCoroutineScope()
        val imagePicker = rememberFilePickerLauncher(
            type = PickerType.Image,
        ) { image ->
            image?.let {
                scope.launch {
                    editedSong = editedSong.copy(artwork = image.readBytes())
                }
            }
        }
        val songPicker = rememberFilePickerLauncher(
            type = PickerType.File(SUPPORTED_EXTENSIONS),
        ) { songFile ->
            songFile?.path?.let {
                scope.launch {
                    editedSong = editedSong.copy(path = it)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Path: ${editedSong.path}",
                modifier = Modifier.clickable(
                    onClick = { songPicker.launch() }
                )
            )
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Title: "
            )
            TextField(
                value = editedSong.title,
                onValueChange = { value ->
                    editedSong = editedSong.copy(title = value)
                }
            )
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Album: "
            )
            TextField(
                value = editedSong.album ?: "Unknown Album",
                onValueChange = { value ->
                    editedSong = editedSong.copy(album = value)
                }
            )
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Artist: "
            )
            TextField(
                value = editedSong.artist,
                onValueChange = { value ->
                    editedSong = editedSong.copy(artist = value)
                }
            )
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Artwork: "
            )
            CoilImage(
                imageRequest = {
                    ImageRequest.Builder(context).data(editedSong.artwork).build()
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
