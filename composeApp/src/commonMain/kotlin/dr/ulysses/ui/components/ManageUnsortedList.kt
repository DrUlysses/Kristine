package dr.ulysses.ui.components

import androidx.compose.runtime.Composable
import dr.ulysses.entities.Song

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
