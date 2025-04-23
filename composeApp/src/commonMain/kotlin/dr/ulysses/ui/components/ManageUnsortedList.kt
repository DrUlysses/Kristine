package dr.ulysses.ui.components

import androidx.compose.runtime.Composable
import dr.ulysses.entities.Song
import kotlinx.serialization.Serializable

@Serializable
object ManageUnsortedGraph

@Serializable
object ManageUnsortedList

@Composable
fun ManageUnsortedList(
    unsortedSongs: List<Song>,
    onClick: (Song) -> Unit = {},
) {
    SongsList(
        songs = unsortedSongs,
        onClick = onClick
    )
}
