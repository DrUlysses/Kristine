package dr.ulysses.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dr.ulysses.entities.Song

@Composable
expect fun SongsList(
    modifier: Modifier = Modifier,
    songs: List<Song>,
    onSongsChanged: (List<Song>) -> Unit = {},
    onClick: (Song) -> Unit = {},
    rearrangeable: Boolean = false,
    /**
     * Shown at the end of each song instead of its download button.
     */
    action: (@Composable (Song) -> Unit)? = null,
)
