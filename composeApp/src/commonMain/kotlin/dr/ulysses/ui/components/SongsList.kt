package dr.ulysses.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dr.ulysses.entities.Song
import kotlinx.serialization.Serializable

@Serializable
object SongsList

@Composable
expect fun SongsList(
    modifier: Modifier = Modifier,
    songs: List<Song>,
    onSongsChanged: (List<Song>) -> Unit = {},
    onPlaySongCommand: (Song) -> Unit = {},
    rearrangeable: Boolean = false,
)
