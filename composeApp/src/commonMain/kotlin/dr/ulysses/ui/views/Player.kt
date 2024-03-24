package dr.ulysses.ui.views

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dr.ulysses.entities.Song
import dr.ulysses.ui.components.PlayerButtons

@Composable
fun Player(
    currentSong: Song?,
    isPlaying: Boolean,
    onPreviousCommand: () -> Unit,
    onNextCommand: () -> Unit,
    onPlayOrPauseCommand: () -> Unit
) {
    BottomAppBar {
        Row {
            // TODO: Icon and song info
            Text(
                currentSong?.title ?: "No song selected"
            )
            PlayerButtons(
                isPlaying = isPlaying,
                onPlayOrPauseCommand = onPlayOrPauseCommand,
                onPreviousCommand = onPreviousCommand,
                onNextCommand = onNextCommand
            )
        }
    }
}
